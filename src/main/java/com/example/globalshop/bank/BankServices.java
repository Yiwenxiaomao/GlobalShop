package com.example.globalshop.bank;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One bank service per running server, the same pattern as every other feature here.
 */
public final class BankServices {

    private static final Map<MinecraftServer, BankService> SERVICES = new WeakHashMap<>();

    private BankServices() {
    }

    public static synchronized BankService get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, BankService::new);
    }

    public static BankService get(ServerPlayer player) {
        return get(player.getServer());
    }

    public static synchronized void release(MinecraftServer server) {
        SERVICES.remove(server);
    }
}
