package com.example.globalshop.feature.auction;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;

/**
 * Who currently has the auction window open.
 *
 * <p>A bid is broadcast to the players looking at the auction house, not to the whole server: they
 * are the only ones for whom the number on screen has just changed. The set is filled by the
 * request packet and emptied when the window closes or the player disconnects — and it is a hint,
 * never a permission: a player who is not in here can still bid, they simply will not receive the
 * live update for it and will see the new price when they next open the window.
 *
 * <p>Concurrent because a bid can be placed while another player's request arrives on a different
 * connection thread.
 */
public final class AuctionViewers {

    private static final Map<MinecraftServer, Set<UUID>> VIEWERS = new IdentityHashMap<>();

    private AuctionViewers() {
    }

    private static synchronized Set<UUID> setFor(MinecraftServer server) {
        return VIEWERS.computeIfAbsent(server, key -> ConcurrentHashMap.newKeySet());
    }

    public static void open(MinecraftServer server, UUID playerId) {
        setFor(server).add(playerId);
    }

    public static void close(MinecraftServer server, UUID playerId) {
        setFor(server).remove(playerId);
    }

    /** Everyone with the window open right now. A copy the caller may iterate freely. */
    public static List<UUID> watching(MinecraftServer server) {
        return new ArrayList<>(setFor(server));
    }

    public static synchronized void release(MinecraftServer server) {
        if (server != null) {
            VIEWERS.remove(server);
        }
    }
}
