package com.example.globalshop.network.packet;

import com.example.globalshop.auction.AuctionService;
import com.example.globalshop.auction.AuctionServices;
import com.example.globalshop.auction.AuctionViewers;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "I have the auction window open, show me the list", or "I have closed it".
 *
 * <p>Opens and closes in one packet because they are the same fact — is this player watching —
 * and the server needs the second one to stop broadcasting. The query and the sort are the
 * client's own view settings, echoed back so the reply can be matched to what was asked for
 * rather than to whatever the server assumed.
 */
public class RequestAuctionsPacket {

    private final String query;
    private final int sort;
    private final boolean open;

    public RequestAuctionsPacket(String query, int sort, boolean open) {
        this.query = query == null ? "" : query;
        this.sort = sort;
        this.open = open;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(query, 64);
        buf.writeVarInt(sort);
        buf.writeBoolean(open);
    }

    public static RequestAuctionsPacket decode(FriendlyByteBuf buf) {
        return new RequestAuctionsPacket(buf.readUtf(64), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(RequestAuctionsPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!message.open) {
                // Closing the window is not a reason to send anything back.
                AuctionViewers.close(sender.getServer(), sender.getUUID());
                return;
            }
            AuctionViewers.open(sender.getServer(), sender.getUUID());
            AuctionService auctions = AuctionServices.get(sender);
            // The reply carries the server's clock, which is what the client calibrates against.
            // It is never the other way round: a time from a client is discarded on arrival.
            com.example.globalshop.network.ModNetwork.sendToPlayer(sender,
                    AuctionDataPacket.build(auctions, sender, message.query, message.sort));
        });
        context.setPacketHandled(true);
    }
}
