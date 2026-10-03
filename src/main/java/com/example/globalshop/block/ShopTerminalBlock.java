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
 * The marketplace terminal.
 *
 * <p>Right-clicking it opens the shared market. The open is requested from the
 * server only -- the client half of this method merely reports "the interaction was
 * accepted" so the hand swings; it does not put a screen on the client's own
 * initiative. That keeps the rule "the server decides what a client may see" intact.
 *
 * <p>Sneaking is passed through so that a player can still place blocks against the
 * terminal, which is the usual convention for a block that owns its right-click.
 */
public class ShopTerminalBlock extends Block {

    public ShopTerminalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
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
            // Server-side is where the menu actually opens; this just accepts the click.
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            GlobalShop.openShop(serverPlayer);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
