package com.example.globalshop.feature.checkin.net;

import com.example.globalshop.feature.checkin.CheckinResult;
import com.example.globalshop.feature.checkin.CheckinService;
import com.example.globalshop.feature.checkin.CheckinServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;
import com.example.globalshop.core.network.ModNetwork;

/**
 * Client to server: open the check-in panel, or collect a day of it.
 *
 * <p>One packet for both, the way the title window does it. The day is an index into the week, 1 for
 * Monday, and the server decides everything that matters: whether that day is today, whether it is
 * in the past and therefore costs the make-up fee, and whether it has already been collected. A
 * client that sends day 7 on a Monday is simply told the day has not arrived.
 */
public class CheckinActionPacket {

    public static final int OPEN = 0;
    public static final int COLLECT = 1;

    private final int action;
    private final int day;

    public CheckinActionPacket(int action, int day) {
        this.action = action;
        this.day = day;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeVarInt(day);
    }

    public static CheckinActionPacket decode(FriendlyByteBuf buf) {
        return new CheckinActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CheckinActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            CheckinService checkin = CheckinServices.get(sender);
            if (message.action == COLLECT) {
                boolean fee = checkin.costsFee(sender, message.day);
                CheckinResult result = checkin.collect(sender, message.day);
                sender.sendSystemMessage(result.isSuccess()
                        ? Component.translatable("globalshop.checkin.collected",
                                Component.translatable(
                                        "globalshop.checkin.day" + message.day),
                                com.example.globalshop.api.economy.MoneyFormat.formatWithName(
                                        checkin.rewardFor(message.day)),
                                fee ? Component.translatable("globalshop.checkin.with_fee",
                                        com.example.globalshop.api.economy.MoneyFormat.formatWithName(
                                                checkin.makeupCost())) : Component.empty())
                        : Component.translatable(result.translationKey()));
                // The balance moved either way unless nothing happened, and the panel shows it.
                BalanceUpdatePacket.push(sender);
            }
            com.example.globalshop.core.network.ModNetwork.sendToPlayer(sender,
                    CheckinDataPacket.build(checkin, sender));
        });
        context.setPacketHandled(true);
    }
}
