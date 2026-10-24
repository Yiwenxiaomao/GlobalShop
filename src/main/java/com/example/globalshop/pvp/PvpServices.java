package com.example.globalshop.pvp;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One PvP service per running server, the same pattern as every other feature here: an
 * {@code IdentityHashMap} keyed on the server, so a client that reconnects to another world cannot be
 * answered from the previous one's duels.
 */
public final class PvpServices {

    private static final Map<MinecraftServer, PvpService> SERVICES = new WeakHashMap<>();

    private PvpServices() {
    }

    public static synchronized PvpService get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, PvpService::new);
    }

    public static PvpService get(ServerPlayer player) {
        return get(player.getServer());
    }

    public static synchronized void release(MinecraftServer server) {
        SERVICES.remove(server);
    }
}
