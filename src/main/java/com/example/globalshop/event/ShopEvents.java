package com.example.globalshop.event;

import com.example.globalshop.Config;
import com.example.globalshop.GlobalShop;
import com.example.globalshop.command.MoneyCommand;
import com.example.globalshop.command.ServerShopCommand;
import com.example.globalshop.command.ShopCommand;
import com.example.globalshop.economy.EconomyServices;
import com.example.globalshop.servershop.ServerShopService;
import com.example.globalshop.servershop.ServerShopServices;
import com.example.globalshop.shop.ShopService;
import com.example.globalshop.shop.ShopServices;
import com.example.globalshop.test.SelfTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server-side wiring: commands, first-login account creation, mailbox delivery and
 * lifecycle cleanup.
 *
 * <p>Everything here is server-only. Registered on the Forge event bus, which only
 * fires server events for these types, so a client never runs this code in a way
 * that could touch authoritative data.
 */
public final class ShopEvents {

    /** Set {@code -Dglobalshop.selftest=true} to run the built-in checks and stop the server. */
    public static final String SELFTEST_PROPERTY = "globalshop.selftest";

    private ShopEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MoneyCommand.register(event.getDispatcher());
        // The build context is Forge's registry view; the item argument type needs it to
        // resolve what an operator types, so it has to reach the server shop's subtree too.
        ShopCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    /**
     * Creates the account on first sight and hands over anything owed.
     *
     * <p>{@code ensureAccount} never re-applies the starting balance to an account
     * that already exists, so logging in repeatedly cannot farm money.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        EconomyServices.get(server).ensureAccount(
                player.getUUID(), player.getGameProfile().getName(), Config.initialBalance());

        int delivered = ShopServices.get(server).deliverMail(player);
        if (delivered > 0) {
            player.sendSystemMessage(Component.translatable("globalshop.command.claimed", delivered));
        }

        // What sold while they were away, with the amount after the shop's commission. This is
        // the sign-in half of the notification: an online seller is told the moment a sale
        // happens, and everyone else is told here.
        ShopServices.get(server).deliverNotices(player);

        // Private messages that arrived while they were away. The notice names where to read them:
        // a message the player cannot find is the same as one that was never delivered. Announced
        // once per message, so logging in five times does not mean being told five times.
        com.example.globalshop.social.SocialService social =
                com.example.globalshop.social.SocialServices.get(server);
        social.announceOnLogin(player);

        // An offer only exists while its window is open, and closing the window hands the item back.
        // If the server stopped before that could happen, this is where it finds its way home.
        social.withdrawAll(player);

        // VIP is bought by the period. A member whose time ran out while they were away is charged
        // again here if they asked for it — or told why not, if they can no longer afford it.
        ShopServices.get(server).renewDueVips();
    }

    /** How often the auto-renew sweep runs, in ticks. Five minutes. */
    private static final int RENEW_CHECK_TICKS = 20 * 60 * 5;

    private static int renewTickCounter;

    /**
     * Sweeps for lapsed auto-renewing memberships while the server runs.
     *
     * <p>Without this, a member who stayed logged in across their lapse would quietly drop to the
     * ordinary listing limit and only find out by counting their lots.
     */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) {
            return;
        }
        if (++renewTickCounter < RENEW_CHECK_TICKS) {
            return;
        }
        renewTickCounter = 0;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ShopServices.get(server).renewDueVips();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server != null) {
            // Drop the anti-flood throttle state for a player who is gone.
            ShopServices.get(server).forget(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();

        // Applies the server shop's time settings and stocks the built-in lines on a fresh
        // save. Done before the self test so the test sees the same state a real server would.
        ServerShopService serverShop = ServerShopServices.get(server);
        serverShop.onServerStarted();
        GlobalShop.LOGGER.info("globalshop: server shop loaded with {} product(s), daily recycle limit {}",
                serverShop.size(), serverShop.dailyLimit());

        if (Boolean.getBoolean(SELFTEST_PROPERTY)) {
            GlobalShop.LOGGER.info("globalshop: running self test because -D{}=true", SELFTEST_PROPERTY);
            CommandSourceStack source = server.createCommandSourceStack();
            int failures = SelfTest.run(source);
            GlobalShop.LOGGER.info("globalshop: self test finished with {} failure(s); stopping server", failures);
            server.halt(false);
        } else {
            ShopService.MarketSummary summary = ShopServices.get(server).summary();
            GlobalShop.LOGGER.info("globalshop: market loaded with {} listing(s) and {} log entr(ies)",
                    summary.listings(), summary.logEntries());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        // Drop the per-server caches so nothing survives into the next world load.
        ShopServices.release(server);
        ServerShopServices.release(server);
        EconomyServices.release(server);
    }
}
