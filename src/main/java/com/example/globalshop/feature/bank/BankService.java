package com.example.globalshop.feature.bank;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bank: deposits that earn interest, and loans that charge it.
 *
 * <h2>Deposits are safe, loans are not</h2>
 * Money in the bank cannot be spent — that is the point of putting it there. It also cannot be lost:
 * nothing in this mod touches a deposit except the depositor and the interest it earns. A loan is the
 * opposite, and its interest is charged against the game clock rather than the wall clock, so a server
 * that is off overnight does not quietly double what somebody owes.
 *
 * <h2>Two rates, two clocks</h2>
 * A deposit earns {@code bankDepositInterestPercent} of itself per <em>real</em> day. A loan charges
 * {@code bankLoanInterestPercent} of itself per <em>game</em> day — twenty minutes of a running
 * server. Each is measured against the clock it was described in, which is why one accrues from
 * {@code System.currentTimeMillis()} and the other from the overworld's day time.
 *
 * <h2>What happens when a loan cannot be paid</h2>
 * The interest is added to what is owed, and then the wallet is emptied into the debt as far as it
 * goes. A player who cannot pay does not go into negative money — nothing in this mod can do that —
 * but the debt stays and keeps charging, so the balance is taken every game day until it is cleared.
 * Earning something and repaying is the way out, which is the point of lending at all.
 *
 * <h2>Locks</h2>
 * The bank's own lock is taken around each account's read-validate-write, and the economy takes its
 * own inside that. The order is always bank then economy; nothing anywhere goes the other way, so the
 * two cannot deadlock. Where both are involved the economy re-checks what it is asked to do, so two
 * clicks racing each other are still settled correctly.
 */
public class BankService {

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    /** One game day, in ticks. The same 24000 the world uses. */
    public static final long TICKS_PER_GAME_DAY = 24_000L;

    /** One real day, in milliseconds. */
    public static final long MILLIS_PER_REAL_DAY = 86_400_000L;

    private final MinecraftServer server;

    public BankService(MinecraftServer server) {
        this.server = server;
        // The repair is deliberately not here. A tick can reach the bank while the server is still
        // starting, and asking for the world's day time then is a crash; it belongs to the first
        // accrual, which cannot happen before the world exists.
    }

    private BankSavedData data() {
        return BankSavedData.get(server);
    }

    private EconomyService economy() {
        return EconomyServices.get(server);
    }

    /** The overworld's day time, which is what a game-day interest charge is measured in. */
    public long gameTime() {
        return server.overworld().getDayTime();
    }

    public long now() {
        return System.currentTimeMillis();
    }

    public long depositInterestBasisPoints() {
        return Config.bankDepositInterestBasisPoints();
    }

    public long loanInterestBasisPoints() {
        return Config.bankLoanInterestBasisPoints();
    }

    public long loanLimit() {
        return Config.bankLoanLimit();
    }

    /** A player's deposit, zero if they have never used the bank. */
    public long depositOf(UUID playerId) {
        BankSavedData.Account account = data().existing(playerId);
        return account == null ? 0L : account.deposit();
    }

    /** What a player owes, zero if they have never borrowed. */
    public long loanOf(UUID playerId) {
        BankSavedData.Account account = data().existing(playerId);
        return account == null ? 0L : account.loan();
    }

    /** The most this player could borrow right now, given the limit and what they already owe. */
    public long borrowingRoom(UUID playerId) {
        return Math.max(0L, loanLimit() - loanOf(playerId));
    }

    // ==================================================================
    // Interest
    // ==================================================================

    /**
     * Repairs the loaded accounts' timestamps, once per server.
     *
     * <p>Called from the constructor rather than from a lifecycle event, because there is no event
     * that is guaranteed to run before the first balance is looked at, and this must happen before
     * anybody can see a number.
     *
     * <p>An account saved before these fields existed has a stamp of zero, which is 1970: the first
     * accrual would pay out twenty thousand days of interest in one go. Forgiving it is the right
     * mistake to make.
     */
    private void repairOnLoad() {
        BankSavedData data = data();
        synchronized (data) {
            data.repairTimestamps(now(), gameTime());
        }
    }

    /**
     * Whether the loaded accounts have had their timestamps checked.
     *
     * <p>Not done in the constructor: a tick can reach the bank while the server is still starting,
     * and the world does not exist then.
     */
    private boolean timestampsRepaired;

    /**
     * Repairs the loaded accounts' timestamps, the first time the bank is touched.
     *
     * <p>An account saved before these fields existed has a stamp of zero, which is 1970: without
     * this its first accrual would pay out twenty thousand days of interest at once. Forgiving it is
     * the right mistake to make.
     *
     * <p>Silent while the world is missing, and retried on the next call, because that is the only
     * moment it cannot run.
     */
    private void repairIfNeeded(BankSavedData data, long now, long gameTime) {
        if (timestampsRepaired || server.overworld() == null) {
            return;
        }
        timestampsRepaired = true;
        data.repairTimestamps(now, gameTime);
        LOGGER.info("bank: timestamps checked; {} account(s) on file", data.all().size());
    }

    /** Brings one account up to date. Called on every read of a player's numbers. */
    public void accrue(UUID playerId) {
        accrueAt(playerId, now(), gameTime());
    }

    /**
     * Brings an account up to date as if it were a given moment.
     *
     * <p>Separate from {@link #accrue} so the two clocks can be supplied rather than read. That is
     * what makes interest testable at all: the wall clock cannot be moved by a test, and moving the
     * world's day time turned out not to be reliable either — so the arithmetic is handed two
     * numbers instead of being asked to look them up. It is also the honest way to reason about the
     * two rates, which are measured against different clocks on purpose.
     */
    public void accrueAt(UUID playerId, long now, long gameTime) {
        BankSavedData data = data();
        synchronized (data) {
            repairIfNeeded(data, now, gameTime);
            accrueLocked(data, playerId, data.account(playerId, now, gameTime), now, gameTime);
        }
    }

    /**
     * Brings every account up to date.
     *
     * <p>Runs on a timer so interest does not depend on a player being online to look at it. The work
     * is proportional to the number of players who have ever used the bank.
     */
    public void accrueAll() {
        BankSavedData data = data();
        long now = now();
        long gameTime = gameTime();
        synchronized (data) {
            repairIfNeeded(data, now, gameTime);
            for (java.util.Map.Entry<UUID, BankSavedData.Account> entry : data.all().entrySet()) {
                accrueLocked(data, entry.getKey(), entry.getValue(), now, gameTime);
            }
        }
        logOnceThenOnlyWhenPaying(data, now);
    }

    /**
     * Says what the bank sees, once, and afterwards only when it has paid something.
     *
     * <p>The point is the silent case: if a player moves the clock and the deposit does not grow, one
     * line here says whether the bank agreed a whole day had passed, which is the difference between
     * a bug in the arithmetic and a clock the process cannot see.
     */
    private void logOnceThenOnlyWhenPaying(BankSavedData data, long now) {
        if (explained) {
            return;
        }
        explained = true;
        synchronized (data) {
            for (java.util.Map.Entry<UUID, BankSavedData.Account> entry : data.all().entrySet()) {
                BankSavedData.Account account = entry.getValue();
                long days = (now - account.lastDepositAccrual()) / MILLIS_PER_REAL_DAY;
                LOGGER.info("bank: account {} deposit={} owed={} stamp={} now={}"
                                + " -> {} whole real day(s) since the last payment;"
                                + " rate {} basis points",
                        entry.getKey(), account.deposit(), account.loan(),
                        account.lastDepositAccrual(), now, days,
                        depositInterestBasisPoints());
            }
        }
    }

    private boolean explained;

    /**
     * The accrual itself. Caller holds the bank's lock.
     *
     * <p>Each timestamp moves to the last whole day that has passed, not to now, so a part-day carries
     * forward instead of being thrown away — a player who checks their balance every few hours is paid
     * exactly as much as one who never looks.
     */
    private void accrueLocked(BankSavedData data, UUID playerId, BankSavedData.Account account,
                              long now, long gameTime) {
        boolean changed = false;

        long realDays = (now - account.lastDepositAccrual()) / MILLIS_PER_REAL_DAY;
        if (realDays > 0L) {
            long interest = account.deposit() * depositInterestBasisPoints() * realDays
                    / 10_000L;
            if (interest > 0L) {
                account.addDeposit(interest);
            }
            LOGGER.info("bank: paid {} on a deposit of {} for {} whole day(s)"
                            + " (rate {} basis points; stamp {} -> {})",
                    interest, account.deposit() - interest, realDays,
                    depositInterestBasisPoints(), account.lastDepositAccrual(),
                    account.lastDepositAccrual() + realDays * MILLIS_PER_REAL_DAY);
            account.setLastDepositAccrual(account.lastDepositAccrual()
                    + realDays * MILLIS_PER_REAL_DAY);
            changed = true;
        }

        long gameDays = (gameTime - account.lastLoanAccrual()) / TICKS_PER_GAME_DAY;
        if (gameDays > 0L) {
            if (account.loan() > 0L) {
                account.addLoan(account.loan() * loanInterestBasisPoints() * gameDays
                        / 10_000L);
                drainIntoDebt(playerId, account);
            }
            account.setLastLoanAccrual(account.lastLoanAccrual() + gameDays * TICKS_PER_GAME_DAY);
            changed = true;
        }

        if (changed) {
            data.setDirty();
        }
    }

    /**
     * Empties the wallet into the debt, as far as it goes.
     *
     * <p>By UUID rather than by player, so an offline borrower's balance is still taken — which is the
     * whole point of the rule. What cannot be paid stays owed rather than pushing the wallet negative,
     * because nothing else in this mod can leave a player in debt and the bank should not be the first.
     */
    private void drainIntoDebt(UUID playerId, BankSavedData.Account account) {
        long owed = account.loan();
        if (owed <= 0L) {
            return;
        }
        long wallet = economy().getBalance(playerId);
        long paying = Math.min(owed, Math.max(0L, wallet));
        if (paying > 0L && economy().removeBalance(playerId, paying).isSuccess()) {
            account.reduceLoan(paying);
        }
    }

    // ==================================================================
    // Moving money
    // ==================================================================

    /** Moves money from the wallet into the bank. */
    public BankResult deposit(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return BankResult.INVALID_AMOUNT;
        }
        EconomyService economy = economy();
        economy.ensureAccount(player, Config.initialBalance());
        if (!economy.hasEnough(player.getUUID(), amount)) {
            return BankResult.INSUFFICIENT_FUNDS;
        }
        BankSavedData data = data();
        long now = now();
        long gameTime = gameTime();
        synchronized (data) {
            BankSavedData.Account account = data.account(player.getUUID(), now, gameTime);
            accrueLocked(data, player.getUUID(), account, now, gameTime);
            // Checked again inside the lock: the economy refuses to go negative, so a second click
            // racing this one cannot take the wallet below zero.
            if (!economy.removeBalance(player.getUUID(), amount).isSuccess()) {
                return BankResult.INSUFFICIENT_FUNDS;
            }
            account.addDeposit(amount);
            data.setDirty();
            return BankResult.SUCCESS;
        }
    }

    /** Moves money back out of the bank. */
    public BankResult withdraw(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return BankResult.INVALID_AMOUNT;
        }
        BankSavedData data = data();
        long now = now();
        long gameTime = gameTime();
        synchronized (data) {
            BankSavedData.Account account = data.account(player.getUUID(), now, gameTime);
            accrueLocked(data, player.getUUID(), account, now, gameTime);
            if (account.deposit() < amount) {
                return BankResult.INSUFFICIENT_DEPOSIT;
            }
            account.takeDeposit(amount);
            data.setDirty();
            economy().addBalance(player.getUUID(), amount);
            return BankResult.SUCCESS;
        }
    }

    /** Borrows against future earnings, up to the limit. */
    public BankResult borrow(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return BankResult.INVALID_AMOUNT;
        }
        BankSavedData data = data();
        long now = now();
        long gameTime = gameTime();
        synchronized (data) {
            BankSavedData.Account account = data.account(player.getUUID(), now, gameTime);
            accrueLocked(data, player.getUUID(), account, now, gameTime);
            if (amount > Math.max(0L, loanLimit() - account.loan())) {
                return BankResult.LOAN_LIMIT_REACHED;
            }
            account.addLoan(amount);
            data.setDirty();
            economy().addBalance(player.getUUID(), amount);
            return BankResult.SUCCESS;
        }
    }

    /** Pays the debt down from the wallet. */
    public BankResult repay(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return BankResult.INVALID_AMOUNT;
        }
        BankSavedData data = data();
        long now = now();
        long gameTime = gameTime();
        synchronized (data) {
            BankSavedData.Account account = data.account(player.getUUID(), now, gameTime);
            accrueLocked(data, player.getUUID(), account, now, gameTime);
            if (account.loan() <= 0L) {
                return BankResult.NOTHING_OWED;
            }
            long paying = Math.min(amount, account.loan());
            EconomyService economy = economy();
            economy.ensureAccount(player, Config.initialBalance());
            if (!economy.hasEnough(player.getUUID(), paying)) {
                return BankResult.INSUFFICIENT_FUNDS;
            }
            if (!economy.removeBalance(player.getUUID(), paying).isSuccess()) {
                return BankResult.INSUFFICIENT_FUNDS;
            }
            account.reduceLoan(paying);
            data.setDirty();
            return BankResult.SUCCESS;
        }
    }

    /** Puts everything the player is carrying into the bank. */
    public BankResult depositAll(ServerPlayer player) {
        long amount = economy().getBalance(player.getUUID());
        return amount <= 0L ? BankResult.INVALID_AMOUNT : deposit(player, amount);
    }

    /** Takes the whole deposit back out. */
    public BankResult withdrawAll(ServerPlayer player) {
        long amount = depositOf(player.getUUID());
        return amount <= 0L ? BankResult.INVALID_AMOUNT : withdraw(player, amount);
    }

    /**
     * Pays off as much of the debt as the wallet can.
     *
     * <p>As much as it can, not all of it: a debt larger than the wallet is normal — that is what the
     * drain rule leaves behind — and a button that refused to do anything in that case would be
     * useless exactly when it is wanted.
     */
    public BankResult repayAll(ServerPlayer player) {
        long amount = Math.min(loanOf(player.getUUID()), economy().getBalance(player.getUUID()));
        return amount <= 0L ? BankResult.INVALID_AMOUNT : repay(player, amount);
    }

    /**
     * Clears a player's account, deposit and debt together.
     *
     * <p>An operator tool rather than part of the normal flow: money in the bank is meant to stay
     * there, and this is for putting right a mistake.
     */
    public void reset(java.util.UUID playerId) {
        BankSavedData data = data();
        synchronized (data) {
            data.clear(playerId);
        }
    }

    /** Repairs timestamps after loading, for accounts saved before they existed. */
    public void repair() {
        BankSavedData data = data();
        synchronized (data) {
            data.repairTimestamps(now(), gameTime());
        }
    }

    /** Total deposited, for the diagnostics command. */
    public long totalDeposits() {
        BankSavedData data = data();
        synchronized (data) {
            long total = 0L;
            for (BankSavedData.Account account : data.all().values()) {
                total += account.deposit();
            }
            return total;
        }
    }
}
