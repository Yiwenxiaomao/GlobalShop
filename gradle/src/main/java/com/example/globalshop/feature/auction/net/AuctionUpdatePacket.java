package com.example.globalshop.feature.auction.net;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.feature.auction.AuctionViewers;
import com.example.globalshop.core.client.ClientScreens;
import com.example.globalshop.core.network.ModNetwork;

/**
 * Server to every watching client: this auction's numbers just changed.
 *
 * <p>Carries the new top bid, who holds it, the new end instant <em>and the server's clock at the
 * moment it was sent</em>. The clock is mandatory, not a convenience: a bid inside the closing
 * window moves the end, and a client that applied the new end against its own stale offset would
 * draw a countdown that disagreed with everybody else's. Receiving this packet replaces the
 * client's offset outright, so the next frame is computed from a freshly measured difference.
 *
 * <p>Sent only to players with the window open. Everyone else finds out when they open it.
 */
public class AuctionUpdatePacket {

    private final UUID id;
    private final long topBid;
    private final String topBidderName;
    private final long endTimeMillis;
    private final long serverNowMillis;
    private final int extensions;
    private final boolean ended;

    public AuctionUpdatePacket(UUID id, long topBid, String topBidderName, long endTimeMillis,
                               long serverNowMillis, int extensions, boolean ended) {
        this.id = id;
        this.topBid = topBid;
        this.topBidderName = topBidderName == null ? "" : topBidderName;
        this.endTimeMillis = endTimeMillis;
        this.serverNowMillis = serverNowMillis;
        this.extensions = extensions;
        this.ended = ended;
    }

    public UUID id() {
        return id;
    }

    public long topBid() {
        return topBid;
    }

    public String topBidderName() {
        return topBidderName;
    }

    public long endTimeMillis() {
        return endTimeMillis;
    }

    public long serverNowMillis() {
        return serverNowMillis;
    }

    public int extensions() {
        return extensions;
    }

    public boolean ended() {
        return ended;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeLong(topBid);
        buf.writeUtf(topBidderName, 32);
        buf.writeLong(endTimeMillis);
        buf.writeLong(serverNowMillis);
        buf.writeVarInt(extensions);
        buf.writeBoolean(ended);
    }

    public static AuctionUpdatePacket decode(FriendlyByteBuf buf) {
        return new AuctionUpdatePacket(buf.readUUID(), buf.readLong(), buf.readUtf(32),
                buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(AuctionUpdatePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.core.client.ClientScreens.acceptAuctionUpdate(message);
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * Sends one update to everyone who currently has the window open.
     *
     * <p>"Currently watching" is tracked by the request packet. It is a delivery optimisation, not
     * a permission: a player who is not in the set simply learns the new price when they next open
     * the window, and can still bid in the meantime.
     */
    public static void broadcast(net.minecraft.server.MinecraftServer server,
                                 AuctionUpdatePacket update) {
        for (UUID viewer : com.example.globalshop.feature.auction.AuctionViewers.watching(server)) {
            net.minecraft.server.level.ServerPlayer player =
                    server.getPlayerList().getPlayer(viewer);
            if (player != null) {
                com.example.globalshop.core.network.ModNetwork.sendToPlayer(player, update);
            }
        }
    }
}
