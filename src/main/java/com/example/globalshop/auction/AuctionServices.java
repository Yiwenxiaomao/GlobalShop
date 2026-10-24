package com.example.globalshop.auction;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-server {@link AuctionService} instances.
 *
 * <p>Mirrors {@code ShopServices} deliberately. One instance per server matters here for the same
 * reason it does there: the settlement sweep must run once, not once per player who has the window
 * open, and a second instance would mean two sweeps racing over the same auctions.
 */
public final class AuctionServices {

    private static final Map<MinecraftServer, AuctionService> INSTANCES = new IdentityHashMap<>();

    private AuctionServices() {
    }

    public static synchronized AuctionService get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return INSTANCES.computeIfAbsent(server, AuctionService::new);
    }

    public static AuctionService get(ServerPlayer player) {
        return get(Objects.requireNonNull(player.getServer(), "player has no server"));
    }

    public static synchronized void release(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }
}
