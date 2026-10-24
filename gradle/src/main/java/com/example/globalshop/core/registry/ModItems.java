package com.example.globalshop.core.registry;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.core.registry.ServerShopTerminalItem;
import com.example.globalshop.core.registry.ShopTerminalItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items added by GlobalShop. */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GlobalShop.MODID);

    /**
     * The shop terminal item. It is a {@code BlockItem}, so an ordinary right click on
     * the ground places {@link ModBlocks#SHOP_TERMINAL}.
     */
    public static final RegistryObject<Item> SHOP_TERMINAL = ITEMS.register("shop_terminal",
            () -> new ShopTerminalItem(ModBlocks.SHOP_TERMINAL.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    /**
     * The server shop terminal item.
     *
     * <p>A distinct item from {@link #SHOP_TERMINAL}: the two blocks are not interchangeable, so
     * neither are the items that place them. Rarer than the market terminal to signal that it is
     * bought from the server rather than obtained freely.
     *
     * <p>Not craftable. The only source is the server shop's own catalogue, which is what makes
     * its per-player purchase limit mean anything.
     */
    public static final RegistryObject<Item> SERVER_SHOP_TERMINAL = ITEMS.register("server_shop_terminal",
            () -> new ServerShopTerminalItem(ModBlocks.SERVER_SHOP_TERMINAL.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
