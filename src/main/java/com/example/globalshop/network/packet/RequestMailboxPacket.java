package com.example.globalshop.network.packet;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "open my mailbox".
 *
 * <p>Carries nothing at all: the answer is always the sender's own mail, so there is no argument a
 * modified client could use to ask for anyone else's.
 */
public class RequestMailboxPacket {

    public RequestMailboxPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
        // Nothing to send.
    }

    public static RequestMailboxPacket decode(FriendlyByteBuf buf) {
        return new RequestMailboxPacket();
    }

    public static void handle(RequestMailboxPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ModNetwork.sendToPlayer(sender,
                    MailboxDataPacket.build(ShopServices.get(sender), sender.getUUID()));
        });
        context.setPacketHandled(true);
    }
}
