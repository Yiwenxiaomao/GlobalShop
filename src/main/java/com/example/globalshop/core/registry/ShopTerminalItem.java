package com.example.globalshop.core.registry;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The shop terminal as an item.
 *
 * <p>Extending {@link BlockItem} gives the required behaviour for free: an ordinary
 * right click on the ground places the terminal block, with the usual placement
 * rules. Only a tooltip is added.
 */
public class ShopTerminalItem extends BlockItem {

    public ShopTerminalItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                               TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("block.globalshop.shop_terminal.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.globalshop.shop_terminal.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
