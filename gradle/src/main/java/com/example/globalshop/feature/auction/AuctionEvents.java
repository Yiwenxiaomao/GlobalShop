package com.example.globalshop.feature.auction;

import com.example.globalshop.GlobalShop;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Drives the auction house: settles finished auctions, and cleans up per-server state.
 *
 * <p>Registered by annotation rather than by editing the existing event class. 1.0.0 is submitted
 * for review, and its event handler is part of what is being looked at; a new subscriber class
 * keeps the whole feature additive and lets the auction be reasoned about on its own.
 *
 * <p>The sweep runs once a second. That is the resolution the countdown is displayed at, so a
 * hammer falling "late" by up to a second is invisible; and it is cheap, because it iterates a map
 * of live auctions and usually does nothing at all.
 */
@Mod.EventBusSubscriber(modid = "globalshop")
public final class AuctionEvents {

    private static final int SWEEP_INTERVAL_TICKS = 20;

    private static int tickCounter;

    private AuctionEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < SWEEP_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        try {
            AuctionServices.get(server).settleDue();
        } catch (RuntimeException failure) {
            // A settlement that throws must not take the server tick loop with it. The auction
            // stays in the save with its half-finished flags, so the next sweep picks it up again.
            GlobalShop.LOGGER.error("globalshop: auction settlement sweep failed", failure);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        AuctionServices.release(event.getServer());
    }
}
