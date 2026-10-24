package com.example.globalshop.feature.title;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * One title service per running server.
 *
 * <p>Same pattern as the shop, the social side and the auction house: an {@code IdentityHashMap}
 * keyed on the server instance, so a client that reconnects to a different world cannot be handed
 * the previous world's titles, and nothing is static in a way that survives a restart.
 */
public final class TitleServices {

    private static final Map<MinecraftServer, TitleService> SERVICES = new WeakHashMap<>();

    private TitleServices() {
    }

    public static synchronized TitleService get(MinecraftServer server) {
        MinecraftServer key = server == null ? ServerLifecycleHooks.getCurrentServer() : server;
        return SERVICES.computeIfAbsent(key, TitleService::new);
    }

    public static TitleService get(ServerPlayer player) {
        return get(player.getServer());
    }

    /** Drops a server's service when it stops, so a new world starts clean. */
    public static synchronized void release(MinecraftServer server) {
        SERVICES.remove(server);
    }
}
