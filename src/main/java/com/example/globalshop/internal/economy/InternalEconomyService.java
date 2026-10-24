package com.example.globalshop.internal.economy;

import com.example.globalshop.Config;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.MinecraftServer;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.api.economy.TransactionResult;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;

/**
 * The economy implementation bundled with GlobalShop.
 *
 * <p>It keeps every balance in {@link EconomySavedData}, i.e. in the server's world
 * save, and performs all validation and mutation inside a single
 * {@code synchronized} block on that instance.
 *
 * <h2>Why this cannot lose or duplicate money</h2>
 * <ul>
 *   <li>Amounts must be strictly positive, so a "negative give" cannot become a
 *       hidden withdrawal.</li>
 *   <li>Every mutation validates <em>before</em> touching the maps, so a rejected
 *       operation leaves no partial change behind.</li>
 *   <li>A transfer checks both the debit and the credit, including overflow and the
 *       balance cap, before applying either. There is no window in which money
 *       exists in both accounts or in neither.</li>
 *   <li>Arithmetic uses {@link Math#addExact} / {@link Math#multiplyExact}, so an
 *       amount that would wrap a {@code long} is rejected instead of silently
 *       turning negative or small.</li>
 *   <li>The whole check-and-apply sequence is inside one monitor, so two concurrent
 *       callers (for example two requests arriving on different threads) are
 *       serialised rather than interleaved.</li>
 * </ul>
 *
 * <p>In normal Forge operation every call already arrives on the server thread.
 * The locking is deliberate defence in depth: it costs nothing measurable and it
 * removes a whole class of exploit if any future caller touches the economy from
 * an async context.
 */
public final class InternalEconomyService implements EconomyService {

    private final MinecraftServer server;
    private final LongSupplier maxBalance;

    public InternalEconomyService(MinecraftServer server) {
        this(server, Config::maxBalance);
    }

    public InternalEconomyService(MinecraftServer server, LongSupplier maxBalance) {
        this.server = Objects.requireNonNull(server, "server");
        this.maxBalance = Objects.requireNonNull(maxBalance, "maxBalance");
    }

    private EconomySavedData data() {
        return EconomySavedData.get(server);
    }

    private long cap() {
        return Math.max(0L, maxBalance.getAsLong());
    }

    // ------------------------------------------------------------------

    @Override
    public long getBalance(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        EconomySavedData data = data();
        synchronized (data) {
            return data.balanceOf(playerId);
        }
    }

    @Override
    public boolean hasAccount(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        EconomySavedData data = data();
        synchronized (data) {
            return data.accountExists(playerId);
        }
    }

    @Override
    public Optional<String> getLastKnownName(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        EconomySavedData data = data();
        synchronized (data) {
            return data.nameOf(playerId);
        }
    }

    @Override
    public TransactionResult ensureAccount(UUID playerId, String lastKnownName, long initialBalance) {
        Objects.requireNonNull(playerId, "playerId");
        EconomySavedData data = data();
        long createdBalance = 0L;
        synchronized (data) {
            boolean nameChanged = false;
            if (lastKnownName != null && !lastKnownName.isBlank()
                    && !lastKnownName.equals(data.nameOf(playerId).orElse(null))) {
                data.rememberName(playerId, lastKnownName);
                nameChanged = true;
            }
            if (data.accountExists(playerId)) {
                // Never re-apply the starting balance: logging in repeatedly must not
                // be a money printer.
                if (nameChanged) {
                    data.setDirty();
                }
                return TransactionResult.SUCCESS;
            }
            long start = Math.min(Math.max(0L, initialBalance), cap());
            data.putBalance(playerId, start);
            data.setDirty();
            announce(playerId);
            createdBalance = start;
        }
        // Past the lock before telling anybody. The early return above is the account already
        // existing, which is not a balance change and wants no event.
        fireBalanceChanged(playerId, 0L, createdBalance, "account");
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult setBalance(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount < 0L) {
            return TransactionResult.INVALID_AMOUNT;
        }
        if (amount > cap()) {
            return TransactionResult.BALANCE_LIMIT_EXCEEDED;
        }
        EconomySavedData data = data();
        long before;
        synchronized (data) {
            before = data.balanceOf(playerId);
            data.putBalance(playerId, amount);
            data.setDirty();
            announce(playerId);
        }
        fireBalanceChanged(playerId, before, amount, "set");
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult addBalance(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount <= 0L) {
            return TransactionResult.INVALID_AMOUNT;
        }
        EconomySavedData data = data();
        long before;
        long after;
        synchronized (data) {
            before = data.balanceOf(playerId);
            long updated;
            try {
                updated = Math.addExact(before, amount);
            } catch (ArithmeticException overflow) {
                return TransactionResult.OVERFLOW;
            }
            if (updated > cap()) {
                return TransactionResult.BALANCE_LIMIT_EXCEEDED;
            }
            after = updated;
            data.putBalance(playerId, after);
            data.setDirty();
            announce(playerId);
        }
        fireBalanceChanged(playerId, before, after, "add");
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult removeBalance(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (amount <= 0L) {
            return TransactionResult.INVALID_AMOUNT;
        }
        EconomySavedData data = data();
        long current;
        synchronized (data) {
            if (!data.accountExists(playerId)) {
                return TransactionResult.NO_ACCOUNT;
            }
            current = data.balanceOf(playerId);
            if (current < amount) {
                // Refuse rather than letting the balance go negative.
                return TransactionResult.INSUFFICIENT_FUNDS;
            }
            data.putBalance(playerId, current - amount);
            data.setDirty();
            announce(playerId);
        }
        fireBalanceChanged(playerId, current, current - amount, "remove");
        return TransactionResult.SUCCESS;
    }

    @Override
    public TransactionResult transfer(UUID fromId, UUID toId, long amount) {
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        if (amount <= 0L) {
            return TransactionResult.INVALID_AMOUNT;
        }
        if (fromId.equals(toId)) {
            return TransactionResult.SAME_ACCOUNT;
        }
        EconomySavedData data = data();
        long fromBalance;
        long credited;
        synchronized (data) {
            if (!data.accountExists(fromId)) {
                return TransactionResult.NO_ACCOUNT;
            }
            fromBalance = data.balanceOf(fromId);
            if (fromBalance < amount) {
                return TransactionResult.INSUFFICIENT_FUNDS;
            }
            try {
                credited = Math.addExact(data.balanceOf(toId), amount);
            } catch (ArithmeticException overflow) {
                return TransactionResult.OVERFLOW;
            }
            if (credited > cap()) {
                return TransactionResult.BALANCE_LIMIT_EXCEEDED;
            }
            // Everything above validated; only now do we mutate, and both sides in
            // the same block so the transfer is all-or-nothing.
            data.putBalance(fromId, fromBalance - amount);
            data.putBalance(toId, credited);
            data.setDirty();
            announce(fromId);
            announce(toId);
        }
        // Both sides, after the lock: one transfer is two balance changes and a listener may care
        // about either end of it.
        fireBalanceChanged(fromId, fromBalance, fromBalance - amount, "transfer");
        fireBalanceChanged(toId, credited - amount, credited, "transfer");
        return TransactionResult.SUCCESS;
    }

    /**
     * Tells a player's client that their balance changed.
     *
     * <p>Here, in the one place every balance change goes through, rather than at each call site.
     * Twice now a feature has taken money and left the window showing the old figure, which reads
     * exactly like the money was never taken — the auction did it, then PvP and the teleport price
     * did it again. A rule that lives in the ledger cannot be forgotten by the next feature, and the
     * bank will get it for free.
     *
     * <p>Safe to call while holding the balance lock: writing a packet is not a data access, and the
     * read below re-enters a lock this thread already holds, which Java's monitors allow.
     */
    private void announce(UUID playerId) {
        net.minecraft.server.level.ServerPlayer player =
                server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            // Nobody to tell. An offline player's balance is written to the save either way, and
            // they will be given the current figure when they next open a window.
            return;
        }
        com.example.globalshop.core.network.ModNetwork.sendToPlayer(player,
                new com.example.globalshop.internal.economy.net.BalanceUpdatePacket(
                        data().balanceOf(playerId)));
    }

    /**
     * Posts the balance event, once the lock is released.
     *
     * <p>Deliberately not inside {@link #announce}: a listener is another mod's code and may call back
     * into GlobalShop. The bank takes its own lock and then the balance lock, and a listener here
     * holding the balance lock while reaching for the bank would be the reverse — the one ordering
     * this project never uses, and the one that deadlocks a server when two threads disagree.
     *
     * <p>Silent when nothing changed, so a caller cannot announce a no-op as a movement.
     */
    private void fireBalanceChanged(UUID playerId, long before, long after, String reason) {
        if (before == after) {
            return;
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.example.globalshop.api.event.BalanceChangedEvent(
                        playerId, before, after, reason));
    }

    /** Number of accounts on file. Used by the diagnostics command. */
    public int accountCount() {
        EconomySavedData data = data();
        synchronized (data) {
            return data.accountCount();
        }
    }

    /** Every balance, copied while holding the lock so the ranking cannot read a half-change. */
    @Override
    public java.util.Map<java.util.UUID, Long> allBalances() {
        EconomySavedData data = data();
        synchronized (data) {
            return data.allBalances();
        }
    }
}
