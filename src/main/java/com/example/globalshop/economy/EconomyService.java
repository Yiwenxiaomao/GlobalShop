package com.example.globalshop.economy;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * The economy contract used by the rest of GlobalShop.
 *
 * <p>Everything else in the mod -- the shop, the commands, the GUI -- talks to this
 * interface only, and obtains it from {@link EconomyServices}. To swap the bundled
 * economy for something else (a different storage backend, a hook onto another
 * mod's currency) implement this interface and call
 * {@link EconomyServices#install(EconomyServices.EconomyServiceFactory)}. No other
 * file needs to change.
 *
 * <h2>Identity</h2>
 * Accounts are keyed by {@link UUID}, never by player name, so renaming an account
 * cannot lose a balance and players who are offline keep their money. The
 * {@link ServerPlayer} overloads are conveniences for the common online case.
 *
 * <h2>Atomicity</h2>
 * Implementations must treat every mutating call as a single atomic transaction:
 * either the whole change is applied, or nothing is. In particular a transfer must
 * never debit one account without crediting the other, and no sequence of calls
 * may create money, destroy money, or drive a balance negative. Amounts are always
 * positive; the direction is expressed by which method you call.
 */
public interface EconomyService {

    // ------------------------------------------------------------------
    // Identity based API (the authoritative one)
    // ------------------------------------------------------------------

    /** @return the balance in minor units, or {@code 0} if the account does not exist. */
    long getBalance(UUID playerId);

    /** @return whether an account has been created for this id. */
    boolean hasAccount(UUID playerId);

    /** @return the last seen name for this id, if one was ever recorded. */
    Optional<String> getLastKnownName(UUID playerId);

    /**
     * Every account's balance, for the ranking.
     *
     * <p>Empty by default. An implementation that cannot enumerate its accounts simply offers no
     * ranking, which is a better contract than making the interface impossible to implement for
     * anyone whose balances live somewhere that cannot be listed.
     */
    default java.util.Map<UUID, Long> allBalances() {
        return java.util.Map.of();
    }

    /**
     * Creates the account if it does not exist yet, recording the last known name.
     * Never changes the balance of an account that already exists, so calling it on
     * every login cannot hand out the starting balance twice.
     *
     * @param initialBalance balance to create the account with, in minor units
     */
    TransactionResult ensureAccount(UUID playerId, String lastKnownName, long initialBalance);

    /** Sets an absolute balance. Used by the OP {@code /money set} command. */
    TransactionResult setBalance(UUID playerId, long amount);

    /** Adds money. Used by the OP {@code /money give} command and by shop payouts. */
    TransactionResult addBalance(UUID playerId, long amount);

    /** Removes money, refusing to go below zero. */
    TransactionResult removeBalance(UUID playerId, long amount);

    /**
     * Moves money from one account to another in a single atomic step. Either both
     * accounts change or neither does.
     */
    TransactionResult transfer(UUID fromId, UUID toId, long amount);

    /** @return whether the account holds at least {@code amount}. */
    default boolean hasEnough(UUID playerId, long amount) {
        return amount >= 0 && getBalance(playerId) >= amount;
    }

    // ------------------------------------------------------------------
    // Convenience overloads for online players
    // ------------------------------------------------------------------

    default long getBalance(ServerPlayer player) {
        return getBalance(player.getUUID());
    }

    default boolean hasAccount(ServerPlayer player) {
        return hasAccount(player.getUUID());
    }

    default boolean hasEnough(ServerPlayer player, long amount) {
        return hasEnough(player.getUUID(), amount);
    }

    default TransactionResult ensureAccount(ServerPlayer player, long initialBalance) {
        return ensureAccount(player.getUUID(), player.getGameProfile().getName(), initialBalance);
    }

    default TransactionResult setBalance(ServerPlayer player, long amount) {
        return setBalance(player.getUUID(), amount);
    }

    default TransactionResult addBalance(ServerPlayer player, long amount) {
        return addBalance(player.getUUID(), amount);
    }

    default TransactionResult removeBalance(ServerPlayer player, long amount) {
        return removeBalance(player.getUUID(), amount);
    }

    default TransactionResult transfer(ServerPlayer from, ServerPlayer to, long amount) {
        return transfer(from.getUUID(), to.getUUID(), amount);
    }
}
