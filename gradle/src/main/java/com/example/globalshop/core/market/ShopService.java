package com.example.globalshop.core.market;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.api.economy.TransactionResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import com.example.globalshop.core.servershop.ShopClock;
import com.example.globalshop.core.social.Mail;
import com.example.globalshop.feature.tradelog.TradeLogEntry;

/**
 * The shared marketplace. Every operation that can move an item or a coin lives
 * here, and all of it runs on the server.
 *
 * <h2>Trust model</h2>
 * Nothing in this class takes client input at face value. A request carries, at
 * most, "which listing" and "how many"; the price, the seller, the stock and the
 * escrowed items are read from the server's own {@link ShopSavedData}. A modified
 * client can therefore ask for things, but it cannot name a price, invent stock, or
 * assert a balance.
 *
 * <h2>How duplication and free money are prevented</h2>
 * <ul>
 *   <li><b>Escrow, never a promise.</b> Listing items removes them from the
 *       player's input slot and backpack immediately ({@link ItemStacks#escrow}).
 *       The market can only ever hand out what it actually holds, so a listing
 *       cannot pay out items the seller never gave up.</li>
 *   <li><b>Validate, then mutate.</b> Every check -- ownership, quantity window,
 *       stock, price overflow, balance -- happens before the first change. A
 *       refusal therefore leaves no trace, and there is no half-applied state to
 *       exploit by retrying.</li>
 *   <li><b>One lock per operation.</b> Check-and-apply runs inside
 *       {@code synchronized (data)}, so two concurrent requests cannot both observe
 *       "1 left in stock" or "balance is sufficient".</li>
 *   <li><b>Money before goods.</b> A purchase charges the buyer first and only
 *       delivers once the transfer succeeded, so a purchase can never hand over
 *       items that were not paid for.</li>
 *   <li><b>Deliver after unlocking.</b> Payouts are collected during the critical
 *       section and handed out afterwards, so an inventory callback cannot re-enter
 *       the shop while the lock is held.</li>
 * </ul>
 */
public final class ShopService {

    private final MinecraftServer server;

    /** Last purchase request time per player, for the anti-flood throttle. */
    private final Map<UUID, Long> lastPurchaseAt = new HashMap<>();

    public ShopService(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    private ShopSavedData data() {
        return ShopSavedData.get(server);
    }

    private EconomyService economy() {
        return EconomyServices.get(server);
    }

    // ==================================================================
    // Queries
    // ==================================================================

    /** Total number of listings, including sold-out ones. */
    public int listingCount() {
        ShopSavedData data = data();
        synchronized (data) {
            return data.listingCount();
        }
    }

    /** @return the listing, or {@code null}. The returned object must not be mutated. */
    @Nullable
    public Listing find(UUID listingId) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.listing(listingId);
        }
    }

    /**
     * The market view used by the GUI and by {@code /globalshop}.
     *
     * <p>Sold-out listings are excluded from the public market (the requirement's
     * "delist once stock hits zero" behaviour) but remain visible to their seller
     * through {@link #listingsOf(UUID)}, where they show as sold out and can be
     * restocked or deleted.
     *
     * @param query        free text matched against item name, registry name and seller name
     * @param sort         ordering; never {@code null}
     * @param sellerFilter when set, only this player's listings are returned
     */
    public List<Listing> search(String query, ShopSort sort, @Nullable UUID sellerFilter) {
        return search(query, sort, sellerFilter, false);
    }

    public List<Listing> search(String query, ShopSort sort, @Nullable UUID sellerFilter,
                                boolean includeSoldOut) {
        String needle = (query == null) ? "" : query.trim().toLowerCase(Locale.ROOT);
        ShopSavedData data = data();
        List<Listing> matches = new ArrayList<>();
        synchronized (data) {
            for (Listing listing : data.allListings()) {
                if (!includeSoldOut && (listing.isSoldOut() || listing.isExpired())) {
                    continue;
                }
                if (sellerFilter != null && !listing.isSeller(sellerFilter)) {
                    continue;
                }
                if (!needle.isEmpty() && !matchesQuery(listing, needle)) {
                    continue;
                }
                matches.add(listing);
            }
        }
        matches.sort(sort.comparator());
        return matches;
    }

    /** Everything this player has on the market, sold-out listings included. */
    public List<Listing> listingsOf(UUID sellerId) {
        return search("", ShopSort.TIME_NEWEST, sellerId, true);
    }

    /** Newest-first slice of the trade log. */
    public List<TradeLogEntry> recentLog(int limit) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.logTail(limit);
        }
    }

    public int logSize() {
        ShopSavedData data = data();
        synchronized (data) {
            return data.logSize();
        }
    }

    /** A cheap snapshot of how much the market holds, for diagnostics and {@code /globalshop reload}. */
    public record MarketSummary(int listings, int logEntries) {
    }

    public MarketSummary summary() {
        ShopSavedData data = data();
        synchronized (data) {
            return new MarketSummary(data.listingCount(), data.logEntryCount());
        }
    }

    /**
     * Re-applies configuration to the live market and persists it.
     *
     * <p>There is deliberately no "read the file again" step: the in-memory
     * {@link ShopSavedData} <em>is</em> the authority while the server runs, and it is
     * loaded from the world save at startup. Re-reading the file mid-run could only
     * discard purchases made since launch. What reload does is apply the current
     * config (currency formatting, limits, log retention) and force the data to be
     * written out.
     */
    public MarketSummary reload(int logRetention) {
        ShopSavedData data = data();
        synchronized (data) {
            data.trimLog(logRetention);
            data.setDirty();
            return new MarketSummary(data.listingCount(), data.logEntryCount());
        }
    }

    private static boolean matchesQuery(Listing listing, String needle) {
        String displayName = listing.template().getHoverName().getString().toLowerCase(Locale.ROOT);
        if (displayName.contains(needle)) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(listing.template().getItem());
        if (key != null && key.toString().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return listing.sellerName().toLowerCase(Locale.ROOT).contains(needle);
    }

    // ==================================================================
    // Create a listing
    // ==================================================================

    /**
     * Puts items up for sale, taking them out of {@code pools} (the GUI passes the
     * listing input slot first, then the player's backpack).
     *
     * <p>The escrow amount is taken from the pools, not from the caller's claim, so
     * the listing is backed by items that provably existed.
     *
     * @param template  the item to sell; only its identity and tags are used
     * @param pools     where the escrow items are taken from, in priority order
     */
    public ShopResult create(ServerPlayer seller, ItemStack template, int stock, long unitPrice,
                             int minPerBuy, int maxPerBuy, List<ItemPool> pools) {
        if (!ItemStacks.isListable(template)) {
            return ShopResult.EMPTY_ITEM;
        }
        if (!Config.allowListing() && !seller.hasPermissions(2)) {
            return ShopResult.LISTING_DISABLED;
        }
        if (unitPrice <= 0L || unitPrice > Config.maxUnitPrice()) {
            return ShopResult.INVALID_PRICE;
        }
        if (stock <= 0 || stock > Config.maxStock()) {
            return ShopResult.INVALID_STOCK;
        }
        if (minPerBuy <= 0 || minPerBuy > stock) {
            return ShopResult.INVALID_MIN;
        }
        if (maxPerBuy < minPerBuy || maxPerBuy > stock || maxPerBuy > Config.maxPerPurchase()) {
            return ShopResult.INVALID_MAX;
        }

        // Snapshot the identity before escrow shrinks (possibly empties) the source slot.
        ItemStack escrowIdentity = template.copyWithCount(1);

        ShopSavedData data = data();
        synchronized (data) {
            // Checked here rather than only in the screen: the screen is a convenience, and a
            // client that skipped it must still be refused.
            String blockedId = registryIdOf(template);
            if (blockedId != null && data.isBlacklisted(blockedId)) {
                return ShopResult.FORBIDDEN_ITEM;
            }
            if (data.listingCountOf(seller.getUUID())
                    >= Config.listingLimitFor(data.isVip(seller.getUUID(),
                            com.example.globalshop.core.servershop.ShopClock.nowMillis()))) {
                return ShopResult.LISTING_LIMIT_REACHED;
            }
            if (!ItemStacks.escrow(template, stock, pools)) {
                return ShopResult.NOT_ENOUGH_ITEMS;
            }
            UUID id = UUID.randomUUID();
            // The shop's own clock, not the system one: expiry is decided by ShopClock, so
            // stamping a listing with a different clock is how a lot could be born expired.
            long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
            String sellerName = seller.getGameProfile().getName();
            Listing listing = new Listing(id, seller.getUUID(), sellerName, escrowIdentity,
                    unitPrice, stock, minPerBuy, maxPerBuy, now, expiryFor(now));
            data.putListing(listing);
            data.appendLog(new TradeLogEntry(data.nextSeq(), now, TradeLogEntry.Kind.LIST, id,
                    seller.getUUID(), sellerName, seller.getUUID(), sellerName,
                    escrowIdentity, stock, unitPrice, safeMultiply(unitPrice, stock)),
                    Config.tradeLogRetention());
            data.setDirty();
            return ShopResult.SUCCESS;
        }
    }

    // ==================================================================
    // Buy
    // ==================================================================

    /**
     * Buys {@code quantity} items from a listing.
     *
     * <p>Order of operations is deliberate: validate everything, move the money,
     * then reduce stock, then (outside the lock) hand over the items. Money therefore
     * can never move without the buyer receiving goods, and goods can never be handed
     * over before payment.
     */
    public ShopResult purchase(ServerPlayer buyer, UUID listingId, int quantity) {
        if (throttled(buyer.getUUID())) {
            return ShopResult.COOLDOWN;
        }
        if (quantity <= 0) {
            return ShopResult.INVALID_QUANTITY;
        }

        ShopSavedData data = data();
        ItemStack payout;
        synchronized (data) {
            Listing listing = data.listing(listingId);
            if (listing == null) {
                return ShopResult.LISTING_NOT_FOUND;
            }
            if (listing.isSoldOut()) {
                return ShopResult.SOLD_OUT;
            }
            if (listing.isExpired()) {
                // Timed out. The items are still in escrow and still the seller's; only the
                // seller can take them back, by unlisting.
                return ShopResult.EXPIRED;
            }
            if (listing.isSeller(buyer.getUUID())) {
                return ShopResult.CANNOT_BUY_OWN;
            }
            // Report the specific limit that was broken rather than a generic failure,
            // so the buyer knows what to change.
            if (quantity < listing.minPerBuy()) {
                return ShopResult.BELOW_MINIMUM;
            }
            if (quantity > listing.maxPerBuy()) {
                return ShopResult.ABOVE_MAXIMUM;
            }
            if (quantity > listing.stock()) {
                return ShopResult.INSUFFICIENT_STOCK;
            }

            long total;
            try {
                total = listing.totalPrice(quantity);
            } catch (ArithmeticException overflow) {
                return ShopResult.INVALID_QUANTITY;
            }
            long commission = commissionOn(total);
            long net = total - commission;

            // Refuse before any money moves if the goods could go neither to the backpack nor to
            // the mailbox. Selling somebody a purchase and then destroying it is not an option, and
            // refusing afterwards would mean unwinding a payment that already went through.
            ItemStack delivery = listing.template().copyWithCount(quantity);
            if (cannotAccept(buyer, delivery)) {
                return ShopResult.MAILBOX_FULL;
            }

            EconomyService economy = economy();
            if (!economy.hasEnough(buyer.getUUID(), total)) {
                return ShopResult.INSUFFICIENT_FUNDS;
            }
            // The buyer is debited the whole amount in one operation before anybody is credited.
            // Crediting the seller first and then collecting the shop's cut leaves a window in which
            // a concurrent /pay can empty the buyer's account: the fee debit then fails, this method
            // reports failure, and the seller has already been paid — a purchase that "did not
            // happen" but moved money. Taking it all up front removes the window instead of relying
            // on the lock to close it.
            economy.ensureAccount(listing.sellerId(), listing.sellerName(), 0L);
            TransactionResult charged = economy.removeBalance(buyer.getUUID(), total);
            if (!charged.isSuccess()) {
                return mapEconomy(charged);
            }
            TransactionResult credited = economy.addBalance(listing.sellerId(), net);
            if (!credited.isSuccess()) {
                // The sale did not happen, so the buyer keeps their money. The refund cannot fail
                // for want of funds: it is going back to the account it was taken from.
                economy.addBalance(buyer.getUUID(), total);
                return mapEconomy(credited);
            }
            // The shop's cut is never credited to anyone, which is what makes the shop a money sink
            // rather than a third account.

            listing.reduceStock(quantity);
            payout = listing.template().copyWithCount(quantity);

            long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
            data.appendLog(new TradeLogEntry(data.nextSeq(), now, TradeLogEntry.Kind.PURCHASE, listingId,
                    buyer.getUUID(), buyer.getGameProfile().getName(),
                    listing.sellerId(), listing.sellerName(),
                    listing.template(), quantity, listing.unitPrice(), total),
                    Config.tradeLogRetention());

            // Tell the seller what sold and what they were actually paid. Queued in the save so
            // it survives a restart and reaches them on login if they are not here now.
            data.addNotice(listing.sellerId(), new SaleNotice(data.nextSeq(), now,
                    listing.template().getHoverName().getString(), quantity, total, commission, net,
                    buyer.getGameProfile().getName()));
            // A sold-out listing stays on file so the seller can restock it, but it is
            // filtered out of the public market by search(...).
            data.setDirty();
        }

        // Handing items over touches inventories; do it with the shop lock released. Anything that
        // does not fit becomes mail rather than being dropped where a hopper or the despawn timer
        // would destroy it, and the buyer is told so they know where to look.
        mailIfItDoesNotFit(buyer, List.of(payout), Mail.Source.MARKET);
        // And tell the seller now if they happen to be online.
        flushNotices(sellerIdOf(listingId));
        return ShopResult.SUCCESS;
    }

    /** The seller of a listing, or {@code null} if it vanished. Used after the lock is released. */
    @Nullable
    private UUID sellerIdOf(UUID listingId) {
        ShopSavedData data = data();
        synchronized (data) {
            Listing listing = data.listing(listingId);
            return listing == null ? null : listing.sellerId();
        }
    }

    /** The shop's cut of a total, rounded down so the shop never takes more than its share. */
    static long commissionOn(long total) {
        int percent = Config.marketCommissionPercent();
        if (percent <= 0 || total <= 0L) {
            return 0L;
        }
        return Math.min(total, total * percent / 100L);
    }

    /** When a listing created now should stop being purchasable. 0 means never. */
    private static long expiryFor(long now) {
        long lifetime = Config.listingLifetimeMillis();
        return lifetime <= 0L ? 0L : now + lifetime;
    }

    // ==================================================================
    // Seller edits
    // ==================================================================

    /**
     * Changes a listing's price, stock or purchase-size limits.
     *
     * <p>Raising the stock requires the seller to actually supply the extra items:
     * they are escrowed from {@code pools} before anything is written, so a seller
     * cannot inflate a listing beyond what they own. Lowering the stock returns the
     * difference to the seller (or their mailbox when offline).
     */
    /**
     * Tops a listing up with everything of that item the seller is carrying.
     *
     * <p>The one-click version of raising the stock field. The common case is "put the rest of
     * what I have into this lot", and working out the number to type gets it wrong when the
     * escrow and the backpack each hold part of it.
     *
     * <p>Implemented through {@link #modify} rather than beside it, so the escrow, the price
     * validation and the trade log all go through the path that is already covered.
     */
    public ShopResult restock(ServerPlayer seller, UUID listingId, List<ItemPool> pools) {
        ShopSavedData data = data();
        Listing listing;
        int carried;
        synchronized (data) {
            listing = data.listing(listingId);
            if (listing == null) {
                return ShopResult.LISTING_NOT_FOUND;
            }
            if (!listing.isSeller(seller.getUUID())) {
                return ShopResult.NOT_THE_OWNER;
            }
            carried = ItemStacks.countMatching(listing.template(), pools);
        }
        if (carried <= 0) {
            return ShopResult.NOT_ENOUGH_ITEMS;
        }
        int target = (int) Math.min(Config.maxStock(), (long) listing.stock() + carried);
        return modify(seller, listingId, listing.unitPrice(), target,
                listing.minPerBuy(), listing.maxPerBuy(), pools);
    }

    /**
     * Lists the stack the player is holding.
     *
     * <p>The command form of dropping an item into the sell slot: the same validation and the same
     * escrow, with a different way of naming the item. The held stack goes through the same one-slot
     * container the window uses, so the two paths cannot drift apart.
     */
    public ShopResult createHeld(ServerPlayer seller, ItemStack held, int stock, long unitPrice,
                                 int minPerBuy, int maxPerBuy) {
        if (held == null || held.isEmpty()) {
            return ShopResult.EMPTY_ITEM;
        }
        net.minecraft.world.SimpleContainer carrying = new net.minecraft.world.SimpleContainer(1);
        carrying.setItem(0, held.copyWithCount(Math.min(held.getCount(), stock)));
        ShopResult result = create(seller, held, stock, unitPrice, minPerBuy, maxPerBuy,
                List.of(new ContainerPool(carrying)));
        if (result.isSuccess()) {
            // The escrow took exactly `stock` — the copy in the container holds min(held, stock),
            // and an escrow that cannot find the full `stock` fails rather than taking part of it,
            // so success means the container had all of it and the same leaves the hand.
            held.shrink(stock);
            seller.getInventory().setChanged();
        }
        return result;
    }

    public ShopResult modify(ServerPlayer actor, UUID listingId, long unitPrice, int newStock,
                             int minPerBuy, int maxPerBuy, List<ItemPool> pools) {
        if (unitPrice <= 0L || unitPrice > Config.maxUnitPrice()) {
            return ShopResult.INVALID_PRICE;
        }
        if (newStock <= 0 || newStock > Config.maxStock()) {
            return ShopResult.INVALID_STOCK;
        }
        if (minPerBuy <= 0 || minPerBuy > newStock) {
            return ShopResult.INVALID_MIN;
        }
        if (maxPerBuy < minPerBuy || maxPerBuy > newStock || maxPerBuy > Config.maxPerPurchase()) {
            return ShopResult.INVALID_MAX;
        }

        UUID returnTo = null;
        ItemStack returned = ItemStack.EMPTY;

        ShopSavedData data = data();
        synchronized (data) {
            Listing listing = data.listing(listingId);
            if (listing == null) {
                return ShopResult.LISTING_NOT_FOUND;
            }
            boolean admin = actor.hasPermissions(2);
            if (!listing.isSeller(actor.getUUID()) && !admin) {
                return ShopResult.NOT_THE_OWNER;
            }

            int delta = newStock - listing.stock();
            if (delta > 0 && !ItemStacks.escrow(listing.template(), delta, pools)) {
                // Refuse rather than quietly listing items the seller does not have.
                return ShopResult.NOT_ENOUGH_ITEMS;
            }

            listing.setUnitPrice(unitPrice);
            listing.setStock(newStock);
            listing.setMinPerBuy(minPerBuy);
            listing.setMaxPerBuy(maxPerBuy);

            if (delta < 0) {
                returnTo = listing.sellerId();
                returned = listing.template().copyWithCount(-delta);
            }

            long now = System.currentTimeMillis();
            data.appendLog(new TradeLogEntry(data.nextSeq(), now, TradeLogEntry.Kind.MODIFY, listingId,
                    actor.getUUID(), actor.getGameProfile().getName(),
                    listing.sellerId(), listing.sellerName(),
                    listing.template(), newStock, unitPrice, safeMultiply(unitPrice, newStock)),
                    Config.tradeLogRetention());
            data.setDirty();
        }

        if (returnTo != null && !returned.isEmpty()) {
            deliver(returnTo, returned);
        }
        return ShopResult.SUCCESS;
    }

    /** The owner (or an operator) takes a listing down; unsold items go back to the seller. */
    public ShopResult unlist(ServerPlayer actor, UUID listingId) {
        return remove(actor.getUUID(), actor.getGameProfile().getName(),
                actor.hasPermissions(2), listingId, false);
    }

    /** Operator force-removal, usable from the console where there is no player. */
    /**
     * Takes every one of this player's lots off the market.
     *
     * <p>Implemented by collecting the ids and then calling the ordinary unlist for each, rather
     * than by clearing the map: that way the escrow return, the mailbox fallback and the trade
     * log all go through the one path that is already tested. A bulk operation that reimplemented
     * the return would be a second place for items to go missing.
     *
     * @return how many lots were removed
     */
    public int unlistAll(ServerPlayer seller) {
        List<UUID> mine = new ArrayList<>();
        for (Listing listing : listingsOf(seller.getUUID())) {
            mine.add(listing.id());
        }
        int removed = 0;
        for (UUID id : mine) {
            if (unlist(seller, id).isSuccess()) {
                removed++;
            }
        }
        return removed;
    }

    // ==================================================================
    // Sale notices
    // ==================================================================

    /**
     * Hands over everything a seller has been owed a message about.
     *
     * <p>Called on login, which is what makes an offline seller's notification arrive when they
     * come back. Each notice names the item and, importantly, the amount <em>after</em> the
     * shop's commission — the number the seller actually received.
     *
     * @return how many notices were delivered
     */
    public int deliverNotices(ServerPlayer player) {
        List<SaleNotice> pending;
        ShopSavedData data = data();
        synchronized (data) {
            if (!data.hasNotices(player.getUUID())) {
                return 0;
            }
            pending = data.takeNotices(player.getUUID());
            data.setDirty();
        }
        for (SaleNotice notice : pending) {
            player.sendSystemMessage(Component.translatable("globalshop.notice.sold",
                    notice.quantity(), notice.itemName(), notice.buyerName(),
                    com.example.globalshop.api.economy.MoneyFormat.formatWithName(notice.net()),
                    com.example.globalshop.api.economy.MoneyFormat.formatWithName(notice.commission())));
        }
        return pending.size();
    }

    public boolean hasNotices(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.hasNotices(playerId);
        }
    }

    /** Sends anything queued for a seller who is online right now. */
    private void flushNotices(@Nullable UUID sellerId) {
        if (sellerId == null) {
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(sellerId);
        if (online != null) {
            deliverNotices(online);
        }
    }

    // ==================================================================
    // VIP
    // ==================================================================

    public boolean isVip(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.isVip(playerId, com.example.globalshop.core.servershop.ShopClock.nowMillis());
        }
    }

    /** When this player's VIP lapses, or {@code 0} if they have never had any. */
    public long vipUntil(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.vipUntil(playerId);
        }
    }

    /** Whether this player asked to be charged again when their period ends. */
    public boolean vipAutoRenew(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.vipAutoRenew(playerId);
        }
    }

    public void setVipAutoRenew(UUID playerId, boolean on) {
        ShopSavedData data = data();
        synchronized (data) {
            data.setVipAutoRenew(playerId, on);
            data.setDirty();
        }
    }

    /** How many lots this player may have on the market, given their rank. */
    public int listingLimitFor(UUID playerId) {
        return Config.listingLimitFor(isVip(playerId));
    }

    /**
     * Buys one period of VIP with the in-game currency.
     *
     * <p>Buying while a period is still running <em>adds</em> to it rather than replacing it, so a
     * member who renews early is not robbed of the time they had left.
     *
     * <p>Charged first and granted second, both inside the lock, and the charge is only accepted if
     * the player can afford it — so nobody ends up a member without having paid, nor has paid
     * without being one.
     */
    public ShopResult purchaseVip(ServerPlayer player) {
        ShopSavedData data = data();
        synchronized (data) {
            long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
            long price = Config.vipPrice();
            EconomyService economy = economy();
            economy.ensureAccount(player, Config.initialBalance());
            if (!economy.hasEnough(player.getUUID(), price)) {
                return ShopResult.INSUFFICIENT_FUNDS;
            }
            TransactionResult paid = economy.removeBalance(player.getUUID(), price);
            if (!paid.isSuccess()) {
                return mapEconomy(paid);
            }
            data.grantVip(player.getUUID(),
                    Math.max(now, data.vipUntil(player.getUUID())) + Config.vipMillis());
            data.setDirty();
        }
        return ShopResult.SUCCESS;
    }

    /**
     * Charges everyone whose paid-for time has run out and who asked to be renewed.
     *
     * <p>Run on login and periodically while the server is up, because a player who stays online
     * across the boundary should not silently drop to the lower listing limit.
     *
     * <p>If somebody cannot afford it, auto-renew turns itself off and they are told. Silently
     * failing would leave a member believing they were covered, and silently going into debt is not
     * something this mod does anywhere else.
     *
     * @return how many periods were bought
     */
    public int renewDueVips() {
        List<UUID> due = new ArrayList<>();
        ShopSavedData data = data();
        long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
        synchronized (data) {
            for (UUID playerId : data.autoRenewers()) {
                if (data.vipUntil(playerId) <= now) {
                    due.add(playerId);
                }
            }
        }

        int renewed = 0;
        for (UUID playerId : due) {
            synchronized (data) {
                long at = com.example.globalshop.core.servershop.ShopClock.nowMillis();
                if (data.vipUntil(playerId) > at) {
                    continue; // somebody renewed it in the meantime
                }
                EconomyService economy = economy();
                long price = Config.vipPrice();
                if (!economy.hasEnough(playerId, price)) {
                    // Stop trying, and say why: an unexplained silent lapse is worse than a lapse.
                    data.setVipAutoRenew(playerId, false);
                    data.setDirty();
                    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                    if (online != null) {
                        online.sendSystemMessage(Component.translatable(
                                "globalshop.vip.renew_failed",
                                com.example.globalshop.api.economy.MoneyFormat.formatWithName(price)));
                    }
                    continue;
                }
                TransactionResult paid = economy.removeBalance(playerId, price);
                if (paid.isSuccess()) {
                    data.grantVip(playerId, at + Config.vipMillis());
                    data.setDirty();
                    renewed++;
                    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                    if (online != null) {
                        online.sendSystemMessage(Component.translatable("globalshop.vip.renewed",
                                Config.vipDays(),
                                com.example.globalshop.api.economy.MoneyFormat.formatWithName(price)));
                    }
                }
            }
        }
        return renewed;
    }

    /** Operator override, for granting or removing a rank without a payment. */
    public void setVip(UUID playerId, boolean vip) {
        ShopSavedData data = data();
        synchronized (data) {
            if (vip) {
                data.grantVip(playerId,
                        com.example.globalshop.core.servershop.ShopClock.nowMillis() + Config.vipMillis());
            } else {
                data.revokeVip(playerId);
            }
            data.setDirty();
        }
    }

    /** Members whose period is currently running. */
    public int vipCount() {
        ShopSavedData data = data();
        synchronized (data) {
            return data.vipCount(com.example.globalshop.core.servershop.ShopClock.nowMillis());
        }
    }

    // ==================================================================
    // The item blacklist
    // ==================================================================

    /**
     * Whether an operator has blocked this item from being listed.
     *
     * <p>Keyed on the registry name, so a renamed or enchanted copy of a blocked item is blocked
     * too. That is the point: the rule is about the item, not about one stack of it.
     */
    public boolean blacklisted(ItemStack stack) {
        String id = registryIdOf(stack);
        if (id == null) {
            return false;
        }
        ShopSavedData data = data();
        synchronized (data) {
            return data.isBlacklisted(id);
        }
    }

    /** Blocks an item from being listed. @return whether it was not already blocked */
    public boolean blockItem(String itemId) {
        ShopSavedData data = data();
        synchronized (data) {
            boolean added = data.addToBlacklist(itemId);
            if (added) {
                data.setDirty();
            }
            return added;
        }
    }

    /** Unblocks an item. @return whether it had been blocked */
    public boolean unblockItem(String itemId) {
        ShopSavedData data = data();
        synchronized (data) {
            boolean removed = data.removeFromBlacklist(itemId);
            if (removed) {
                data.setDirty();
            }
            return removed;
        }
    }

    /** Every blocked item, sorted so the list does not reshuffle between opens. */
    public List<String> blacklist() {
        ShopSavedData data = data();
        synchronized (data) {
            return data.blacklist();
        }
    }

    /** @return the item's registry name, or {@code null} for an empty stack */
    @Nullable
    static String registryIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    // ==================================================================
    // The player's own trades
    // ==================================================================

    /**
     * The player's own player-to-player trades, newest first.
     *
     * <p>Filtered to completed purchases in which they were either the buyer or the seller.
     * Listing and unlisting are not trades, and the server shop keeps a separate log of its own,
     * so what remains is exactly the player-to-player history — and only the part of it that
     * belongs to the person asking. There is deliberately no argument for whose trades to show,
     * so a client cannot ask for anyone else's.
     */
    public List<TradeLogEntry> tradesOf(UUID playerId, int limit) {
        ShopSavedData data = data();
        synchronized (data) {
            List<TradeLogEntry> mine = new ArrayList<>();
            // 0 means "the whole log"; the log is already bounded by the retention setting.
            for (TradeLogEntry entry : data.logTail(0)) {
                // Only completed trades. This used to accept PURCHASE alone, which silently dropped
                // every auction sale — the entry was written and then filtered out of the very list
                // it was written for. The set now lives on the enum as isTrade(), so a new kind
                // cannot be forgotten here.
                if (!entry.kind().isTrade()) {
                    continue;
                }
                if (playerId.equals(entry.actorId()) || playerId.equals(entry.sellerId())) {
                    mine.add(entry);
                    if (limit > 0 && mine.size() >= limit) {
                        break;
                    }
                }
            }
            return List.copyOf(mine);
        }
    }

    public ShopResult forceRemove(UUID listingId, String actorName) {
        return remove(null, actorName, true, listingId, true);
    }

    private ShopResult remove(@Nullable UUID actorId, String actorName, boolean admin,
                              UUID listingId, boolean operatorAction) {
        UUID returnTo = null;
        ItemStack returned = ItemStack.EMPTY;

        ShopSavedData data = data();
        synchronized (data) {
            Listing listing = data.listing(listingId);
            if (listing == null) {
                return ShopResult.LISTING_NOT_FOUND;
            }
            if (!admin && (actorId == null || !listing.isSeller(actorId))) {
                return ShopResult.NOT_THE_OWNER;
            }
            data.removeListing(listingId);
            if (!listing.isSoldOut()) {
                // Everything still in escrow belongs to the seller, not to whoever
                // pressed the button.
                returnTo = listing.sellerId();
                returned = listing.template().copyWithCount(listing.stock());
            }
            long now = System.currentTimeMillis();
            data.appendLog(new TradeLogEntry(data.nextSeq(), now,
                    operatorAction ? TradeLogEntry.Kind.ADMIN_REMOVE : TradeLogEntry.Kind.UNLIST,
                    listingId, actorId, actorName, listing.sellerId(), listing.sellerName(),
                    listing.template(), listing.stock(), listing.unitPrice(),
                    safeMultiply(listing.unitPrice(), listing.stock())),
                    Config.tradeLogRetention());
            data.setDirty();
        }

        if (returnTo != null && !returned.isEmpty()) {
            deliver(returnTo, returned);
        }
        return ShopResult.SUCCESS;
    }

    // ==================================================================
    // Mailbox
    // ==================================================================

    /**
     * Hands over as much of the mailbox as will fit into the backpack.
     *
     * <p>Called on login and by the claim command. Each mail is taken out of the mailbox *before*
     * its items are placed — the same discipline the rest of the mod uses. A crash in between
     * leaves the mail in the world save (nothing was written yet) rather than leaving both the
     * mail and a copy of its contents in the player's backpack, which would be item creation.
     *
     * <p>Expired mail is skipped: it can no longer be claimed, only deleted.
     *
     * @return how many mails were emptied
     */
    public int deliverMail(ServerPlayer player) {
        List<Mail> pending = new ArrayList<>();
        ShopSavedData data = data();
        long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
        synchronized (data) {
            for (Mail mail : data.mailsOf(player.getUUID())) {
                if (!mail.isExpired(now)) {
                    pending.add(mail);
                }
            }
            for (Mail mail : pending) {
                data.removeMail(player.getUUID(), mail.id());
            }
            if (!pending.isEmpty()) {
                data.setDirty();
            }
        }

        int emptied = 0;
        for (Mail mail : pending) {
            List<ItemStack> leftover = new ArrayList<>();
            for (ItemStack stack : mail.items()) {
                ItemStack remaining = ItemStacks.insertIntoInventory(player.getInventory(), stack);
                if (!remaining.isEmpty()) {
                    leftover.add(remaining);
                }
            }
            if (leftover.isEmpty()) {
                emptied++;
            } else {
                // Put the remainder back with its original creation time, so taking part of a
                // delivery cannot be used to keep it alive past its expiry.
                synchronized (data) {
                    data.restoreMail(player.getUUID(), mail.withItems(leftover));
                    data.setDirty();
                }
            }
        }
        return emptied;
    }

    /**
     * Every mail waiting for this player, oldest first. A copy: callers cannot mutate the store.
     *
     * <p>Expired mail is deleted as a side effect of reading. The mailbox is bounded by time rather
     * than by count, and this is where that becomes real: a player who has not looked in a month
     * sees what is actually left, not a list of things they can no longer have.
     */
    public List<Mail> mailsOf(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            purgeExpiredMail(data, playerId);
            return List.copyOf(data.mailsOf(playerId));
        }
    }

    public int mailCount(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            purgeExpiredMail(data, playerId);
            return data.mailCount(playerId);
        }
    }

    /** Deletes this player's lapsed mail, marking the store dirty when anything went. */
    private void purgeExpiredMail(ShopSavedData data, UUID playerId) {
        if (data.purgeExpired(playerId,
                com.example.globalshop.core.servershop.ShopClock.nowMillis()) > 0) {
            data.setDirty();
        }
    }

    /**
     * Claims one mail.
     *
     * <p>Refused when the mail has lapsed or the backpack cannot hold it: a claimed mail that did
     * not fit would have to be dropped or deleted, and neither is acceptable.
     */
    public ShopResult claimMail(ServerPlayer player, long mailId) {
        ShopSavedData data = data();
        Mail mail;
        synchronized (data) {
            long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
            mail = null;
            for (Mail candidate : data.mailsOf(player.getUUID())) {
                if (candidate.id() == mailId) {
                    mail = candidate;
                    break;
                }
            }
            if (mail == null) {
                return ShopResult.MAIL_NOT_FOUND;
            }
            if (mail.isExpired(now)) {
                return ShopResult.MAIL_EXPIRED;
            }
            for (ItemStack stack : mail.items()) {
                if (!ItemStacks.wouldFit(player.getInventory(), stack)) {
                    // Room, not ownership: the player owns this, they just cannot hold it yet.
                    return ShopResult.NOT_ENOUGH_ROOM;
                }
            }
            // Take it out now, so a claim cannot be performed twice by clicking twice quickly.
            data.removeMail(player.getUUID(), mailId);
            data.setDirty();
        }
        for (ItemStack stack : mail.items()) {
            ItemStack remaining = ItemStacks.insertIntoInventory(player.getInventory(), stack);
            if (!remaining.isEmpty()) {
                // Cannot happen: every stack was checked for room under the same lock, and only
                // this thread can change this player's inventory in the meantime.
                ItemStacks.giveOrDrop(player, remaining);
            }
        }
        return ShopResult.SUCCESS;
    }

    /**
     * Deletes one mail without claiming it.
     *
     * <p>Allowed for lapsed mail as well — it is the only thing left to do with it — and it is what
     * the mailbox screen's right-click calls.
     *
     * @return whether a mail with that id was there
     */
    public boolean deleteMail(UUID playerId, long mailId) {
        ShopSavedData data = data();
        synchronized (data) {
            boolean removed = data.removeMail(playerId, mailId) != null;
            if (removed) {
                data.setDirty();
            }
            return removed;
        }
    }

    /** Whether a delivery would be refused: no backpack room and no mailbox space either. */
    public boolean cannotAccept(ServerPlayer player, ItemStack delivery) {
        if (ItemStacks.wouldFit(player.getInventory(), delivery)) {
            return false;
        }
        ShopSavedData data = data();
        synchronized (data) {
            return data.mailSpaceFull(player.getUUID(),
                    com.example.globalshop.core.servershop.ShopClock.nowMillis());
        }
    }

    /**
     * Hands a delivery to a player, mailing whatever does not fit. Used by the server shop too.
     *
     * @return whether anything had to be mailed
     */
    public boolean deliverOrMail(ServerPlayer player, List<ItemStack> delivery, Mail.Source source) {
        return mailIfItDoesNotFit(player, delivery, source);
    }

    public boolean hasMail(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            return data.hasMail(playerId);
        }
    }

    /**
     * Records a completed trade in the shared log, whatever kind it was.
     *
     * <p>One entry point for the market, the auction house, the official shop and private trades,
     * so a player's history has every way of buying in one list — which is the point of it. The
     * buyer is recorded as the actor and the seller as the seller, the same shape a market
     * purchase has, so both players' histories pick it up without any extra filtering.
     *
     * <p>A server-shop purchase has no player on the other side; its seller is left null, which
     * matches nobody and therefore shows in the buyer's history only.
     */
    public void logTrade(TradeLogEntry.Kind kind, UUID sourceId, UUID buyerId, String buyerName,
                         @Nullable UUID sellerId, @Nullable String sellerName, ItemStack template,
                         int quantity, long totalPrice) {
        int count = Math.max(1, quantity);
        ShopSavedData data = data();
        synchronized (data) {
            data.appendLog(new TradeLogEntry(data.nextSeq(),
                    com.example.globalshop.core.servershop.ShopClock.nowMillis(), kind, sourceId,
                    buyerId, buyerName, sellerId, sellerName, template.copyWithCount(1), count,
                    totalPrice / count, totalPrice), Config.tradeLogRetention());
            data.setDirty();
        }
    }

    /**
     * Files a delivery as mail without ever trying the backpack.
     *
     * <p>Added for the auction house, which is required to deliver a win to the mailbox and nowhere
     * else: a win must not depend on how much room the winner happens to have, and the player is
     * meant to collect it deliberately.
     *
     * @return whether it was accepted. {@code false} means the mailbox is full, and the caller is
     *         expected to keep hold of the goods and try again rather than drop them.
     */
    public boolean postMail(UUID playerId, List<ItemStack> items, Mail.Source source) {
        return fileMail(playerId, items, source);
    }

    /** Whether this player's mailbox could take one more delivery right now. */
    public boolean hasMailRoom(UUID playerId) {
        ShopSavedData data = data();
        synchronized (data) {
            return !data.mailSpaceFull(playerId,
                    com.example.globalshop.core.servershop.ShopClock.nowMillis());
        }
    }

    /** Drops per-player transient state when they disconnect. */
    public synchronized void forget(UUID playerId) {
        lastPurchaseAt.remove(playerId);
    }

    // ==================================================================
    // Internals
    // ==================================================================

    /**
     * Sends items to a player, or files them as mail when they are offline or have no room.
     *
     * <p>The mailbox is the second choice; the floor is not a choice at all. An item dropped at the
     * feet of a player whose backpack is full is eaten by a hopper or the despawn timer, which is
     * indistinguishable from destroying it — and the player paid for it.
     *
     * @return whether anything had to be mailed
     */
    private boolean deliver(UUID playerId, ItemStack stack) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return mailIfItDoesNotFit(online, List.of(stack), Mail.Source.MARKET);
        }
        return fileMail(playerId, List.of(stack), Mail.Source.MARKET);
    }

    /**
     * Places a delivery, mailing whatever does not fit, and tells the player when it does.
     *
     * @return whether anything had to be mailed
     */
    private boolean mailIfItDoesNotFit(ServerPlayer player, List<ItemStack> delivery,
                                       Mail.Source source) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : delivery) {
            ItemStack remaining = ItemStacks.insertIntoInventory(player.getInventory(), stack);
            if (!remaining.isEmpty()) {
                overflow.add(remaining);
            }
        }
        if (overflow.isEmpty()) {
            return false;
        }
        if (!fileMail(player.getUUID(), overflow, source)) {
            // Should be unreachable: every path that could need mail space is checked before it
            // takes money or items. If it ever happens, dropping is the lesser evil -- it can be
            // picked up again, and deleting cannot be undone.
            com.example.globalshop.GlobalShop.LOGGER.error(
                    "globalshop: mailbox full for {}, dropping {} stack(s) instead",
                    player.getGameProfile().getName(), overflow.size());
            for (ItemStack stack : overflow) {
                ItemStacks.giveOrDrop(player, stack);
            }
            return false;
        }
        int count = 0;
        for (ItemStack stack : overflow) {
            count += stack.getCount();
        }
        player.sendSystemMessage(Component.translatable("globalshop.mail.received", count));
        return true;
    }

    /** Files a delivery as one mail. @return whether it was accepted; false means the box is full */
    private boolean fileMail(UUID playerId, List<ItemStack> items, Mail.Source source) {
        ShopSavedData data = data();
        synchronized (data) {
            long now = com.example.globalshop.core.servershop.ShopClock.nowMillis();
            Mail mail = new Mail(data.nextMailId(), now, source, items);
            if (!data.addMail(playerId, mail, now)) {
                return false;
            }
            data.setDirty();
            return true;
        }
    }

    private synchronized boolean throttled(UUID playerId) {
        int limitMs = Config.purchaseCooldownMs();
        if (limitMs <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastPurchaseAt.get(playerId);
        if (last != null && now - last < limitMs) {
            return true;
        }
        lastPurchaseAt.put(playerId, now);
        return false;
    }

    private static ShopResult mapEconomy(TransactionResult result) {
        switch (result) {
            case SUCCESS:
                return ShopResult.SUCCESS;
            case INSUFFICIENT_FUNDS:
                return ShopResult.INSUFFICIENT_FUNDS;
            case BALANCE_LIMIT_EXCEEDED:
                return ShopResult.BALANCE_LIMIT_EXCEEDED;
            case INVALID_AMOUNT:
                return ShopResult.INVALID_QUANTITY;
            case SAME_ACCOUNT:
                return ShopResult.CANNOT_BUY_OWN;
            default:
                return ShopResult.ECONOMY_ERROR;
        }
    }

    /** Multiplies for display only; an overflow becomes "very large" rather than a negative. */
    private static long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** Comparator helper kept here so the GUI and commands agree on ordering. */
    public static Comparator<Listing> defaultOrder() {
        return ShopSort.DEFAULT.comparator();
    }
}
