package com.example.globalshop.feature.teleport.net;

import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.feature.teleport.TeleportResult;
import com.example.globalshop.feature.teleport.TeleportService;
import com.example.globalshop.feature.teleport.TeleportServices;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: everything the teleport window can do.
 *
 * <p>One packet for six actions, the way the title and check-in windows do it. The only value that
 * travels is a target's name, and even that is not trusted: the service looks the name up in the
 * server's own player list, so a client cannot ask to be sent to somebody who is not there, and a
 * request is never carried out without the player being asked agreeing.
 */
public class TeleportActionPacket {

    public static final int OPEN = 0;
    public static final int RANDOM = 1;
    public static final int REQUEST = 2;
    public static final int ACCEPT = 3;
    public static final int DENY = 4;
    public static final int CANCEL = 5;

    public static final int MAX_NAME = 16;

    private final int action;
    private final String target;

    public TeleportActionPacket(int action, String target) {
        this.action = action;
        this.target = target == null ? "" : target;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(target, MAX_NAME);
    }

    public static TeleportActionPacket decode(FriendlyByteBuf buf) {
        return new TeleportActionPacket(buf.readVarInt(), buf.readUtf(MAX_NAME));
    }

    public static void handle(TeleportActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            TeleportService teleports = TeleportServices.get(sender);
            switch (message.action) {
                case RANDOM -> report(sender,
                        teleports.random(sender), "globalshop.teleport.done_random");
                case REQUEST -> ask(teleports, sender, message.target);
                case ACCEPT -> report(sender, teleports.accept(sender),
                        "globalshop.teleport.done_accept");
                case DENY -> report(sender, teleports.deny(sender), "globalshop.teleport.done_deny");
                case CANCEL -> report(sender, teleports.cancel(sender),
                        "globalshop.teleport.done_cancel");
                default -> {
                    // OPEN: nothing to do but answer with the current state below.
                }
            }
            ModNetwork.sendToPlayer(sender, TeleportDataPacket.build(teleports, sender));
        });
        context.setPacketHandled(true);
    }

    /**
     * Sends a request and tells the player being asked about it.
     *
     * <p>The chat line carries two clickable buttons. That is a real chat interaction rather than a
     * description of one: the click runs {@code /globalshop tp accept}, which is the same command a
     * player could type, so the buttons and the command cannot drift apart — and the player being
     * asked can answer without opening anything.
     */
    private static void ask(TeleportService teleports, ServerPlayer sender, String targetName) {
        TeleportResult result = teleports.request(sender, targetName);
        if (!result.isSuccess()) {
            sender.sendSystemMessage(Component.translatable(result.translationKey()));
            return;
        }
        sender.sendSystemMessage(Component.translatable("globalshop.teleport.requested",
                targetName, com.example.globalshop.Config.teleportRequestSeconds()));
        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return;
        }
        Style accept = Style.EMPTY.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/globalshop tp accept"));
        Style deny = Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/globalshop tp deny"));
        target.sendSystemMessage(Component.translatable("globalshop.teleport.asked",
                        sender.getGameProfile().getName())
                .append(Component.literal("  "))
                .append(Component.translatable("globalshop.teleport.accept_button").withStyle(accept))
                .append(Component.literal("  "))
                .append(Component.translatable("globalshop.teleport.deny_button").withStyle(deny)));
    }

    /** Reports a result, or a success message when there is one worth showing. */
    private static void report(ServerPlayer sender, TeleportResult result, String successKey) {
        sender.sendSystemMessage(Component.translatable(result.isSuccess()
                ? successKey
                : result.translationKey()));
    }
}
