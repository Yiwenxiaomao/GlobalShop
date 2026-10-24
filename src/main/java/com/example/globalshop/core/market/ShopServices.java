package com.example.globalshop.core.market;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.example.globalshop.feature.ranking.RankingService;

/**
 * Per-server {@link ShopService} instances.
 *
 * <p>A single instance per server matters: the service keeps the anti-flood
 * throttle state, and the whole point is that all requests from all players are
 * serialised through one object.
 */
public final class ShopServices {

    private static final Map<MinecraftServer, ShopService> INSTANCES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, RankingService> RANKINGS = new IdentityHashMap<>();

    private ShopServices() {
    }

    public static synchronized ShopService get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return INSTANCES.computeIfAbsent(server, ShopService::new);
    }

    /**
     * The ranking cache for this server.
     *
     * <p>One per server, like the shop service, because its whole purpose is to be rebuilt at
     * most once every five minutes rather than once per player who opens the window.
     */
    public static synchronized RankingService ranking(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return RANKINGS.computeIfAbsent(server, RankingService::new);
    }

    public static RankingService ranking(ServerPlayer player) {
        return ranking(Objects.requireNonNull(player.getServer(), "player has no server"));
    }

    public static ShopService get(ServerPlayer player) {
        return get(Objects.requireNonNull(player.getServer(), "player has no server"));
    }

    public static ShopService get(CommandSourceStack source) {
        return get(Objects.requireNonNull(source.getServer(), "command source has no server"));
    }

    public static synchronized void release(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
            RANKINGS.remove(server);
        }
    }
}
