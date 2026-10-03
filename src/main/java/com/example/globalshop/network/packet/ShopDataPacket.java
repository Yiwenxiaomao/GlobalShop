package com.example.globalshop.network.packet;

import com.example.globalshop.client.ClientShopCache;
import com.example.globalshop.economy.EconomyServices;
import com.example.globalshop.network.ListingView;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.ShopViewState;
import com.example.globalshop.shop.Listing;
import com.example.globalshop.shop.ShopService;
import com.example.globalshop.shop.ShopServices;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: one page of the market, plus the viewer's own balance.
 *
 * <p>Every field is produced by the server. The client stores this as a cache for
 * drawing only; it is not a source of truth and is replaced wholesale by the next
 * page, the next action result, or the next reconnect.
 */
public class ShopDataPacket {

    /** Listings per page: the market grid is 6 columns by 6 rows. */
    public static final int PAGE_SIZE = 36;

    private final List<ListingView> listings;
    private final ShopViewState view;
    private final int pageCount;
    private final int totalMatches;
    private final long viewerBalance;
    private final int viewerListingCount;
    /** Whether the viewer owns the VIP rank. Drives the badge beside the balance. */
    private final boolean viewerVip;
    /** Whether the viewer may edit the blacklist. Decides whether the button is offered at all. */
    private final boolean operator;
    /**
     * The blocked item ids, and only ever for an operator who asked for the editor.
     *
     * <p>Empty for everyone else: an operator's blacklist is not market data, and there is no
     * reason to send it to players who cannot see or change it.
     */
    private final List<String> blacklist;

    public ShopDataPacket(List<ListingView> listings, ShopViewState view, int pageCount,
                          int totalMatches, long viewerBalance, int viewerListingCount,
                          boolean viewerVip, boolean operator, List<String> blacklist) {
        this.listings = List.copyOf(listings);
        this.view = view;
        this.pageCount = pageCount;
        this.totalMatches = totalMatches;
        this.viewerBalance = viewerBalance;
        this.viewerListingCount = viewerListingCount;
        this.viewerVip = viewerVip;
        this.operator = operator;
        this.blacklist = List.copyOf(blacklist);
    }

    public List<ListingView> listings() {
        return listings;
    }

    public ShopViewState view() {
        return view;
    }

    public int pageCount() {
        return pageCount;
    }

    public int totalMatches() {
        return totalMatches;
    }

    public long viewerBalance() {
        return viewerBalance;
    }

    public int viewerListingCount() {
        return viewerListingCount;
    }

    public boolean viewerVip() {
        return viewerVip;
    }

    public boolean operator() {
        return operator;
    }

    /** Blocked item ids, empty unless an operator asked for the editor. */
    public List<String> blacklist() {
        return blacklist;
    }

    // ------------------------------------------------------------------
    // Wire
    // ------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeVarInt(pageCount);
        buf.writeVarInt(totalMatches);
        buf.writeLong(viewerBalance);
        buf.writeVarInt(viewerListingCount);
        buf.writeBoolean(viewerVip);
        buf.writeBoolean(operator);
        buf.writeVarInt(blacklist.size());
        for (String blocked : blacklist) {
            buf.writeUtf(blocked, 128);
        }
        buf.writeVarInt(listings.size());
        for (ListingView listing : listings) {
            listing.encode(buf);
        }
    }

    public static ShopDataPacket decode(FriendlyByteBuf buf) {
        ShopViewState view = ShopViewState.decode(buf);
        int pageCount = buf.readVarInt();
        int totalMatches = buf.readVarInt();
        long balance = buf.readLong();
        int mine = buf.readVarInt();
        boolean vip = buf.readBoolean();
        boolean operator = buf.readBoolean();
        int blockedCount = buf.readVarInt();
        List<String> blocked = new ArrayList<>(Math.max(0, blockedCount));
        for (int i = 0; i < blockedCount; i++) {
            blocked.add(buf.readUtf(128));
        }
        int count = buf.readVarInt();
        List<ListingView> listings = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            listings.add(ListingView.decode(buf));
        }
        return new ShopDataPacket(listings, view, pageCount, totalMatches, balance, mine, vip,
                operator, blocked);
    }

    public static void handle(ShopDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientShopCache.accept(message));
        context.setPacketHandled(true);
    }

    // ------------------------------------------------------------------
    // Server-side construction
    // ------------------------------------------------------------------

    /**
     * Builds the page the player asked for.
     *
     * <p>The requested page is clamped to the real page count, so a client asking for
     * page 9999 simply receives the last page rather than an error or an empty screen.
     */
    public static ShopDataPacket build(net.minecraft.server.level.ServerPlayer viewer, ShopViewState requested) {
        ShopViewState view = requested.validated();
        ShopService shop = ShopServices.get(viewer);
        UUID filter = view.mineOnly() ? viewer.getUUID() : null;

        // The seller's own tab includes sold-out lots so they can restock or delete them.
        List<Listing> matches = shop.search(view.query(), view.sort(), filter, view.mineOnly());

        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(Math.max(0, view.page()), pageCount - 1);
        ShopViewState effectiveView = view.withPage(page);

        int from = page * PAGE_SIZE;
        int to = Math.min(matches.size(), from + PAGE_SIZE);
        List<ListingView> views = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            views.add(ListingView.of(matches.get(i), viewer.getUUID(),
                    com.example.globalshop.shop.ShopServices.get(viewer)
                            .isVip(matches.get(i).sellerId())));
        }

        long balance = EconomyServices.get(viewer).getBalance(viewer.getUUID());
        int ownListings = shop.listingsOf(viewer.getUUID()).size();
        boolean operator = viewer.hasPermissions(2);
        if (!operator) {
            // Downgrade the echoed mode as well, so a client that asked for the editor is told it
            // did not get it instead of drawing an editor it cannot use.
            effectiveView = effectiveView.withBlacklist(false);
        }
        List<String> blacklist = operator && effectiveView.blacklist()
                ? shop.blacklist()
                : List.of();
        return new ShopDataPacket(views, effectiveView, pageCount, matches.size(), balance, ownListings,
                shop.isVip(viewer.getUUID()), operator, blacklist);
    }

    /** Sends the page to the player. Server side only. */
    public static void send(net.minecraft.server.level.ServerPlayer viewer, ShopViewState requested) {
        ModNetwork.sendToPlayer(viewer, build(viewer, requested));
    }
}
