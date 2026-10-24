package com.example.globalshop.command;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.TeleportActionPacket;
import com.example.globalshop.network.packet.TeleportDataPacket;
import com.example.globalshop.teleport.TeleportResult;
import com.example.globalshop.teleport.TeleportServices;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /globalshop tp} branch, and the reason it exists.
 *
 * <h2>Why a command is the right shape for a chat button</h2>
 * A teleport request arrives as a chat line with two clickable answers. Those clicks run
 * {@code /globalshop tp accept} and {@code /globalshop tp deny} — the same commands a player could
 * type. Making the button a real command means the two ways of answering cannot drift apart, the
 * permission check is the ordinary one, and nothing has to special-case a click that arrived from
 * chat rather than from a keyboard.
 *
 * <p>Opening the window is a command too, so the button in the shop and the command share one path.
 */
public final class TeleportCommand {

    private TeleportCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tp")
                .executes(context -> open(context))
                .then(Commands.literal("open").executes(context -> open(context)))
                .then(Commands.literal("accept").executes(context -> accept(context)))
                .then(Commands.literal("deny").executes(context -> deny(context)))
                .then(Commands.literal("cancel").executes(context -> cancel(context)))
                .then(Commands.literal("random").executes(context -> random(context)));
    }

    /** Opens the window: the reply carries everything the screen needs to draw itself. */
    private static int open(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ModNetwork.sendToPlayer(player,
                TeleportDataPacket.build(TeleportServices.get(player), player));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        reply(player, TeleportServices.get(player).accept(player),
                "globalshop.teleport.done_accept");
        refresh(player);
        return 1;
    }

    private static int deny(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        reply(player, TeleportServices.get(player).deny(player), "globalshop.teleport.done_deny");
        refresh(player);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        reply(player, TeleportServices.get(player).cancel(player),
                "globalshop.teleport.done_cancel");
        refresh(player);
        return 1;
    }

    private static int random(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        reply(player, TeleportServices.get(player).random(player), "globalshop.teleport.done_random");
        refresh(player);
        return 1;
    }

    private static void reply(ServerPlayer player, TeleportResult result, String successKey) {
        player.sendSystemMessage(Component.translatable(result.isSuccess()
                ? successKey
                : result.translationKey()));
    }

    /** Pushes the new state so an open window follows the answer without being reopened. */
    private static void refresh(ServerPlayer player) {
        ModNetwork.sendToPlayer(player,
                TeleportDataPacket.build(TeleportServices.get(player), player));
    }

    /** @return the player who ran the command, or {@code null} if it was the console */
    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("globalshop.player_only"));
        }
        return player;
    }
}
