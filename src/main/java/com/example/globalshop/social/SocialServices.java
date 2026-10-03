package com.example.globalshop.social;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-server {@link SocialService} instances.
 *
 * <p>One per server, for the same reason the shop service is: it holds the message throttle, and the
 * whole point of a throttle is that every request from every player is measured against one clock.
 */
public final class SocialServices {

    private static final Map<MinecraftServer, SocialService> INSTANCES = new IdentityHashMap<>();

    private SocialServices() {
    }

    public static synchronized SocialService get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return INSTANCES.computeIfAbsent(server, SocialService::new);
    }

    public static SocialService get(ServerPlayer player) {
        return get(Objects.requireNonNull(player.getServer(), "player has no server"));
    }

    public static synchronized void release(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }
}
