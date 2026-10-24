package com.example.globalshop.feature.pvp;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;

/**
 * Player against player: who has agreed, who is currently fighting whom, and what losing costs.
 *
 * <h2>Two agreements, not one</h2>
 * A duel exists only when both players have turned PvP on and one of them has named the other. That
 * is what "off by default" has to mean to be worth anything: if picking a target were enough, anybody
 * could be dragged into a fight by somebody else's choice, and the setting would only protect players
 * who were never picked.
 *
 * <p>Both halves are recorded on one pair, so a duel is symmetric — there is no attacker and
 * defender, only two people who have agreed to hit each other, and either may end it.
 *
 * <h2>What losing costs</h2>
 * The loser of a duel pays a fraction of a stake: {@code pvpStake} multiplied by a random percentage
 * between {@code pvpLossMinPercent} and {@code pvpLossMaxPercent}. The default stake of 300 and range
 * of 1 to 20 means a loss somewhere between 3 and 60 — enough to sting without being ruinous, and
 * unpredictable enough that the fight is not a calculation. The money is destroyed, not paid to the
 * winner, so duelling is a sink rather than a way to take somebody's money.
 */
public class PvpService {

    private final MinecraftServer server;
    private final Random random = new Random();

    /** The duel each player is in, if any. Both players in a pair map to each other. */
    private final Map<UUID, UUID> duels = new HashMap<>();

    public PvpService(MinecraftServer server) {
        this.server = server;
    }

    private PvpSavedData data() {
        return PvpSavedData.get(server);
    }

    /** Whether this player has agreed to be fightable. */
    public boolean isWilling(UUID playerId) {
        return data().isWilling(playerId);
    }

    /** Turns it on or off. Turning it off ends any duel, because there is no consent left. */
    public void setWilling(ServerPlayer player, boolean value) {
        PvpSavedData data = data();
        synchronized (data) {
            data.setWilling(player.getUUID(), value);
        }
        if (!value) {
            endDuel(player.getUUID());
        }
    }

    @Nullable
    public UUID opponentOf(UUID playerId) {
        return duels.get(playerId);
    }

    public boolean isDuelling(UUID playerId) {
        return duels.containsKey(playerId);
    }

    /** The stake, and the range the loss is rolled in. */
    public long stake() {
        return Config.pvpStake();
    }

    public int lossMinPercent() {
        return Config.pvpLossMinPercent();
    }

    public int lossMaxPercent() {
        return Config.pvpLossMaxPercent();
    }

    /**
     * How much a loss costs right now, rolled fresh.
     *
     * <p>Rolled at the moment of death rather than fixed when the duel starts, so neither player can
     * know what is at stake before the fight and fight differently because of it.
     */
    public long rollLoss() {
        int min = Math.max(1, Math.min(100, lossMinPercent()));
        int max = Math.max(min, Math.min(100, lossMaxPercent()));
        int percent = min + random.nextInt(max - min + 1);
        return stake() * percent / 100L;
    }

    /** Starts a duel between two willing players. */
    public PvpResult challenge(ServerPlayer from, ServerPlayer target) {
        if (target.getUUID().equals(from.getUUID())) {
            return PvpResult.THAT_IS_YOU;
        }
        if (!isWilling(from.getUUID())) {
            return PvpResult.YOU_DECLINED;
        }
        if (!isWilling(target.getUUID())) {
            return PvpResult.THEY_DECLINED;
        }
        if (!from.level().dimension().equals(target.level().dimension())) {
            return PvpResult.WRONG_DIMENSION;
        }
        if (isDuelling(from.getUUID()) || isDuelling(target.getUUID())) {
            // One duel at a time, so a player cannot be fighting three people and have no idea who
            // just hit them.
            return PvpResult.ALREADY_DUELLING;
        }
        duels.put(from.getUUID(), target.getUUID());
        duels.put(target.getUUID(), from.getUUID());
        return PvpResult.SUCCESS;
    }

    /** Ends a duel, whoever asked. */
    public PvpResult endDuel(UUID playerId) {
        UUID opponent = duels.remove(playerId);
        if (opponent == null) {
            return PvpResult.NO_DUEL;
        }
        duels.remove(opponent);
        return PvpResult.SUCCESS;
    }

    /**
     * Whether one player may damage another.
     *
     * <p>Only ever asked about player against player: everything else in the world is left alone.
     * Two players may hurt each other exactly when they are in a duel together, which means both of
     * them agreed. A server that wants ordinary PvP back can turn the restriction off in the config
     * and this always answers yes.
     */
    public boolean damageAllowed(UUID attackerId, UUID victimId) {
        if (!Config.pvpRequiresDuel()) {
            return true;
        }
        UUID opponent = duels.get(attackerId);
        return opponent != null && opponent.equals(victimId);
    }

    /**
     * Settles a death: the loser pays, and the duel ends.
     *
     * @return the amount charged, or 0 when the player was not in a duel
     */
    public long settleDeath(ServerPlayer loser) {
        UUID opponentId = duels.get(loser.getUUID());
        if (opponentId == null) {
            return 0L;
        }
        long loss = rollLoss();
        long charged = 0L;
        EconomyService economy = EconomyServices.get(server);
        economy.ensureAccount(loser, Config.initialBalance());
        long balance = economy.getBalance(loser.getUUID());
        // Never more than they have: a duel should not be able to leave a player in debt, which
        // nothing else in this mod can do.
        long affordable = Math.min(loss, Math.max(0L, balance));
        if (affordable > 0L) {
            // Paid to the winner rather than destroyed. The two of them agreed to fight over this,
            // so taking the money out of the world would punish both of them for the same fight.
            // A transfer works by account, so a winner who has already logged out is still paid.
            if (economy.transfer(loser.getUUID(), opponentId, affordable).isSuccess()) {
                charged = affordable;
            }
        }
        endDuel(loser.getUUID());

        ServerPlayer winner = server.getPlayerList().getPlayer(opponentId);
        Component message = Component.translatable(charged > 0L
                        ? "globalshop.pvp.lost"
                        : "globalshop.pvp.lost_broke",
                com.example.globalshop.api.economy.MoneyFormat.formatWithName(charged));
        loser.sendSystemMessage(message);
        if (winner != null) {
            winner.sendSystemMessage(Component.translatable("globalshop.pvp.won",
                    loser.getGameProfile().getName(),
                    com.example.globalshop.api.economy.MoneyFormat.formatWithName(charged)));
        }
        // The balance moved, and both of them may be looking at a window that shows one. Without
        // this the money is taken and the figure on screen never changes, which reads as a bug —
        // and did, until this was added.
        com.example.globalshop.internal.economy.net.BalanceUpdatePacket.push(loser);
        com.example.globalshop.internal.economy.net.BalanceUpdatePacket.push(winner);
        return charged;
    }

    /** Drops a player's duel when they leave, so the other side is not left stuck in one. */
    public void forgetPlayer(UUID playerId) {
        endDuel(playerId);
    }

    /** Only for tests and the server-stopping hook. */
    public void clear() {
        duels.clear();
    }
}
