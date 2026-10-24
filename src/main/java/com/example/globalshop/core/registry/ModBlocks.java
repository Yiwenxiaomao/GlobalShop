package com.example.globalshop.core.registry;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.core.registry.ServerShopTerminalBlock;
import com.example.globalshop.core.registry.ShopTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Blocks added by GlobalShop. */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, GlobalShop.MODID);

    /** The marketplace terminal. Placed from {@link ModItems#SHOP_TERMINAL}. */
    public static final RegistryObject<Block> SHOP_TERMINAL =
            BLOCKS.register("shop_terminal", ShopTerminalBlock::new);

    /**
     * The official server shop's terminal.
     *
     * <p>A separate block from {@link #SHOP_TERMINAL}, with its own item and its own screen, so
     * the two shops can never be confused for one another.
     */
    public static final RegistryObject<Block> SERVER_SHOP_TERMINAL =
            BLOCKS.register("server_shop_terminal", ServerShopTerminalBlock::new);

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
