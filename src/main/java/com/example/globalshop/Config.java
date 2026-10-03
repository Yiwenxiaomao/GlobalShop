package com.example.globalshop;

import com.example.globalshop.economy.MoneyFormat;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration.
 *
 * <p>Only <em>policy</em> lives here: starting balance, price limits, whether
 * listing is allowed. No balance, stock, listing or trade record is ever stored in
 * a config file -- all of that is authoritative server-side {@code SavedData} in
 * the world save. A config file can therefore be edited freely without being able
 * to invent money or items.
 *
 * <p>Every accessor is null-safe: reading before the config has loaded returns the
 * documented default rather than throwing, so nothing can crash during early
 * startup.
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;
    private static final Common COMMON;

    /** The file Forge writes for this spec: {@code <modid>-<type>.toml}. */
    public static final String FILE_NAME = "globalshop-common.toml";

    static {
        Pair<Common, ForgeConfigSpec> configured = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = configured.getLeft();
        SPEC = configured.getRight();
    }

    private static volatile boolean loaded = false;

    private Config() {
    }

    /** Called on config load and reload. */
    public static void onLoad() {
        loaded = true;
        MoneyFormat.configure(minorUnitsPerMajor(), currencyName());
    }

    /**
     * Re-reads the config file from disk into the live spec.
     *
     * <p>Forge loads a config once, at startup, and does not watch the file — so editing the TOML
     * of a running server does nothing on its own, and vanilla's {@code /reload} does not help
     * either because it reloads datapacks rather than configuration. Without this step
     * {@code /globalshop reload} would only re-apply the values that were already in memory, and
     * an operator who had just corrected a setting would reasonably conclude it was broken.
     *
     * @return whether a config file was found and read
     */
    public static boolean reloadFromFile() {
        return reloadFrom(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(FILE_NAME));
    }

    /**
     * Re-reads one file into the live spec.
     *
     * <p>Separate from {@link #reloadFromFile()} so the self test can exercise the mechanism
     * against a copy instead of the operator's own file.
     *
     * <p>A failure is logged and reported rather than thrown: a config that cannot be parsed
     * should leave the server running on the values it already had, not stop it.
     */
    public static boolean reloadFrom(java.nio.file.Path path) {
        try {
            if (!java.nio.file.Files.isRegularFile(path)) {
                return false;
            }
            // NightConfig, which is what Forge itself uses to read these files.
            com.electronwill.nightconfig.core.file.CommentedFileConfig fromDisk =
                    com.electronwill.nightconfig.core.file.CommentedFileConfig.of(path);
            fromDisk.load();
            SPEC.setConfig(fromDisk);
            onLoad();
            return true;
        } catch (Exception failure) {
            GlobalShop.LOGGER.error("globalshop: could not re-read the config file {}", path, failure);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Null-safe accessors used by the rest of the mod
    // ------------------------------------------------------------------

    public static long initialBalance() {
        return COMMON == null ? 0L : longOr(COMMON.initialBalance, 0L);
    }

    public static long maxBalance() {
        return COMMON == null ? 1_000_000_000_000L : longOr(COMMON.maxBalance, 1_000_000_000_000L);
    }

    public static int minorUnitsPerMajor() {
        return COMMON == null ? 100 : intOr(COMMON.minorUnitsPerMajor, 100);
    }

    public static String currencyName() {
        if (COMMON == null) {
            return "Gold";
        }
        try {
            String value = COMMON.currencyName.get();
            return (value == null || value.isBlank()) ? "Gold" : value;
        } catch (IllegalStateException notLoadedYet) {
            return "Gold";
        }
    }

    public static int maxListingsPerPlayer() {
        return COMMON == null ? 10 : intOr(COMMON.listingLimit, 10);
    }

    /** How many lots one player may have on the market at once, given their rank. */
    public static int listingLimitFor(boolean vip) {
        return vip ? vipListingsPerPlayer() : maxListingsPerPlayer();
    }

    public static int vipListingsPerPlayer() {
        return COMMON == null ? 20 : intOr(COMMON.vipListingLimit, 20);
    }

    /** What one VIP unlock costs, in minor units. 1_000_000 with 100 minor per major is 10000.00. */
    public static long vipPrice() {
        return COMMON == null ? 1_000_000L : longOr(COMMON.vipPrice, 1_000_000L);
    }

    /** The shop's cut of every sale, as a percentage. 0 disables it. */
    public static int marketCommissionPercent() {
        return COMMON == null ? 5 : intOr(COMMON.marketCommissionPercent, 5);
    }

    /** How long a listing stays on the market. 0 means it never expires. */
    public static int listingLifetimeHours() {
        return COMMON == null ? 12 : intOr(COMMON.listingLifetimeHours, 12);
    }

    public static long listingLifetimeMillis() {
        return listingLifetimeHours() * 3_600_000L;
    }

    /** How many unclaimed deliveries one player's mailbox may hold. */
    public static int mailboxLimit() {
        return COMMON == null ? 100 : intOr(COMMON.mailboxLimit, 100);
    }

    /** How long a mail stays claimable, in real days. */
    public static int mailLifetimeDays() {
        return COMMON == null ? 14 : intOr(COMMON.mailLifetimeDays, 14);
    }

    public static long mailLifetimeMillis() {
        return mailLifetimeDays() * 86_400_000L;
    }

    /** How long one VIP purchase lasts, in real days. */
    public static int vipDays() {
        return COMMON == null ? 14 : intOr(COMMON.vipDays, 14);
    }

    public static long vipMillis() {
        return vipDays() * 86_400_000L;
    }

    public static long maxUnitPrice() {
        return COMMON == null ? 1_000_000_000L : longOr(COMMON.maxUnitPrice, 1_000_000_000L);
    }

    public static int maxStock() {
        return COMMON == null ? 1_000_000 : intOr(COMMON.maxStock, 1_000_000);
    }

    public static int maxPerPurchase() {
        return COMMON == null ? 4096 : intOr(COMMON.maxPerPurchase, 4096);
    }

    public static boolean allowListing() {
        if (COMMON == null) {
            return true;
        }
        try {
            return COMMON.allowListing.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    public static int purchaseCooldownMs() {
        return COMMON == null ? 150 : intOr(COMMON.purchaseCooldownMs, 150);
    }

    public static int tradeLogRetention() {
        return COMMON == null ? 2000 : intOr(COMMON.tradeLogRetention, 2000);
    }

    // ------------------------------------------------------------------
    // Server shop
    // ------------------------------------------------------------------

    /** Items one player may sell back to the server per real-world day, all lines combined. */
    public static int serverShopDailyRecycleLimit() {
        return COMMON == null ? 128 : intOr(COMMON.serverShopDailyRecycleLimit, 128);
    }

    public static String serverShopTimezone() {
        if (COMMON == null) {
            return "";
        }
        try {
            String value = COMMON.serverShopTimezone.get();
            return value == null ? "" : value;
        } catch (IllegalStateException notLoadedYet) {
            return "";
        }
    }

    public static boolean serverShopUseOnlineTime() {
        return boolOr(COMMON == null ? null : COMMON.serverShopUseOnlineTime, false);
    }

    public static String serverShopNtpHost() {
        if (COMMON == null) {
            return "pool.ntp.org";
        }
        try {
            String value = COMMON.serverShopNtpHost.get();
            return (value == null || value.isBlank()) ? "pool.ntp.org" : value;
        } catch (IllegalStateException notLoadedYet) {
            return "pool.ntp.org";
        }
    }

    public static int serverShopNtpTimeoutMs() {
        return COMMON == null ? 3000 : intOr(COMMON.serverShopNtpTimeoutMs, 3000);
    }

    /**
     * Whether the server shop stocks the PLAYER MARKET terminal
     * ({@code globalshop:shop_terminal}).
     *
     * <p>This is the line the "ten per player" rule belongs to, not the server shop's own
     * terminal: a market terminal is what lets a player sell to everyone else, so it is worth
     * rationing. See {@link #serverShopSellServerTerminal()} for the other one.
     */
    public static boolean serverShopSellMarketTerminal() {
        return boolOr(COMMON == null ? null : COMMON.serverShopSellMarketTerminal, true);
    }

    public static long serverShopMarketTerminalPrice() {
        return COMMON == null ? 100_000L : longOr(COMMON.serverShopMarketTerminalPrice, 100_000L);
    }

    public static int serverShopMarketTerminalLimit() {
        return COMMON == null ? 10 : intOr(COMMON.serverShopMarketTerminalLimit, 10);
    }

    /**
     * Whether the server shop stocks its own terminal ({@code globalshop:server_shop_terminal}).
     *
     * <p><b>Off by default.</b> It is only a way into the shop, not something to trade: an
     * operator can place one, and {@code /globalshop server} opens the shop anywhere. What the
     * shop is for is buying and recycling goods, so only the player market terminal — the block
     * that lets a player sell to everyone else — is stocked and rationed.
     */
    public static boolean serverShopSellServerTerminal() {
        return boolOr(COMMON == null ? null : COMMON.serverShopStockOwnTerminal, false);
    }

    public static long serverShopServerTerminalPrice() {
        return COMMON == null ? 5_000L : longOr(COMMON.serverShopServerTerminalPrice, 5_000L);
    }

    /** Zero means unlimited. */
    public static int serverShopServerTerminalLimit() {
        return COMMON == null ? 0 : intOr(COMMON.serverShopServerTerminalLimit, 0);
    }

    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        if (value == null || !loaded) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    private static long longOr(ForgeConfigSpec.LongValue value, long fallback) {
        if (!loaded) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    private static int intOr(ForgeConfigSpec.IntValue value, int fallback) {
        if (!loaded) {
            return fallback;
        }
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    /** The declared options, in file order. */
    public static final class Common {

        // -- economy ------------------------------------------------------
        public final ForgeConfigSpec.LongValue initialBalance;
        public final ForgeConfigSpec.LongValue maxBalance;
        public final ForgeConfigSpec.IntValue minorUnitsPerMajor;
        public final ForgeConfigSpec.ConfigValue<String> currencyName;

        // -- shop ---------------------------------------------------------
        public final ForgeConfigSpec.BooleanValue allowListing;
        public final ForgeConfigSpec.IntValue listingLimit;
        public final ForgeConfigSpec.IntValue vipListingLimit;
        public final ForgeConfigSpec.LongValue vipPrice;
        public final ForgeConfigSpec.IntValue marketCommissionPercent;
        public final ForgeConfigSpec.IntValue listingLifetimeHours;
        public final ForgeConfigSpec.IntValue mailboxLimit;
        public final ForgeConfigSpec.IntValue mailLifetimeDays;
        public final ForgeConfigSpec.IntValue vipDays;
        public final ForgeConfigSpec.LongValue maxUnitPrice;
        public final ForgeConfigSpec.IntValue maxStock;
        public final ForgeConfigSpec.IntValue maxPerPurchase;
        public final ForgeConfigSpec.IntValue purchaseCooldownMs;
        public final ForgeConfigSpec.IntValue tradeLogRetention;

        // -- server shop --------------------------------------------------
        public final ForgeConfigSpec.IntValue serverShopDailyRecycleLimit;
        public final ForgeConfigSpec.ConfigValue<String> serverShopTimezone;
        public final ForgeConfigSpec.BooleanValue serverShopUseOnlineTime;
        public final ForgeConfigSpec.ConfigValue<String> serverShopNtpHost;
        public final ForgeConfigSpec.IntValue serverShopNtpTimeoutMs;
        public final ForgeConfigSpec.BooleanValue serverShopSellMarketTerminal;
        public final ForgeConfigSpec.LongValue serverShopMarketTerminalPrice;
        public final ForgeConfigSpec.IntValue serverShopMarketTerminalLimit;
        public final ForgeConfigSpec.BooleanValue serverShopStockOwnTerminal;
        public final ForgeConfigSpec.LongValue serverShopServerTerminalPrice;
        public final ForgeConfigSpec.IntValue serverShopServerTerminalLimit;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "GlobalShop configuration.",
                    "Only policy lives here. Balances, listings, stock and trade records are stored",
                    "server-side in the world save (world/data/globalshop_*.dat), never in this file.")
                    .push("economy");

            initialBalance = builder
                    .comment("Balance, in minor units, given to a player the first time they are seen.",
                            "0 disables the starting grant. The grant is applied once per player and",
                            "never again, so relogging cannot farm it.")
                    .defineInRange("initialBalance", 0L, 0L, Long.MAX_VALUE);

            maxBalance = builder
                    .comment("Hard cap on any single balance, in minor units. Operations that would",
                            "exceed it are refused rather than clamped, so money is never created.")
                    .defineInRange("maxBalance", 1_000_000_000_000L, 0L, Long.MAX_VALUE);

            minorUnitsPerMajor = builder
                    .comment("How many minor units make one major unit. 100 means amounts display",
                            "with two decimals, e.g. 1234 -> \"12.34\". Only powers of ten are honoured;",
                            "other values are rounded down to the nearest power of ten.")
                    .defineInRange("minorUnitsPerMajor", 100, 1, 1_000_000_000);

            currencyName = builder
                    .comment("Display name of the currency, shown after every amount.")
                    .define("currencyName", "Gold");

            builder.pop().push("shop");

            allowListing = builder
                    .comment("Whether ordinary players may create listings. When false only operators",
                            "can list, and everyone can still buy.")
                    .define("allowListing", true);

            listingLimit = builder
                    .comment("How many active listings an ordinary player may have at a time.",
                            "Renamed from maxListingsPerPlayer so the new default actually takes",
                            "effect: Forge keeps the value already in an existing config file.")
                    .defineInRange("listingLimit", 10, 1, 10_000);

            vipListingLimit = builder
                    .comment("How many active listings a VIP may have at a time.")
                    .defineInRange("vipListingLimit", 20, 1, 10_000);

            vipPrice = builder
                    .comment("What one VIP period costs, in minor units. With 100 minor units per",
                            "major, 1000000 is 10000.00. Bought in game with the currency itself.",
                            "One purchase lasts vipDays days; buying again while a period is still",
                            "running adds to it. See vipDays below.")
                    .defineInRange("vipPrice", 1_000_000L, 1L, Long.MAX_VALUE);

            marketCommissionPercent = builder
                    .comment("The shop's cut of every sale, as a percentage of the total.",
                            "Deducted when a sale succeeds and never returned; the seller is told",
                            "what they actually received. 0 disables the commission.")
                    .defineInRange("marketCommissionPercent", 5, 0, 100);

            listingLifetimeHours = builder
                    .comment("How long a listing stays purchasable, in real-world hours. Measured",
                            "against the system clock, not game time, so a listing expires while",
                            "the server is switched off too. 0 means listings never expire.")
                    .defineInRange("listingLifetimeHours", 12, 0, 24 * 365);

            mailboxLimit = builder
                    .comment("How many unclaimed deliveries one player's mailbox may hold.",
                            "A delivery counts as one, however many stacks it needs. At the limit",
                            "a purchase that would have to be mailed is refused before any money",
                            "moves, rather than being delivered onto the floor where a hopper or",
                            "the despawn timer would destroy it. Expired mail is discarded first,",
                            "so a full mailbox of lapsed mail clears itself.")
                    .defineInRange("mailboxLimit", 100, 1, 10_000);

            mailLifetimeDays = builder
                    .comment("How long a mail stays claimable, in real days. Measured against the",
                            "wall clock, so mail lapses while the server is off. Lapsed mail",
                            "cannot be claimed but stays visible until the player deletes it.")
                    .defineInRange("mailLifetimeDays", 14, 1, 3650);

            vipDays = builder
                    .comment("How long one VIP purchase lasts, in real days. Buying again while a",
                            "period is still running adds to it rather than replacing it. With",
                            "auto-renew on, the same price is charged again when it lapses; if the",
                            "player cannot afford it, auto-renew turns itself off and says so.")
                    .defineInRange("vipDays", 14, 1, 3650);

            maxUnitPrice = builder
                    .comment("Highest allowed price per single item, in minor units.")
                    .defineInRange("maxUnitPrice", 1_000_000_000L, 1L, Long.MAX_VALUE);

            maxStock = builder
                    .comment("Highest allowed stock for a single listing.")
                    .defineInRange("maxStock", 1_000_000, 1, Integer.MAX_VALUE);

            maxPerPurchase = builder
                    .comment("Ceiling for a listing's \"maximum per purchase\" value. Keeps a single",
                            "purchase from having to hand out an unbounded number of stacks.")
                    .defineInRange("maxPerPurchase", 4096, 1, 1_000_000);

            purchaseCooldownMs = builder
                    .comment("Minimum milliseconds between two purchase requests from the same player.",
                            "Guards against packet flooding. 0 disables the throttle.")
                    .defineInRange("purchaseCooldownMs", 150, 0, 60_000);

            tradeLogRetention = builder
                    .comment("How many trade log entries to keep. Oldest entries are dropped first.")
                    .defineInRange("tradeLogRetention", 2000, 0, 1_000_000);

            builder.pop().push("serverShop");

            serverShopDailyRecycleLimit = builder
                    .comment("How many items one player may sell back to the SERVER SHOP per real-world",
                            "day, counting every catalogue line together. Buying has no such limit.")
                    .defineInRange("dailyRecycleLimit", 128, 0, 1_000_000);

            serverShopTimezone = builder
                    .comment("Time zone used to decide when 'today' rolls over for that quota,",
                            "e.g. \"Asia/Shanghai\". Empty means the server's own time zone.",
                            "An unknown value falls back to the server zone with a warning rather",
                            "than breaking recycling.")
                    .define("timezone", "");

            serverShopUseOnlineTime = builder
                    .comment("When true the date is taken from an NTP server instead of the machine",
                            "clock. The lookup runs in the background and is cached; it is never done",
                            "while a player is trading. If it fails the system clock is used and a",
                            "warning is logged -- recycling keeps working either way.")
                    .define("useOnlineTime", false);

            serverShopNtpHost = builder
                    .comment("NTP host used when useOnlineTime is true.")
                    .define("ntpHost", "pool.ntp.org");

            serverShopNtpTimeoutMs = builder
                    .comment("How long to wait for the NTP reply before falling back to the system clock.")
                    .defineInRange("ntpTimeoutMs", 3000, 250, 60_000);

            serverShopSellMarketTerminal = builder
                    .comment("Whether the server shop stocks the PLAYER MARKET terminal",
                            "(globalshop:shop_terminal) - the block that opens the player market.",
                            "Neither terminal is craftable, so the only way to obtain one is to buy",
                            "it here, which is what makes the purchase limit meaningful.")
                    .define("sellMarketTerminal", true);

            serverShopMarketTerminalPrice = builder
                    .comment("Price of one player market terminal, in minor units. High by default",
                            "so it is a deliberate purchase rather than a free hand-out.")
                    .defineInRange("marketTerminalPrice", 100_000L, 1L, Long.MAX_VALUE);

            serverShopMarketTerminalLimit = builder
                    .comment("How many player market terminals one player may ever buy. Counted per",
                            "player UUID and persisted, so relogging does not reset it.")
                    .defineInRange("marketTerminalPurchaseLimit", 10, 1, 1_000_000);

            serverShopStockOwnTerminal = builder
                    .comment("Whether the server shop stocks its OWN terminal",
                            "(globalshop:server_shop_terminal). OFF by default: it is only a way",
                            "into the shop, not something to trade. An operator can place one, and",
                            "/globalshop server opens the shop anywhere.",
                            "",
                            "Renamed from sellServerShopTerminal so that the new default actually",
                            "takes effect: Forge keeps the value already written in an existing",
                            "config file, so changing a default in code alone would leave every",
                            "server that had already run the old build still stocking it.")
                    .define("stockOwnTerminal", false);

            serverShopServerTerminalPrice = builder
                    .comment("Price of one server shop terminal, in minor units.")
                    .defineInRange("serverShopTerminalPrice", 5_000L, 1L, Long.MAX_VALUE);

            serverShopServerTerminalLimit = builder
                    .comment("How many server shop terminals one player may buy. 0 means unlimited.")
                    .defineInRange("serverShopTerminalPurchaseLimit", 0, 0, 1_000_000);

            builder.pop();
        }
    }
}
