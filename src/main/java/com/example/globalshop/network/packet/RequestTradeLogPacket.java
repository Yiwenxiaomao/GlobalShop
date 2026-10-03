package com.example.globalshop.network.packet;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "show me my own trades".
 *
 * <p>Carries nothing, and the server answers with the sender's own history. There is no way to
 * ask for another player's log, because there is no field to put their id in.
 */
public class RequestTradeLogPacket {

    public RequestTradeLogPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
        // Nothing to send.
    }

    public static RequestTradeLogPacket decode(FriendlyByteBuf buf) {
        return new RequestTradeLogPacket();
    }

    public static void handle(RequestTradeLogPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ModNetwork.sendToPlayer(sender, TradeLogDataPacket.build(
                    ShopServices.get(sender), sender.getUUID()));
        });
        context.setPacketHandled(true);
    }
}
