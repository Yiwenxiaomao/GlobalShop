package com.example.globalshop.core.servershop;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.example.globalshop.core.market.ShopServices;

/**
 * Per-server {@link ServerShopService} instances.
 *
 * <p>One instance per server, like the market's service and for the same reason: the anti-flood
 * throttle is per-service state, and the point of it is that every request from every player is
 * serialised through one object.
 *
 * <p>Kept separate from {@code ShopServices} rather than folded into it, so that neither shop's
 * lifetime controls the other's: releasing the market does not drop the server shop's throttle
 * state, and vice versa.
 */
public final class ServerShopServices {

    private static final Map<MinecraftServer, ServerShopService> INSTANCES = new IdentityHashMap<>();

    private ServerShopServices() {
    }

    public static synchronized ServerShopService get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return INSTANCES.computeIfAbsent(server, ServerShopService::new);
    }

    public static ServerShopService get(ServerPlayer player) {
        return get(Objects.requireNonNull(player.getServer(), "player has no server"));
    }

    public static ServerShopService get(CommandSourceStack source) {
        return get(Objects.requireNonNull(source.getServer(), "command source has no server"));
    }

    public static synchronized void release(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }
}
