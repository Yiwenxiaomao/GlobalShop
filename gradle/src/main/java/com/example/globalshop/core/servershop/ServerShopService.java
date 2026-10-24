package com.example.globalshop.core.servershop;

import com.example.globalshop.Config;
import com.example.globalshop.GlobalShop;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.api.economy.TransactionResult;
import com.example.globalshop.core.registry.ModItems;
import com.example.globalshop.core.market.ContainerPool;
import com.example.globalshop.core.market.ItemPool;
import com.example.globalshop.core.market.ItemStacks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.market.ShopServices;
import com.example.globalshop.core.social.Mail;
import com.example.globalshop.feature.tradelog.TradeLogEntry;

/**
 * The official server shop: an operator-configured catalogue that buys from and sells to
 * players out of nothing.
 *
 * <h2>Two different shops, one purse</h2>
 * This is deliberately not the player market. The market moves items between players and needs
 * escrow, ownership and stock; the server shop has no counterparty, so its stock is infinite
 * and there is nothing to escrow. What the two <em>do</em> share is the economy: every amount
 * below goes through the same {@link EconomyService}, so {@code /money}, {@code /pay}, the
 * market and this shop all read and write one balance. There is no second currency here.
 *
 * <h2>The one limit that matters</h2>
 * Buying is unlimited. Selling <em>to</em> the server is not, because that is the direction in
 * which a player can turn items into money without another player's consent. Two rules guard
 * it: the buy-back price must be strictly below the sale price (checked when a line is added
 * <em>and</em> again on every trade), and each player has a daily allowance counted in items,
 * shared across every catalogue line.
 *
 * <h2>Everything is validated on the server</h2>
 * These methods are the only way to move anything. The client sends "buy this line, this many
 * times" and is told the outcome; it never decides whether a trade is allowed.
 */
public final class ServerShopService {

    private final MinecraftServer server;

    /** uuid -> last trade attempt, for the anti-flood throttle. */
    private final Map<UUID, Long> lastTrade = new HashMap<>();

    public ServerShopService(MinecraftServer server) {
        this.server = server;
    }

    private ServerShopSavedData data() {
        return ServerShopSavedData.get(server);
    }

    private EconomyService economy() {
        return EconomyServices.get(server);
    }

    // ==================================================================
    // Catalogue
    // ==================================================================

    public List<ServerShopEntry> entries() {
        return data().entries();
    }

    /**
     * The lines one page shows.
     *
     * <p>The recycling page lists only what the server will buy back, so a player is never
     * offered an item that would be refused. The management page lists everything, including
     * lines that are buy-only, because an operator editing the catalogue needs to see all of it.
     */
    public List<ServerShopEntry> entriesFor(ServerShopTab tab) {
        List<ServerShopEntry> all = entries();
        if (tab == ServerShopTab.MANAGE) {
            return all;
        }
        List<ServerShopEntry> matching = new ArrayList<>();
        for (ServerShopEntry entry : all) {
            boolean fits = tab == ServerShopTab.RECYCLE
                    ? entry.allowBuyback() && entry.buybackPrice() > 0L
                    : entry.sellPrice() > 0L;
            if (fits) {
                matching.add(entry);
            }
        }
        return matching;
    }

    /** True when this player may write the catalogue. Re-checked on every edit, not just here. */
    public boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    public int size() {
        return data().size();
    }

    @Nullable
    public ServerShopEntry find(ItemStack probe) {
        return data().find(probe);
    }

    /**
     * Adds a catalogue line, or restocks the existing line for the same item.
     *
     * <p>A line may be sale-only, buy-back-only, or both:
     *
     * <ul>
     *   <li>a sale price above zero means the server sells it;</li>
     *   <li>a buy-back price above zero means the server buys it from players;</li>
     *   <li>a sale price of zero is a <em>recycling-only</em> line — something the server wants
     *       to take in and never hands out. Requiring a sale price here would have made it
     *       impossible to stock, which is what an earlier version did;</li>
     *   <li>both zero is a line that does nothing and is refused.</li>
     * </ul>
     *
     * <p>When there is a sale price, the buy-back price is required to be strictly below it. If
     * it were not, a player could buy an item and immediately sell it back for more, which is a
     * money printer with extra steps. The check is here so a bad line can never enter the
     * catalogue, and again in {@link #recycle} so a line that somehow got in cannot be exploited.
     * A recycling-only line has no such pair to compare, and cannot be bought at all.
     */
    public ServerShopResult add(ItemStack template, long sellPrice, long buybackPrice,
                                int bundleSize, boolean allowBuyback, int purchaseLimit) {
        if (template == null || template.isEmpty()) {
            return ServerShopResult.EMPTY_ITEM;
        }
        if (sellPrice < 0L || sellPrice > Config.maxUnitPrice()) {
            return ServerShopResult.INVALID_PRICE;
        }
        if (buybackPrice < 0L) {
            return ServerShopResult.INVALID_PRICE;
        }
        if (allowBuyback && buybackPrice <= 0L) {
            return ServerShopResult.INVALID_PRICE;
        }
        ServerShopResult pairProblem = pricePairProblem(sellPrice, buybackPrice);
        if (pairProblem != null) {
            return pairProblem;
        }
        if (bundleSize < 1 || bundleSize > Config.maxPerPurchase()) {
            return ServerShopResult.INVALID_BUNDLE;
        }
        if (purchaseLimit < 0) {
            return ServerShopResult.INVALID_LIMIT;
        }

        ServerShopSavedData data = data();
        synchronized (data) {
            data.put(template, sellPrice, buybackPrice, bundleSize, allowBuyback, purchaseLimit);
            data.setDirty();
        }
        return ServerShopResult.SUCCESS;
    }

    /**
     * The rule that stops a line from being a money printer: if the server sells an item, it must
     * buy it back for strictly less.
     *
     * <h2>Why this is one method and not two checks</h2>
     * It used to be written out twice — once where a line is created, once where it is traded —
     * and the two drifted. A recycling-only line (no sale price) was allowed into the catalogue,
     * but the copy on the trading path compared its buy-back price against a sale price of zero,
     * concluded that 20 was not below 0, and refused every attempted sale. The line existed and
     * could never be used. One method, called from both places, is what stops that happening
     * again.
     *
     * <p>A recycling-only line has no sale price to undercut, so the comparison does not apply to
     * it; it is also unbuyable, so there is nothing to exploit.
     *
     * @return the refusal to send back, or {@code null} when the pair is acceptable
     */
    @Nullable
    private static ServerShopResult pricePairProblem(long sellPrice, long buybackPrice) {
        if (sellPrice < 0L || buybackPrice < 0L) {
            return ServerShopResult.INVALID_PRICE;
        }
        if (sellPrice == 0L && buybackPrice == 0L) {
            // Neither for sale nor bought back: there is nothing this line could ever do.
            return ServerShopResult.INVALID_PRICE;
        }
        if (sellPrice > 0L && buybackPrice >= sellPrice) {
            return ServerShopResult.BUYBACK_NOT_BELOW_SALE;
        }
        return null;
    }

    public ServerShopResult remove(ItemStack probe) {
        ServerShopSavedData data = data();
        synchronized (data) {
            if (data.remove(probe) == null) {
                return ServerShopResult.NOT_IN_CATALOG;
            }
            data.setDirty();
        }
        return ServerShopResult.SUCCESS;
    }

    public List<ServerShopSavedData.LogLine> recentLog(int limit) {
        return data().recentLog(limit);
    }

    public int logSize() {
        return data().logSize();
    }

    // ==================================================================
    // Limits
    // ==================================================================

    public int dailyLimit() {
        return Config.serverShopDailyRecycleLimit();
    }

    /** Items this player has already sold back today. */
    public int recycledToday(UUID playerId) {
        return data().recycledToday(playerId);
    }

    /**
     * Items this player may still sell back today.
     *
     * <p>Reads through {@link ServerShopSavedData#recycledToday}, which compares the stored date
     * with today's real-world date — so a server that was switched off across midnight reports
     * a full allowance the moment it comes back, with no timer and no catch-up pass.
     */
    public int remainingToday(UUID playerId) {
        return Math.max(0, dailyLimit() - recycledToday(playerId));
    }

    /** Operator override, for when a quota is wrong rather than earned. */
    public void resetDaily(@Nullable UUID playerId) {
        ServerShopSavedData data = data();
        synchronized (data) {
            data.resetDaily(playerId);
            data.setDirty();
        }
    }

    // ==================================================================
    // Buying
    // ==================================================================

    /**
     * Sells {@code actions} bundles to the player.
     *
     * <p>Unlimited apart from the line's own purchase limit: the shop's stock is infinite by
     * definition, so nothing here counts down.
     *
     * <p>Money is taken before the items are handed over, matching the market. That is safe in
     * this direction because handing over items cannot fail — anything that does not fit the
     * backpack is dropped at the player's feet.
     */
    public Outcome buy(ServerPlayer player, ServerShopEntry entry, int actions) {
        if (entry == null) {
            return Outcome.of(ServerShopResult.NOT_IN_CATALOG);
        }
        if (actions < 1) {
            return Outcome.of(ServerShopResult.INVALID_QUANTITY);
        }
        if (throttled(player)) {
            return Outcome.of(ServerShopResult.COOLDOWN);
        }
        // Bound the multiplication before doing it, so a huge request cannot overflow.
        if ((long) actions * entry.bundleSize() > Config.maxPerPurchase() * 64L) {
            return Outcome.of(ServerShopResult.INVALID_QUANTITY);
        }
        int items = entry.bundleSize() * actions;

        int allowed = entry.remainingFor(player.getUUID());
        if (allowed != Integer.MAX_VALUE && items > allowed) {
            return Outcome.of(ServerShopResult.PURCHASE_LIMIT_REACHED);
        }

        long cost;
        try {
            cost = Math.multiplyExact(entry.sellPrice(), (long) items);
        } catch (ArithmeticException overflow) {
            return Outcome.of(ServerShopResult.INVALID_PRICE);
        }

        ServerShopSavedData data = data();
        synchronized (data) {
            UUID id = player.getUUID();
            EconomyService economy = economy();
            economy.ensureAccount(player, Config.initialBalance());
            if (!economy.hasEnough(id, cost)) {
                return Outcome.of(ServerShopResult.INSUFFICIENT_FUNDS);
            }
            TransactionResult paid = economy.removeBalance(id, cost);
            if (!paid.isSuccess()) {
                return Outcome.of(ServerShopResult.INSUFFICIENT_FUNDS);
            }

            give(player, entry.template(), items);
            entry.recordPurchase(id, items);
            data.appendLog(new ServerShopSavedData.LogLine(data.nextSeq(), System.currentTimeMillis(),
                    ServerShopSavedData.Action.BUY, id, player.getGameProfile().getName(),
                    entry.template(), items, cost));
            data.setDirty();
        }
        // Also into the shared history, so an official-shop purchase sits beside the player's market
        // and auction trades instead of in a second log nobody opens. Done outside the lock above,
        // which is a different lock from the one the shared log takes.
        com.example.globalshop.core.market.ShopServices.get(server).logTrade(
                com.example.globalshop.feature.tradelog.TradeLogEntry.Kind.SERVER_SHOP, null, player.getUUID(),
                player.getGameProfile().getName(), null, null, entry.template(), items, cost);
        return new Outcome(ServerShopResult.SUCCESS, items, cost);
    }

    // ==================================================================
    // Selling back
    // ==================================================================

    /** Sells exactly one item back. */
    public Outcome recycleOne(ServerPlayer player, ServerShopEntry entry) {
        return recycle(player, entry, 1);
    }

    /**
     * Sells the whole stack back, as far as the daily allowance permits.
     *
     * <p>When the player is carrying more than they may sell today, only the remainder of the
     * allowance is taken. The caller is told how many were actually taken and how many were
     * left, so the message can say so rather than silently keeping items back.
     */
    public Outcome recycleAll(ServerPlayer player, ServerShopEntry entry) {
        return recycle(player, entry, -1);
    }

    /**
     * @param requested how many items to take, or {@code -1} for "as many as allowed"
     */
    private Outcome recycle(ServerPlayer player, ServerShopEntry entry, int requested) {
        if (entry == null) {
            return Outcome.of(ServerShopResult.NOT_IN_CATALOG);
        }
        if (entry.template().isEmpty()) {
            return Outcome.of(ServerShopResult.EMPTY_ITEM);
        }
        if (!entry.allowBuyback()) {
            return Outcome.of(ServerShopResult.BUYBACK_DISABLED);
        }
        if (entry.buybackPrice() <= 0L) {
            return Outcome.of(ServerShopResult.INVALID_PRICE);
        }
        // The same rule as at add time, through the same method — a second copy is what let a
        // recycling-only line be stocked and then refused at the counter.
        ServerShopResult pairProblem = pricePairProblem(entry.sellPrice(), entry.buybackPrice());
        if (pairProblem != null) {
            return Outcome.of(pairProblem);
        }
        if (throttled(player)) {
            return Outcome.of(ServerShopResult.COOLDOWN);
        }

        ServerShopSavedData data = data();
        synchronized (data) {
            UUID id = player.getUUID();
            int remainingToday = remainingToday(id);
            if (remainingToday <= 0) {
                return Outcome.of(ServerShopResult.DAILY_QUOTA_EXHAUSTED);
            }

            List<ItemPool> pools = List.of(new ContainerPool(player.getInventory()));
            int carried = ItemStacks.countMatching(entry.template(), pools);
            if (carried <= 0) {
                return Outcome.of(ServerShopResult.NOT_ENOUGH_ITEMS);
            }

            int count = (requested < 0) ? Math.min(carried, remainingToday) : requested;
            count = Math.min(count, remainingToday);
            count = Math.min(count, carried);
            if (count <= 0) {
                return Outcome.of(ServerShopResult.NOTHING_TO_RECYCLE);
            }

            long income;
            try {
                income = Math.multiplyExact(entry.buybackPrice(), (long) count);
            } catch (ArithmeticException overflow) {
                return Outcome.of(ServerShopResult.INVALID_PRICE);
            }

            EconomyService economy = economy();
            economy.ensureAccount(player, Config.initialBalance());
            // Check the credit will be accepted BEFORE taking the items, so a player can never
            // end up lighter by the goods and no richer.
            if (economy.getBalance(id) > Config.maxBalance() - income) {
                return Outcome.of(ServerShopResult.BALANCE_LIMIT_EXCEEDED);
            }

            if (!ItemStacks.escrow(entry.template(), count, pools)) {
                // The count was taken a moment ago and nothing else runs in between, so this
                // means the inventory is not what we just measured. Refuse rather than guess.
                return Outcome.of(ServerShopResult.NOT_ENOUGH_ITEMS);
            }

            TransactionResult credited = economy.addBalance(id, income);
            if (!credited.isSuccess()) {
                give(player, entry.template(), count);
                return Outcome.of(ServerShopResult.ECONOMY_ERROR);
            }

            data.recordRecycle(id, count);
            data.appendLog(new ServerShopSavedData.LogLine(data.nextSeq(), System.currentTimeMillis(),
                    ServerShopSavedData.Action.RECYCLE, id, player.getGameProfile().getName(),
                    entry.template(), count, income));
            data.setDirty();
            return new Outcome(ServerShopResult.SUCCESS, count, income);
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Hands over {@code total} items, in valid stack sizes.
     *
     * <p>Anything that does not fit goes to the player's mailbox rather than onto the floor. By the
     * time this runs the shop has already taken payment, so an item dropped at a full player's feet
     * is a purchase destroyed by a hopper or the despawn timer.
     */
    private void give(ServerPlayer player, ItemStack template, int total) {
        int max = Math.max(1, template.getMaxStackSize());
        java.util.List<ItemStack> chunks = new java.util.ArrayList<>();
        int left = total;
        while (left > 0) {
            int chunk = Math.min(max, left);
            chunks.add(template.copyWithCount(chunk));
            left -= chunk;
        }
        com.example.globalshop.core.market.ShopServices.get(player).deliverOrMail(player, chunks,
                com.example.globalshop.core.social.Mail.Source.SERVER_SHOP);
    }

    /**
     * The anti-flood throttle, shared with the market's setting.
     *
     * <p>The timestamp is recorded before the request is validated, exactly as the market does,
     * so a client that spams invalid requests is slowed down just like one that spams valid
     * ones.
     */
    private boolean throttled(ServerPlayer player) {
        int cooldown = Config.purchaseCooldownMs();
        if (cooldown <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastTrade.get(player.getUUID());
        lastTrade.put(player.getUUID(), now);
        return last != null && now - last < cooldown;
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    /**
     * Applies the time settings and stocks the built-in lines on a brand-new save.
     *
     * <p>Called once when the server starts. The online-clock lookup is fired here, in the
     * background, so that no trade ever waits on the network.
     */
    public void onServerStarted() {
        applyClockSettings();

        ServerShopSavedData data = data();
        synchronized (data) {
            int applied = data.seededVersion();
            if (applied > 0 && applied < ServerShopSavedData.DEFAULTS_VERSION) {
                GlobalShop.LOGGER.info(
                        "globalshop: server shop defaults version {} has been applied over version {}",
                        ServerShopSavedData.DEFAULTS_VERSION, applied);
            }

            // Which of the built-in lines should exist is decided by the config, and that is
            // reconciled on every start rather than only during a version bump.
            //
            // A one-shot migration was tried first and was not good enough: it marked the world
            // as up to date even on the run where the config still said to stock a line, so the
            // correction was spent without taking effect and never came round again. Presence is
            // an invariant that can simply be re-established.
            //
            // Prices are written only when a line is first created, so an operator's edits to a
            // built-in line are never overwritten. The cost of this design is that removing a
            // built-in line by hand is undone on the next start; the config flag is the supported
            // way to be rid of it.
            reconcileBuiltIn(data, new ItemStack(ModItems.SHOP_TERMINAL.get()),
                    Config.serverShopSellMarketTerminal(), Config.serverShopMarketTerminalPrice(),
                    Config.serverShopMarketTerminalLimit());
            reconcileBuiltIn(data, new ItemStack(ModItems.SERVER_SHOP_TERMINAL.get()),
                    Config.serverShopSellServerTerminal(), Config.serverShopServerTerminalPrice(),
                    Config.serverShopServerTerminalLimit());

            data.markSeeded();
            data.setDirty();
        }
    }

    /**
     * Makes one built-in line match what the config asks for.
     *
     * <p>Adds it when wanted and absent, removes it when unwanted and present, and leaves it
     * completely alone when it already exists — which is what keeps an operator's prices.
     */
    private void reconcileBuiltIn(ServerShopSavedData data, ItemStack probe, boolean wanted,
                                  long price, int limit) {
        ServerShopEntry existing = data.find(probe);
        if (wanted && existing == null) {
            data.put(probe, price, 0L, 1, false, limit);
        } else if (!wanted && existing != null) {
            data.remove(probe);
        }
    }

    /** Re-applies config changes to the running server. */
    public void reload() {
        applyClockSettings();
        ServerShopSavedData data = data();
        synchronized (data) {
            data.setDirty();
        }
    }

    private void applyClockSettings() {
        ShopClock.configure(Config.serverShopTimezone(), Config.serverShopUseOnlineTime(),
                Config.serverShopNtpHost(), Config.serverShopNtpTimeoutMs());
        if (Config.serverShopUseOnlineTime()) {
            ShopClock.refreshOnlineTimeAsync();
        }
    }

    /** Drops counters for deleted accounts, so the save cannot grow forever. */
    public void forget(java.util.Set<UUID> known) {
        ServerShopSavedData data = data();
        synchronized (data) {
            data.forgetPlayers(known);
            data.setDirty();
        }
    }

    /**
     * Forgets the anti-flood throttle.
     *
     * <p>Only the self test uses this. Real callers get the throttle, which is the point of it;
     * a test that had to sleep between every trade would be slower and would still be guessing
     * at the timing.
     */
    public void clearThrottleForTest() {
        lastTrade.clear();
    }

    // ==================================================================
    // Results
    // ==================================================================

    /**
     * What a trade actually did.
     *
     * <p>Richer than the enum alone because "recycle all" can legitimately succeed for fewer
     * items than the player is carrying, and the message has to be able to say so.
     *
     * @param items how many items changed hands
     * @param money how much money moved, always in the direction the outcome implies
     */
    public record Outcome(ServerShopResult result, int items, long money) {

        public boolean isSuccess() {
            return result.isSuccess();
        }

        static Outcome of(ServerShopResult result) {
            return new Outcome(result, 0, 0L);
        }
    }
}
