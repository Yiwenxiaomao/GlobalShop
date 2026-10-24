package com.example.globalshop.api.economy;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.example.globalshop.internal.economy.InternalEconomyService;

/**
 * Locates the {@link EconomyService} for a server, and is the single seam where the
 * bundled economy can be replaced.
 *
 * <p>By default this hands out {@link InternalEconomyService}. To substitute a
 * different economy -- for example to bridge to another mod's currency, or to store
 * balances somewhere else entirely -- implement {@link EconomyService} and call
 * {@link #install(EconomyServiceFactory)} once during mod setup:
 *
 * <pre>{@code
 * EconomyServices.install(server -> new MyEconomyService(server));
 * }</pre>
 *
 * <p>Nothing else in the mod needs changing: the shop, the GUI and every command
 * obtain the economy through {@link #get(MinecraftServer)}.
 *
 * <p>One instance is cached per running server, so a replacement implementation may
 * hold per-server state. {@link #release(MinecraftServer)} drops the cache when a
 * server stops.
 */
public final class EconomyServices {

    /** Creates the economy for a server. */
    @FunctionalInterface
    public interface EconomyServiceFactory {
        EconomyService create(MinecraftServer server);
    }

    private static final EconomyServiceFactory DEFAULT = InternalEconomyService::new;

    private static volatile EconomyServiceFactory factory = DEFAULT;

    private static final Map<MinecraftServer, EconomyService> INSTANCES = new IdentityHashMap<>();

    private EconomyServices() {
    }

    /**
     * Replaces the economy implementation. Passing {@code null} restores the
     * bundled one. Cached instances are discarded.
     */
    public static synchronized void install(EconomyServiceFactory newFactory) {
        factory = (newFactory == null) ? DEFAULT : newFactory;
        INSTANCES.clear();
    }

    /** @return whether the bundled economy is currently in use. */
    public static boolean isBundled() {
        return factory == DEFAULT;
    }

    /** @return the economy for this server, creating it on first request. */
    public static synchronized EconomyService get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        EconomyService existing = INSTANCES.get(server);
        if (existing != null) {
            return existing;
        }
        EconomyService created = factory.create(server);
        if (created == null) {
            throw new IllegalStateException("The installed economy factory returned null for " + server);
        }
        INSTANCES.put(server, created);
        return created;
    }

    public static EconomyService get(ServerPlayer player) {
        return get(Objects.requireNonNull(player.getServer(), "player has no server"));
    }

    public static EconomyService get(CommandSourceStack source) {
        return get(Objects.requireNonNull(source.getServer(), "command source has no server"));
    }

    /** Forgets the cached instance for a server that is shutting down. */
    public static synchronized void release(MinecraftServer server) {
        if (server != null) {
            INSTANCES.remove(server);
        }
    }
}
