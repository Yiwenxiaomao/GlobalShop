package com.example.globalshop.teleport;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One teleport service per running server, the same pattern as the shop, auction, titles and
 * check-in. Its state is deliberately in memory only — a pending teleport request means nothing
 * after a restart.
 */
public final class TeleportServices {

    private static final Map<MinecraftServer, TeleportService> SERVICES = new WeakHashMap<>();

    private TeleportServices() {
    }

    public static synchronized TeleportService get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, TeleportService::new);
    }

    public static TeleportService get(ServerPlayer player) {
        return get(player.getServer());
    }

    public static synchronized void release(MinecraftServer server) {
        SERVICES.remove(server);
    }
}
