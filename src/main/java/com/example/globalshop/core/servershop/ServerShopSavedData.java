package com.example.globalshop.core.servershop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import com.example.globalshop.core.market.ShopSavedData;
import com.example.globalshop.feature.tradelog.TradeLogEntry;

/**
 * Server-side persistence for the official server shop.
 *
 * <p>Separate from {@link com.example.globalshop.core.market.ShopSavedData} on purpose: the player
 * market and the server shop are different features with different lifetimes, and an operator
 * clearing the market should not disturb the catalogue or anyone's daily recycling quota. Both
 * live in the same world save, and both go through the same economy.
 *
 * <p>What is stored here:
 * <ul>
 *   <li>the catalogue an operator configured;</li>
 *   <li>how many of each line every player has bought, for the optional purchase limit;</li>
 *   <li>how many items every player has recycled today, with the date it refers to, so the
 *       quota resets by itself when the date changes — including while the server is running
 *       across midnight, and across a restart.</li>
 * </ul>
 */
public class ServerShopSavedData extends SavedData {

    public static final String DATA_NAME = "globalshop_servershop";

    private static final String TAG_CATALOG = "Catalog";
    private static final String TAG_RECYCLE = "Recycle";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_LAST_DATE = "LastRecycleDate";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_SEEDED = "SeededDefaults";
    private static final String TAG_SEEDED_VERSION = "SeededVersion";

    /**
     * Which revision of the built-in catalogue has been applied.
     *
     * <p>A version rather than a flag, because the defaults themselves can be wrong. Version 1
     * stocked the wrong terminal: the "ten per player" line was applied to the server shop's own
     * terminal instead of the player market terminal. Version 2 corrected that but still stocked
     * the server shop's own terminal, which was not wanted either. Version 3 keeps only the
     * market terminal.
     *
     * <p>This is now a <em>record</em> rather than a trigger. Which built-in lines exist is
     * reconciled from the config on every start, because a one-shot migration proved too fragile:
     * it marked a world as up to date on a run where the config still asked for the line, so the
     * correction was spent without taking effect. Keeping the revision is still worth it for
     * diagnostics — it says which defaults a world has seen.
     */
    public static final int DEFAULTS_VERSION = 3;

    /** Catalogue in insertion order, which is the order the screen lists it in. */
    private final List<ServerShopEntry> catalog = new ArrayList<>();

    /** player uuid -> how many items they have recycled on {@code day}. */
    private final Map<UUID, RecycleCounter> recycle = new HashMap<>();

    /**
     * Which revision of the built-in lines has been applied.
     *
     * <p>Zero means never: a brand-new save, which gets the current defaults. Version 1 was the
     * release that stocked the wrong terminal, so it is treated as needing the correction.
     */
    private int seededVersion;

    public ServerShopSavedData() {
    }

    /**
     * One player's daily recycling tally.
     *
     * <p>Holds the real-world date it refers to, as {@code yyyy-MM-dd}, rather than a day
     * number: the requirement asks for a {@code lastRecycleDate} field, and a calendar date
     * stays readable in the save file when someone is diagnosing a quota problem at 2am.
     *
     * <p>Nothing resets this on a timer. Every read and every write compares the stored date
     * with {@link ShopClock#todayIso()}, so the reset happens on first contact after midnight —
     * which covers a server that ran through midnight, one that was restarted the next morning,
     * and one that was switched off for a week.
     */
    private static final class RecycleCounter {
        private String lastRecycleDate;
        private int count;

        RecycleCounter(String lastRecycleDate, int count) {
            this.lastRecycleDate = lastRecycleDate;
            this.count = count;
        }
    }

    /** Today's real-world date in the configured zone, e.g. {@code 2026-10-02}. */
    public static String currentDate() {
        return ShopClock.todayIso();
    }

    // ------------------------------------------------------------------
    // SavedData plumbing
    // ------------------------------------------------------------------

    public static ServerShopSavedData load(CompoundTag tag) {
        ServerShopSavedData data = new ServerShopSavedData();

        ListTag entries = tag.getList(TAG_CATALOG, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            ServerShopEntry entry = ServerShopEntry.load(entries.getCompound(i));
            if (entry != null) {
                data.catalog.add(entry);
            }
        }

        ListTag counters = tag.getList(TAG_RECYCLE, Tag.TAG_COMPOUND);
        for (int i = 0; i < counters.size(); i++) {
            CompoundTag counterTag = counters.getCompound(i);
            if (!counterTag.hasUUID(TAG_PLAYER)) {
                continue;
            }
            int count = counterTag.getInt(TAG_COUNT);
            String date = counterTag.getString(TAG_LAST_DATE);
            if (count > 0 && !date.isBlank()) {
                data.recycle.put(counterTag.getUUID(TAG_PLAYER), new RecycleCounter(date, count));
            }
        }
        // Version 1 stored only a boolean, so reading one that is set means "version 1 applied".
        // This is what makes the correction reach worlds saved by the previous build.
        data.seededVersion = tag.contains(TAG_SEEDED_VERSION)
                ? tag.getInt(TAG_SEEDED_VERSION)
                : (tag.getBoolean(TAG_SEEDED) ? 1 : 0);

        ListTag lines = tag.getList(TAG_LOG, Tag.TAG_COMPOUND);
        long highest = 0L;
        for (int i = 0; i < lines.size(); i++) {
            LogLine line = LogLine.load(lines.getCompound(i));
            if (line != null) {
                data.log.add(line);
                highest = Math.max(highest, line.seq());
            }
        }
        data.nextSeq = Math.max(1L, highest + 1L);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (ServerShopEntry entry : catalog) {
            entries.add(entry.save(new CompoundTag()));
        }
        tag.put(TAG_CATALOG, entries);

        ListTag counters = new ListTag();
        List<UUID> players = new ArrayList<>(recycle.keySet());
        players.sort(Comparator.comparing(UUID::toString));
        for (UUID player : players) {
            RecycleCounter counter = recycle.get(player);
            if (counter == null || counter.count <= 0) {
                continue;
            }
            CompoundTag counterTag = new CompoundTag();
            counterTag.putUUID(TAG_PLAYER, player);
            counterTag.putString(TAG_LAST_DATE, counter.lastRecycleDate);
            counterTag.putInt(TAG_COUNT, counter.count);
            counters.add(counterTag);
        }
        tag.put(TAG_RECYCLE, counters);
        // Both forms are written: the version is what this build reads, and the old boolean is
        // kept so a world opened by the previous build still looks seeded rather than getting
        // its defaults added a second time.
        tag.putInt(TAG_SEEDED_VERSION, seededVersion);
        tag.putBoolean(TAG_SEEDED, seededVersion >= DEFAULTS_VERSION);

        ListTag lines = new ListTag();
        for (LogLine line : log) {
            lines.add(line.save());
        }
        tag.put(TAG_LOG, lines);
        return tag;
    }

    /** True when the current defaults have already been applied. */
    public boolean isSeeded() {
        return seededVersion >= DEFAULTS_VERSION;
    }

    /** Which revision of the defaults is in place; 0 on a save that has never been seeded. */
    public int seededVersion() {
        return seededVersion;
    }

    public void markSeeded() {
        this.seededVersion = DEFAULTS_VERSION;
    }

    /** Always the overworld, so the whole server shares one catalogue. */
    public static ServerShopSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(ServerShopSavedData::load, ServerShopSavedData::new, DATA_NAME);
    }

    /** Serialises and re-reads, exactly as a server restart does. */
    public static ServerShopSavedData reload(ServerShopSavedData existing) {
        return load(existing.save(new CompoundTag()));
    }

    // ------------------------------------------------------------------
    // Catalogue
    // ------------------------------------------------------------------

    public List<ServerShopEntry> entries() {
        return List.copyOf(catalog);
    }

    public int size() {
        return catalog.size();
    }

    /** The catalogue line stocking exactly this item, or {@code null}. */
    @Nullable
    public ServerShopEntry find(ItemStack probe) {
        if (probe.isEmpty()) {
            return null;
        }
        for (ServerShopEntry entry : catalog) {
            if (entry.stocks(probe)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Adds a line, or replaces the prices of the existing line for the same item.
     *
     * @return the entry that is now in the catalogue
     */
    public ServerShopEntry put(ItemStack template, long sellPrice, long buybackPrice, int bundleSize,
                               boolean allowBuyback, int purchaseLimit) {
        ServerShopEntry existing = find(template);
        if (existing != null) {
            existing.setPrices(sellPrice, buybackPrice);
            existing.setBundleSize(bundleSize);
            existing.setAllowBuyback(allowBuyback);
            existing.setPurchaseLimit(purchaseLimit);
            return existing;
        }
        ServerShopEntry entry = new ServerShopEntry(template, sellPrice, buybackPrice, bundleSize,
                allowBuyback, purchaseLimit);
        catalog.add(entry);
        return entry;
    }

    /** @return the removed entry, or {@code null} if nothing stocked that item. */
    @Nullable
    public ServerShopEntry remove(ItemStack probe) {
        ServerShopEntry entry = find(probe);
        if (entry != null) {
            catalog.remove(entry);
        }
        return entry;
    }

    // ------------------------------------------------------------------
    // Daily recycling quota
    // ------------------------------------------------------------------

    /** Items this player has recycled today. Zero once the real-world date has rolled over. */
    public int recycledToday(UUID playerId) {
        RecycleCounter counter = recycle.get(playerId);
        if (counter == null || !counter.lastRecycleDate.equals(currentDate())) {
            return 0;
        }
        return counter.count;
    }

    /** The stored date for a player, or {@code null} if they have never recycled. Diagnostics. */
    @Nullable
    public String lastRecycleDate(UUID playerId) {
        RecycleCounter counter = recycle.get(playerId);
        return counter == null ? null : counter.lastRecycleDate;
    }

    /**
     * Adds to today's tally, rolling the counter over first if it belongs to an earlier date.
     *
     * @return the new tally for today
     */
    public int recordRecycle(UUID playerId, int items) {
        String today = currentDate();
        RecycleCounter counter = recycle.get(playerId);
        if (counter == null) {
            counter = new RecycleCounter(today, 0);
            recycle.put(playerId, counter);
        } else if (!counter.lastRecycleDate.equals(today)) {
            // Crossed midnight, or the server was down across it. Start the new day at zero.
            counter.count = 0;
            counter.lastRecycleDate = today;
        }
        counter.count += Math.max(0, items);
        return counter.count;
    }

    public void resetDaily(@Nullable UUID playerId) {
        if (playerId == null) {
            recycle.clear();
        } else {
            recycle.remove(playerId);
        }
    }

    /** Drops counters for accounts that no longer exist, so the save cannot grow forever. */
    public int forgetPlayers(Set<UUID> known) {
        int before = recycle.size();
        recycle.keySet().removeIf(id -> !known.contains(id));
        int dropped = before - recycle.size();
        for (ServerShopEntry entry : catalog) {
            entry.forgetPlayers(known);
        }
        return dropped;
    }

    /** Every player with a non-zero tally today, for diagnostics. */
    public Set<UUID> recyclersToday() {
        String today = currentDate();
        Set<UUID> active = new HashSet<>();
        for (Map.Entry<UUID, RecycleCounter> entry : recycle.entrySet()) {
            if (entry.getValue().lastRecycleDate.equals(today) && entry.getValue().count > 0) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    // ------------------------------------------------------------------
    // Trade log
    // ------------------------------------------------------------------

    private static final String TAG_LOG = "TradeLog";
    private static final String TAG_SEQ = "Seq";
    private static final String TAG_TIME = "Time";
    private static final String TAG_ACTION = "Action";
    private static final String TAG_PLAYER_ID = "PlayerId";
    private static final String TAG_PLAYER_NAME = "PlayerName";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_MONEY = "Money";

    /** Bounded so a busy server's save cannot grow without limit. Oldest lines go first. */
    private static final int LOG_CAPACITY = 2000;

    /** Which direction a server-shop trade went, from the player's point of view. */
    public enum Action {
        /** The player paid the server. */
        BUY,
        /** The server paid the player. */
        RECYCLE;

        public String translationKey() {
            return "globalshop.servershop.action." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One audit line.
     *
     * <p>Kept separate from the marketplace's {@code TradeLogEntry} because the fields do not
     * line up: there is no seller, no listing id and no unit price, but there is an action and
     * a quota. Forcing both through one shape would have meant a record where half the columns
     * are always empty.
     */
    public record LogLine(long seq, long time, Action action, UUID playerId, String playerName,
                          ItemStack item, int items, long money) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_SEQ, seq);
            tag.putLong(TAG_TIME, time);
            tag.putString(TAG_ACTION, action.name());
            tag.putUUID(TAG_PLAYER_ID, playerId);
            tag.putString(TAG_PLAYER_NAME, playerName == null ? "" : playerName);
            tag.put(TAG_ITEM, item.save(new CompoundTag()));
            tag.putInt(TAG_ITEMS, items);
            tag.putLong(TAG_MONEY, money);
            return tag;
        }

        /** @return the line, or {@code null} when the stored item no longer exists. */
        @Nullable
        static LogLine load(CompoundTag tag) {
            ItemStack stack = ItemStack.of(tag.getCompound(TAG_ITEM));
            if (stack.isEmpty() || !tag.hasUUID(TAG_PLAYER_ID)) {
                return null;
            }
            Action action;
            try {
                action = Action.valueOf(tag.getString(TAG_ACTION));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
            return new LogLine(tag.getLong(TAG_SEQ), tag.getLong(TAG_TIME), action,
                    tag.getUUID(TAG_PLAYER_ID), tag.getString(TAG_PLAYER_NAME), stack,
                    tag.getInt(TAG_ITEMS), tag.getLong(TAG_MONEY));
        }
    }

    private final List<LogLine> log = new ArrayList<>();
    private long nextSeq = 1;

    /** Monotonic id for the next log line. */
    public long nextSeq() {
        return nextSeq++;
    }

    public void appendLog(LogLine line) {
        log.add(line);
        while (log.size() > LOG_CAPACITY) {
            log.remove(0);
        }
    }

    /** The newest {@code limit} lines, newest first. */
    public List<LogLine> recentLog(int limit) {
        int from = Math.max(0, log.size() - Math.max(0, limit));
        List<LogLine> tail = new ArrayList<>(log.subList(from, log.size()));
        java.util.Collections.reverse(tail);
        return tail;
    }

    public int logSize() {
        return log.size();
    }
}
