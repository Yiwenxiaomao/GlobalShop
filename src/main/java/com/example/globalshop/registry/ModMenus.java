package com.example.globalshop.registry;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.menu.ServerShopMenu;
import com.example.globalshop.menu.ShopMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Container menus added by GlobalShop. */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, GlobalShop.MODID);

    /**
     * The marketplace menu.
     *
     * <p>Created through {@code IForgeMenuType} so the client can build the same menu
     * when it receives the open-screen packet. No data travels in that packet: the
     * screen asks the server for the first page once it is open, so nothing the
     * client constructs is trusted.
     */
    public static final RegistryObject<MenuType<ShopMenu>> SHOP =
            MENUS.register("shop", () -> IForgeMenuType.create(ShopMenu::new));

    /**
     * The server shop menu.
     *
     * <p>A second menu type rather than a mode of the first: the two screens share no widgets,
     * and the market's menu owns a real slot that this one must not have.
     */
    public static final RegistryObject<MenuType<ServerShopMenu>> SERVER_SHOP =
            MENUS.register("server_shop", () -> IForgeMenuType.create(ServerShopMenu::new));

    /**
     * The private-trade window.
     *
     * <p>Its own menu because it holds the player's real backpack slots: the window exists partly so
     * that items can be moved while talking, and a menu is what makes a slot real.
     */
    public static final RegistryObject<MenuType<com.example.globalshop.menu.TradeMenu>> TRADE =
            MENUS.register("trade",
                    () -> IForgeMenuType.create(com.example.globalshop.menu.TradeMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
