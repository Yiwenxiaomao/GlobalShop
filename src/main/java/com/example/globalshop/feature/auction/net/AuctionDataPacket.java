package com.example.globalshop.feature.auction.net;

import com.example.globalshop.feature.auction.AuctionListing;
import com.example.globalshop.feature.auction.AuctionService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.core.client.ClientScreens;

/**
 * Server to client: the auction list, and the server's clock.
 *
 * <h2>The clock is the point</h2>
 * {@link #serverNowMillis} travels with every list and every bid update. The client stores the
 * difference between it and its own clock once and then renders every countdown from
 * {@code endTimeMillis - (localNow + offset)}. That is the whole of the time-synchronisation
 * design: the server never asks a client what time it is, and a client whose machine clock is
 * wrong — or in another time zone, or set to 1998 — still counts down to the right instant,
 * because only the <em>difference</em> between two clocks is used, and it is re-measured every
 * time a packet arrives.
 */
public class AuctionDataPacket {

    /** One row of the list. Everything the window draws, so it never has to compute a price. */
    public record Row(UUID id, ItemStack item, String sellerName, boolean mine, long startPrice,
                      long minIncrement, long topBid, String topBidderName, boolean hasBid,
                      long buyoutPrice, long endTimeMillis, int extensions) {
    }

    private final long serverNowMillis;
    private final long viewerBalance;
    private final long viewerHeld;
    private final String query;
    private final int sort;
    private final List<Row> rows;

    public AuctionDataPacket(long serverNowMillis, long viewerBalance, long viewerHeld, String query,
                             int sort, List<Row> rows) {
        this.serverNowMillis = serverNowMillis;
        this.viewerBalance = viewerBalance;
        this.viewerHeld = viewerHeld;
        this.query = query == null ? "" : query;
        this.sort = sort;
        this.rows = List.copyOf(rows);
    }

    public long serverNowMillis() {
        return serverNowMillis;
    }

    public long viewerBalance() {
        return viewerBalance;
    }

    public long viewerHeld() {
        return viewerHeld;
    }

    public String query() {
        return query;
    }

    public int sort() {
        return sort;
    }

    public List<Row> rows() {
        return rows;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(serverNowMillis);
        buf.writeLong(viewerBalance);
        buf.writeLong(viewerHeld);
        buf.writeUtf(query, 64);
        buf.writeVarInt(sort);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeUUID(row.id());
            buf.writeItem(row.item());
            buf.writeUtf(row.sellerName(), 32);
            buf.writeBoolean(row.mine());
            buf.writeLong(row.startPrice());
            buf.writeLong(row.minIncrement());
            buf.writeLong(row.topBid());
            buf.writeUtf(row.topBidderName(), 32);
            buf.writeBoolean(row.hasBid());
            buf.writeLong(row.buyoutPrice());
            buf.writeLong(row.endTimeMillis());
            buf.writeVarInt(row.extensions());
        }
    }

    public static AuctionDataPacket decode(FriendlyByteBuf buf) {
        long now = buf.readLong();
        long balance = buf.readLong();
        long held = buf.readLong();
        String query = buf.readUtf(64);
        int sort = buf.readVarInt();
        int size = buf.readVarInt();
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new Row(buf.readUUID(), buf.readItem(), buf.readUtf(32), buf.readBoolean(),
                    buf.readLong(), buf.readLong(), buf.readLong(), buf.readUtf(32), buf.readBoolean(),
                    buf.readLong(), buf.readLong(), buf.readVarInt()));
        }
        return new AuctionDataPacket(now, balance, held, query, sort, rows);
    }

    public static void handle(AuctionDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.core.client.ClientScreens.acceptAuctionList(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the list for one viewer, applying their search and sort. Server side only. */
    public static AuctionDataPacket build(AuctionService auctions, ServerPlayer viewer, String query,
                                          int sort) {
        long now = auctions.serverNow();
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<AuctionListing> open = auctions.active();
        open.removeIf(listing -> !needle.isEmpty()
                && !listing.item().getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                        .contains(needle));
        if (sort == AuctionSort.ENDING_SOONEST) {
            open.sort(java.util.Comparator.comparingLong(AuctionListing::endTimeMillis));
        } else {
            open.sort(java.util.Comparator.comparingLong(AuctionListing::createdAt).reversed());
        }

        List<Row> rows = new ArrayList<>(open.size());
        for (AuctionListing listing : open) {
            rows.add(new Row(listing.id(), listing.item(), listing.sellerName(),
                    listing.sellerId().equals(viewer.getUUID()), listing.startPrice(),
                    listing.minIncrement(), listing.topBid(), listing.topBidderName(),
                    listing.hasBid(), listing.buyoutPrice(), listing.endTimeMillis(),
                    listing.extensions()));
        }
        long balance = com.example.globalshop.api.economy.EconomyServices.get(viewer.getServer())
                .getBalance(viewer.getUUID());
        return new AuctionDataPacket(now, balance, auctions.heldFor(viewer.getUUID()), needle, sort,
                rows);
    }

    /** The two orders the window offers. Kept here so client and server cannot disagree. */
    public static final class AuctionSort {
        public static final int ENDING_SOONEST = 0;
        public static final int NEWEST = 1;

        private AuctionSort() {
        }
    }
}
