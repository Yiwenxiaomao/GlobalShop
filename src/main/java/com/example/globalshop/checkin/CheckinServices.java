package com.example.globalshop.checkin;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One check-in service per running server, the same pattern as the shop, the auction house and the
 * titles: an {@code IdentityHashMap} keyed on the server, so a client that reconnects to a different
 * world cannot be answered from the previous world's state.
 */
public final class CheckinServices {

    private static final Map<MinecraftServer, CheckinService> SERVICES = new WeakHashMap<>();

    private CheckinServices() {
    }

    public static synchronized CheckinService get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, CheckinService::new);
    }

    public static CheckinService get(ServerPlayer player) {
        return get(player.getServer());
    }

    /** Drops a server's service when it stops. */
    public static synchronized void release(MinecraftServer server) {
        SERVICES.remove(server);
    }
}
