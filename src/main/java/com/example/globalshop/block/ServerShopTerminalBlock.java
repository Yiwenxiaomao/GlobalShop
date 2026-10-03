package com.example.globalshop.block;

import com.example.globalshop.GlobalShop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The official server shop's terminal.
 *
 * <p>A different block from {@link ShopTerminalBlock} on purpose. The two shops are different
 * features with different rules — one moves items between players, the other mints and destroys
 * them against an operator's price list — so a player must be able to tell at a glance which one
 * they are standing in front of, and neither block's interactions should be able to reach the
 * other's screen.
 *
 * <p>Construction is not implemented: the item is not craftable, so the only way to obtain one
 * is to buy it from the server shop itself, which is what gives its per-player purchase limit
 * something to limit.
 *
 * <p>Sneaking is passed through so a player can still place blocks against the terminal, which is
 * the usual convention for a block that owns its right-click.
 */
public class ServerShopTerminalBlock extends Block {

    public ServerShopTerminalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // The menu is opened server-side; this only accepts the click so the hand swings.
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            GlobalShop.openServerShop(serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
