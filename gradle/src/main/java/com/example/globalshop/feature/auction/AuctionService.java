package com.example.globalshop.feature.auction;

import com.example.globalshop.Config;
import com.example.globalshop.GlobalShop;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.api.economy.TransactionResult;
import com.example.globalshop.core.servershop.ShopClock;
import com.example.globalshop.core.market.ItemPool;
import com.example.globalshop.core.market.ItemStacks;
import com.example.globalshop.core.social.Mail;
import com.example.globalshop.core.market.ShopService;
import com.example.globalshop.core.market.ShopServices;
import com.example.globalshop.feature.tradelog.TradeLogEntry;
import com.example.globalshop.feature.auction.net.AuctionUpdatePacket;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.feature.auction.net.AuctionToastPacket;
import com.example.globalshop.core.network.ModNetwork;

/**
 * The auction house: putting things up, bidding, buying out, and settling.
 *
 * <h2>Everything that decides anything happens here</h2>
 * The client sends an intention — "bid this much on that auction" — and nothing else. It does not
 * send a price it claims is current, a countdown it computed, or a balance. Every check below reads
 * the server's own state, on the server's own clock, which is also why the countdown a client draws
 * is only ever a rendering of two timestamps the server gave it.
 *
 * <h2>Money is deducted, not reserved</h2>
 * A bid takes the money immediately. That is what makes the requirement's anti-overspend rule fall
 * out for free: a player with 1000 who bids 800 on one auction has 200, so a second 800 bid is
 * refused by the ordinary balance check with no extra bookkeeping. It also means the money is
 * already somewhere safe if the server dies mid-auction — it is in the save, attached to the
 * auction that is holding it, and a restart can still refund it. {@link AuctionSavedData#audit()}
 * asserts the invariants that keep this true.
 *
 * <h2>The two halves of a settlement are settled separately</h2>
 * The hammer falling, the goods moving and the seller being paid are three moments. Delivering to a
 * mailbox that turns out to be full, or paying a seller whose balance is at the cap, must not be
 * able to lose the other half. So each is a flag on the listing and a sweep retries whatever is
 * outstanding, for as long as it takes. Nothing is ever dropped on the floor as a fallback.
 */
public final class AuctionService {

    /** The shortest auction that makes sense: long enough for somebody to notice it. */
    public static final long MIN_DURATION_MINUTES = 1L;

    private final MinecraftServer server;

    public AuctionService(MinecraftServer server) {
        this.server = server;
    }

    // ==================================================================
    // Clock
    // ==================================================================

    /**
     * The server's absolute time, in epoch milliseconds.
     *
     * <p>This is the single clock every auction timestamp is written with and compared against, and
     * the value the client is told so it can compute its own offset. It is never sent to the server
     * by a client, and a client that does send a time has it ignored.
     */
    public long serverNow() {
        return ShopClock.nowMillis();
    }

    private AuctionSavedData data() {
        return AuctionSavedData.get(server);
    }

    private EconomyService economy() {
        return EconomyServices.get(server);
    }

    private ShopService shop() {
        return ShopServices.get(server);
    }

    // ==================================================================
    // Reading
    // ==================================================================

    /** Every auction still open for bids, in the order it was put up. */
    public List<AuctionListing> active() {
        AuctionSavedData data = data();
        synchronized (data) {
            return data.active();
        }
    }

    @Nullable
    public AuctionListing find(UUID auctionId) {
        AuctionSavedData data = data();
        synchronized (data) {
            return data.byId(auctionId);
        }
    }

    /** What this player has tied up in auctions they are currently winning. */
    public long heldFor(UUID playerId) {
        AuctionSavedData data = data();
        synchronized (data) {
            return data.heldFor(playerId);
        }
    }

    /** Everything the house is holding, across all players. */
    public long heldTotal() {
        AuctionSavedData data = data();
        synchronized (data) {
            return data.heldTotal();
        }
    }

    /** Complaints about the accounting. Empty means sound; the self test asserts exactly that. */
    public List<String> audit() {
        AuctionSavedData data = data();
        synchronized (data) {
            return data.audit();
        }
    }

    // ==================================================================
    // Putting something up
    // ==================================================================

    /**
     * Puts a stack up for auction, taking it out of the seller's possession.
     *
     * <p>The item is escrowed — really removed — rather than marked. A listing that only claimed to
     * hold the goods could have them sold, dropped or crafted away while the auction was running.
     *
     * @param pools where the item is to be taken from; the caller decides whether that is a window
     *              slot or the player's backpack
     */
    public AuctionResult list(ServerPlayer seller, ItemStack template, int count, long startPrice,
                              long minIncrement, long buyoutPrice, long minutes,
                              List<ItemPool> pools) {
        if (template == null || template.isEmpty() || count <= 0) {
            return AuctionResult.ITEM_REQUIRED;
        }
        if (startPrice <= 0L || startPrice > Config.maxUnitPrice() || minIncrement <= 0L) {
            return AuctionResult.INVALID_PRICE;
        }
        // A buyout at or below the asking price is not a buyout, it is a confusing second price.
        if (buyoutPrice != 0L && buyoutPrice <= startPrice) {
            return AuctionResult.INVALID_PRICE;
        }
        if (minutes < MIN_DURATION_MINUTES || minutes > Config.auctionMaxMinutes()) {
            return AuctionResult.INVALID_DURATION;
        }

        AuctionSavedData data = data();
        synchronized (data) {
            if (data.openCountFor(seller.getUUID()) >= Config.auctionLimit()) {
                return AuctionResult.TOO_MANY_LISTINGS;
            }
        }
        // The goods come back here if nobody bids, so the room is checked before the item is taken.
        // Discovering the problem afterwards would mean holding goods with nowhere to put them.
        if (!shop().hasMailRoom(seller.getUUID())) {
            return AuctionResult.MAILBOX_FULL;
        }

        // The stack to be held is copied BEFORE the escrow, and that order is the whole point.
        //
        // The template the caller passes is the player's own hand slot — the auction window offers
        // "the item you are holding" and the packet reads it straight from there. Escrowing removes
        // that item from the inventory, which empties the very stack that was handed in. Copying
        // afterwards therefore stored air: the item left the player's hand and went nowhere, and the
        // chat line named air as well. Copied first, the listing holds the item that was really
        // there. (The private-trade window does the same thing, and does it in this order.)
        ItemStack toHold = template.copyWithCount(count);

        // A count-1 probe as the identity: matching a whole stack compares the stack, and the seller
        // may be holding the same item in several slots with different damage or NBT.
        ItemStack probe = template.copyWithCount(1);
        if (!ItemStacks.escrow(probe, count, pools)) {
            return AuctionResult.ITEM_REQUIRED;
        }

        long now = serverNow();
        AuctionListing listing = new AuctionListing(UUID.randomUUID(), seller.getUUID(),
                seller.getGameProfile().getName(), toHold, startPrice,
                minIncrement, buyoutPrice, now, now + minutes * 60_000L);
        synchronized (data) {
            data.put(listing);
        }
        return AuctionResult.SUCCESS;
    }

    // ==================================================================
    // Bidding
    // ==================================================================

    /**
     * Places a bid, taking the money at once.
     *
     * <p>Order matters: the new bidder's money is taken <em>before</em> the previous bidder is
     * refunded, so a bid that cannot be paid changes nothing at all. Doing it the other way round
     * would hand the previous bidder their money back and then fail to collect, leaving the auction
     * holding a bid it never received.
     */
    public AuctionResult bid(ServerPlayer bidder, UUID auctionId, long amount) {
        AuctionSavedData data = data();
        AuctionListing peek;
        synchronized (data) {
            peek = data.byId(auctionId);
        }
        if (peek == null) {
            return AuctionResult.NO_SUCH_AUCTION;
        }
        // A bid at or above the buyout is the buyout. Charging the larger number would take more
        // than the seller asked for, so the buyout price is what is charged.
        if (peek.hasBuyout() && amount >= peek.buyoutPrice()) {
            return buyout(bidder, auctionId);
        }

        long now = serverNow();
        long refund;
        UUID refundTo;
        boolean extended;
        long broadcastEnd;
        long broadcastTop;
        String broadcastName;
        int broadcastExtensions;
        synchronized (data) {
            AuctionListing listing = data.byId(auctionId);
            if (listing == null) {
                return AuctionResult.NO_SUCH_AUCTION;
            }
            if (listing.state() != AuctionListing.State.ACTIVE || listing.isExpired(now)) {
                return AuctionResult.AUCTION_ENDED;
            }
            if (listing.sellerId().equals(bidder.getUUID())) {
                return AuctionResult.CANNOT_BID_ON_OWN;
            }
            if (bidder.getUUID().equals(listing.topBidderId())) {
                return AuctionResult.ALREADY_TOP_BIDDER;
            }
            if (amount < listing.minimumBid()) {
                return AuctionResult.BID_TOO_LOW;
            }
            if (!shop().hasMailRoom(bidder.getUUID())) {
                return AuctionResult.MAILBOX_FULL;
            }

            // ---- the money is taken first, and this is the whole anti-overspend mechanism ----
            economy().ensureAccount(bidder, Config.initialBalance());
            TransactionResult charged = economy().removeBalance(bidder.getUUID(), amount);
            if (!charged.isSuccess()) {
                return AuctionResult.INSUFFICIENT_FUNDS;
            }

            refundTo = listing.topBidderId();
            refund = listing.applyBid(bidder.getUUID(), bidder.getGameProfile().getName(), amount);
            extended = listing.applySnipeWindow(now);
            data.setDirty();

            // Captured inside the lock so the broadcast describes the state it was accepted in.
            broadcastEnd = listing.endTimeMillis();
            broadcastTop = listing.topBid();
            broadcastName = listing.topBidderName();
            broadcastExtensions = listing.extensions();

            if (refundTo != null && refund > 0L) {
                // Outbid money goes back the instant it is beaten. Held "until the auction ends"
                // would mean a player could only bid on one thing at a time with any confidence.
                economy().addBalance(refundTo, refund);
            }
        }

        // Everyone with the window open is told at once, and told what the server's clock says, so
        // each of them can reset their own countdown from the same instant.
        AuctionUpdatePacket.broadcast(server, new AuctionUpdatePacket(auctionId, broadcastTop,
                broadcastName, broadcastEnd, serverNow(), broadcastExtensions, false));

        // Both balances moved, and either player may be looking at a window that shows one.
        BalanceUpdatePacket.push(bidder);
        if (refundTo != null) {
            BalanceUpdatePacket.push(server.getPlayerList().getPlayer(refundTo));
        }
        if (refundTo != null && refund > 0L) {
            notify(refundTo, "globalshop.auction.outbid",
                    new Object[] { labelOf(peek.item()), format(refund) },
                    "globalshop.auction.toast_outbid", null, 0L);
        }
        logAudit();
        return AuctionResult.SUCCESS;
    }

    // ==================================================================
    // Buying out
    // ==================================================================

    /**
     * Takes the buyout price, ending the auction immediately.
     *
     * <p>Any standing bidder is refunded in the same breath. The buyout does not "outbid" them with
     * their own money; it ends the auction and their bid is returned in full.
     */
    public AuctionResult buyout(ServerPlayer buyer, UUID auctionId) {
        long now = serverNow();
        AuctionSavedData data = data();
        long refund = 0L;
        UUID refundTo = null;
        AuctionListing listing;
        long broadcastEnd;
        long broadcastTop;
        String broadcastName;
        int broadcastExtensions;
        synchronized (data) {
            listing = data.byId(auctionId);
            if (listing == null) {
                return AuctionResult.NO_SUCH_AUCTION;
            }
            if (listing.state() != AuctionListing.State.ACTIVE || listing.isExpired(now)) {
                return AuctionResult.AUCTION_ENDED;
            }
            if (listing.sellerId().equals(buyer.getUUID())) {
                return AuctionResult.CANNOT_BUYOUT_OWN;
            }
            if (!listing.hasBuyout()) {
                return AuctionResult.NO_BUYOUT;
            }
            if (!shop().hasMailRoom(buyer.getUUID())) {
                return AuctionResult.MAILBOX_FULL;
            }

            long price = listing.buyoutPrice();
            economy().ensureAccount(buyer, Config.initialBalance());
            TransactionResult charged = economy().removeBalance(buyer.getUUID(), price);
            if (!charged.isSuccess()) {
                return AuctionResult.INSUFFICIENT_FUNDS;
            }

            refundTo = listing.topBidderId();
            refund = listing.applyBid(buyer.getUUID(), buyer.getGameProfile().getName(), price);
            // The hammer falls now, not at the end timestamp: a buyout ends the auction.
            listing.setState(AuctionListing.State.SOLD);
            data.setDirty();
            broadcastEnd = listing.endTimeMillis();
            broadcastTop = listing.topBid();
            broadcastName = listing.topBidderName();
            broadcastExtensions = listing.extensions();

            if (refundTo != null && refund > 0L) {
                economy().addBalance(refundTo, refund);
            }
        }

        // Everybody watching sees the auction close, with the clock that says when.
        AuctionUpdatePacket.broadcast(server, new AuctionUpdatePacket(auctionId, broadcastTop,
                broadcastName, broadcastEnd, serverNow(), broadcastExtensions, true));

        // Both balances moved, and either player may be looking at a window that shows one.
        BalanceUpdatePacket.push(buyer);
        if (refundTo != null) {
            BalanceUpdatePacket.push(server.getPlayerList().getPlayer(refundTo));
        }
        if (refundTo != null && refund > 0L) {
            notify(refundTo, "globalshop.auction.outbid",
                    new Object[] { labelOf(listing.item()), format(refund) },
                    "globalshop.auction.toast_outbid", null, 0L);
        }
        settleOne(listing);
        logAudit();
        return AuctionResult.SUCCESS;
    }

    // ==================================================================
    // Settling
    // ==================================================================

    /**
     * Drops the hammer on anything whose time is up, then does as much of the settlement as can be
     * done right now. Called once a second by {@link AuctionEvents}.
     *
     * @return how many auctions were concluded
     */
    public int settleDue() {
        long now = serverNow();
        AuctionSavedData data = data();
        List<UUID> concluded = new ArrayList<>();
        synchronized (data) {
            for (AuctionListing listing : data.all()) {
                if (listing.state() == AuctionListing.State.ACTIVE && listing.isExpired(now)) {
                    listing.setState(listing.hasBid()
                            ? AuctionListing.State.SOLD : AuctionListing.State.EXPIRED);
                    concluded.add(listing.id());
                }
            }
            if (!concluded.isEmpty()) {
                data.setDirty();
            }
        }

        for (UUID id : concluded) {
            AuctionListing listing;
            synchronized (data) {
                listing = data.byId(id);
            }
            if (listing != null) {
                settleOne(listing);
            }
        }
        // Anything whose delivery or payout failed earlier gets another try, every sweep, until it
        // works. Nothing is ever written off.
        for (AuctionListing listing : data.all()) {
            if (listing.state() != AuctionListing.State.ACTIVE) {
                settleOne(listing);
            }
        }
        if (!concluded.isEmpty()) {
            logAudit();
        }
        return concluded.size();
    }

    /**
     * Finishes one auction as far as it can be finished.
     *
     * <p>Each half is independent and idempotent. Deliberately no fallback that drops the goods:
     * if the mailbox will not take them, they stay in the auction and this runs again next second.
     */
    private void settleOne(AuctionListing listing) {
        AuctionSavedData data = data();

        if (listing.state() == AuctionListing.State.SOLD && !listing.itemDelivered()) {
            // The requirement is explicit that auction goods never go straight into a backpack.
            // They go to the mailbox so the winner collects them deliberately, and so a full
            // backpack cannot interfere with a win.
            if (shop().postMail(listing.topBidderId(), List.of(listing.item()),
                    Mail.Source.AUCTION)) {
                synchronized (data) {
                    listing.markItemDelivered();
                    data.setDirty();
                }
                notify(listing.topBidderId(), "globalshop.auction.won",
                        new Object[] { labelOf(listing.item()), format(listing.topBid()) },
                        "globalshop.auction.toast_won", labelOf(listing.item()),
                        listing.topBid());
            }
        }

        if (listing.state() == AuctionListing.State.SOLD && listing.itemDelivered()
                && !listing.paidOut()) {
            long gross = listing.topBid();
            long net = gross - commissionOn(gross);
            if (economy().addBalance(listing.sellerId(), net).isSuccess()) {
                synchronized (data) {
                    listing.markPaidOut();
                    data.setDirty();
                }
                // The seller may be standing in the market window, or in this one, with the old
                // total on screen. Being paid should not require pressing refresh, so the new
                // balance is pushed to them.
                BalanceUpdatePacket.push(server.getPlayerList().getPlayer(listing.sellerId()));
                tell(listing.sellerId(), Component.translatable("globalshop.auction.sold",
                        labelOf(listing.item()), format(net)));
                // Written into the shared trade log, so an auction sale sits in the same history as
                // a market one. Logged here, at the moment the money is handed over, and guarded by
                // paidOut above, so a retry cannot record the same sale twice.
                shop().logTrade(TradeLogEntry.Kind.AUCTION, listing.id(), listing.topBidderId(),
                        listing.topBidderName(), listing.sellerId(), listing.sellerName(),
                        listing.item(), listing.item().getCount(), gross);
            }
        }

        if (listing.state() == AuctionListing.State.EXPIRED && !listing.itemDelivered()) {
            if (shop().postMail(listing.sellerId(), List.of(listing.item()),
                    Mail.Source.AUCTION)) {
                synchronized (data) {
                    listing.markItemDelivered();
                    data.setDirty();
                }
                tell(listing.sellerId(), Component.translatable("globalshop.auction.flopped",
                        labelOf(listing.item())));
            }
        }

        synchronized (data) {
            AuctionListing live = data.byId(listing.id());
            if (live != null && live.isFinished()) {
                data.remove(listing.id());
            }
        }
    }

    // ==================================================================
    // Internals
    // ==================================================================

    /**
     * The house's cut, from the same setting the market uses.
     *
     * <p>One tax setting for both, so a seller cannot be taxed differently for selling the same
     * thing through two windows. The cut is destroyed rather than credited to anyone, exactly as it
     * is in the market: it is a money sink, not a third account.
     */
    private long commissionOn(long amount) {
        int percent = Config.marketCommissionPercent();
        if (percent <= 0 || amount <= 0L) {
            return 0L;
        }
        return amount * percent / 100L;
    }

    /** Formats money the way every other message in the mod does. */
    private static String format(long minorUnits) {
        return com.example.globalshop.api.economy.MoneyFormat.formatWithName(minorUnits);
    }

    /**
     * An item's name with its count, for anything a player reads.
     *
     * <p>"Golden Apple x64" and not "Golden Apple": a stack named without a number looks like one
     * apple, and the winner of a stack of 64 was told they had won "a golden apple" and had to open
     * the mailbox to find out how many. The count is only added above one, because "x1" is noise.
     */
    private static String labelOf(ItemStack item) {
        return item.getCount() > 1
                ? item.getHoverName().getString() + " x" + item.getCount()
                : item.getHoverName().getString();
    }

    /** Sends a chat line to a player if they are online; offline players find out from the mailbox. */
    private void tell(UUID playerId, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            online.sendSystemMessage(message);
        }
    }

    /**
     * Tells a player something, as a popup if they are looking at the auction window and as a chat
     * line if they are not — which is what the requirement asks for, and the reason the server
     * tracks who is watching at all.
     *
     * <p>The message is sent as a translation key rather than a finished sentence, so the player
     * reads it in their own language rather than in the server's.
     */
    private void notify(UUID playerId, String chatKey, Object[] chatArgs, String toastKey,
                        @Nullable String toastItem, long toastAmount) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online == null) {
            return;
        }
        online.sendSystemMessage(Component.translatable(chatKey, chatArgs));
        if (toastKey != null
                && com.example.globalshop.feature.auction.AuctionViewers.watching(server).contains(playerId)) {
            com.example.globalshop.core.network.ModNetwork.sendToPlayer(online,
                    new com.example.globalshop.feature.auction.net.AuctionToastPacket(toastKey,
                            toastItem == null ? "" : toastItem, toastAmount));
        }
    }

    /**
     * Runs the accounting audit and complains loudly if it ever fails.
     *
     * <p>Cheap — it walks the live auctions, which is a handful — and the alternative is finding out
     * from a player that their money vanished.
     */
    private void logAudit() {
        List<String> complaints = audit();
        if (!complaints.isEmpty()) {
            GlobalShop.LOGGER.error("globalshop: auction accounting audit failed: {}", complaints);
        }
    }
}
