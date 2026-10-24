package com.example.globalshop.bank;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What every player has in the bank, and what they owe it.
 *
 * <h2>Two clocks, on purpose</h2>
 * A deposit grows by a percentage of a <em>real</em> day, so its timestamp is wall-clock milliseconds.
 * A loan grows by a percentage of a <em>game</em> day, so its timestamp is the overworld's day time in
 * ticks. That is why they are two fields and not one.
 *
 * <p>The difference matters: a loan taken out before a two-week shutdown does not grow while the
 * server is off, because no game day passed. A deposit taken out over the same shutdown does grow,
 * because two weeks really did pass. Each rate is measured against the clock it was described in.
 */
public class BankSavedData extends SavedData {

    private static final String DATA_NAME = "globalshop_bank";

    /** One player's account. */
    public static final class Account {

        private long deposit;
        private long loan;
        /** When the deposit last earned interest, in epoch milliseconds. */
        private long lastDepositAccrual;
        /** When the loan last charged interest, in overworld day ticks. */
        private long lastLoanAccrual;

        public Account(long now, long gameTime) {
            this.lastDepositAccrual = now;
            this.lastLoanAccrual = gameTime;
        }

        public long deposit() {
            return deposit;
        }

        public long loan() {
            return loan;
        }

        public void addDeposit(long amount) {
            deposit += amount;
        }

        public void takeDeposit(long amount) {
            deposit = Math.max(0L, deposit - amount);
        }

        public void addLoan(long amount) {
            loan += amount;
        }

        public void reduceLoan(long amount) {
            loan = Math.max(0L, loan - amount);
        }

        public boolean isEmpty() {
            return deposit == 0L && loan == 0L;
        }

        /** When the deposit last earned interest, in epoch milliseconds. */
        public long lastDepositAccrual() {
            return lastDepositAccrual;
        }

        public void setLastDepositAccrual(long value) {
            this.lastDepositAccrual = value;
        }

        /** When the loan last charged interest, in overworld day ticks. */
        public long lastLoanAccrual() {
            return lastLoanAccrual;
        }

        public void setLastLoanAccrual(long value) {
            this.lastLoanAccrual = value;
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();

    /** The account for a player, created on first use. */
    public Account account(UUID playerId, long now, long gameTime) {
        return accounts.computeIfAbsent(playerId, id -> {
            setDirty();
            return new Account(now, gameTime);
        });
    }

    /**
     * Forgets a player's account entirely.
     *
     * <p>For an operator putting right a mistake — a deposit that should never have been taken, a
     * loan that was recorded against the wrong player — and for tests: the file is saved, so without
     * this a second run of the suite starts with the first run's balances.
     */
    public void clear(UUID playerId) {
        if (accounts.remove(playerId) != null) {
            setDirty();
        }
    }

    /** The account for a player, or {@code null} if they have never used the bank. */
    @Nullable
    public Account existing(UUID playerId) {
        return accounts.get(playerId);
    }

    /** Every account, for the interest sweep. */
    public Map<UUID, Account> all() {
        return accounts;
    }

    public int size() {
        return accounts.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        for (Map.Entry<UUID, Account> entry : accounts.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putLong("deposit", entry.getValue().deposit);
            one.putLong("loan", entry.getValue().loan);
            one.putLong("depositAt", entry.getValue().lastDepositAccrual);
            one.putLong("loanAt", entry.getValue().lastLoanAccrual);
            stored.put(entry.getKey().toString(), one);
        }
        tag.put("accounts", stored);
        return tag;
    }

    public static BankSavedData load(CompoundTag tag) {
        BankSavedData data = new BankSavedData();
        CompoundTag stored = tag.getCompound("accounts");
        for (String key : stored.getAllKeys()) {
            try {
                CompoundTag one = stored.getCompound(key);
                Account account = new Account(one.getLong("depositAt"), one.getLong("loanAt"));
                account.deposit = one.getLong("deposit");
                account.loan = one.getLong("loan");
                data.accounts.put(UUID.fromString(key), account);
            } catch (IllegalArgumentException notAUuid) {
                // One damaged entry is dropped rather than the whole file.
            }
        }
        return data;
    }

    /**
     * Repairs the timestamps of accounts loaded from a file written before they existed.
     *
     * <p>A missing timestamp reads as zero, which is 1970 for a deposit and the first game day for a
     * loan — both of which would pay out years of interest at once. Any account whose stamp is older
     * than the file could plausibly be is reset to now, which forgives the interest rather than
     * inventing it.
     */
    public void repairTimestamps(long now, long gameTime) {
        for (Account account : accounts.values()) {
            if (account.lastDepositAccrual <= 0L || account.lastDepositAccrual > now) {
                account.lastDepositAccrual = now;
                setDirty();
            }
            if (account.lastLoanAccrual <= 0L || account.lastLoanAccrual > gameTime) {
                account.lastLoanAccrual = gameTime;
                setDirty();
            }
        }
    }

    public static BankSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(BankSavedData::load, BankSavedData::new, DATA_NAME);
    }
}
