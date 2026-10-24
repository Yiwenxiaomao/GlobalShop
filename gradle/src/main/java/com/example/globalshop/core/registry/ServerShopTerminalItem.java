package com.example.globalshop.core.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The server shop terminal as an item.
 *
 * <p>A plain {@link BlockItem}: right-clicking the ground places
 * {@link com.example.globalshop.core.registry.ModBlocks#SERVER_SHOP_TERMINAL}, and right-clicking the
 * placed block with an empty hand opens the server shop. The market terminal behaves the same
 * way, so the two are used identically and differ only in which shop they reach.
 *
 * <p>There is deliberately no recipe for it anywhere in the mod's data. The only source is the
 * server shop's own catalogue, which is what makes the per-player purchase limit meaningful —
 * a craftable terminal would sidestep it entirely.
 */
public class ServerShopTerminalItem extends BlockItem {

    public ServerShopTerminalItem(Block block, Item.Properties properties) {
        super(block, properties);
    }
}
