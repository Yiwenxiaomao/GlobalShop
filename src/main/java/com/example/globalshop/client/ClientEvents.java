package com.example.globalshop.client;

import com.example.globalshop.GlobalShop;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side lifecycle wiring.
 *
 * <p>Client-only by construction: {@code value = Dist.CLIENT} keeps this class out of
 * a dedicated server's class loading entirely, so the screen classes it touches are
 * never referenced where they do not exist.
 */
@Mod.EventBusSubscriber(modid = GlobalShop.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    private ClientEvents() {
    }

    /**
     * Throws away the cached market on disconnect.
     *
     * <p>This is what makes "the client cache is not authoritative" enforceable rather
     * than merely intended: after leaving a server there is nothing left to show, so a
     * reconnect cannot display a stale or foreign market, and the first thing the
     * screen does is ask the server for its page. It also stops one server's trade data
     * from ever being visible on another.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientShopCache.reset();
        ClientServerShopCache.reset();
    }
}
