package com.example.globalshop.core.client;

import com.example.globalshop.core.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.example.globalshop.core.market.client.ShopScreen;
import com.example.globalshop.core.servershop.client.ServerShopScreen;
import com.example.globalshop.core.social.client.TradeScreen;

/**
 * Client-only setup.
 *
 * <p>Kept in its own class and referenced only from a {@code Dist.CLIENT} check so
 * that none of it is ever loaded on a dedicated server, where the screen classes do
 * not exist.
 */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.SHOP.get(), ShopScreen::new);
            // Two menu types, two screens. Registering the server shop separately is what keeps
            // the two windows from ever being interchangeable.
            MenuScreens.register(ModMenus.SERVER_SHOP.get(), ServerShopScreen::new);
            MenuScreens.register(ModMenus.TRADE.get(), TradeScreen::new);
        });
    }
}
