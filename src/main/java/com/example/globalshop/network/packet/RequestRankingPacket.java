package com.example.globalshop.network.packet;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "show me the money ranking".
 *
 * <p>Carries nothing at all. There is no argument for whose ranking, or how many rows, because
 * the answer is the same for everyone and the server decides its size — a client cannot ask for
 * a thousand rows or for someone else's view.
 */
public class RequestRankingPacket {

    public RequestRankingPacket() {
    }

    public void encode(FriendlyByteBuf buf) {
        // Nothing to send.
    }

    public static RequestRankingPacket decode(FriendlyByteBuf buf) {
        return new RequestRankingPacket();
    }

    public static void handle(RequestRankingPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            // Deliberately no permission check: every player may see how they compare.
            ModNetwork.sendToPlayer(sender,
                    RankingDataPacket.build(ShopServices.ranking(sender)));
        });
        context.setPacketHandled(true);
    }
}
