package com.example.globalshop.social;

import com.example.globalshop.Config;
import com.example.globalshop.economy.EconomyService;
import com.example.globalshop.economy.EconomyServices;
import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.economy.TransactionResult;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.ConversationDataPacket;
import com.example.globalshop.shop.ItemPool;
import com.example.globalshop.shop.Mail;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.shop.ShopServices;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Private messages, the player directory, and paying another player by name.
 *
 * <h2>Who is in the directory</h2>
 * Everyone the economy has ever seen. The economy already records a name per account, so the
 * directory is derived rather than stored — there is no second list of players to fall out of step
 * with the first, and no need for a player to have logged in since the feature existed.
 *
 * <h2>Leaving a message for somebody offline</h2>
 * A message is accepted whether or not the recipient is online. What changes is only whether they
 * see it now or are told about it when they next log in. Refusing to accept it would mean the
 * feature only worked when both players happened to be awake at the same time.
 */
public final class SocialService {

    /** Players listed per page. The requirement is five, and it is also what fits the window. */
    public static final int PAGE_SIZE = 5;

    /** How many messages a conversation shows. Older ones stay on file but are not sent. */
    public static final int CONVERSATION_LIMIT = 60;

    private final MinecraftServer server;

    /** The last time each player sent something, for the anti-flood throttle. */
    private final Map<UUID, Long> lastSentAt = new java.util.HashMap<>();

    public SocialService(MinecraftServer server) {
        this.server = server;
    }

    /**
     * One line of the player directory.
     *
     * @param duplicateName whether another account carries this same name, so the screen can show
     *                      something that tells the two apart
     */
    public record Entry(UUID id, String name, boolean online, long balance, boolean duplicateName) {
    }

    /** One page of the directory, plus what the screen needs to draw its paging controls. */
    public record Page(List<Entry> entries, int page, int pageCount, int totalMatches) {
    }

    /** One message as the conversation screen shows it. */
    public record Line(UUID from, String fromName, String text, long time) {
    }

    private SocialSavedData data() {
        return SocialSavedData.get(server);
    }

    public boolean isOnline(UUID playerId) {
        return server.getPlayerList().getPlayer(playerId) != null;
    }

    @Nullable
    public String nameOf(UUID playerId) {
        return EconomyServices.get(server).getLastKnownName(playerId).orElse(null);
    }

    /**
     * The directory, filtered and paged.
     *
     * <p>Online players come first, then alphabetical order. That ordering is the whole point of a
     * directory you use to start a conversation: the people you can talk to right now should not be
     * something you have to search a hundred offline names for.
     */
    public Page directory(String query, int requestedPage) {
        EconomyService economy = EconomyServices.get(server);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Entry> matches = new ArrayList<>();
        java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();
        for (Map.Entry<UUID, Long> account : economy.allBalances().entrySet()) {
            UUID id = account.getKey();
            String name = economy.getLastKnownName(id).orElse(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            nameCounts.merge(name.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }

        for (Map.Entry<UUID, Long> account : economy.allBalances().entrySet()) {
            UUID id = account.getKey();
            String name = economy.getLastKnownName(id).orElse(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            // Counted over every account rather than over the page: two accounts with the same
            // name can land on different pages, and then neither would be marked.
            boolean duplicate = nameCounts.getOrDefault(name.toLowerCase(Locale.ROOT), 0) > 1;
            matches.add(new Entry(id, name, isOnline(id), account.getValue(), duplicate));
        }
        matches.sort(Comparator
                .comparing(Entry::online).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));

        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(Math.max(0, requestedPage), pageCount - 1);
        int from = page * PAGE_SIZE;
        int to = Math.min(matches.size(), from + PAGE_SIZE);
        return new Page(List.copyOf(matches.subList(from, to)), page, pageCount, matches.size());
    }

    /** Whether this id names an account at all. Used to refuse messages to nobody. */
    public boolean isKnown(UUID playerId) {
        return EconomyServices.get(server).hasAccount(playerId);
    }

    /** The conversation between two players, newest first. */
    public List<Line> conversation(UUID viewer, UUID partner) {
        SocialSavedData data = data();
        synchronized (data) {
            List<DirectMessage> found = data.conversation(viewer, partner, CONVERSATION_LIMIT);
            List<Line> lines = new ArrayList<>(found.size());
            for (DirectMessage message : found) {
                lines.add(new Line(message.from(), message.fromName(), message.text(), message.time()));
            }
            return List.copyOf(lines);
        }
    }

    /**
     * Sends one private message.
     *
     * <p>Refused only for reasons the sender can act on: an empty or over-long message, sending to
     * yourself, or a name that is not a player. Being offline is deliberately not one of them.
     */
    public ShopResult send(ServerPlayer from, UUID toId, String text) {
        if (toId == null || toId.equals(from.getUUID())) {
            return ShopResult.CANNOT_MESSAGE_SELF;
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return ShopResult.MESSAGE_EMPTY;
        }
        if (trimmed.length() > DirectMessage.MAX_LENGTH) {
            return ShopResult.MESSAGE_TOO_LONG;
        }
        if (!isKnown(toId)) {
            return ShopResult.UNKNOWN_PLAYER;
        }
        if (throttled(from.getUUID())) {
            return ShopResult.COOLDOWN;
        }
        SocialSavedData data = data();
        synchronized (data) {
            data.append(new DirectMessage(data.nextId(), from.getUUID(),
                            from.getGameProfile().getName(), toId, trimmed, System.currentTimeMillis(),
                            false),
                    Config.tradeLogRetention());
            data.setDirty();
        }
        // If they are here, tell them now; if not, the login notice will.
        ServerPlayer online = server.getPlayerList().getPlayer(toId);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("globalshop.social.incoming",
                    from.getGameProfile().getName(), trimmed));
        }
        return ShopResult.SUCCESS;
    }

    /**
     * Tells a player about messages that arrived while they were away.
     *
     * <p>Called on login. The notice names the place to read them, because a message the player
     * cannot find is the same as a message that was never delivered. Delivered once: a player who
     * logs in five times is told once, not five times.
     *
     * @return how many messages they were told about
     */
    public int announceOnLogin(ServerPlayer player) {
        SocialSavedData data = data();
        List<DirectMessage> waiting;
        synchronized (data) {
            waiting = data.unnotified(player.getUUID());
            if (waiting.isEmpty()) {
                return 0;
            }
            for (DirectMessage message : waiting) {
                message.markDelivered();
            }
            data.setDirty();
        }
        String lastFrom = waiting.get(waiting.size() - 1).fromName();
        player.sendSystemMessage(Component.translatable("globalshop.social.waiting", waiting.size()));
        player.sendSystemMessage(Component.translatable("globalshop.social.where", lastFrom));
        return waiting.size();
    }

    /** How many messages are waiting to be announced. Diagnostics and tests. */
    public int unnotifiedCount(UUID playerId) {
        SocialSavedData data = data();
        synchronized (data) {
            return data.unnotified(playerId).size();
        }
    }

    /**
     * Pays another player directly.
     *
     * <p>The same atomic transfer the {@code /pay} command uses, so a payment from the window and a
     * payment from the command cannot behave differently. The recipient may be offline: the balance
     * is keyed by UUID, so the money is theirs either way.
     */
    public TransactionResult pay(ServerPlayer from, UUID toId, long amount) {
        if (toId == null || toId.equals(from.getUUID())) {
            return TransactionResult.SAME_ACCOUNT;
        }
        if (amount <= 0L) {
            return TransactionResult.INVALID_AMOUNT;
        }
        if (!isKnown(toId)) {
            return TransactionResult.NO_ACCOUNT;
        }
        return EconomyServices.get(server).transfer(from.getUUID(), toId, amount);
    }

    /** The sender's balance, for the window to show beside the payment field. */
    public long balanceOf(UUID playerId) {
        return EconomyServices.get(server).getBalance(playerId);
    }

    // ==================================================================
    // Offers inside a conversation
    // ==================================================================

    /** The offer between two players, or {@code null}. */
    @Nullable
    public TradeOffer offerBetween(UUID a, UUID b) {
        SocialSavedData data = data();
        synchronized (data) {
            return data.findOffer(a, b);
        }
    }

    /**
     * Offers whatever is in the seller's offer slot for sale to one player.
     *
     * <p>The item is taken out of the slot and held by the offer. It is no longer the seller's to
     * spend, drop or sell elsewhere — which is the whole reason a buyer can safely agree a price.
     *
     * <p>The offer is exactly the contents of that slot and nothing else: a trade that quietly
     * helped itself to the rest of the seller's backpack would be a nasty surprise.
     */
    public ShopResult offerTo(ServerPlayer seller, UUID buyerId, List<ItemPool> pools) {
        if (buyerId == null || buyerId.equals(seller.getUUID())) {
            return ShopResult.CANNOT_MESSAGE_SELF;
        }
        if (!isKnown(buyerId)) {
            return ShopResult.UNKNOWN_PLAYER;
        }
        ItemPool slot = pools.isEmpty() ? null : pools.get(0);
        if (slot == null || slot.slotCount() < 1 || slot.get(0).isEmpty()) {
            return ShopResult.EMPTY_ITEM;
        }
        SocialSavedData data = data();
        synchronized (data) {
            if (data.findOffer(seller.getUUID(), buyerId) != null) {
                return ShopResult.TRADE_ALREADY_OPEN;
            }
            ItemStack offered = slot.get(0).copy();
            slot.set(0, ItemStack.EMPTY);
            slot.markChanged();
            data.putOffer(new TradeOffer(seller.getUUID(), seller.getGameProfile().getName(),
                    buyerId, nameOf(buyerId), offered, 0L, false, System.currentTimeMillis()));
            data.setDirty();
        }
        ServerPlayer other = server.getPlayerList().getPlayer(buyerId);
        if (other != null) {
            other.sendSystemMessage(Component.translatable("globalshop.social.offer_made",
                    seller.getGameProfile().getName()));
        }
        announceOfferChange(seller.getUUID(), buyerId);
        return ShopResult.SUCCESS;
    }

    /**
     * Publishes a price on the offer between two players.
     *
     * <p>Either of them may: the price is one shared number both can see and change, not two private
     * ones that have to be reconciled. Changing it is visible to the other side at once.
     */
    public ShopResult publishPrice(ServerPlayer actor, UUID partnerId, long price) {
        if (price <= 0L || price > Config.maxUnitPrice()) {
            return ShopResult.INVALID_PRICE;
        }
        SocialSavedData data = data();
        synchronized (data) {
            TradeOffer offer = data.findOffer(actor.getUUID(), partnerId);
            if (offer == null) {
                return ShopResult.NO_TRADE_OPEN;
            }
            offer.publishPrice(price);
            data.setDirty();
        }
        announceOfferChange(actor.getUUID(), partnerId);
        return ShopResult.SUCCESS;
    }

    /** Withdraws an offer, returning the held items to the seller. */
    public ShopResult withdraw(ServerPlayer seller, UUID partnerId) {
        TradeOffer removed;
        SocialSavedData data = data();
        synchronized (data) {
            TradeOffer offer = data.findOffer(seller.getUUID(), partnerId);
            if (offer == null) {
                return ShopResult.NO_TRADE_OPEN;
            }
            if (!offer.isSeller(seller.getUUID())) {
                return ShopResult.NOT_YOUR_TRADE;
            }
            removed = data.removeOffer(seller.getUUID(), partnerId);
            data.setDirty();
        }
        if (removed != null) {
            // Back to the seller, or their mailbox when they have no room — the same rule as every
            // other delivery in this mod.
            ShopServices.get(server).deliverOrMail(seller, List.of(removed.item()),
                    Mail.Source.MARKET);
        }
        announceOfferChange(seller.getUUID(), partnerId);
        return ShopResult.SUCCESS;
    }

    /**
     * The buyer pays the published price and receives the held item.
     *
     * <p>{@code expectedPrice} is the number the buyer was looking at when they clicked. If the offer
     * has moved since, the payment is refused rather than taken — otherwise a seller could raise the
     * price in the instant before the click and have the new one paid.
     *
     * <p>The money moves and the offer is removed in the same locked step, so the two cannot come
     * apart. The item is handed over afterwards with the lock released, because placing items touches
     * the buyer's inventory.
     */
    public ShopResult payOffer(ServerPlayer buyer, UUID partnerId, long expectedPrice) {
        TradeOffer offer;
        SocialSavedData data = data();
        synchronized (data) {
            offer = data.findOffer(buyer.getUUID(), partnerId);
            if (offer == null) {
                return ShopResult.NO_TRADE_OPEN;
            }
            if (!offer.isBuyer(buyer.getUUID())) {
                return ShopResult.NOT_YOUR_TRADE;
            }
            if (!offer.priced()) {
                return ShopResult.TRADE_NOT_PRICED;
            }
            if (offer.price() != expectedPrice) {
                return ShopResult.PRICE_CHANGED;
            }
            EconomyService economy = EconomyServices.get(server);
            if (!economy.hasEnough(buyer.getUUID(), offer.price())) {
                return ShopResult.INSUFFICIENT_FUNDS;
            }
            TransactionResult paid = economy.transfer(buyer.getUUID(), offer.sellerId(),
                    offer.price());
            if (!paid.isSuccess()) {
                return ShopResult.ECONOMY_ERROR;
            }
            data.removeOffer(buyer.getUUID(), partnerId);
            data.setDirty();
        }
        // No commission: this is one player paying another, exactly like /pay. Charging the market's
        // cut here would make the same transfer cost different amounts depending on the window.
        ShopServices.get(server).deliverOrMail(buyer, List.of(offer.item()), Mail.Source.MARKET);
        ServerPlayer seller = server.getPlayerList().getPlayer(offer.sellerId());
        if (seller != null) {
            seller.sendSystemMessage(Component.translatable("globalshop.social.offer_sold",
                    buyer.getGameProfile().getName(),
                    MoneyFormat.formatWithName(offer.price())));
        }
        announceOfferChange(buyer.getUUID(), partnerId);
        // Into the shared history as a private trade. One entry is enough here: the buyer paid and
        // the seller offered, so the ordinary "bought from / sold to" phrasing is already correct
        // from both sides, and the tag says how it was agreed.
        ShopServices.get(server).logTrade(
                com.example.globalshop.shop.TradeLogEntry.Kind.PRIVATE_TRADE, null, buyer.getUUID(),
                buyer.getGameProfile().getName(), offer.sellerId(), offer.sellerName(),
                offer.item(), offer.item().getCount(), offer.price());
        return ShopResult.SUCCESS;
    }

    /**
     * Tells both sides that an offer changed, so the other window updates without being asked.
     *
     * <p>Sent to each of them as <em>their</em> conversation with the other. A client looking at a
     * different conversation ignores it rather than being dragged into this one.
     */
    private void announceOfferChange(UUID actor, UUID partner) {
        for (UUID viewer : List.of(actor, partner)) {
            ServerPlayer online = server.getPlayerList().getPlayer(viewer);
            if (online == null) {
                continue;
            }
            UUID other = viewer.equals(actor) ? partner : actor;
            ModNetwork.sendToPlayer(online, ConversationDataPacket.build(this, online, other));
        }
    }

    /**
     * Withdraws every offer this player has up for sale, returning the items.
     *
     * <p>Called when their trade window closes and again on login, so that an offer exists only
     * while its window is open. The alternative — an offer that outlives the window — leaves the
     * seller holding goods they cannot see and did not mean to leave on the table, which reads as
     * the item having vanished. Handing it back is the reading that cannot lose anything.
     *
     * @return how many offers were taken back
     */
    public int withdrawAll(ServerPlayer seller) {
        List<TradeOffer> mine = new ArrayList<>();
        SocialSavedData data = data();
        synchronized (data) {
            for (TradeOffer offer : data.offersOf(seller.getUUID())) {
                if (offer.isSeller(seller.getUUID())) {
                    mine.add(offer);
                }
            }
            for (TradeOffer offer : mine) {
                data.removeOffer(offer.sellerId(), offer.buyerId());
            }
            if (!mine.isEmpty()) {
                data.setDirty();
            }
        }
        for (TradeOffer offer : mine) {
            // Back to the seller, or their mailbox when there is no room — the same rule as every
            // other delivery in this mod, so closing a window can never destroy an item.
            ShopServices.get(server).deliverOrMail(seller, List.of(offer.item()),
                    Mail.Source.MARKET);
            ServerPlayer buyer = server.getPlayerList().getPlayer(offer.buyerId());
            if (buyer != null) {
                buyer.sendSystemMessage(Component.translatable("globalshop.social.offer_withdrawn",
                        seller.getGameProfile().getName()));
            }
        }
        return mine.size();
    }

    /**
     * Offers the stack the player is holding.
     *
     * <p>The command form of dropping an item into the window's slot: the same rules and the same
     * escrow, with a different way of naming the item. The held stack is passed through the same
     * one-slot container the window uses, so both paths cannot drift apart.
     */
    public ShopResult offerHeld(ServerPlayer seller, UUID buyerId, ItemStack held) {
        if (held == null || held.isEmpty()) {
            return ShopResult.EMPTY_ITEM;
        }
        net.minecraft.world.SimpleContainer carrying = new net.minecraft.world.SimpleContainer(1);
        carrying.setItem(0, held.copy());
        ShopResult result = offerTo(seller, buyerId,
                List.of(new com.example.globalshop.shop.ContainerPool(carrying)));
        if (result.isSuccess()) {
            // The offer holds the item now, so it leaves the hand.
            held.setCount(0);
            seller.getInventory().setChanged();
        }
        return result;
    }

    public String formatMoney(long amount) {        return MoneyFormat.formatWithName(amount);
    }

    private boolean throttled(UUID playerId) {
        int limitMs = Config.purchaseCooldownMs();
        if (limitMs <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastSentAt.put(playerId, now);
        return last != null && now - last < limitMs;
    }

    /** Drops per-player transient state when they disconnect. */
    public synchronized void forget(UUID playerId) {
        lastSentAt.remove(playerId);
    }

    /** Clears the throttle. Used by the self test, which sends faster than a person can. */
    public synchronized void clearThrottleForTest() {
        lastSentAt.clear();
    }
}
