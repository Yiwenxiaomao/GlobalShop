package com.example.globalshop.feature.pvp.net;

import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.feature.pvp.PvpResult;
import com.example.globalshop.feature.pvp.PvpService;
import com.example.globalshop.feature.pvp.PvpServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: everything the PvP window can do.
 *
 * <p>One packet for four actions, the way the title, check-in and teleport windows do it. The only
 * value that travels is a target's name, and it is looked up in the server's own player list, so a
 * client cannot start a duel with somebody who is not there — or with somebody who has not agreed,
 * which is the check that matters most here.
 */
public class PvpActionPacket {

    public static final int OPEN = 0;
    public static final int TOGGLE = 1;
    public static final int CHALLENGE = 2;
    public static final int END = 3;

    public static final int MAX_NAME = 16;

    private final int action;
    private final String target;

    public PvpActionPacket(int action, String target) {
        this.action = action;
        this.target = target == null ? "" : target;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(target, MAX_NAME);
    }

    public static PvpActionPacket decode(FriendlyByteBuf buf) {
        return new PvpActionPacket(buf.readVarInt(), buf.readUtf(MAX_NAME));
    }

    public static void handle(PvpActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            PvpService pvp = PvpServices.get(sender);
            switch (message.action) {
                case TOGGLE -> {
                    boolean now = !pvp.isWilling(sender.getUUID());
                    pvp.setWilling(sender, now);
                    sender.sendSystemMessage(Component.translatable(now
                            ? "globalshop.pvp.turned_on"
                            : "globalshop.pvp.turned_off"));
                }
                case CHALLENGE -> challenge(pvp, sender, message.target);
                case END -> {
                    PvpResult result = pvp.endDuel(sender.getUUID());
                    sender.sendSystemMessage(Component.translatable(result.isSuccess()
                            ? "globalshop.pvp.ended"
                            : result.translationKey()));
                }
                default -> {
                    // OPEN: nothing to do but answer with the current state below.
                }
            }
            ModNetwork.sendToPlayer(sender, PvpDataPacket.build(pvp, sender));
        });
        context.setPacketHandled(true);
    }

    /** Starts a duel, telling both sides who they are now fighting. */
    private static void challenge(PvpService pvp, ServerPlayer sender, String targetName) {
        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            sender.sendSystemMessage(Component.translatable(PvpResult.NO_SUCH_PLAYER.translationKey()));
            return;
        }
        PvpResult result = pvp.challenge(sender, target);
        sender.sendSystemMessage(Component.translatable(result.isSuccess()
                ? "globalshop.pvp.started"
                : result.translationKey(), target.getGameProfile().getName()));
        if (!result.isSuccess()) {
            return;
        }
        target.sendSystemMessage(Component.translatable("globalshop.pvp.challenged",
                sender.getGameProfile().getName()));
        // Both windows follow at once, so neither player is looking at a stale answer.
        ModNetwork.sendToPlayer(target, PvpDataPacket.build(pvp, target));
    }
}
