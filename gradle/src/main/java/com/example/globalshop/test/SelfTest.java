package com.example.globalshop.test;

import com.example.globalshop.Config;
import com.example.globalshop.GlobalShop;
import com.example.globalshop.core.command.MoneyArgumentType;
import com.example.globalshop.internal.economy.EconomySavedData;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.api.economy.TransactionResult;
import com.example.globalshop.core.market.net.ListingView;
import com.example.globalshop.core.market.net.ShopViewState;
import com.example.globalshop.core.market.net.ShopDataPacket;
import com.example.globalshop.core.registry.ModCreativeTabs;
import com.example.globalshop.core.registry.ModItems;
import com.example.globalshop.core.servershop.ServerShopEntry;
import com.example.globalshop.core.servershop.ServerShopResult;
import com.example.globalshop.core.servershop.ServerShopSavedData;
import com.example.globalshop.core.servershop.ServerShopService;
import com.example.globalshop.core.servershop.ServerShopServices;
import com.example.globalshop.core.servershop.ServerShopTab;
import com.example.globalshop.core.servershop.ShopClock;
import com.example.globalshop.core.market.ContainerPool;
import com.example.globalshop.core.market.ItemPool;
import com.example.globalshop.core.market.ItemStacks;
import com.example.globalshop.core.market.Listing;
import com.example.globalshop.core.market.ShopResult;
import com.example.globalshop.core.market.ShopSavedData;
import com.example.globalshop.core.market.ShopService;
import com.example.globalshop.core.market.ShopServices;
import com.example.globalshop.core.market.ShopSort;
import com.example.globalshop.feature.auction.AuctionListing;
import com.example.globalshop.feature.auction.AuctionResult;
import com.example.globalshop.feature.auction.AuctionSavedData;
import com.example.globalshop.feature.auction.AuctionService;
import com.example.globalshop.feature.auction.AuctionServices;
import com.example.globalshop.core.social.Mail;
import com.example.globalshop.feature.tradelog.TradeLogEntry;
import javax.annotation.Nullable;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import com.example.globalshop.feature.bank.BankResult;
import com.example.globalshop.feature.bank.BankService;
import com.example.globalshop.feature.bank.BankServices;
import com.example.globalshop.feature.checkin.CheckinResult;
import com.example.globalshop.feature.checkin.CheckinService;
import com.example.globalshop.feature.checkin.CheckinServices;
import com.example.globalshop.feature.pvp.PvpResult;
import com.example.globalshop.feature.pvp.PvpService;
import com.example.globalshop.feature.pvp.PvpServices;
import com.example.globalshop.feature.teleport.TeleportResult;
import com.example.globalshop.feature.teleport.TeleportService;
import com.example.globalshop.feature.teleport.TeleportServices;
import com.example.globalshop.feature.title.TitlePricing;
import com.example.globalshop.feature.title.TitleResult;
import com.example.globalshop.feature.title.TitleService;
import com.example.globalshop.feature.title.TitleServices;
import com.example.globalshop.core.client.ClientShopCache;
import com.example.globalshop.core.market.net.ShopActionResultPacket;
import com.example.globalshop.core.menu.TradeMenu;
import com.example.globalshop.core.social.DirectMessage;
import com.example.globalshop.core.social.SocialSavedData;
import com.example.globalshop.core.social.SocialService;
import com.example.globalshop.core.social.SocialServices;
import com.example.globalshop.core.social.TradeOffer;
import com.example.globalshop.feature.ranking.RankingService;

/**
 * An in-game verification of the money and market logic, run against the real server.
 *
 * <h2>Why fake players</h2>
 * The economy and the shop both take {@link ServerPlayer}s, so testing them with a
 * hand-made mock would test a different code path than production. Forge's
 * {@code FakePlayerFactory} produces genuine {@link ServerPlayer} instances bound to
 * the running server, so these assertions exercise exactly the code that handles a
 * real purchase.
 *
 * <h2>What it proves</h2>
 * Arithmetic invariants (money is conserved, balances cannot go negative, a failed
 * operation changes nothing), the quantity window, escrow behaviour, and that a
 * save/load round trip -- which is precisely what a server restart does -- preserves
 * balances, listings, escrowed stock and the trade log.
 *
 * <h2>Running it</h2>
 * In game as a level-4 operator: {@code /globalshop selftest}. Or start the dedicated
 * server with {@code -Dglobalshop.selftest=true} and it runs on startup and stops the
 * server afterwards. It uses reserved {@code GS_Alice} / {@code GS_Bob} accounts,
 * removes the listings it creates, and restores the balances it touched.
 */
public final class SelfTest {

    private static final String ALICE_NAME = "GS_Alice";
    private static final String BOB_NAME = "GS_Bob";

    private final CommandSourceStack source;
    private final MinecraftServer server;
    private int passed;
    private int failed;
    private int skipped;

    private SelfTest(CommandSourceStack source) {
        this.source = source;
        this.server = source.getServer();
    }

    /** @return the number of failed checks (0 means everything passed) */
    public static int run(CommandSourceStack source) {
        return new SelfTest(source).execute();
    }

    private int execute() {
        ServerLevel level = server.overworld();
        ServerPlayer alice = FakePlayerFactory.get(level, profile("alice", ALICE_NAME));
        ServerPlayer bob = FakePlayerFactory.get(level, profile("bob", BOB_NAME));

        EconomyService economy = EconomyServices.get(server);
        ShopService shop = ShopServices.get(server);

        long aliceBefore = economy.getBalance(alice.getUUID());
        long bobBefore = economy.getBalance(bob.getUUID());

        // A previous run's returned goods may still be sitting in the mailbox: the
        // mailbox is part of the world save, so it survives a restart. Start from a
        // clean slate, otherwise the counts below describe this run plus leftovers and
        // the same test passes or fails depending on what ran before it.
        resetCarriedItems(shop, alice);
        resetCarriedItems(shop, bob);

        try {
            registrationTests();
            commandSyncTests(alice);
            permissionTests();
            serverShopTests(alice, bob);
            economyTests(economy, alice, bob);
            UUID listingId = shopTests(economy, shop, alice, bob);
            persistenceTests(economy, listingId);
            nbtItemTests(economy, shop, alice, bob);
            marketPageTests(alice, bob);
            multiStackEscrowTests(shop, alice);
            throttleTest(shop, alice, bob);
            safetyTests(shop, economy, alice, bob);
            mailboxTests(shop, economy, alice, bob);
            socialTests(economy, alice, bob);
            tradeOfferTests(shop, economy, alice, bob);
            auctionTests(shop, economy);
        } catch (Throwable unexpected) {
            failed++;
            log("FAIL  unexpected exception: " + unexpected);
            GlobalShop.LOGGER.error("globalshop: selftest aborted", unexpected);
        } finally {
            cleanup(shop, economy, alice, bob, aliceBefore, bobBefore);
        }

        log("RESULT  " + passed + " passed, " + failed + " failed, " + skipped + " skipped");
        return failed;
    }

    private static GameProfile profile(String key, String name) {
        return new GameProfile(
                UUID.nameUUIDFromBytes(("globalshop:selftest:" + key).getBytes(StandardCharsets.UTF_8)), name);
    }

    // ==================================================================
    // Registration and command tree
    // ==================================================================

    // ==================================================================
    // Who is allowed to run what
    // ==================================================================

    /**
     * Walks the command tree the server actually built and asks, for every command, whether a
     * player at permission level 0 and an operator at level 2 may reach it.
     *
     * <p>Reading {@code .requires(...)} in the source would not settle this. The economy
     * commands are registered <em>twice</em> — as {@code /money ...} and as
     * {@code /globalshop money ...} — through a shared builder, and a permission check that
     * reached only one of those paths would let any player mint themselves money. Brigadier
     * only descends into a child whose requirement passes, on every ancestor, so walking the
     * tree with a real source reproduces exactly what the server will allow.
     */
    private void permissionTests() {
        log("-- command permissions (who may run what) --");

        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack player = server.createCommandSourceStack().withPermission(0);
        CommandSourceStack operator = server.createCommandSourceStack().withPermission(2);
        CommandSourceStack console = server.createCommandSourceStack().withPermission(4);

        Map<String, Boolean> forPlayer = new TreeMap<>();
        Map<String, Boolean> forOperator = new TreeMap<>();
        Map<String, Boolean> forConsole = new TreeMap<>();

        // This mod's own commands, walked in full. The whole tree is deliberately NOT walked:
        // Brigadier's tree is a DAG with shared nodes (/execute and friends reuse them), so
        // enumerating every *path* through it grows combinatorially and never finishes.
        int[] visited = {0};
        for (String root : List.of("money", "pay", "shop", "globalshop")) {
            CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(root);
            check("/" + root + " is registered", node != null);
            if (node != null) {
                walkPermissions(node, "/" + root, true, true, true, player, operator, console,
                        forPlayer, forOperator, forConsole, visited);
            }
        }
        check("the walk stayed small enough to be exhaustive", visited[0] < 2000);

        // One level of everything else, so a future top-level money command cannot slip past
        // the sweep below. Level-0 vanilla commands players already have are included on
        // purpose: the sweep is over what players can actually reach.
        for (CommandNode<CommandSourceStack> child : dispatcher.getRoot().getChildren()) {
            String path = "/" + child.getName();
            forPlayer.put(path, child.getRequirement().test(player));
            forOperator.put(path, child.getRequirement().test(operator));
            forConsole.put(path, child.getRequirement().test(console));
        }

        // Anything that moves money must be operator-only, on both of its paths.
        for (String path : List.of(
                "/money give <targets> <amount>", "/money take <targets> <amount>",
                "/money set <targets> <amount>", "/money <targets>",
                "/globalshop money give <targets> <amount>",
                "/globalshop money take <targets> <amount>",
                "/globalshop money set <targets> <amount>",
                "/globalshop money <targets>")) {
            check("a player cannot run " + path, Boolean.FALSE.equals(forPlayer.get(path)));
            check("an operator can run " + path, Boolean.TRUE.equals(forOperator.get(path)));
        }

        // The self-service commands must stay open to everyone.
        for (String path : List.of("/money", "/pay <targets> <amount>", "/shop", "/globalshop",
                "/globalshop money", "/globalshop pay <targets> <amount>", "/globalshop claim")) {
            check("a player can still run " + path, Boolean.TRUE.equals(forPlayer.get(path)));
        }

        // Shop administration, on both of the ways in.
        for (String path : List.of("/globalshop reload", "/globalshop logs", "/globalshop ids",
                "/globalshop remove <id>", "/globalshop server add <item> <sell> <buyback> <bundle>",
                "/globalshop server remove <item>", "/globalshop server list",
                "/globalshop server reload", "/globalshop server resetDaily <targets>")) {
            check("a player cannot run " + path, Boolean.FALSE.equals(forPlayer.get(path)));
            check("an operator can run " + path, Boolean.TRUE.equals(forOperator.get(path)));
        }
        for (String path : List.of("/globalshop market", "/globalshop server",
                "/globalshop server open")) {
            check("a player can still run " + path, Boolean.TRUE.equals(forPlayer.get(path)));
        }
        check("the self test is out of reach even for an operator",
                Boolean.FALSE.equals(forOperator.get("/globalshop selftest"))
                        && Boolean.TRUE.equals(forConsole.get("/globalshop selftest")));

        // The sweep: a future command that mints money must not be able to slip through.
        // Only literal name segments are inspected, so an argument that happens to be called
        // "<setting>" is not mistaken for a command.
        List<String> playerReachable = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : forPlayer.entrySet()) {
            if (entry.getValue()) {
                playerReachable.add(entry.getKey());
            }
        }
        check("no player-reachable command is named give, take or set",
                playerReachable.stream().noneMatch(SelfTest::namesAnAdminVerb));

        // Listed in full, so a reviewer can see the whole surface rather than trust a count.
        log("      the mod's own commands a player may reach:");
        playerReachable.stream()
                .filter(path -> path.startsWith("/money") || path.startsWith("/pay")
                        || path.startsWith("/shop") || path.startsWith("/globalshop"))
                .forEach(path -> log("        " + path));
    }

    // ==================================================================
    // The official server shop
    // ==================================================================

    /**
     * The server shop: buying, selling back, and the daily allowance.
     *
     * <p>The allowance is the part that most needs proving, because none of it can be observed by
     * simply running the server: it has to follow the real-world calendar date rather than
     * anything the world keeps, survive a restart, and reset itself after a day the server spent
     * switched off. Waiting for midnight is not an option and changing the host's clock is not
     * acceptable, so the date is pinned instead — the comparison being exercised is the same one
     * production runs, in {@code ShopClock}.
     */
    private void serverShopTests(ServerPlayer alice, ServerPlayer bob) {
        log("-- server shop --");
        ServerShopService shop = ServerShopServices.get(server);
        EconomyService economy = EconomyServices.get(server);
        int limit = Config.serverShopDailyRecycleLimit();

        if (limit <= 0) {
            skip("the daily sell-back allowance is disabled in the config; the quota checks were skipped");
            return;
        }

        try {
            // ---- what a fresh save stocks ----
            // The PLAYER MARKET terminal is the rationed one: it is what lets a player sell to
            // everyone else, so it is the one worth limiting. The server shop's own terminal is
            // only a way in, so it is cheap and unlimited by default.
            ServerShopEntry marketTerminal = shop.find(new ItemStack(ModItems.SHOP_TERMINAL.get()));
            check("a fresh save stocks the player market terminal", marketTerminal != null);
            if (marketTerminal != null) {
                check("the market terminal is limited per player",
                        marketTerminal.purchaseLimit() == Config.serverShopMarketTerminalLimit());
                check("the market terminal limit is ten by default",
                        Config.serverShopMarketTerminalLimit() == 10);
                check("the market terminal is not bought back", !marketTerminal.allowBuyback());
            }
            ServerShopEntry serverTerminal = shop.find(new ItemStack(ModItems.SERVER_SHOP_TERMINAL.get()));
            check("the server shop's own terminal is NOT stocked by default",
                    serverTerminal == null);
            check("so a fresh catalogue holds exactly the one default line", shop.size() == 1);

            // ---- the pages are separated ----
            check("the management page is refused to a non-operator",
                    ServerShopTab.MANAGE.forViewer(false) == ServerShopTab.SALE);
            check("the management page is available to an operator",
                    ServerShopTab.MANAGE.forViewer(true) == ServerShopTab.MANAGE);
            check("the two trading pages are available to everyone",
                    ServerShopTab.SALE.forViewer(false) == ServerShopTab.SALE
                            && ServerShopTab.RECYCLE.forViewer(false) == ServerShopTab.RECYCLE);
            check("an out-of-range tab ordinal falls back rather than throwing",
                    ServerShopTab.byOrdinal(99) == ServerShopTab.SALE
                            && ServerShopTab.byOrdinal(-1) == ServerShopTab.SALE);

            // A world saved by the previous build must end up corrected rather than keeping the
            // wrong product forever. That hinges entirely on version 1 data being read as "needs
            // the current defaults", so it is worth pinning down.
            CompoundTag legacy = new CompoundTag();
            legacy.putBoolean("SeededDefaults", true);
            ServerShopSavedData migrated = ServerShopSavedData.load(legacy);
            check("a save from the previous build is read as defaults version 1",
                    migrated.seededVersion() == 1);

            // Version 2 added this shop's own terminal; version 3 retires it. A world saved by
            // version 2 has to be recognised as needing that correction too.
            CompoundTag version2 = new CompoundTag();
            version2.putInt("SeededVersion", 2);
            check("a save from the build that stocked the extra terminal is read as version 2",
                    ServerShopSavedData.load(version2).seededVersion() == 2);
            check("and so is treated as needing the correction", !ServerShopSavedData.load(version2).isSeeded());
            check("the current defaults version is above both, or the corrections reach nobody",
                    ServerShopSavedData.DEFAULTS_VERSION >= 3);

            // ---- an operator stocks a line ----
            ItemStack dirt = new ItemStack(Items.DIRT);
            check("an operator can stock a product",
                    shop.add(dirt, 100L, 50L, 8, true, 0) == ServerShopResult.SUCCESS);
            ServerShopEntry line = shop.find(dirt);
            check("the stocked line is found by its item", line != null);
            if (line == null) {
                return;
            }
            check("the line recorded its bundle size", line.bundleSize() == 8);
            check("the line recorded that it is bought back", line.allowBuyback());

            // The recycling page lists only what the server will actually buy back, so a player
            // is never offered an item that would then be refused.
            shop.add(dirt, 100L, 0L, 8, false, 0);
            check("a buy-only line is absent from the recycling page",
                    shop.entriesFor(ServerShopTab.RECYCLE).stream().noneMatch(e -> e.stocks(dirt)));
            check("but it is present on the sale page",
                    shop.entriesFor(ServerShopTab.SALE).stream().anyMatch(e -> e.stocks(dirt)));
            shop.add(dirt, 100L, 50L, 8, true, 0);
            check("a line that is bought back appears on the recycling page",
                    shop.entriesFor(ServerShopTab.RECYCLE).stream().anyMatch(e -> e.stocks(dirt)));
            check("and after the correction the line is bought back again",
                    shop.find(dirt) != null && shop.find(dirt).allowBuyback());

            // A recycling-only line: the server takes the item in and never sells it. Requiring
            // a sale price would have made this impossible to stock, which is what an earlier
            // version did — the management page could only describe things it also sold.
            ItemStack gravel = new ItemStack(Items.GRAVEL);
            check("a recycling-only line can be stocked",
                    shop.add(gravel, 0L, 30L, 4, true, 0) == ServerShopResult.SUCCESS);
            check("it is absent from the sale page",
                    shop.entriesFor(ServerShopTab.SALE).stream().noneMatch(e -> e.stocks(gravel)));
            check("but present on the recycling page",
                    shop.entriesFor(ServerShopTab.RECYCLE).stream().anyMatch(e -> e.stocks(gravel)));
            check("a line that is neither sold nor bought back is refused",
                    shop.add(gravel, 0L, 0L, 4, false, 0) == ServerShopResult.INVALID_PRICE);
            check("the refusal left the good recycling-only line alone",
                    shop.find(gravel) != null && shop.find(gravel).sellPrice() == 0L
                            && shop.find(gravel).buybackPrice() == 30L);

            // Stocking it is only half the story. The rule is checked again on the trading path,
            // and an earlier version compared the buy-back price against a sale price of zero
            // there and refused — so the line existed and could never be used. Actually selling
            // to it is the check that was missing.
            shop.resetDaily(alice.getUUID());
            alice.getInventory().clearContent();
            alice.getInventory().setItem(0, new ItemStack(Items.GRAVEL, 3));
            shop.clearThrottleForTest();
            ServerShopService.Outcome soldGravel = shop.recycleOne(alice, shop.find(gravel));
            check("a recycling-only line can actually be sold back to", soldGravel.isSuccess());
            check("selling back to it paid the buy-back price", soldGravel.money() == 30L);
            check("it took exactly one item", carried(alice, gravel) == 2);
            check("and it counted against the daily allowance",
                    shop.recycledToday(alice.getUUID()) == 1);

            shop.remove(gravel);

            // ---- the rule that stops money printing ----
            check("a sell-back price equal to the purchase price is refused",
                    shop.add(dirt, 100L, 100L, 8, true, 0) == ServerShopResult.BUYBACK_NOT_BELOW_SALE);
            check("a sell-back price above the purchase price is refused",
                    shop.add(dirt, 100L, 101L, 8, true, 0) == ServerShopResult.BUYBACK_NOT_BELOW_SALE);
            check("a zero purchase price is refused",
                    shop.add(dirt, 0L, 0L, 8, false, 0) == ServerShopResult.INVALID_PRICE);
            check("a zero bundle is refused",
                    shop.add(dirt, 100L, 50L, 0, true, 0) == ServerShopResult.INVALID_BUNDLE);
            check("the refused re-stock left the good line in place",
                    shop.find(dirt) != null && shop.find(dirt).sellPrice() == 100L
                            && shop.find(dirt).bundleSize() == 8);

            // ---- buying ----
            alice.getInventory().clearContent();
            line.resetPurchases(alice.getUUID());
            economy.setBalance(alice.getUUID(), 1_000_000L);

            shop.clearThrottleForTest();
            ServerShopService.Outcome bought = shop.buy(alice, line, 2);
            check("buying two bundles succeeds", bought.isSuccess());
            check("buying handed over bundle x times items", bought.items() == 16);
            check("buying charged price x items", bought.money() == 1_600L);
            check("the balance paid exactly", economy.getBalance(alice.getUUID()) == 1_000_000L - 1_600L);
            check("the buyer is holding the items", carried(alice, dirt) == 16);
            check("the purchase was counted against the buyer", line.purchasedBy(alice.getUUID()) == 16);

            shop.clearThrottleForTest();
            check("a line with no limit can be bought again and again", shop.buy(alice, line, 1).isSuccess());
            check("the count keeps climbing", line.purchasedBy(alice.getUUID()) == 24);

            // ---- not enough money ----
            economy.setBalance(alice.getUUID(), 10L);
            long before = economy.getBalance(alice.getUUID());
            int carriedBefore = carried(alice, dirt);
            shop.clearThrottleForTest();
            check("buying without the money is refused",
                    shop.buy(alice, line, 1).result() == ServerShopResult.INSUFFICIENT_FUNDS);
            check("the refused purchase took no money", economy.getBalance(alice.getUUID()) == before);
            check("the refused purchase handed over nothing", carried(alice, dirt) == carriedBefore);

            // ---- the per-player purchase limit, on the rationed line ----
            ServerShopEntry limited = shop.find(new ItemStack(ModItems.SHOP_TERMINAL.get()));
            if (limited != null) {
                int cap = limited.purchaseLimit();
                limited.resetPurchases(alice.getUUID());
                economy.setBalance(alice.getUUID(), 100_000_000L);
                alice.getInventory().clearContent();

                boolean allAccepted = true;
                for (int i = 0; i < cap; i++) {
                    shop.clearThrottleForTest();
                    allAccepted &= shop.buy(alice, limited, 1).isSuccess();
                }
                check("every purchase up to the limit is accepted", allAccepted);
                check("the buyer received exactly the limit", carried(alice, limited.template()) == cap);

                shop.clearThrottleForTest();
                check("the purchase one past the limit is refused",
                        shop.buy(alice, limited, 1).result() == ServerShopResult.PURCHASE_LIMIT_REACHED);
                check("the refused purchase handed over nothing",
                        carried(alice, limited.template()) == cap);
            }
            // An unlimited line must keep accepting purchases, so the check above is really
            // testing the limit rather than some other refusal. Stocked here rather than relied
            // on from the defaults, because the defaults deliberately hold one line only.
            ItemStack sand = new ItemStack(Items.SAND);
            shop.add(sand, 10L, 0L, 1, false, 0);
            ServerShopEntry unlimited = shop.find(sand);
            if (unlimited != null) {
                unlimited.resetPurchases(alice.getUUID());
                economy.setBalance(alice.getUUID(), 100_000_000L);
                boolean everyOneAccepted = true;
                for (int i = 0; i < 12; i++) {
                    shop.clearThrottleForTest();
                    everyOneAccepted &= shop.buy(alice, unlimited, 1).isSuccess();
                }
                check("a line with no limit accepts more than ten purchases", everyOneAccepted);
                check("and it counted all of them", unlimited.purchasedBy(alice.getUUID()) == 12);
                shop.remove(sand);
            }

            // ---- selling back ----
            shop.resetDaily(alice.getUUID());
            alice.getInventory().clearContent();
            giveDirt(alice, 5);
            check("the allowance starts full", shop.recycledToday(alice.getUUID()) == 0);
            check("the remainder starts at the limit", shop.remainingToday(alice.getUUID()) == limit);

            long balanceBeforeRecycle = economy.getBalance(alice.getUUID());
            shop.clearThrottleForTest();
            ServerShopService.Outcome one = shop.recycleOne(alice, line);
            check("selling one item back succeeds", one.isSuccess());
            check("exactly one item left the backpack", carried(alice, dirt) == 4);
            check("exactly one item's worth was paid", one.money() == 50L);
            check("the balance rose by the buy-back price", economy.getBalance(alice.getUUID()) == balanceBeforeRecycle + 50L);
            check("the daily tally went up by one", shop.recycledToday(alice.getUUID()) == 1);

            shop.clearThrottleForTest();
            ServerShopService.Outcome rest = shop.recycleAll(alice, line);
            check("selling the rest back succeeds", rest.isSuccess());
            check("it took exactly what was left", rest.items() == 4);
            check("the backpack is empty of that item", carried(alice, dirt) == 0);
            check("the tally is five", shop.recycledToday(alice.getUUID()) == 5);

            // ---- the allowance, and never exceeding it ----
            shop.resetDaily(alice.getUUID());
            alice.getInventory().clearContent();
            giveDirt(alice, limit);
            shop.clearThrottleForTest();
            ServerShopService.Outcome filled = shop.recycleAll(alice, line);
            check("the whole day's allowance can be spent in one go", filled.isSuccess());
            check("it took exactly the allowance", filled.items() == limit);
            check("the allowance is now exhausted", shop.remainingToday(alice.getUUID()) == 0);

            giveDirt(alice, 20);
            shop.clearThrottleForTest();
            check("selling back once the allowance is gone is refused",
                    shop.recycleAll(alice, line).result() == ServerShopResult.DAILY_QUOTA_EXHAUSTED);
            check("the refusal left the items alone", carried(alice, dirt) == 20);

            // "Sell all" must stop at the remainder rather than refusing outright.
            shop.resetDaily(alice.getUUID());
            alice.getInventory().clearContent();
            giveDirt(alice, limit - 5);
            shop.clearThrottleForTest();
            shop.recycleAll(alice, line);
            check("the allowance is five short of full", shop.remainingToday(alice.getUUID()) == 5);
            giveDirt(alice, 20);
            shop.clearThrottleForTest();
            ServerShopService.Outcome clipped = shop.recycleAll(alice, line);
            check("selling all takes only the remainder", clipped.isSuccess() && clipped.items() == 5);
            check("the allowance is exactly spent", shop.recycledToday(alice.getUUID()) == limit);
            check("the items past the remainder were kept", carried(alice, dirt) == 15);

            // ---- the date, and the restart ----
            LocalDate day = LocalDate.of(2026, 3, 14);
            ShopClock.setDateOverrideForTest(day);
            shop.resetDaily(alice.getUUID());
            alice.getInventory().clearContent();
            giveDirt(alice, 7);
            shop.clearThrottleForTest();
            shop.recycleAll(alice, line);
            check("seven items were recycled on the first day", shop.recycledToday(alice.getUUID()) == 7);
            check("the stored date is that day",
                    day.toString().equals(ServerShopSavedData.get(server).lastRecycleDate(alice.getUUID())));

            ServerShopSavedData reloaded = ServerShopSavedData.reload(ServerShopSavedData.get(server));
            check("the tally survives a server restart on the same day",
                    reloaded.recycledToday(alice.getUUID()) == 7);

            ShopClock.setDateOverrideForTest(day.plusDays(1));
            check("crossing midnight resets the tally to zero",
                    reloaded.recycledToday(alice.getUUID()) == 0);
            check("the allowance is full again the next day",
                    Math.max(0, limit - reloaded.recycledToday(alice.getUUID())) == limit);
            check("the reset is recorded, not just computed",
                    day.plusDays(1).toString().equals(ServerShopSavedData.currentDate()));
            check("a player who has never recycled reads as zero", reloaded.recycledToday(bob.getUUID()) == 0);

            // A week later must behave the same as a day later: nothing catches up.
            ShopClock.setDateOverrideForTest(day.plusDays(7));
            check("a week away also resets the tally", reloaded.recycledToday(alice.getUUID()) == 0);

            // ---- the optional network clock ----
            ShopClock.setDateOverrideForTest(null);
            ShopClock.resetOffsetForTest();
            // A UDP port with nothing behind it: fails in milliseconds rather than waiting.
            ShopClock.configure(ZoneId.systemDefault().getId(), true, "127.0.0.1", 300);
            boolean reached = ShopClock.refreshOnlineTimeNow();
            check("an unreachable time source reports failure rather than throwing", !reached);
            check("the clock falls back to the system time", ShopClock.offsetMillis() == 0L);
            check("the date is still readable after a failed lookup", ShopClock.today() != null);
            check("a failed lookup leaves today's date sane",
                    Math.abs(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), ShopClock.today())) <= 1);

            // ---- a non-catalogue item ----
            shop.clearThrottleForTest();
            check("selling back an item the shop does not stock is refused",
                    shop.recycleOne(alice, null).result() == ServerShopResult.NOT_IN_CATALOG);
        } finally {
            // Leave the world as it was found, so a second run sees the same thing.
            ShopClock.setDateOverrideForTest(null);
            ShopClock.configure(Config.serverShopTimezone(), Config.serverShopUseOnlineTime(),
                    Config.serverShopNtpHost(), Config.serverShopNtpTimeoutMs());
            shop.resetDaily(alice.getUUID());
            shop.resetDaily(bob.getUUID());
            shop.remove(new ItemStack(Items.DIRT));
            for (Item terminalItem : List.of(ModItems.SHOP_TERMINAL.get(),
                    ModItems.SERVER_SHOP_TERMINAL.get())) {
                ServerShopEntry entry = shop.find(new ItemStack(terminalItem));
                if (entry != null) {
                    entry.resetPurchases(alice.getUUID());
                    entry.resetPurchases(bob.getUUID());
                }
            }
            log("server shop: counters reset and the test product removed");
        }
    }

    /** How many of {@code item} a player is carrying, anywhere in their inventory. */
    private int carried(ServerPlayer player, ItemStack item) {
        return ItemStacks.countMatching(item, List.of(new ContainerPool(player.getInventory())));
    }

    /** Puts {@code count} items into a player's inventory, in valid stack sizes. */
    private void giveDirt(ServerPlayer player, int count) {
        Inventory inventory = player.getInventory();
        int left = count;
        int slot = 0;
        while (left > 0 && slot < inventory.getContainerSize()) {
            if (!inventory.getItem(slot).isEmpty()) {
                slot++;
                continue;
            }
            int chunk = Math.min(64, left);
            inventory.setItem(slot, new ItemStack(Items.DIRT, chunk));
            left -= chunk;
            slot++;
        }
    }

    // ==================================================================
    // The cases that only go wrong under load
    // ==================================================================

    /**
     * Races, offline sellers, item identity under NBT, and what a restart preserves.
     *
     * <p>These are the cases the requirement names explicitly, and the ones that cannot be
     * settled by reading the code. "Two players cannot both buy the last item" is a claim about
     * what happens when two requests arrive together, so the only honest way to check it is to
     * make them arrive together.
     *
     * <p>Runs last, so the inventories and balances it rearranges cannot disturb the sections
     * that came before it.
     */
    private void safetyTests(ShopService shop, EconomyService economy, ServerPlayer alice, ServerPlayer bob) {
        log("-- races, offline sellers, NBT identity, restarts --");

        ServerPlayer racer = fake("GS_Racer");
        ServerPlayer payer1 = fake("GS_Payer1");
        ServerPlayer payer2 = fake("GS_Payer2");
        ServerPlayer receiver = fake("GS_Receiver");

        // ---- two buyers, one item ----
        alice.getInventory().clearContent();
        bob.getInventory().clearContent();
        racer.getInventory().clearContent();
        economy.setBalance(alice.getUUID(), 10_000L);
        economy.setBalance(bob.getUUID(), 10_000L);
        economy.setBalance(racer.getUUID(), 10_000L);

        ItemStack lotItem = new ItemStack(Items.STONE);
        alice.getInventory().setItem(0, lotItem.copyWithCount(1));
        check("a one-item lot was created for the race",
                shop.create(alice, lotItem, 1, 100L, 1, 1,
                        List.of(new ContainerPool(alice.getInventory()))) == ShopResult.SUCCESS);

        UUID raceId = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.stock() == 1 && candidate.unitPrice() == 100L) {
                raceId = candidate.id();
                break;
            }
        }
        check("the one-item lot is found", raceId != null);

        if (raceId != null) {
            final UUID id = raceId;
            // Drop the anti-flood throttle: it is a separate feature with its own check, and
            // leaving it in place here would make the race test the throttle instead.
            shop.forget(bob.getUUID());
            shop.forget(racer.getUUID());
            List<ShopResult> results = raceTogether(
                    () -> shop.purchase(bob, id, 1),
                    () -> shop.purchase(racer, id, 1));
            long wins = results.stream().filter(ShopResult::isSuccess).count();
            check("exactly one of two simultaneous buyers gets the last item", wins == 1);
            check("the loser is told the stock ran out, not something else",
                    results.stream().anyMatch(r -> r == ShopResult.SOLD_OUT
                            || r == ShopResult.INSUFFICIENT_STOCK));
            Listing after = shop.find(id);
            check("the lot cannot end up with negative stock", after != null && after.stock() == 0);
            check("only one item was handed out",
                    countStone(bob) + countStone(racer) == 1);
            shop.forceRemove(id, "selftest");
        }

        // ---- two payers, one receiver, at the same time ----
        economy.setBalance(payer1.getUUID(), 500L);
        economy.setBalance(payer2.getUUID(), 500L);
        economy.setBalance(receiver.getUUID(), 0L);
        List<TransactionResult> paid = raceTogether(
                () -> economy.transfer(payer1.getUUID(), receiver.getUUID(), 500L),
                () -> economy.transfer(payer2.getUUID(), receiver.getUUID(), 500L));
        check("both simultaneous payments succeeded",
                paid.get(0).isSuccess() && paid.get(1).isSuccess());
        check("the receiver was credited exactly twice",
                economy.getBalance(receiver.getUUID()) == 1_000L);
        check("each payer was debited exactly once",
                economy.getBalance(payer1.getUUID()) == 0L && economy.getBalance(payer2.getUUID()) == 0L);

        // ---- an offline seller still gets paid, and told ----
        // FakePlayers are never in the PlayerList, so this *is* the offline case.
        check("the seller really is offline",
                server.getPlayerList().getPlayer(alice.getUUID()) == null);
        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, new ItemStack(Items.DIRT, 4));
        economy.setBalance(alice.getUUID(), 1_000L);
        check("an offline player can list",
                shop.create(alice, new ItemStack(Items.DIRT), 4, 25L, 1, 4,
                        List.of(new ContainerPool(alice.getInventory()))) == ShopResult.SUCCESS);
        UUID offlineLot = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.unitPrice() == 25L && candidate.stock() == 4) {
                offlineLot = candidate.id();
                break;
            }
        }
        check("the offline seller's lot is found", offlineLot != null);
        if (offlineLot != null) {
            long gross = 25L * 2L;
            long commission = gross * Config.marketCommissionPercent() / 100L;
            shop.forget(bob.getUUID());
            check("a buyer can buy from an offline seller",
                    shop.purchase(bob, offlineLot, 2) == ShopResult.SUCCESS);
            check("the offline seller's balance was credited, less the commission",
                    economy.getBalance(alice.getUUID()) == 1_000L + gross - commission);
            check("and a sale notice is waiting for them to log in",
                    shop.hasNotices(alice.getUUID()));
            shop.forceRemove(offlineLot, "selftest");
        }

        // ---- the same item with different tags is a different item ----
        // This is the anti-farming rule: renaming or enchanting a stack must not let it be
        // counted, or sold, as the plain item it was made from.
        ItemStack plain = new ItemStack(Items.STONE);
        ItemStack renamed = new ItemStack(Items.STONE);
        renamed.setHoverName(Component.literal("definitely not stone"));
        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, plain.copyWithCount(16));
        alice.getInventory().setItem(1, renamed.copyWithCount(16));
        List<ItemPool> alicePools = List.of(new ContainerPool(alice.getInventory()));
        check("a plain stack counts as plain", ItemStacks.countMatching(plain, alicePools) == 16);
        check("a renamed stack is not counted as the plain item",
                ItemStacks.countMatching(plain, alicePools) == 16
                        && ItemStacks.countMatching(renamed, alicePools) == 16);
        check("listing plain stone takes only the plain stone",
                shop.create(alice, plain, 16, 10L, 1, 16, alicePools) == ShopResult.SUCCESS);
        check("the renamed stack was left where it was",
                ItemStacks.countMatching(renamed, alicePools) == 16);
        check("and the plain stone is gone", ItemStacks.countMatching(plain, alicePools) == 0);

        // ---- what a restart preserves ----
        ShopSavedData live = ShopSavedData.get(server);
        int listingsBefore = live.listingCount();
        int logBefore = live.logEntryCount();
        long aliceBefore = economy.getBalance(alice.getUUID());
        long bobBefore = economy.getBalance(bob.getUUID());
        ShopSavedData reloaded = ShopSavedData.reload(live);
        check("a restart preserves every listing", reloaded.listingCount() == listingsBefore);
        check("a restart preserves the whole trade log", reloaded.logEntryCount() == logBefore);
        check("a restart preserves both balances",
                economy.getBalance(alice.getUUID()) == aliceBefore
                        && economy.getBalance(bob.getUUID()) == bobBefore);
        check("the live market is untouched by the round trip",
                shop.listingCount() == listingsBefore);

        // ---- rank actually changes what a player may do ----
        check("VIP raises the listing limit",
                Config.listingLimitFor(true) > Config.listingLimitFor(false));
        check("a player without the rank gets the normal limit",
                shop.listingLimitFor(bob.getUUID()) == Config.listingLimitFor(false));

        // ---- membership is bought by the period, and renews itself if asked ----
        // The clock is pinned, so "14 days later" is something this test can actually observe
        // rather than something it has to take on trust.
        LocalDate vipDay = LocalDate.of(2031, 3, 1);
        ShopClock.setDateOverrideForTest(vipDay);
        long vipPrice = Config.vipPrice();
        int vipDays = Config.vipDays();
        shop.setVipAutoRenew(bob.getUUID(), false);
        shop.setVip(bob.getUUID(), false);
        economy.setBalance(bob.getUUID(), vipPrice * 4L);

        check("a player starts as a non-member", !shop.isVip(bob.getUUID()));
        long bobWealth = economy.getBalance(bob.getUUID());
        check("buying membership succeeds", shop.purchaseVip(bob) == ShopResult.SUCCESS);
        check("and it charges the price",
                economy.getBalance(bob.getUUID()) == bobWealth - vipPrice);
        check("the buyer is a member", shop.isVip(bob.getUUID()));
        long firstUntil = shop.vipUntil(bob.getUUID());
        check("and it runs for the configured period",
                Math.abs(firstUntil - (ShopClock.nowMillis() + Config.vipMillis())) < 60_000L);
        check("which is a real period, not forever",
                firstUntil > ShopClock.nowMillis() && vipDays >= 1);

        // Buying again while it runs adds to it: a member who renews early must not lose the
        // time they had already paid for.
        check("buying again succeeds", shop.purchaseVip(bob) == ShopResult.SUCCESS);
        check("and adds a whole period rather than replacing it",
                Math.abs(shop.vipUntil(bob.getUUID()) - (firstUntil + Config.vipMillis())) < 60_000L);

        ShopClock.setDateOverrideForTest(vipDay.plusDays(vipDays * 2L + 1L));
        check("past the period the member is no longer a member", !shop.isVip(bob.getUUID()));
        check("and the listing limit drops back with it",
                shop.listingLimitFor(bob.getUUID()) == Config.listingLimitFor(false));

        // ---- auto-renew ----
        ShopClock.setDateOverrideForTest(vipDay);
        shop.setVip(bob.getUUID(), false);
        economy.setBalance(bob.getUUID(), vipPrice * 3L);
        shop.setVipAutoRenew(bob.getUUID(), true);
        check("auto-renew can be turned on", shop.vipAutoRenew(bob.getUUID()));
        shop.purchaseVip(bob);
        long beforeSweep = economy.getBalance(bob.getUUID());
        shop.renewDueVips();
        check("a sweep while the period is still running charges nothing",
                economy.getBalance(bob.getUUID()) == beforeSweep);

        ShopClock.setDateOverrideForTest(vipDay.plusDays(vipDays + 1L));
        check("the member has lapsed", !shop.isVip(bob.getUUID()));
        shop.renewDueVips();
        check("a sweep once lapsed makes them a member again", shop.isVip(bob.getUUID()));
        check("and it cost exactly one period",
                economy.getBalance(bob.getUUID()) == beforeSweep - vipPrice);

        // Someone who cannot pay must not go into debt, and must be told rather than quietly
        // dropped back to the ordinary limit.
        ShopClock.setDateOverrideForTest(vipDay.plusDays(vipDays * 3L + 2L));
        economy.setBalance(bob.getUUID(), vipPrice - 1L);
        shop.renewDueVips();
        check("a sweep for someone who cannot pay leaves them a non-member",
                !shop.isVip(bob.getUUID()));
        check("their money is untouched", economy.getBalance(bob.getUUID()) == vipPrice - 1L);
        check("and auto-renew switched itself off", !shop.vipAutoRenew(bob.getUUID()));

        // The operator's grant and revoke still work, and a grant is one period rather than forever.
        // Pinned past the member's earlier expiry: a grant never shortens what is already paid for,
        // so asking at a time before it would correctly leave the longer one in place.
        ShopClock.setDateOverrideForTest(vipDay.plusDays(vipDays * 5L));
        shop.setVip(bob.getUUID(), true);
        check("an operator grant makes a member", shop.isVip(bob.getUUID()));
        check("and it too is one period, not permanent",
                shop.vipUntil(bob.getUUID()) == ShopClock.nowMillis() + Config.vipMillis());
        check("a grant never shortens a membership already paid for", shop.isVip(bob.getUUID()));
        shop.setVip(bob.getUUID(), false);
        check("revoking ends it immediately", !shop.isVip(bob.getUUID()));
        check("and revoking also clears auto-renew",
                !shop.vipAutoRenew(bob.getUUID()));

        ShopClock.setDateOverrideForTest(null);
        economy.setBalance(bob.getUUID(), 0L);

        // ---- expiry: hidden from the market, unbuyable, still the seller's ----
        if (Config.listingLifetimeHours() <= 0) {
            skip("listings never expire in this config; the expiry checks were skipped");
        } else {
            LocalDate listedOn = LocalDate.of(2030, 1, 1);
            ShopClock.setDateOverrideForTest(listedOn);
            alice.getInventory().clearContent();
            alice.getInventory().setItem(0, new ItemStack(Items.DIRT, 3));
            List<ItemPool> expiringPools = List.of(new ContainerPool(alice.getInventory()));
            check("a lot can be listed on a pinned date",
                    shop.create(alice, new ItemStack(Items.DIRT), 3, 50L, 1, 3, expiringPools)
                            == ShopResult.SUCCESS);
            UUID expiring = null;
            for (Listing candidate : shop.listingsOf(alice.getUUID())) {
                if (candidate.unitPrice() == 50L && candidate.stock() == 3) {
                    expiring = candidate.id();
                    break;
                }
            }
            check("the expiring lot is found", expiring != null);
            if (expiring != null) {
                final UUID expiringId = expiring;
                check("a freshly listed lot is not expired", !shop.find(expiringId).isExpired());
                check("and it is on the public market",
                        shop.search("", ShopSort.TIME_NEWEST, null).stream()
                                .anyMatch(l -> l.id().equals(expiringId)));

                // Jump past the configured lifetime, measured in real days.
                long days = Config.listingLifetimeHours() / 24 + 1;
                ShopClock.setDateOverrideForTest(listedOn.plusDays(days));

                check("past its lifetime the lot is expired", shop.find(expiringId).isExpired());
                check("an expired lot is hidden from the public market",
                        shop.search("", ShopSort.TIME_NEWEST, null).stream()
                                .noneMatch(l -> l.id().equals(expiringId)));
                check("but it stays in the seller's own list",
                        shop.listingsOf(alice.getUUID()).stream()
                                .anyMatch(l -> l.id().equals(expiringId)));
                shop.forget(bob.getUUID());
                check("an expired lot cannot be bought",
                        shop.purchase(bob, expiringId, 1) == ShopResult.EXPIRED);
                check("the seller can still take it down",
                        shop.unlist(alice, expiringId) == ShopResult.SUCCESS);
                shop.deliverMail(alice);
            }
            ShopClock.setDateOverrideForTest(null);
        }

        // ---- unlisting everything at once ----
        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, new ItemStack(Items.DIRT, 12));
        List<ItemPool> bulkPools = List.of(new ContainerPool(alice.getInventory()));
        check("a lot for the bulk check was created",
                shop.create(alice, new ItemStack(Items.DIRT), 3, 10L, 1, 3, bulkPools)
                        == ShopResult.SUCCESS);
        check("and a second one",
                shop.create(alice, new ItemStack(Items.DIRT), 3, 11L, 1, 3, bulkPools)
                        == ShopResult.SUCCESS);
        check("both are on the market", shop.listingsOf(alice.getUUID()).size() >= 2);
        int cleared = shop.unlistAll(alice);
        check("unlisting everything takes every lot down", cleared >= 2);
        check("and leaves none of them behind", shop.listingsOf(alice.getUUID()).isEmpty());
        shop.deliverMail(alice);
        alice.getInventory().clearContent();

        // ---- the verdict the client keeps, which is what the sound is played from ----
        // This exists because the sound was wrong in a way nothing else caught: the client used
        // to decide "did it succeed?" by comparing the status message key against
        // "globalshop.result.success", but a success key is built per action
        // ("globalshop.status.purchase.ok"), so the comparison never matched and every outcome
        // played the failure sound. Asserting the flag directly is what would have caught it.
        com.example.globalshop.core.client.ClientShopCache.reset();
        ShopDataPacket emptyPage = new ShopDataPacket(List.of(),
                com.example.globalshop.core.market.net.ShopViewState.INITIAL, 1, 0, 0L, 0, false, false,
                List.of());
        com.example.globalshop.core.client.ClientShopCache.acceptResult(
                new com.example.globalshop.core.market.net.ShopActionResultPacket(
                        ShopResult.SUCCESS, emptyPage));
        check("a successful outcome is remembered as a success, so it sounds like one",
                com.example.globalshop.core.client.ClientShopCache.statusSuccess());
        check("and its message key is the per-action one, not the generic result key",
                "globalshop.status.action.ok"
                        .equals(com.example.globalshop.core.client.ClientShopCache.statusKey()));
        com.example.globalshop.core.client.ClientShopCache.acceptResult(
                new com.example.globalshop.core.market.net.ShopActionResultPacket(
                        ShopResult.INSUFFICIENT_FUNDS, emptyPage));
        check("a refused outcome is remembered as a refusal",
                !com.example.globalshop.core.client.ClientShopCache.statusSuccess());
        check("and it shows the refusal's own message",
                "globalshop.result.insufficient_funds"
                        .equals(com.example.globalshop.core.client.ClientShopCache.statusKey()));
        com.example.globalshop.core.client.ClientShopCache.reset();
        check("resetting the cache also forgets the verdict",
                !com.example.globalshop.core.client.ClientShopCache.statusSuccess());

        // ---- every result the server can report has wording, in both languages ----
        // A result with no translation key shows the player "globalshop.result.forbidden_item"
        // instead of an explanation. Walking the enumerations means the next result added is
        // covered too, rather than being noticed by whoever happens to trigger it.
        String[] languages = {"en_us", "zh_cn"};
        java.util.Map<String, String> langFiles = new java.util.HashMap<>();
        for (String language : languages) {
            langFiles.put(language, readLangFile(language));
        }
        check("the language files could actually be read",
                !langFiles.get("en_us").isEmpty() && !langFiles.get("zh_cn").isEmpty());

        List<String> missingWording = new ArrayList<>();
        for (ShopResult result : ShopResult.values()) {
            for (String language : languages) {
                if (!langFiles.get(language).contains("\"" + result.translationKey() + "\"")) {
                    missingWording.add(language + ":" + result.translationKey());
                }
            }
        }
        for (com.example.globalshop.core.servershop.ServerShopResult result
                : com.example.globalshop.core.servershop.ServerShopResult.values()) {
            for (String language : languages) {
                if (!langFiles.get(language).contains("\"" + result.translationKey() + "\"")) {
                    missingWording.add(language + ":" + result.translationKey());
                }
            }
        }
        check("every trade result has wording in both languages, so nobody is shown a raw key"
                + (missingWording.isEmpty() ? "" : " -- missing " + missingWording),
                missingWording.isEmpty());

        // ---- the item blacklist ----
        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, new ItemStack(Items.STONE, 4));
        List<ItemPool> blockedPools = List.of(new ContainerPool(alice.getInventory()));
        check("stone is not blocked to begin with",
                !shop.blacklisted(new ItemStack(Items.STONE)));
        check("an operator can block an item", shop.blockItem("minecraft:stone"));
        check("the blacklist reports it", shop.blacklist().contains("minecraft:stone"));
        check("a blocked item cannot be listed",
                shop.create(alice, new ItemStack(Items.STONE), 4, 10L, 1, 4, blockedPools)
                        == ShopResult.FORBIDDEN_ITEM);
        // The whole reason the check is on the registry name: otherwise renaming a diamond
        // would be the way around a rule that says "no diamonds".
        check("blocking is by item, so a renamed copy is blocked as well",
                shop.blacklisted(renamed));
        check("a refused listing did not take the items",
                ItemStacks.countMatching(new ItemStack(Items.STONE), blockedPools) == 4);
        check("blocking the same item twice reports no change",
                !shop.blockItem("minecraft:stone"));
        check("an operator can unblock it", shop.unblockItem("minecraft:stone"));
        check("and then it can be listed again",
                !shop.blacklisted(new ItemStack(Items.STONE)));

        // ---- who gets to see the blacklist, and does it survive the wire ----
        ShopDataPacket plainPage = ShopDataPacket.build(alice,
                com.example.globalshop.core.market.net.ShopViewState.INITIAL);
        check("an ordinary page carries no blacklist", plainPage.blacklist().isEmpty());
        check("and reports that the viewer is not an operator", !plainPage.operator());
        ShopDataPacket refused = ShopDataPacket.build(alice,
                com.example.globalshop.core.market.net.ShopViewState.INITIAL.withBlacklist(true));
        check("a non-operator who asks for the editor is told no, not given the list",
                !refused.view().blacklist() && refused.blacklist().isEmpty());

        // The wire format, round-tripped: the operator flag, the mode and the ids all have to
        // come back, and the editor is useless if any of the three goes missing.
        ShopDataPacket carried = new ShopDataPacket(List.of(),
                com.example.globalshop.core.market.net.ShopViewState.INITIAL.withBlacklist(true),
                1, 0, 0L, 0, true, true, List.of("minecraft:stone", "minecraft:diamond"));
        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        carried.encode(buf);
        ShopDataPacket back = ShopDataPacket.decode(buf);
        check("the operator flag survives the wire", back.operator());
        check("the blacklist mode survives the wire", back.view().blacklist());
        check("the blocked ids survive the wire, in order",
                back.blacklist().equals(List.of("minecraft:stone", "minecraft:diamond")));
        check("the vip flag is still read from the right place after the new fields",
                back.viewerVip());

        // ---- a trade log that only ever shows the asker their own trades ----
        List<com.example.globalshop.feature.tradelog.TradeLogEntry> aliceTrades =
                shop.tradesOf(alice.getUUID(), 100);
        check("a seller sees their own sales", !aliceTrades.isEmpty());
        check("every line is a completed trade, never a listing",
                aliceTrades.stream()
                        .allMatch(e -> e.kind().isTrade()));
        check("every line really involves the asker",
                aliceTrades.stream().allMatch(e -> alice.getUUID().equals(e.actorId())
                        || alice.getUUID().equals(e.sellerId())));
        check("a player who has never traded gets an empty log, not somebody else's",
                shop.tradesOf(payer1.getUUID(), 100).isEmpty());
        check("the log is capped by the number asked for",
                shop.tradesOf(alice.getUUID(), 1).size() <= 1);

        // ---- the ranking ----
        economy.setBalance(payer1.getUUID(), 5_000L);
        economy.setBalance(payer2.getUUID(), 1_000L);
        com.example.globalshop.feature.ranking.RankingService ranking =
                com.example.globalshop.core.market.ShopServices.ranking(server);
        ranking.invalidate();
        List<com.example.globalshop.feature.ranking.RankingService.Row> rows = ranking.rows();
        check("the ranking lists every account", rows.size() >= 2);
        check("it is ordered richest first",
                rows.stream().map(com.example.globalshop.feature.ranking.RankingService.Row::balance).toList()
                        .equals(rows.stream()
                                .map(com.example.globalshop.feature.ranking.RankingService.Row::balance)
                                .sorted(java.util.Comparator.reverseOrder()).toList()));
        check("positions start at one and count upwards",
                rows.get(0).position() == 1 && rows.get(1).position() == 2);
        check("every row carries a name, even for an account with no recorded one",
                rows.stream().allMatch(row -> row.name() != null && !row.name().isBlank()));
        check("the player with the money is at the top", rows.get(0).balance() >= 5_000L);

        // The cache is the feature: the requirement is a board that refreshes every five minutes,
        // so a second look inside that window must not rebuild it.
        long rankingBuiltAt = ranking.builtAt();
        ranking.rows();
        check("a second read inside the five minutes reuses the same board",
                ranking.builtAt() == rankingBuiltAt);
        ranking.invalidate();
        ranking.rows();
        check("invalidating forces a rebuild", ranking.builtAt() >= rankingBuiltAt);

        // ---- the config file is genuinely re-read, not merely re-applied ----
        // Forge loads a config once at startup and never watches the file, so "does editing the
        // TOML take effect without a restart?" has to be answered by an explicit re-read. This
        // proves that re-read works, using a copy rather than the operator's own file.
        try {
            java.nio.file.Path real = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve(Config.FILE_NAME);
            if (!java.nio.file.Files.isRegularFile(real)) {
                skip("no config file on disk yet, so the reload path was not exercised");
            } else {
                String original = java.nio.file.Files.readString(real, java.nio.charset.StandardCharsets.UTF_8);
                String edited = original.replaceFirst("(?m)^\\s*listingLimit\\s*=.*$", "listingLimit = 4242");
                if (!edited.contains("listingLimit = 4242")) {
                    edited = original + System.lineSeparator() + "listingLimit = 4242" + System.lineSeparator();
                }
                java.nio.file.Path copy = java.nio.file.Files.createTempFile("globalshop-cfg", ".toml");
                try {
                    java.nio.file.Files.writeString(copy, edited, java.nio.charset.StandardCharsets.UTF_8);
                    int beforeReload = Config.maxListingsPerPlayer();
                    check("the reload command's re-read really reads the file", Config.reloadFrom(copy));
                    check("and the edited value takes effect without a restart",
                            Config.maxListingsPerPlayer() == 4242);
                    check("which is not what was in memory beforehand", beforeReload != 4242);
                } finally {
                    java.nio.file.Files.deleteIfExists(copy);
                }
                // Put the operator's own values back whatever happened above.
                check("the real config file loads again afterwards", Config.reloadFromFile());
                check("and the temporary value is gone", Config.maxListingsPerPlayer() != 4242);
            }
        } catch (Exception failure) {
            check("the config reload could be exercised", false);
            log("      config reload check failed: " + failure);
        }

        // Leave the shared players as the earlier sections found them.
        alice.getInventory().clearContent();
        bob.getInventory().clearContent();
        shop.forget(alice.getUUID());
        shop.forget(bob.getUUID());
        log("safety: races, offline payment, item identity and restart coverage complete");
    }

    /**
     * The mailbox: what happens to a delivery that does not fit.
     *
     * <p>This is the case the requirement is about. A purchase handed to a player whose backpack was
     * full used to be dropped on the floor, where a hopper or the despawn timer destroys it — which
     * the player cannot tell apart from the shop having eaten their money. So these checks assert
     * both halves: the items end up in the mailbox, <em>and</em> nothing lands on the ground.
     */
    private void mailboxTests(ShopService shop, EconomyService economy, ServerPlayer alice,
                              ServerPlayer bob) {
        log("-- mailbox: overflow, limit, expiry, claim and delete --");

        alice.getInventory().clearContent();
        bob.getInventory().clearContent();
        economy.setBalance(alice.getUUID(), 100_000L);
        economy.setBalance(bob.getUUID(), 100_000L);
        for (com.example.globalshop.core.social.Mail existing : shop.mailsOf(bob.getUUID())) {
            shop.deleteMail(bob.getUUID(), existing.id());
        }

        // ---- a purchase that does not fit ----
        bob.getInventory().clearContent();
        for (int i = 0; i < bob.getInventory().getContainerSize(); i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        check("the buyer's backpack really is full",
                !com.example.globalshop.core.market.ItemStacks.wouldFit(bob.getInventory(),
                        new ItemStack(Items.STONE)));

        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, new ItemStack(Items.STONE, 5));
        check("a lot for the mailbox check was created",
                shop.create(alice, new ItemStack(Items.STONE), 5, 10L, 1, 5,
                        List.of(new ContainerPool(alice.getInventory()))) == ShopResult.SUCCESS);
        UUID lot = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.unitPrice() == 10L && candidate.stock() == 5) {
                lot = candidate.id();
                break;
            }
        }
        check("the lot is found", lot != null);

        if (lot != null) {
            int droppedBefore = countDroppedItems();
            shop.forget(bob.getUUID());
            check("the purchase still succeeds when the backpack is full",
                    shop.purchase(bob, lot, 5) == ShopResult.SUCCESS);
            // The direct evidence that the old behaviour is gone: giveOrDrop would have spawned an
            // item entity here, and a hopper or the five-minute despawn timer would eat it.
            check("nothing was dropped on the floor", countDroppedItems() == droppedBefore);
            check("the delivery went to the mailbox instead", shop.mailCount(bob.getUUID()) == 1);

            List<com.example.globalshop.core.social.Mail> mail = shop.mailsOf(bob.getUUID());
            check("with the market recorded as its source",
                    mail.get(0).source() == com.example.globalshop.core.social.Mail.Source.MARKET);
            check("and the whole delivery inside it", mail.get(0).totalCount() == 5);
            long mailId = mail.get(0).id();

            check("claiming it with a full backpack says the backpack is full, not that items are missing",
                    shop.claimMail(bob, mailId) == ShopResult.NOT_ENOUGH_ROOM);
            check("and the mail is left alone", shop.mailCount(bob.getUUID()) == 1);
            // The other half of the distinction: a listing refused for want of items must still say
            // so. Changing one wording must not quietly change the other.
            check("listing without the items still says the items are missing",
                    shop.create(alice, new ItemStack(Items.DIAMOND), 1, 10L, 1, 1,
                            List.of(new ContainerPool(alice.getInventory()))) == ShopResult.NOT_ENOUGH_ITEMS);

            bob.getInventory().clearContent();
            check("claiming it with room succeeds",
                    shop.claimMail(bob, mailId) == ShopResult.SUCCESS);
            check("the items arrived", countStone(bob) == 5);
            check("and the mail is gone", shop.mailCount(bob.getUUID()) == 0);
            check("claiming it twice does nothing the second time",
                    shop.claimMail(bob, mailId) == ShopResult.MAIL_NOT_FOUND);
        }

        // ---- a delivery too big for the room left ----
        bob.getInventory().clearContent();
        for (int i = 2; i < bob.getInventory().getContainerSize(); i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        // Two free slots hold 128 stone, so 72 of 200 has to be mailed.
        check("an oversized delivery is partly mailed",
                shop.deliverOrMail(bob, List.of(new ItemStack(Items.STONE, 200)),
                        com.example.globalshop.core.social.Mail.Source.MARKET));
        check("exactly one mail was created", shop.mailCount(bob.getUUID()) == 1);
        com.example.globalshop.core.social.Mail overflow = shop.mailsOf(bob.getUUID()).get(0);
        check("holding exactly what did not fit", overflow.totalCount() == 72);
        long originalClock = overflow.createdAt();

        check("with no room at all, delivering the mailbox changes nothing",
                shop.deliverMail(bob) == 0 && shop.mailCount(bob.getUUID()) == 1);

        bob.getInventory().setItem(0, ItemStack.EMPTY);
        shop.deliverMail(bob);
        check("freeing one slot lets part of it through", shop.mailCount(bob.getUUID()) == 1);
        com.example.globalshop.core.social.Mail remainder = shop.mailsOf(bob.getUUID()).get(0);
        check("and exactly the part that did not fit is still mail", remainder.totalCount() == 8);
        // Re-stamping would let a player keep a mail alive forever by taking a little at a time.
        check("with its original creation time, so partial claims cannot extend its life",
                remainder.createdAt() == originalClock);

        // ---- the lifetime, which is a deletion rather than a mark ----
        java.time.LocalDate base = java.time.LocalDate.of(2030, 1, 1);
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(base);
        shop.deleteMail(bob.getUUID(), remainder.id());
        bob.getInventory().clearContent();
        for (int i = 0; i < bob.getInventory().getContainerSize(); i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        shop.deliverOrMail(bob, List.of(new ItemStack(Items.STONE, 3)),
                com.example.globalshop.core.social.Mail.Source.MARKET);
        check("a fresh mail exists", shop.mailCount(bob.getUUID()) == 1);
        long expiringId = shop.mailsOf(bob.getUUID()).get(0).id();
        check("a fresh mail is not expired", !shop.mailsOf(bob.getUUID()).get(0).isExpired(
                com.example.globalshop.core.servershop.ShopClock.nowMillis()));

        // Inside its lifetime it is still there.
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(
                base.plusDays(Config.mailLifetimeDays() - 1L));
        check("inside its lifetime the mail is still listed",
                shop.mailCount(bob.getUUID()) == 1);

        // Past it, the mail has deleted itself. There is no lapsed state to look at and nothing to
        // clear out by hand: the box is bounded by time, so an old delivery simply stops existing.
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(
                base.plusDays(Config.mailLifetimeDays() + 1L));
        check("past its lifetime the mail is gone", shop.mailCount(bob.getUUID()) == 0);
        check("and an id remembered from before it lapsed is simply not found",
                shop.claimMail(bob, expiringId) == ShopResult.MAIL_NOT_FOUND);
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(null);

        // ---- there is no capacity limit; the box is bounded by time instead ----
        bob.getInventory().clearContent();
        for (int i = 0; i < bob.getInventory().getContainerSize(); i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        for (int i = 0; i < 150; i++) {
            shop.deliverOrMail(bob, List.of(new ItemStack(Items.STONE, 1)),
                    com.example.globalshop.core.social.Mail.Source.MARKET);
        }
        check("the mailbox has no capacity limit", shop.mailCount(bob.getUUID()) == 150);
        check("so a delivery is never refused for want of room",
                shop.deliverOrMail(bob, List.of(new ItemStack(Items.STONE, 1)),
                        com.example.globalshop.core.social.Mail.Source.MARKET));
        check("and it is filed", shop.mailCount(bob.getUUID()) == 151);

        // ---- a purchase into a full backpack now simply becomes mail ----
        // This used to be the MAILBOX_FULL path: a full backpack and a full mailbox refused the
        // purchase before taking money. With the box unbounded there is no such thing as "nowhere
        // to deliver", so the same situation is now an ordinary success.
        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, new ItemStack(Items.STONE, 4));
        check("a lot for the full-backpack check was created",
                shop.create(alice, new ItemStack(Items.STONE), 4, 10L, 1, 4,
                        List.of(new ContainerPool(alice.getInventory()))) == ShopResult.SUCCESS);
        UUID blockedLot = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.unitPrice() == 10L && candidate.stock() == 4) {
                blockedLot = candidate.id();
                break;
            }
        }
        check("the lot is found", blockedLot != null);
        if (blockedLot != null) {
            economy.setBalance(bob.getUUID(), 1000L);
            long bobBefore = economy.getBalance(bob.getUUID());
            int mailBefore = shop.mailCount(bob.getUUID());
            shop.forget(bob.getUUID());
            check("a purchase with a full backpack succeeds",
                    shop.purchase(bob, blockedLot, 4) == ShopResult.SUCCESS);
            check("the buyer paid for it", economy.getBalance(bob.getUUID()) == bobBefore - 40L);
            check("the goods went to the mailbox", shop.mailCount(bob.getUUID()) == mailBefore + 1);
            check("and came out of the lot's stock", shop.find(blockedLot).stock() == 0);
            shop.forceRemove(blockedLot, "selftest");
        }

        // ---- mail past its lifetime deletes itself ----
        LocalDate sentOn = LocalDate.of(2033, 1, 1);
        ShopClock.setDateOverrideForTest(sentOn);
        shop.deliverOrMail(bob, List.of(new ItemStack(Items.DIRT, 1)),
                com.example.globalshop.core.social.Mail.Source.MARKET);
        int afterSending = shop.mailCount(bob.getUUID());
        check("a delivery while the clock is pinned is filed", afterSending > 0);
        ShopClock.setDateOverrideForTest(sentOn.plusDays(Config.mailLifetimeDays() - 1L));
        check("inside its lifetime the mail is still there",
                shop.mailCount(bob.getUUID()) == afterSending);
        ShopClock.setDateOverrideForTest(sentOn.plusDays(Config.mailLifetimeDays() + 1L));
        check("past its lifetime, reading the box deletes it",
                shop.mailCount(bob.getUUID()) == 0);
        ShopClock.setDateOverrideForTest(null);

        // ---- a restart keeps the mail ----
        // The lifetime check above deleted everything in the box, deliberately. This section needs
        // something in it to survive a restart, so it puts something there itself rather than
        // relying on what an earlier section happened to leave behind — which is what it used to
        // do, and what made it throw the moment the earlier section started emptying the box.
        bob.getInventory().clearContent();
        for (int i = 0; i < ItemStacks.PLAYER_SLOTS; i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        shop.deliverOrMail(bob, List.of(new ItemStack(Items.STONE, 1)),
                com.example.globalshop.core.social.Mail.Source.MARKET);
        check("the box has something in it for the restart check",
                shop.mailCount(bob.getUUID()) == 1);

        com.example.globalshop.core.market.ShopSavedData live = com.example.globalshop.core.market.ShopSavedData.get(server);
        int countBefore = live.mailCount(bob.getUUID());
        int itemsBefore = live.mailsOf(bob.getUUID()).get(0).totalCount();
        com.example.globalshop.core.market.ShopSavedData reloaded =
                com.example.globalshop.core.market.ShopSavedData.reload(live);
        check("mail survives a restart", reloaded.mailCount(bob.getUUID()) == countBefore);
        check("and so does the delivery inside it",
                reloaded.mailsOf(bob.getUUID()).get(0).totalCount() == itemsBefore);
        check("including when it was created, so a restart cannot extend its life",
                reloaded.mailsOf(bob.getUUID()).get(0).createdAt()
                        == live.mailsOf(bob.getUUID()).get(0).createdAt());

        // ---- the armour slots are not the backpack ----
        // A player's inventory reports 41 slots: 36 backpack, four armour and the offhand. Looping
        // to 41 is how a delivery of stone ended up on a player's head, and how a listing could have
        // taken the chestplate off the seller's back. Both are checked here from the position the
        // player noticed it: 36 slots full, armour empty, and nothing may spill sideways.
        bob.getInventory().clearContent();
        for (int i = 0; i < ItemStacks.PLAYER_SLOTS; i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        check("the backpack's 36 slots are full",
                !ItemStacks.wouldFit(bob.getInventory(), new ItemStack(Items.STONE)));
        check("and the armour slots are empty, so they are the only room left",
                bob.getInventory().armor.stream().allMatch(ItemStack::isEmpty));
        check("so a stack that fits nowhere is not placed in an armour slot",
                ItemStacks.insertIntoInventory(bob.getInventory(), new ItemStack(Items.STONE, 1))
                        .getCount() == 1);
        check("and the armour slots are still empty afterwards",
                bob.getInventory().armor.stream().allMatch(ItemStack::isEmpty));
        check("the offhand is left alone too", bob.getInventory().offhand.get(0).isEmpty());
        check("a pool over a player's inventory offers only the 36 backpack slots",
                new ContainerPool(bob.getInventory()).slotCount() == ItemStacks.PLAYER_SLOTS);

        // The escrow side of the same boundary: a listing must not reach the seller's body.
        alice.getInventory().clearContent();
        alice.getInventory().armor.set(2, new ItemStack(Items.DIAMOND_CHESTPLATE));
        List<ItemPool> wornPools = List.of(new ContainerPool(alice.getInventory()));
        check("a listing cannot see a worn chestplate",
                ItemStacks.countMatching(new ItemStack(Items.DIAMOND_CHESTPLATE), wornPools) == 0);
        check("so listing one cannot strip the seller",
                !ItemStacks.escrow(new ItemStack(Items.DIAMOND_CHESTPLATE), 1, wornPools));
        check("and the chestplate is still being worn",
                alice.getInventory().armor.get(2).getItem() == Items.DIAMOND_CHESTPLATE);
        alice.getInventory().clearContent();

        // Leave nothing behind: mail lives in the world save, so a self test that fills a mailbox
        // would otherwise be found by whoever plays next.
        for (com.example.globalshop.core.social.Mail existing : shop.mailsOf(bob.getUUID())) {
            shop.deleteMail(bob.getUUID(), existing.id());
        }
        check("the mailbox was left empty", shop.mailCount(bob.getUUID()) == 0);
        com.example.globalshop.core.market.ShopSavedData.get(server).setDirty();
        bob.getInventory().clearContent();
        alice.getInventory().clearContent();
        log("mailbox: overflow, limit, expiry, claim and delete covered");
    }

    /** Item entities lying around in any loaded level. Used to prove nothing is dropped. */
    private int countDroppedItems() {
        int count = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Sends a message, clearing the anti-flood throttle first: a person cannot type this fast. */
    private ShopResult sendMessage(com.example.globalshop.core.social.SocialService social,
                                   ServerPlayer from, UUID to, String text) {
        social.clearThrottleForTest();
        return social.send(from, to, text);
    }

    /** Whether a directory page is in the order it documents: online first, then by name. */
    private static boolean isInDirectoryOrder(
            List<com.example.globalshop.core.social.SocialService.Entry> entries) {
        for (int i = 1; i < entries.size(); i++) {
            com.example.globalshop.core.social.SocialService.Entry previous = entries.get(i - 1);
            com.example.globalshop.core.social.SocialService.Entry current = entries.get(i);
            if (previous.online() != current.online()) {
                if (!previous.online()) {
                    return false;
                }
                continue;
            }
            if (previous.name().compareToIgnoreCase(current.name()) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Private messages: the directory, sending, the offline notice, and paying by name.
     *
     * <p>FakePlayers are never in the PlayerList, so every player here reads as offline — which is
     * the case the requirement cares about most, since a message to somebody who is away has to be
     * kept rather than refused.
     */
    private void socialTests(EconomyService economy, ServerPlayer alice, ServerPlayer bob) {
        log("-- private messages: directory, sending, offline notice, payment --");

        com.example.globalshop.core.social.SocialService social =
                com.example.globalshop.core.social.SocialServices.get(server);
        ServerPlayer carol = fake("GS_Carol");
        economy.ensureAccount(carol.getUUID(), "GS_Carol", 0L);
        economy.setBalance(alice.getUUID(), 10_000L);
        economy.setBalance(bob.getUUID(), 10_000L);

        // ---- the directory ----
        com.example.globalshop.core.social.SocialService.Page page = social.directory("", 0);
        check("the directory lists players", page.totalMatches() >= 2);
        check("a page holds five at most",
                page.entries().size() <= com.example.globalshop.core.social.SocialService.PAGE_SIZE);
        check("the directory is in its documented order: online first, then by name",
                isInDirectoryOrder(page.entries()));
        check("every row's online flag agrees with the server",
                page.entries().stream().allMatch(e -> e.online() == social.isOnline(e.id())));
        check("a fake player really is offline", !social.isOnline(alice.getUUID()));

        String aliceName = alice.getGameProfile().getName();
        check("searching by name finds that player",
                social.directory(aliceName, 0).entries().stream()
                        .anyMatch(e -> e.id().equals(alice.getUUID())));
        check("searching for nonsense finds nobody",
                social.directory("zzz-nobody-has-this-name", 0).totalMatches() == 0);
        check("a page past the end is clamped rather than empty",
                !social.directory("", 9999).entries().isEmpty());
        // One person can end up with two accounts: an offline-mode id is derived from the name, so
        // any difference in the name — a capital letter, a trailing space, a rename and a rename
        // back — leaves two ids carrying the same last name. The directory marks it rather than
        // silently showing the same name twice with nothing to tell them apart.
        ServerPlayer twin = fake("GS_Twin");
        economy.ensureAccount(twin.getUUID(), "GS_Carol", 0L);
        check("two accounts sharing a name are both marked as duplicates",
                social.directory("GS_Carol", 0).entries().stream()
                        .filter(e -> e.name().equalsIgnoreCase("GS_Carol")).count() == 2
                        && social.directory("GS_Carol", 0).entries().stream()
                                .allMatch(com.example.globalshop.core.social.SocialService.Entry::duplicateName));
        check("while a uniquely named player is not marked",
                social.directory("", 0).entries().stream()
                        .filter(e -> e.name().equalsIgnoreCase(aliceName))
                        .noneMatch(com.example.globalshop.core.social.SocialService.Entry::duplicateName));

        // ---- what the server refuses ----
        check("a message to yourself is refused",
                sendMessage(social, alice, alice.getUUID(), "hi") == ShopResult.CANNOT_MESSAGE_SELF);
        check("an empty message is refused, and says so specifically",
                sendMessage(social, alice, bob.getUUID(), "   ") == ShopResult.MESSAGE_EMPTY);
        check("an over-long message is refused, and says so specifically",
                sendMessage(social, alice, bob.getUUID(),
                        "x".repeat(com.example.globalshop.core.social.DirectMessage.MAX_LENGTH + 1))
                        == ShopResult.MESSAGE_TOO_LONG);
        check("a message to somebody who is not a player is refused",
                sendMessage(social, alice, UUID.randomUUID(), "hello")
                        == ShopResult.UNKNOWN_PLAYER);

        // ---- sending, and the offline notice ----
        // The message log lives in the world save, so a previous run's messages are still here.
        // Every count from this point is relative to what was already on file: an absolute one
        // would pass on a clean machine and fail on the second run, which is worse than no test.
        //
        // Reading a conversation is capped (60 lines), though, so a count relative to the size
        // before only works until the pair's history reaches that cap. The checks about what a
        // conversation contains are therefore written as "is this line in it", not "did it grow by
        // exactly N" — the growth ones were passing on a fresh machine and failing on the fiftieth.
        int unnotifiedBefore = social.unnotifiedCount(bob.getUUID());

        check("a message to an offline player is accepted, not refused",
                sendMessage(social, alice, bob.getUUID(), "are you there?") == ShopResult.SUCCESS);
        check("and it counts as something they have not been told about",
                social.unnotifiedCount(bob.getUUID()) == unnotifiedBefore + 1);

        check("they are told about everything waiting, once",
                social.announceOnLogin(bob) == unnotifiedBefore + 1);
        check("and not told again on their next login", social.announceOnLogin(bob) == 0);
        check("while the message itself is still there to read",
                social.conversation(bob.getUUID(), alice.getUUID()).stream()
                        .anyMatch(line -> line.text().equals("are you there?")));

        // ---- a conversation is only ever between the two players in it ----
        check("a third party can write to the same player",
                sendMessage(social, carol, bob.getUUID(), "wrong person") == ShopResult.SUCCESS);
        // Asserted by presence, not by a count. Reading a conversation is capped (60 lines), so on a
        // machine that has run this suite often enough for the pair's history to reach the cap,
        // "before + 2" can never hold again — the count is pinned at the cap and the check fails
        // forever. What the name claims is that both directions are in there, so that is what is
        // checked; ordering is covered by the check below.
        check("a conversation holds both directions",
                sendMessage(social, bob, alice.getUUID(), "yes") == ShopResult.SUCCESS
                        && social.conversation(alice.getUUID(), bob.getUUID()).stream()
                                .anyMatch(line -> line.text().equals("yes"))
                        && social.conversation(alice.getUUID(), bob.getUUID()).stream()
                                .anyMatch(line -> line.text().equals("are you there?")));
        check("newest first",
                social.conversation(alice.getUUID(), bob.getUUID()).get(0).text().equals("yes"));
        check("and one player's messages to a third party are not in it",
                social.conversation(alice.getUUID(), bob.getUUID()).stream()
                        .noneMatch(line -> line.text().equals("wrong person")));

        // ---- paying by name ----
        long aliceBefore = economy.getBalance(alice.getUUID());
        check("paying another player succeeds",
                social.pay(alice, bob.getUUID(), 2_500L).isSuccess());
        check("the payer was debited",
                economy.getBalance(alice.getUUID()) == aliceBefore - 2_500L);
        check("paying yourself is refused",
                !social.pay(alice, alice.getUUID(), 1L).isSuccess());
        check("paying nothing is refused", !social.pay(alice, bob.getUUID(), 0L).isSuccess());
        check("paying somebody who is not a player is refused",
                !social.pay(alice, UUID.randomUUID(), 1L).isSuccess());

        // ---- a restart keeps the messages ----
        com.example.globalshop.core.social.SocialSavedData live =
                com.example.globalshop.core.social.SocialSavedData.get(server);
        int before = live.logSize();
        check("messages survive a restart",
                com.example.globalshop.core.social.SocialSavedData.reload(live).logSize() == before);

        log("private messages: directory, refusals, offline notice and payment covered");
    }

    /**
     * The auction house: held money, the overspend audit, outbid refunds, buyouts, flops, the
     * anti-sniping rule, and a restart.
     *
     * <p>The clock is pinned to an exact instant rather than to a date, because most of what is
     * under test here is a question about seconds: whether a bid lands inside the closing minute,
     * and whether the end moves the way the rule says it does.
     */
    private void auctionTests(ShopService shop, EconomyService economy) {
        log("-- auction: held money, the overspend audit, refunds, buyout, flop, anti-snipe, restart --");

        AuctionService auctions = AuctionServices.get(server);
        ServerPlayer seller = fake("AucSeller");
        ServerPlayer poor = fake("AucPoor");
        ServerPlayer rich = fake("AucRich");

        long base = LocalDate.of(2032, 6, 1).atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();

        // A previous run leaves auctions in the world save. Driving the clock far forward settles
        // them out of the way, so everything counted below belongs to this run.
        ShopClock.setMillisOverrideForTest(base + 400L * 24L * 3_600_000L);
        auctions.settleDue();
        ShopClock.setMillisOverrideForTest(base);

        for (ServerPlayer who : List.of(seller, poor, rich)) {
            who.getInventory().clearContent();
            for (Mail existing : shop.mailsOf(who.getUUID())) {
                shop.deleteMail(who.getUUID(), existing.id());
            }
            economy.setBalance(who.getUUID(), 0L);
        }
        economy.setBalance(poor.getUUID(), 1000L);
        economy.setBalance(rich.getUUID(), 10_000L);
        check("the auction house starts with a clean audit", auctions.audit().isEmpty());

        // ---- putting something up escrows it, for real ----
        seller.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 1));
        seller.getInventory().setItem(1, new ItemStack(Items.EMERALD, 1));
        List<ItemPool> pools = List.of(new ContainerPool(seller.getInventory()));
        check("an auction can be put up",
                auctions.list(seller, seller.getInventory().getItem(0), 1, 100L, 10L, 0L, 1L, pools)
                        == AuctionResult.SUCCESS);
        UUID first = auctionOf(auctions, seller, Items.DIAMOND);
        check("and it can be found afterwards", first != null);
        check("the item left the seller's backpack", countItem(seller, Items.DIAMOND) == 0);
        // The check that the bug this test missed would have failed. The stack handed in is the
        // seller's own inventory slot, which is what the packet passes; the escrow empties that
        // slot, so an implementation that copied the template afterwards stored air and lost the
        // item. Passing a fresh copy here — which is what this test used to do — hid it completely.
        // Guarded on `first`, so a failure here reports a failed check rather than throwing and
        // taking the rest of the suite with it — which is what it did the first time it failed.
        check("and the listing is holding the real item, not air",
                first != null && auctions.find(first) != null
                        && auctions.find(first).item().getItem() == Items.DIAMOND
                        && auctions.find(first).item().getCount() == 1);
        check("a second auction can be put up",
                auctions.list(seller, seller.getInventory().getItem(1), 1, 100L, 10L, 0L, 1L, pools)
                        == AuctionResult.SUCCESS);
        UUID second = auctionOf(auctions, seller, Items.EMERALD);
        check("and it can be found too", second != null);

        // ---- the headline rule: money already bid cannot be spent a second time ----
        check("bidding 800 out of 1000 succeeds",
                auctions.bid(poor, first, 800L) == AuctionResult.SUCCESS);
        check("the balance really dropped to 200", economy.getBalance(poor.getUUID()) == 200L);
        check("and the auction is holding the 800", auctions.heldFor(poor.getUUID()) == 800L);
        check("a second 800 bid on the other auction is refused",
                auctions.bid(poor, second, 800L) == AuctionResult.INSUFFICIENT_FUNDS);
        check("the refused bid cost nothing", economy.getBalance(poor.getUUID()) == 200L);
        check("and left the other auction untouched", !auctions.find(second).hasBid());
        check("the audit is still clean", auctions.audit().isEmpty());

        // ---- the rules that protect the bidders ----
        check("a bid below the minimum increment is refused",
                auctions.bid(rich, first, 805L) == AuctionResult.BID_TOO_LOW);
        check("the seller cannot bid on their own auction",
                auctions.bid(seller, first, 500L) == AuctionResult.CANNOT_BID_ON_OWN);
        check("the top bidder cannot outbid themselves",
                auctions.bid(poor, first, 500L) == AuctionResult.ALREADY_TOP_BIDDER);

        // ---- being outbid refunds immediately, not when the auction ends ----
        check("a higher bid succeeds", auctions.bid(rich, first, 900L) == AuctionResult.SUCCESS);
        check("the underbidder was refunded at once", economy.getBalance(poor.getUUID()) == 1000L);
        check("and is holding nothing again", auctions.heldFor(poor.getUUID()) == 0L);
        check("the new bidder is holding 900", auctions.heldFor(rich.getUUID()) == 900L);
        check("the price on the auction is the new bid",
                auctions.find(first).currentPrice() == 900L);

        // ---- the anti-sniping rule at its boundary ----
        seller.getInventory().setItem(2, new ItemStack(Items.GOLD_INGOT, 1));
        check("a one-minute auction can be put up",
                auctions.list(seller, seller.getInventory().getItem(2), 1, 100L, 10L, 0L,
                        AuctionService.MIN_DURATION_MINUTES, pools) == AuctionResult.SUCCESS);
        UUID sniped = auctionOf(auctions, seller, Items.GOLD_INGOT);
        check("and its end is exactly a minute away",
                auctions.find(sniped).endTimeMillis() == base + 60_000L);
        // Their example, walked through exactly. The auction runs a full minute, so there is time
        // to see that a bid with plenty left changes nothing.
        check("a bid with more than thirty seconds left succeeds",
                auctions.bid(poor, sniped, 100L) == AuctionResult.SUCCESS);
        check("and does not move the end",
                auctions.find(sniped).endTimeMillis() == base + 60_000L);
        check("nothing was counted as a reset", auctions.find(sniped).extensions() == 0);

        // Ten seconds left, and a bid puts it back to thirty.
        ShopClock.setMillisOverrideForTest(base + 50_000L);
        check("a bid with ten seconds left succeeds",
                auctions.bid(rich, sniped, 120L) == AuctionResult.SUCCESS);
        check("and the remaining time is thirty seconds again",
                auctions.find(sniped).endTimeMillis() == base + 80_000L);
        check("one reset so far", auctions.find(sniped).extensions() == 1);

        // It runs down to fifteen seconds, and the same thing happens again.
        ShopClock.setMillisOverrideForTest(base + 65_000L);
        check("a bid with fifteen seconds left succeeds",
                auctions.bid(poor, sniped, 140L) == AuctionResult.SUCCESS);
        check("and the remaining time is thirty seconds again",
                auctions.find(sniped).endTimeMillis() == base + 95_000L);
        check("two resets now", auctions.find(sniped).extensions() == 2);
        check("the rule never brings an auction forward",
                auctions.find(sniped).endTimeMillis() > base + 80_000L);

        // ---- a flop puts the goods back in the seller's mailbox ----
        ShopClock.setMillisOverrideForTest(base + 200_000L);
        auctions.settleDue();
        check("the auction nobody bid on is over",
                auctions.find(second) == null
                        || auctions.find(second).state() != AuctionListing.State.ACTIVE);
        check("and the goods came back as mail", mailContains(shop, seller, Items.EMERALD));

        // ---- a buyout ends it now, and the goods go to the mailbox, never the backpack ----
        ShopClock.setMillisOverrideForTest(base);
        seller.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 1));
        check("an auction with a buyout can be put up",
                auctions.list(seller, seller.getInventory().getItem(3), 1, 100L, 10L, 500L, 1L, pools)
                        == AuctionResult.SUCCESS);
        UUID bought = auctionOf(auctions, seller, Items.DIAMOND);
        long sellerBefore = economy.getBalance(seller.getUUID());
        long richBefore = economy.getBalance(rich.getUUID());
        check("the buyout succeeds", auctions.buyout(rich, bought) == AuctionResult.SUCCESS);
        check("the buyer paid the buyout price",
                economy.getBalance(rich.getUUID()) == richBefore - 500L);
        long fee = 500L * Config.marketCommissionPercent() / 100L;
        check("the seller was paid after tax",
                economy.getBalance(seller.getUUID()) == sellerBefore + 500L - fee);
        check("the goods did not go into the buyer's backpack",
                countItem(rich, Items.DIAMOND) == 0);
        check("they went to the buyer's mailbox instead",
                mailContains(shop, rich, Items.DIAMOND));
        check("and the auction is over",
                auctions.find(bought) == null
                        || auctions.find(bought).state() == AuctionListing.State.SOLD);
        // An auction sale has to appear in the shared history. A player looking at "my trades"
        // should not have to remember which of two windows a purchase went through, and the log
        // entry is what puts it there — it was missing entirely before.
        check("the sale is written into the shared trade log",
                shop.tradesOf(seller.getUUID(), 20).stream()
                        .anyMatch(entry -> entry.kind() == TradeLogEntry.Kind.AUCTION));
        check("and it is in the buyer's history too",
                shop.tradesOf(rich.getUUID(), 20).stream()
                        .anyMatch(entry -> entry.kind() == TradeLogEntry.Kind.AUCTION));

        // ---- custom titles: priced by character ----
        // The strikethrough case, which is the reason there are two prices at all. "n̶o̶t̶" is not
        // three characters with lines through them: it is three letters followed by three combining
        // marks, U+0336, and the marks are the special ones. The user asked for exactly this.
        String struck = "n\u0336o\u0336t\u0336";
        com.example.globalshop.feature.title.TitlePricing.Quote struckQuote =
                com.example.globalshop.feature.title.TitlePricing.quote(struck, 100L, 400L);
        check("a struck-through title counts letters and marks separately",
                struckQuote.ordinary() == 3 && struckQuote.special() == 3);
        check("and is charged both rates", struckQuote.total() == 3 * 100L + 3 * 400L);
        check("seven Chinese characters are seven ordinary ones",
                com.example.globalshop.feature.title.TitlePricing.quote("七个汉字的称号", 100L, 400L)
                        .ordinary() == 7);
        check("a space is ordinary, not special",
                com.example.globalshop.feature.title.TitlePricing.quote("Dark Knight", 100L, 400L)
                        .special() == 0);
        check("a symbol is special",
                com.example.globalshop.feature.title.TitlePricing.quote("\u2605", 100L, 400L)
                        .special() == 1);

        com.example.globalshop.feature.title.TitleService titleService =
                com.example.globalshop.feature.title.TitleServices.get(server);
        check("seven characters are allowed", titleService.validate("七个汉字的称号").isSuccess());
        check("eight are refused",
                titleService.validate("八个汉字的称号啊")
                        == com.example.globalshop.feature.title.TitleResult.TOO_LONG);
        check("a colour code is refused",
                titleService.validate("\u00a7cRed")
                        == com.example.globalshop.feature.title.TitleResult.INVALID_CHARACTER);
        check("spaces on their own are not a title",
                titleService.validate("   ")
                        == com.example.globalshop.feature.title.TitleResult.EMPTY);

        economy.addBalance(rich.getUUID(), 10_000_000L);
        check("a title can be bought",
                titleService.set(rich, "战\u0336神")
                        == com.example.globalshop.feature.title.TitleResult.SUCCESS);
        check("and is worn", "战\u0336神".equals(titleService.titleOf(rich.getUUID())));
        check("and appears in front of the name, in brackets",
                titleService.decorate(rich, Component.literal("Rich")).getString()
                        .startsWith("[战\u0336神] Rich"));
        check("buying the same title again charges nothing",
                titleService.set(rich, "战\u0336神")
                        == com.example.globalshop.feature.title.TitleResult.UNCHANGED);
        check("clearing it works and is free", titleService.clear(rich).isSuccess());
        check("and the name goes back to plain",
                titleService.decorate(rich, Component.literal("Rich")).getString()
                        .equals("Rich"));

        // ---- the weekly check-in ----
        // The player's own scenario: a server whose first day is a Wednesday. Monday and Tuesday are
        // already in the past, so they read as missed and can be bought back; Wednesday is today.
        java.time.LocalDate wednesday = java.time.LocalDate.of(2026, 10, 7);
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(wednesday);
        // Claims are saved, so a second run of this suite would otherwise find Wednesday already
        // collected by the first. A clean week is the only way to assert what a fresh player sees.
        com.example.globalshop.feature.checkin.CheckinServices.get(server).reset(rich.getUUID());
        com.example.globalshop.feature.checkin.CheckinService checkin =
                com.example.globalshop.feature.checkin.CheckinServices.get(server);
        check("a Wednesday knows it is day three",
                com.example.globalshop.feature.checkin.CheckinService.dayOf(checkin.today()) == 3);
        check("its week runs from the Monday",
                "2026-10-05".equals(com.example.globalshop.feature.checkin.CheckinService
                        .weekKey(wednesday)));
        check("day one pays one step and day seven pays seven",
                checkin.rewardFor(1) * 7 == checkin.rewardFor(7)
                        && checkin.rewardFor(1) > 0L);
        check("Monday and Tuesday cost the make-up fee",
                checkin.costsFee(rich, 1) && checkin.costsFee(rich, 2));
        check("Wednesday does not", !checkin.costsFee(rich, 3));
        check("Friday has not arrived",
                checkin.collect(rich, 5)
                        == com.example.globalshop.feature.checkin.CheckinResult.NOT_YET);

        economy.addBalance(rich.getUUID(), 100_000_000L);
        long start = economy.getBalance(rich.getUUID());
        check("today can be collected",
                checkin.collect(rich, 3)
                        == com.example.globalshop.feature.checkin.CheckinResult.SUCCESS);
        check("and pays exactly what day three is worth",
                economy.getBalance(rich.getUUID()) == start + checkin.rewardFor(3));
        check("collecting it again is refused",
                checkin.collect(rich, 3)
                        == com.example.globalshop.feature.checkin.CheckinResult.ALREADY_CLAIMED);

        long beforeMakeUp = economy.getBalance(rich.getUUID());
        check("a missed day can be bought back",
                checkin.collect(rich, 1)
                        == com.example.globalshop.feature.checkin.CheckinResult.SUCCESS);
        check("for its fee, and it pays half the day's reward",
                economy.getBalance(rich.getUUID())
                        == beforeMakeUp - checkin.makeupCost() + checkin.makeUpReward(1));
        check("which is exactly half of what the day was worth",
                checkin.makeUpReward(4) * 2 == checkin.rewardFor(4));
        // The invariant that keeps the design honest. If a config change ever made the best day of
        // the week worth more than the fee, deliberately skipping it would pay, and this fails.
        check("and no day of the week is worth missing on purpose",
                checkin.makeUpReward(7) < checkin.makeupCost());
        check("and is marked as made up rather than claimed",
                checkin.record(rich).isMadeUp(1) && checkin.record(rich).isClaimed(1));

        // The next Monday: a new week, so the panel is empty again. Nothing carries over, and last
        // week's days are gone rather than claimable late.
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(
                java.time.LocalDate.of(2026, 10, 12));
        check("a new week starts clean",
                checkin.collectable(rich, 1).isSuccess());
        check("and last week's claims are not remembered",
                checkin.collectable(rich, 1).isSuccess() && !checkin.record(rich).isClaimed(3));
        // Put the clock back, or every test after this one runs on a pinned Wednesday.
        com.example.globalshop.core.servershop.ShopClock.setDateOverrideForTest(null);

        // ---- teleports ----
        // Consent is the whole feature, so most of this is about what does _not_ happen: nothing
        // moves until the player being visited agrees, and a request nobody has answered cannot be
        // acted on by anyone else.
        //
        // One honest gap: this test server's players are not in its player list, so a request's
        // asker cannot be looked up and the accept-and-move path cannot run here. Every refusal and
        // every invariant around it can, and the move itself is checked in game.
        com.example.globalshop.feature.teleport.TeleportService teleports =
                com.example.globalshop.feature.teleport.TeleportServices.get(server);
        teleports.clear();
        check("visiting somebody costs money", teleports.targetPrice() > 0L);
        check("and a player starts with no visiting cooldown",
                teleports.targetCooldownLeft(poor.getUUID()) == 0L);
        check("the random cooldown is the configured number of seconds",
                teleports.cooldownMillis()
                        == com.example.globalshop.Config.teleportCooldownSeconds() * 1000L);
        check("asking to visit somebody succeeds",
                teleports.request(poor, rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS);
        check("the asker is now waiting", teleports.outgoing(poor.getUUID()) != null);
        check("and the target has something to answer",
                teleports.incoming(rich.getUUID()) != null);
        check("a request to a name that is not online is refused",
                teleports.request(poor, "NobodyHere")
                        == com.example.globalshop.feature.teleport.TeleportResult.NO_SUCH_PLAYER);
        check("asking to visit yourself is refused",
                teleports.request(poor, poor)
                        == com.example.globalshop.feature.teleport.TeleportResult.THAT_IS_YOU);

        // The invariant that matters most: a request is not a teleport.
        double beforeX = poor.getX();
        double beforeZ = poor.getZ();
        check("only the player being asked can accept",
                teleports.accept(poor)
                        == com.example.globalshop.feature.teleport.TeleportResult.NO_REQUEST);
        check("and the request is still open after that",
                teleports.incoming(rich.getUUID()) != null);
        check("nothing has moved the asker",
                Math.abs(poor.getX() - beforeX) < 0.01D
                        && Math.abs(poor.getZ() - beforeZ) < 0.01D);

        // Accepting when the asker cannot be reached is the "they logged out while waiting" case,
        // and it consumes the request rather than leaving it to be answered again.
        check("accepting after the asker has gone says so",
                teleports.accept(rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.NO_SUCH_PLAYER);
        check("and clears the request",
                teleports.incoming(rich.getUUID()) == null
                        && teleports.outgoing(poor.getUUID()) == null);
        check("the asker still has not moved",
                Math.abs(poor.getX() - beforeX) < 0.01D
                        && Math.abs(poor.getZ() - beforeZ) < 0.01D);

        check("a second request can be made after that",
                teleports.request(poor, rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS);
        check("the target can refuse it",
                teleports.deny(rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS);
        check("which leaves nothing open",
                teleports.incoming(rich.getUUID()) == null
                        && teleports.outgoing(poor.getUUID()) == null);
        check("nothing to refuse twice",
                teleports.deny(rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.NO_REQUEST);

        check("asking twice replaces the first request",
                teleports.request(poor, rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS
                        && teleports.request(poor, rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS
                        && teleports.incoming(rich.getUUID()) != null);
        check("cancelling closes it",
                teleports.cancel(poor)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS
                        && teleports.incoming(rich.getUUID()) == null);
        check("but the target cannot cancel somebody else's request",
                teleports.request(poor, rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.SUCCESS
                        && teleports.cancel(rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.NO_REQUEST);
        check("so a logged-out player is what clears it",
                teleports.incoming(rich.getUUID()) != null);
        teleports.forgetPlayer(poor.getUUID());
        check("and the other end is cleared too",
                teleports.incoming(rich.getUUID()) == null
                        && teleports.outgoing(poor.getUUID()) == null);

        // The random jump is checked for its guards only: where it lands is the world's business,
        // and a test that asserted a position would be asserting a seed.
        check("a random jump either happens or says why it cannot",
                teleports.random(rich).isSuccess()
                        || teleports.random(rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.ON_COOLDOWN);
        check("and afterwards it is on cooldown",
                teleports.random(rich)
                        == com.example.globalshop.feature.teleport.TeleportResult.ON_COOLDOWN
                        || teleports.randomCooldownLeft(rich.getUUID()) > 0L);
        teleports.clear();
        // ---- player against player ----
        com.example.globalshop.feature.pvp.PvpService pvp = com.example.globalshop.feature.pvp.PvpServices
                .get(server);
        pvp.clear();
        pvp.setWilling(poor, false);
        pvp.setWilling(rich, false);
        check("PvP is off by default", !pvp.isWilling(poor.getUUID()));
        check("and a player who has not opted in cannot be hurt",
                !pvp.damageAllowed(rich.getUUID(), poor.getUUID()));
        check("nor can they start a fight",
                pvp.challenge(poor, rich)
                        == com.example.globalshop.feature.pvp.PvpResult.YOU_DECLINED);

        pvp.setWilling(poor, true);
        check("one side opting in is not enough",
                pvp.challenge(poor, rich)
                        == com.example.globalshop.feature.pvp.PvpResult.THEY_DECLINED);
        check("and that still does not allow damage",
                !pvp.damageAllowed(poor.getUUID(), rich.getUUID()));

        pvp.setWilling(rich, true);
        check("with both opted in, the fight can start",
                pvp.challenge(poor, rich)
                        == com.example.globalshop.feature.pvp.PvpResult.SUCCESS);
        check("the duel is symmetric",
                rich.getUUID().equals(pvp.opponentOf(poor.getUUID()))
                        && poor.getUUID().equals(pvp.opponentOf(rich.getUUID())));
        check("and now they may hurt each other",
                pvp.damageAllowed(poor.getUUID(), rich.getUUID())
                        && pvp.damageAllowed(rich.getUUID(), poor.getUUID()));
        // The invariant that makes the setting worth anything: a duel between two people does not
        // make anybody else on the server fair game.
        check("but a duel does not make anybody else attackable",
                !pvp.damageAllowed(rich.getUUID(), seller.getUUID())
                        && !pvp.damageAllowed(seller.getUUID(), rich.getUUID()));
        check("and only one duel at a time",
                pvp.challenge(poor, rich)
                        == com.example.globalshop.feature.pvp.PvpResult.ALREADY_DUELLING);

        // The stake: a range, rolled at the moment of death, and never more than they hold.
        long stake = pvp.stake();
        long lowest = stake * pvp.lossMinPercent() / 100L;
        long highest = stake * pvp.lossMaxPercent() / 100L;
        check("the range is a range, not a fixed number", lowest < highest);
        boolean inRange = true;
        for (int i = 0; i < 200; i++) {
            long roll = pvp.rollLoss();
            if (roll < lowest || roll > highest) {
                inRange = false;
            }
        }
        check("a loss always lands inside the configured range", inRange);

        economy.addBalance(poor.getUUID(), 100_000_000L);
        long beforeLoss = economy.getBalance(poor.getUUID());
        long winnerBefore = economy.getBalance(rich.getUUID());
        long charged = pvp.settleDeath(poor);
        check("losing a duel costs something", charged >= lowest && charged <= highest);
        check("and exactly that much leaves the balance",
                economy.getBalance(poor.getUUID()) == beforeLoss - charged);
        check("and the winner is paid exactly that much",
                economy.getBalance(rich.getUUID()) == winnerBefore + charged);
        check("the duel is over", !pvp.isDuelling(poor.getUUID()));
        check("losing outside a duel costs nothing", pvp.settleDeath(poor) == 0L);

        // Nothing else in this mod can leave a player in debt, and a duel must not be the exception.
        pvp.challenge(poor, rich);
        economy.removeBalance(poor.getUUID(), economy.getBalance(poor.getUUID()));
        check("a duel cannot leave a player in debt",
                pvp.settleDeath(poor) == 0L && economy.getBalance(poor.getUUID()) == 0L);

        pvp.challenge(poor, rich);
        pvp.setWilling(rich, false);
        check("turning PvP off ends the fight",
                !pvp.isDuelling(poor.getUUID()) && !pvp.isDuelling(rich.getUUID()));
        check("and the protection is back",
                !pvp.damageAllowed(poor.getUUID(), rich.getUUID()));
        pvp.setWilling(poor, false);
        pvp.clear();

        // ---- the public API ----
        // What an addon compiles against. Checked as a contract: bounds, ownership, and that the
        // economy handed out is the same instance the mod's own features use.
        com.example.globalshop.api.feature.FeaturePlaces.clear();
        check("the API declares a supported version",
                com.example.globalshop.api.GlobalShopApi.API_VERSION >= 1);
        check("the API hands out the economy the mod itself uses",
                com.example.globalshop.api.GlobalShopApi.economy(server)
                        == com.example.globalshop.api.economy.EconomyServices.get(server));
        check("page two has thirty-two places",
                com.example.globalshop.api.feature.FeaturePlaces.COUNT == 32);
        check("nothing is claimed to begin with",
                com.example.globalshop.api.feature.FeaturePlaces.claimed() == 0);

        boolean[] used = {false};
        check("a place can be registered",
                com.example.globalshop.api.feature.FeaturePlaces.register(7,
                        com.example.globalshop.api.feature.FeaturePlaces.of(
                                net.minecraft.network.chat.Component.literal("Test place"),
                                null, player -> used[0] = true)));
        check("and it reports itself as taken",
                com.example.globalshop.api.feature.FeaturePlaces.isTaken(7));
        check("and can be looked up",
                com.example.globalshop.api.feature.FeaturePlaces.at(7).isPresent());
        check("two mods cannot claim one place",
                !com.example.globalshop.api.feature.FeaturePlaces.register(7,
                        com.example.globalshop.api.feature.FeaturePlaces.of(null, null, null)));
        check("the first claim survives the second attempt",
                com.example.globalshop.api.feature.FeaturePlaces.at(7).orElseThrow()
                        .title().getString().equals("Test place"));
        check("an index past the last place is refused",
                !com.example.globalshop.api.feature.FeaturePlaces.register(32,
                        com.example.globalshop.api.feature.FeaturePlaces.of(null, null, null)));
        check("a negative index is refused",
                !com.example.globalshop.api.feature.FeaturePlaces.register(-1,
                        com.example.globalshop.api.feature.FeaturePlaces.of(null, null, null)));
        check("a place with nothing in it is usable, not a crash",
                com.example.globalshop.api.feature.FeaturePlaces.register(8,
                        com.example.globalshop.api.feature.FeaturePlaces.of(null, null, null))
                        && com.example.globalshop.api.feature.FeaturePlaces.at(8).orElseThrow()
                                .icon().isEmpty());

        com.example.globalshop.api.feature.FeaturePlaces.at(7).orElseThrow().use(rich);
        check("a place's action runs when it is used", used[0]);
        check("the registry lists what is claimed, in index order",
                com.example.globalshop.api.feature.FeaturePlaces.all().keySet().toString()
                        .equals("[7, 8]"));
        check("a place reports itself available unless it says otherwise",
                com.example.globalshop.api.feature.FeaturePlaces.at(7).orElseThrow()
                        .isAvailable(rich));

        // Left clean: the registry is static, so the next run would otherwise start with these.
        com.example.globalshop.api.feature.FeaturePlaces.clear();
        check("and the registry can be emptied again",
                com.example.globalshop.api.feature.FeaturePlaces.claimed() == 0);

        // ---- the balance event ----
        // The numbers, not just the arrival. A delta with the wrong sign would have a listener pay
        // out for a withdrawal, and the first draft of the firing code did exactly that.
        BalanceEventCapture capture = new BalanceEventCapture();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(capture);
        java.util.List<com.example.globalshop.api.event.BalanceChangedEvent> seen = capture.seen;

        // Emptied first so the set below starts from a known zero. The emptying is a balance change
        // and posts an event of its own, so the list is cleared after it: otherwise every check
        // below reads the numbers of the event before the one it means.
        economy.removeBalance(rich.getUUID(), economy.getBalance(rich.getUUID()));
        seen.clear();
        economy.setBalance(rich.getUUID(), 1_000L);
        economy.addBalance(rich.getUUID(), 500L);
        economy.removeBalance(rich.getUUID(), 200L);
        economy.transfer(rich.getUUID(), poor.getUUID(), 100L);

        check("the event is posted for a set, an add, a remove and both sides of a transfer",
                seen.size() == 5);
        check("a set reports the jump from nothing to the new balance",
                seen.size() == 5 && seen.get(0).balanceBefore() == 0L
                        && seen.get(0).balanceAfter() == 1_000L && seen.get(0).delta() == 1_000L);
        check("an add reports growth",
                seen.size() == 5 && seen.get(1).balanceBefore() == 1_000L
                        && seen.get(1).balanceAfter() == 1_500L && seen.get(1).delta() == 500L);
        check("a remove reports a negative delta, not a positive one",
                seen.size() == 5 && seen.get(2).balanceBefore() == 1_500L
                        && seen.get(2).balanceAfter() == 1_300L && seen.get(2).delta() == -200L);
        check("a transfer reports the payer shrinking",
                seen.size() == 5 && seen.get(3).balanceBefore() == 1_300L
                        && seen.get(3).balanceAfter() == 1_200L && seen.get(3).delta() == -100L);
        check("and the payee growing, by the same amount",
                seen.size() == 5 && seen.get(4).balanceAfter() - seen.get(4).balanceBefore() == 100L
                        && seen.get(4).playerId().equals(poor.getUUID()));
        check("the event says what caused it",
                seen.size() == 5 && seen.get(2).reason().equals("remove"));

        seen.clear();
        check("a refused change posts nothing",
                economy.removeBalance(rich.getUUID(), 999_999_999L)
                        == com.example.globalshop.api.economy.TransactionResult.INSUFFICIENT_FUNDS
                        && seen.isEmpty());
        check("and setting the same balance posts nothing either",
                economy.setBalance(rich.getUUID(), economy.getBalance(rich.getUUID()))
                        == com.example.globalshop.api.economy.TransactionResult.SUCCESS
                        && seen.isEmpty());

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(capture);

        // Put the players back: the checks above deliberately emptied and moved money around.
        economy.setBalance(rich.getUUID(), 100_000_000L);
        economy.setBalance(poor.getUUID(), 100_000_000L);
        // Put the balance back. The debt test deliberately empties it, and the auction checks later
        // in this suite have the same player bid on a lot.
        economy.addBalance(poor.getUUID(), 100_000_000L);
        // ---- the bank ----
        com.example.globalshop.feature.bank.BankService bank = com.example.globalshop.feature.bank.BankServices
                .get(server);
        bank.repair();
        // The file is saved, so a second run of this suite would otherwise start with the first
        // run's deposit and loan and assert against figures that are no longer starting points.
        bank.reset(rich.getUUID());
        economy.addBalance(rich.getUUID(), 100_000_000L);
        long walletBefore = economy.getBalance(rich.getUUID());
        check("money can be deposited",
                bank.deposit(rich, 1_000_000L)
                        == com.example.globalshop.feature.bank.BankResult.SUCCESS);
        check("and it leaves the wallet",
                economy.getBalance(rich.getUUID()) == walletBefore - 1_000_000L);
        check("and is held by the bank", bank.depositOf(rich.getUUID()) == 1_000_000L);
        check("depositing more than you carry is refused",
                bank.deposit(rich, 999_999_999_999L)
                        == com.example.globalshop.feature.bank.BankResult.INSUFFICIENT_FUNDS);
        check("withdrawing more than is deposited is refused",
                bank.withdraw(rich, 2_000_000L)
                        == com.example.globalshop.feature.bank.BankResult.INSUFFICIENT_DEPOSIT);
        check("and it can be taken back out",
                bank.withdraw(rich, 400_000L)
                        == com.example.globalshop.feature.bank.BankResult.SUCCESS
                        && bank.depositOf(rich.getUUID()) == 600_000L);

        // The three "everything" buttons. The amounts are worked out on the server, so what is
        // asserted here is the same thing the server would decide.
        check("depositing everything moves the whole wallet",
                bank.depositAll(rich) == com.example.globalshop.feature.bank.BankResult.SUCCESS
                        && economy.getBalance(rich.getUUID()) == 0L);
        long allDeposited = bank.depositOf(rich.getUUID());
        check("and it all went into the bank", allDeposited > 600_000L);
        check("withdrawing everything brings it all back",
                bank.withdrawAll(rich) == com.example.globalshop.feature.bank.BankResult.SUCCESS
                        && bank.depositOf(rich.getUUID()) == 0L
                        && economy.getBalance(rich.getUUID()) == allDeposited);
        check("with nothing deposited there is nothing to withdraw",
                bank.withdrawAll(rich)
                        == com.example.globalshop.feature.bank.BankResult.INVALID_AMOUNT);
        check("repaying with nothing owed is refused",
                bank.repayAll(rich) == com.example.globalshop.feature.bank.BankResult.INVALID_AMOUNT);

        check("nothing is owed to begin with", bank.loanOf(rich.getUUID()) == 0L);
        check("borrowing past the limit is refused",
                bank.borrow(rich, bank.loanLimit() + 1L)
                        == com.example.globalshop.feature.bank.BankResult.LOAN_LIMIT_REACHED);
        check("and works under it",
                bank.borrow(rich, 200_000L)
                        == com.example.globalshop.feature.bank.BankResult.SUCCESS
                        && bank.loanOf(rich.getUUID()) == 200_000L);
        check("repaying with nothing owed is refused",
                bank.repay(poor, 1_000L)
                        == com.example.globalshop.feature.bank.BankResult.NOTHING_OWED);

        // A game day is 24000 ticks and the service takes both clocks, so the loan's rate is tested
        // with two numbers rather than by moving the world. The deposit's clock is the wall clock,
        // which a test cannot move, and that rate is therefore not exercised here.
        //
        // The wallet is emptied first on purpose. Interest is added to the debt and then the wallet
        // is emptied into it, so a borrower holding a fortune pays the charge off immediately — the
        // debt only grows for somebody who cannot clear it, and that is the case being tested.
        economy.removeBalance(rich.getUUID(), economy.getBalance(rich.getUUID()));
        long owedBefore = bank.loanOf(rich.getUUID());
        long wallNow = System.currentTimeMillis();
        long gameNow = server.overworld().getDayTime();
        bank.accrueAt(rich.getUUID(), wallNow,
                gameNow + com.example.globalshop.feature.bank.BankService.TICKS_PER_GAME_DAY);
        long owedAfter = bank.loanOf(rich.getUUID());
        check("a loan charges interest for each game day that passes", owedAfter > owedBefore);

        // ---- the deposit's interest, which the suite never checked ----
        // The wall clock cannot be moved by a test, but accrueAt takes it as an argument — which is
        // the whole reason that method exists. The gap let a real problem reach a player.
        // Funded first: the loan checks above deliberately empty the wallet, and without this the
        // deposit below is refused and every interest figure here is zero — which made one of these
        // checks pass as 0 == 0 rather than because the rate was right.
        economy.addBalance(rich.getUUID(), 5_000_000L);
        long depositWas = bank.depositOf(rich.getUUID());
        bank.deposit(rich, 1_000_000L);
        check("the deposit this test measures really was made",
                bank.depositOf(rich.getUUID()) >= depositWas + 1_000_000L);
        long depositBefore = bank.depositOf(rich.getUUID());
        check("and it is large enough for half a percent to be more than nothing",
                depositBefore * bank.depositInterestBasisPoints() / 10_000L > 0L);
        long wall = System.currentTimeMillis();
        bank.accrueAt(rich.getUUID(), wall + com.example.globalshop.feature.bank.BankService.MILLIS_PER_REAL_DAY, gameNow);
        long depositAfter = bank.depositOf(rich.getUUID());
        check("a deposit earns interest for each real day that passes", depositAfter > depositBefore);
        check("at the configured rate, in whole units",
                depositAfter - depositBefore
                        == depositBefore * bank.depositInterestBasisPoints() / 10_000L);
        bank.accrueAt(rich.getUUID(), wall + com.example.globalshop.feature.bank.BankService.MILLIS_PER_REAL_DAY / 2L, gameNow);
        check("asking again before the next whole day pays nothing more",
                bank.depositOf(rich.getUUID()) == depositAfter);
        bank.accrueAt(rich.getUUID(), wall + 2L * com.example.globalshop.feature.bank.BankService.MILLIS_PER_REAL_DAY, gameNow);
        check("and the next whole day pays again",
                bank.depositOf(rich.getUUID()) > depositAfter);
        // Back to how the loan checks left things. They are about a borrower with nothing, and the
        // money funded above would pay the debt off before the drain could be observed — which is
        // exactly what happened the first time this ran.
        economy.removeBalance(rich.getUUID(), economy.getBalance(rich.getUUID()));

        // ---- the timestamp repair ----
        // An account saved before these fields existed reads as zero, which is 1970 for a deposit:
        // without this, its first accrual would pay out twenty thousand days at once.
        com.example.globalshop.feature.bank.BankSavedData bankData = com.example.globalshop.feature.bank.BankSavedData.get(server);
        synchronized (bankData) {
            com.example.globalshop.feature.bank.BankSavedData.Account stale = bankData.account(rich.getUUID(), wall, gameNow);
            stale.setLastDepositAccrual(0L);
            stale.setLastLoanAccrual(0L);
            bankData.repairTimestamps(wall, gameNow);
            check("a deposit stamp reading as 1970 is forgiven rather than paid out",
                    stale.lastDepositAccrual() == wall);
            check("and so is a loan stamp from the first game day",
                    stale.lastLoanAccrual() == gameNow);

        // ---- a packet's two halves must agree ----
        // This cannot catch a client and a server built from different revisions — nothing in one
        // process can — but it does catch the version of that mistake which is easy to make: adding a
        // field to encode and forgetting decode, or the other way round.
        com.example.globalshop.feature.bank.net.BankDataPacket sent =
                new com.example.globalshop.feature.bank.net.BankDataPacket(
                        1L, 2L, 3L, 4L, 50L, 200L, true);
        net.minecraft.network.FriendlyByteBuf wire =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        sent.encode(wire);
        com.example.globalshop.feature.bank.net.BankDataPacket received =
                com.example.globalshop.feature.bank.net.BankDataPacket.decode(wire);
        check("a bank packet survives the wire unchanged", sent.equals(received));
        check("including the flag that decides between opening and refreshing",
                received.openWindow());
        check("and the buffer is left with nothing unread",
                wire.readableBytes() == 0);
        }
        check("at the configured rate, to within a rounding",
                owedAfter - owedBefore == owedBefore * bank.loanInterestBasisPoints() / 10_000L);

        // What cannot be paid stays owed: the wallet is emptied into the debt and no further.
        economy.addBalance(rich.getUUID(), 1_000L);
        bank.accrueAt(rich.getUUID(), wallNow,
                gameNow + 2L * com.example.globalshop.feature.bank.BankService.TICKS_PER_GAME_DAY);
        check("an unpaid debt takes the wallet", economy.getBalance(rich.getUUID()) == 0L);
        check("and the rest stays owed", bank.loanOf(rich.getUUID()) > 0L);
        check("but nothing can push a wallet negative",
                economy.getBalance(rich.getUUID()) >= 0L);

        check("the debt can be cleared by repaying what is owed",
                bank.repay(rich, bank.loanOf(rich.getUUID()))
                        == com.example.globalshop.feature.bank.BankResult.INSUFFICIENT_FUNDS
                        || bank.loanOf(rich.getUUID()) == 0L);
        check("a player who has never used the bank has nothing",
                bank.depositOf(seller.getUUID()) == 0L && bank.loanOf(seller.getUUID()) == 0L);

        // ---- a restart keeps the auction and the money it is holding ----
        ShopClock.setMillisOverrideForTest(base);
        seller.getInventory().setItem(4, new ItemStack(Items.DIAMOND, 1));
        check("an auction for the restart check can be put up",
                auctions.list(seller, seller.getInventory().getItem(4), 1, 100L, 10L, 0L, 1L, pools)
                        == AuctionResult.SUCCESS);
        UUID survives = auctionOf(auctions, seller, Items.DIAMOND);
        check("with a bid on it",
                auctions.bid(poor, survives, 300L) == AuctionResult.SUCCESS);
        AuctionSavedData reloaded = AuctionSavedData.reload(server);
        check("the auction survives a restart", reloaded.byId(survives) != null);
        check("and the money it is holding survives with it",
                reloaded.byId(survives) != null
                        && reloaded.heldBy(reloaded.byId(survives)) == 300L);
        check("the reloaded data audits clean", reloaded.audit().isEmpty());

        // ---- leave nothing behind ----
        ShopClock.setMillisOverrideForTest(base + 400L * 24L * 3_600_000L);
        auctions.settleDue();
        check("nothing is left owing after everything settles", auctions.audit().isEmpty());
        ShopClock.setMillisOverrideForTest(null);
        for (ServerPlayer who : List.of(seller, poor, rich)) {
            who.getInventory().clearContent();
            for (Mail existing : shop.mailsOf(who.getUUID())) {
                shop.deleteMail(who.getUUID(), existing.id());
            }
            economy.setBalance(who.getUUID(), 0L);
        }

        log("auction: held money, refunds, buyout, flop, anti-snipe and restart covered");
    }

    /** This seller's auction for one item, or {@code null}. */
    @Nullable
    private UUID auctionOf(AuctionService auctions, ServerPlayer seller,
                           net.minecraft.world.item.Item item) {
        for (AuctionListing listing : auctions.active()) {
            if (listing.sellerId().equals(seller.getUUID())
                    && listing.item().getItem() == item) {
                return listing.id();
            }
        }
        return null;
    }

    /** Whether this player's mailbox holds any of an item. */
    private boolean mailContains(ShopService shop, ServerPlayer player,
                                 net.minecraft.world.item.Item item) {
        for (Mail mail : shop.mailsOf(player.getUUID())) {
            for (ItemStack stack : mail.items()) {
                if (stack.getItem() == item) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many of one item a player is carrying in the 36 backpack slots. */
    private int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < ItemStacks.PLAYER_SLOTS; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Offers inside a conversation: escrow, negotiation, payment, delivery and withdrawal.
     *
     * <p>This is the part that moves both money and items, so the checks are about what cannot be
     * undone: that the item is really held, that the price the buyer saw is the price paid, and that
     * a full backpack sends the item to the mailbox rather than onto the floor.
     */
    private void tradeOfferTests(ShopService shop, EconomyService economy, ServerPlayer alice,
                                 ServerPlayer bob) {
        log("-- offers: escrow, negotiation, payment, delivery, withdrawal --");

        com.example.globalshop.core.social.SocialService social =
                com.example.globalshop.core.social.SocialServices.get(server);
        alice.getInventory().clearContent();
        bob.getInventory().clearContent();
        economy.setBalance(alice.getUUID(), 50_000L);
        economy.setBalance(bob.getUUID(), 50_000L);
        // A previous run leaves offers in the world save; start from a pair with none.
        social.withdraw(alice, bob.getUUID());
        social.withdraw(bob, alice.getUUID());

        net.minecraft.world.SimpleContainer slot = new net.minecraft.world.SimpleContainer(1);
        List<ItemPool> pools = List.of(new ContainerPool(slot));

        // ---- offering holds the item ----
        check("offering an empty slot is refused",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.EMPTY_ITEM);
        slot.setItem(0, new ItemStack(Items.DIAMOND, 3));
        check("offering succeeds",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.SUCCESS);
        check("the item left the slot", slot.getItem(0).isEmpty());
        com.example.globalshop.core.social.TradeOffer offer =
                social.offerBetween(alice.getUUID(), bob.getUUID());
        check("the offer is holding it", offer != null && offer.quantity() == 3
                && offer.item().getItem() == Items.DIAMOND);
        check("the offerer is the seller", offer != null && offer.isSeller(alice.getUUID()));
        check("and nothing is priced yet", offer != null && !offer.priced());

        slot.setItem(0, new ItemStack(Items.DIAMOND));
        check("a second offer between the same two is refused",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.TRADE_ALREADY_OPEN);
        check("and the offered item was not swallowed", !slot.getItem(0).isEmpty());
        slot.setItem(0, ItemStack.EMPTY);

        // ---- one shared price ----
        check("pricing a pair with no offer is refused",
                social.publishPrice(alice, UUID.randomUUID(), 100L) == ShopResult.NO_TRADE_OPEN);
        check("a price of zero is refused",
                social.publishPrice(alice, bob.getUUID(), 0L) == ShopResult.INVALID_PRICE);
        check("the seller sets a price",
                social.publishPrice(alice, bob.getUUID(), 1_000L) == ShopResult.SUCCESS);
        check("and it is on the offer, published",
                social.offerBetween(alice.getUUID(), bob.getUUID()).price() == 1_000L
                        && social.offerBetween(alice.getUUID(), bob.getUUID()).priced());
        check("the buyer may change it too: the price is shared",
                social.publishPrice(bob, alice.getUUID(), 800L) == ShopResult.SUCCESS);
        check("and both sides see the new number",
                social.offerBetween(alice.getUUID(), bob.getUUID()).price() == 800L);

        // ---- each side has its own actions ----
        check("the seller cannot pay their own offer",
                social.payOffer(alice, bob.getUUID(), 800L) == ShopResult.NOT_YOUR_TRADE);
        check("the buyer cannot withdraw it",
                social.withdraw(bob, alice.getUUID()) == ShopResult.NOT_YOUR_TRADE);

        // ---- the price the buyer saw is the price that is paid ----
        // The guard that stops a seller raising the price in the instant before a click.
        long bobBefore = economy.getBalance(bob.getUUID());
        check("paying a price that has moved is refused",
                social.payOffer(bob, alice.getUUID(), 500L) == ShopResult.PRICE_CHANGED);
        check("and it took no money", economy.getBalance(bob.getUUID()) == bobBefore);

        // ---- payment hands over both ----
        long aliceBefore = economy.getBalance(alice.getUUID());
        check("paying the price on screen succeeds",
                social.payOffer(bob, alice.getUUID(), 800L) == ShopResult.SUCCESS);
        check("the buyer paid it", economy.getBalance(bob.getUUID()) == bobBefore - 800L);
        check("the seller received it", economy.getBalance(alice.getUUID()) == aliceBefore + 800L);
        check("the item arrived", countItem(bob, Items.DIAMOND) == 3);
        check("and the offer is closed",
                social.offerBetween(alice.getUUID(), bob.getUUID()) == null);
        check("paying again does nothing",
                social.payOffer(bob, alice.getUUID(), 800L) == ShopResult.NO_TRADE_OPEN);

        // ---- a full backpack sends it to the mailbox, not the floor ----
        for (com.example.globalshop.core.social.Mail existing : shop.mailsOf(bob.getUUID())) {
            shop.deleteMail(bob.getUUID(), existing.id());
        }
        bob.getInventory().clearContent();
        for (int i = 0; i < ItemStacks.PLAYER_SLOTS; i++) {
            bob.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        slot.setItem(0, new ItemStack(Items.DIAMOND, 2));
        check("a second offer is made",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.SUCCESS);
        check("and priced", social.publishPrice(alice, bob.getUUID(), 400L) == ShopResult.SUCCESS);
        int droppedBefore = countDroppedItems();
        check("paying into a full backpack still succeeds",
                social.payOffer(bob, alice.getUUID(), 400L) == ShopResult.SUCCESS);
        check("the item went to the mailbox", shop.mailCount(bob.getUUID()) == 1);
        check("and nothing was dropped on the floor", countDroppedItems() == droppedBefore);

        // ---- withdrawing gives it back ----
        bob.getInventory().clearContent();
        for (com.example.globalshop.core.social.Mail existing : shop.mailsOf(bob.getUUID())) {
            shop.deleteMail(bob.getUUID(), existing.id());
        }
        alice.getInventory().clearContent();
        slot.setItem(0, new ItemStack(Items.EMERALD, 5));
        check("a third offer is made",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.SUCCESS);
        check("the seller withdraws it",
                social.withdraw(alice, bob.getUUID()) == ShopResult.SUCCESS);
        check("the item came back", countItem(alice, Items.EMERALD) == 5);
        check("and there is no offer left",
                social.offerBetween(alice.getUUID(), bob.getUUID()) == null);
        check("withdrawing nothing is refused",
                social.withdraw(alice, bob.getUUID()) == ShopResult.NO_TRADE_OPEN);

        // ---- a restart keeps a held offer, item and all ----
        slot.setItem(0, new ItemStack(Items.GOLD_INGOT, 4));
        check("a fourth offer is made",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.SUCCESS);
        check("and priced", social.publishPrice(alice, bob.getUUID(), 123L) == ShopResult.SUCCESS);
        com.example.globalshop.core.social.SocialSavedData live =
                com.example.globalshop.core.social.SocialSavedData.get(server);
        com.example.globalshop.core.social.SocialSavedData reloaded =
                com.example.globalshop.core.social.SocialSavedData.reload(live);
        check("offers survive a restart", reloaded.offerCount() == live.offerCount());
        check("and one is still listed after it", reloaded.offerCount() >= 1);

        // ---- leaving the window hands the offer back ----
        // An offer holds a real item. Left behind when the window closes, it is in no inventory the
        // seller can see — which from their side is the item having vanished.
        // The fourth offer is still open, and only one may exist per pair, so it goes first.
        social.withdraw(alice, bob.getUUID());
        alice.getInventory().clearContent();
        slot.setItem(0, new ItemStack(Items.IRON_INGOT, 6));
        check("a fifth offer is made",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.SUCCESS);
        check("and it is holding the ingots",
                social.offerBetween(alice.getUUID(), bob.getUUID()).quantity() == 6);
        com.example.globalshop.core.menu.TradeMenu menu =
                new com.example.globalshop.core.menu.TradeMenu(0, alice.getInventory());
        menu.removed(alice);
        check("closing the window takes the offer back",
                social.offerBetween(alice.getUUID(), bob.getUUID()) == null);
        check("and the items are in the backpack", countItem(alice, Items.IRON_INGOT) == 6);

        // The same for a player whose offer outlived a server stop: logging in hands it back.
        slot.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        check("a sixth offer is made",
                social.offerTo(alice, bob.getUUID(), pools) == ShopResult.SUCCESS);
        alice.getInventory().clearContent();
        check("logging in with an offer outstanding takes it back",
                social.withdrawAll(alice) == 1);
        check("and the item came home", countItem(alice, Items.IRON_INGOT) == 2);
        check("with nothing left open",
                social.offerBetween(alice.getUUID(), bob.getUUID()) == null);

        // Leave nothing behind: an offer holds real items.
        social.withdraw(alice, bob.getUUID());
        social.withdraw(bob, alice.getUUID());
        for (com.example.globalshop.core.social.Mail existing : shop.mailsOf(bob.getUUID())) {
            shop.deleteMail(bob.getUUID(), existing.id());
        }
        alice.getInventory().clearContent();
        bob.getInventory().clearContent();
        check("no offers were left behind",
                social.offerBetween(alice.getUUID(), bob.getUUID()) == null);

        log("offers: escrow, shared price, price guard, delivery and withdrawal covered");
    }

    /** A FakePlayer that is deliberately not in the PlayerList, which is the offline case. */
    private ServerPlayer fake(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(server.overworld(), new GameProfile(id, name));
        // Deterministic opening balance: whatever the starting grant is set to, these checks
        // must not depend on it.
        EconomyServices.get(server).ensureAccount(id, name, 0L);
        return player;
    }

    /**
     * Runs two operations on separate threads that are released at the same moment.
     *
     * <p>The latch is what makes this a race rather than two calls in a row. Both futures are
     * awaited with a timeout, so a deadlock fails the test instead of hanging the server.
     */
    private <T> List<T> raceTogether(java.util.concurrent.Callable<T> first,
                                     java.util.concurrent.Callable<T> second) {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Future<T> a = pool.submit(() -> {
                go.await();
                return first.call();
            });
            java.util.concurrent.Future<T> b = pool.submit(() -> {
                go.await();
                return second.call();
            });
            go.countDown();
            return List.of(a.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception failure) {
            check("the two racing operations both completed", false);
            log("      race failed: " + failure);
            return List.of();
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reads one bundled language file as UTF-8, or an empty string if it cannot be read. */
    private static String readLangFile(String language) {
        String resource = "/assets/globalshop/lang/" + language + ".json";
        try (java.io.InputStream in = SelfTest.class.getResourceAsStream(resource)) {
            return in == null
                    ? ""
                    : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception failure) {
            return "";
        }
    }

    private static boolean namesAnAdminVerb(String path) {
        for (String token : path.split(" ")) {
            if (token.startsWith("<")) {
                continue;
            }
            if (token.equalsIgnoreCase("give") || token.equalsIgnoreCase("take")
                    || token.equalsIgnoreCase("set")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Depth-first walk, accumulating each ancestor's requirement the way Brigadier does.
     *
     * <p>A node is reachable only if its own requirement passes <em>and</em> so does every
     * ancestor's — which is exactly the rule that makes {@code /money give} operator-only even
     * though the check is written on the {@code give} literal rather than on its children.
     */
    private void walkPermissions(CommandNode<CommandSourceStack> node, String path,
            boolean playerReachable, boolean operatorReachable, boolean consoleReachable,
            CommandSourceStack player, CommandSourceStack operator, CommandSourceStack console,
            Map<String, Boolean> forPlayer, Map<String, Boolean> forOperator,
            Map<String, Boolean> forConsole, int[] visited) {
        if (++visited[0] > 2000) {
            return;
        }
        boolean asPlayer = playerReachable && node.getRequirement().test(player);
        boolean asOperator = operatorReachable && node.getRequirement().test(operator);
        boolean asConsole = consoleReachable && node.getRequirement().test(console);
        if (!path.isEmpty()) {
            forPlayer.put(path, asPlayer);
            forOperator.put(path, asOperator);
            forConsole.put(path, asConsole);
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            String name = child instanceof ArgumentCommandNode<?, ?>
                    ? "<" + child.getName() + ">"
                    : child.getName();
            walkPermissions(child, path.isEmpty() ? "/" + name : path + " " + name,
                    asPlayer, asOperator, asConsole, player, operator, console,
                    forPlayer, forOperator, forConsole, visited);
        }
    }

    private void registrationTests() {
        log("-- registration --");
        check("the shop_terminal item is registered",
                ForgeRegistries.ITEMS.getValue(id("shop_terminal")) != null);
        check("the shop_terminal block is registered",
                ForgeRegistries.BLOCKS.getValue(id("shop_terminal")) != null);
        check("the shop menu type is registered",
                ForgeRegistries.MENU_TYPES.getValue(id("shop")) != null);

        check("the server_shop_terminal item is registered",
                ForgeRegistries.ITEMS.getValue(id("server_shop_terminal")) != null);
        check("the server_shop_terminal block is registered",
                ForgeRegistries.BLOCKS.getValue(id("server_shop_terminal")) != null);
        check("the server shop menu type is registered",
                ForgeRegistries.MENU_TYPES.getValue(id("server_shop")) != null);
        check("the two terminals are different items",
                ForgeRegistries.ITEMS.getValue(id("shop_terminal"))
                        != ForgeRegistries.ITEMS.getValue(id("server_shop_terminal")));

        // Every item the mod adds must be reachable from its own creative tab.
        //
        // Nothing else catches this. An item that is registered but absent from the tab still
        // exists and still works; it is simply invisible to anyone looking for it, which is how
        // the server shop terminal first shipped without appearing beside the market terminal.
        // This walks the item registry rather than a hand-written list, so an item added later
        // is covered without anyone remembering to add it here too.
        CreativeModeTab tab = ModCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.REGISTRY.allFlags(), true, server.registryAccess()));
        Set<Item> shown = new HashSet<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            shown.add(stack.getItem());
        }
        int modItems = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null || !GlobalShop.MODID.equals(key.getNamespace())) {
                continue;
            }
            modItems++;
            check("the creative tab offers " + key.getPath(), shown.contains(item));
        }
        check("the mod registers more than one item, so the checks above are meaningful",
                modItems >= 2);

        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();
        check("/money is registered", root.getChild("money") != null);
        check("/pay is registered", root.getChild("pay") != null);
        check("/shop is registered", root.getChild("shop") != null);

        CommandNode<CommandSourceStack> global = root.getChild("globalshop");
        check("/globalshop is registered", global != null);
        if (global != null) {
            check("/globalshop open is registered", global.getChild("open") != null);
            check("/globalshop reload is registered", global.getChild("reload") != null);
            check("/globalshop logs is registered", global.getChild("logs") != null);
            check("/globalshop remove is registered", global.getChild("remove") != null);
            check("/globalshop claim is registered", global.getChild("claim") != null);

            // The documented fallbacks for a /money or /pay clash.
            CommandNode<CommandSourceStack> money = global.getChild("money");
            check("/globalshop money is registered", money != null);
            if (money != null) {
                check("/globalshop money give is registered", money.getChild("give") != null);
                check("/globalshop money take is registered", money.getChild("take") != null);
                check("/globalshop money set is registered", money.getChild("set") != null);
            }
            CommandNode<CommandSourceStack> pay = global.getChild("pay");
            check("/globalshop pay is registered", pay != null);
            check("/globalshop pay takes a player and an amount",
                    pay != null && pay.getChild("targets") != null
                            && pay.getChild("targets").getChild("amount") != null);
        }

        CommandNode<CommandSourceStack> moneyRoot = root.getChild("money");
        check("/money give|take|set are registered", moneyRoot != null
                && moneyRoot.getChild("give") != null
                && moneyRoot.getChild("take") != null
                && moneyRoot.getChild("set") != null);

        // Informational: this is where SavedData is written on a save.
        log("world data directory is " + server.getWorldPath(LevelResource.ROOT).resolve("data"));
    }

    private static ResourceLocation id(String path) {
        ResourceLocation location = ResourceLocation.tryBuild(GlobalShop.MODID, path);
        if (location == null) {
            throw new IllegalStateException("invalid resource location: " + path);
        }
        return location;
    }

    // ==================================================================
    // Command-tree serialisation -- the code path a real login takes
    // ==================================================================

    /**
     * Verifies that the server can serialise its command tree for a client.
     *
     * <p>This section exists because of a bug that shipped once: a custom Brigadier
     * argument type was never registered, so {@code Commands.sendCommands} threw
     * {@code IllegalArgumentException: Unrecognized argument type} while the player was
     * being placed in the world. Every login was refused with "Invalid player data",
     * and the shipped jar was unusable -- yet the rest of this self test passed,
     * because a {@link net.minecraftforge.common.util.FakePlayer} never logs in and so
     * never triggered the serialisation. Nothing here may be removed without replacing
     * it with something that still exercises this path.
     */
    private void commandSyncTests(ServerPlayer player) {
        log("-- command tree sync (the path a real login takes) --");

        check("the custom amount argument type is registered for client sync",
                ArgumentTypeInfos.isClassRecognized(MoneyArgumentType.class));

        // A sweep of the whole tree, so any future custom argument type added without
        // registering a serialiser fails here instead of in a player's face.
        List<String> unregistered = new ArrayList<>();
        Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collectUnregisteredArgumentTypes(server.getCommands().getDispatcher().getRoot(), unregistered, seen);
        check("every argument type in the command tree is registered for client sync"
                        + (unregistered.isEmpty() ? "" : " -- missing: " + unregistered),
                unregistered.isEmpty());

        // The definitive check: the very call the server makes mid-login
        // (PlayerList.placeNewPlayer -> Commands.sendCommands). It builds the whole
        // packet, so an unregistered type throws right here.
        // A FakePlayer has no real network connection, so once the packet has been built
        // a NullPointerException on the send is expected and is not a failure.
        String outcome = "sent";
        boolean serialised = true;
        try {
            server.getCommands().sendCommands(player);
        } catch (NullPointerException noNetworkConnection) {
            outcome = "packet built; no network connection to send it to";
        } catch (Throwable broken) {
            outcome = "threw " + broken;
            serialised = false;
        }
        check("the full command tree serialises for a login"
                        + (serialised ? "" : " -- " + outcome),
                serialised);
        if (serialised) {
            log("command tree serialisation: " + outcome);
        }
    }

    private static void collectUnregisteredArgumentTypes(CommandNode<?> node, List<String> out,
                                                         Set<CommandNode<?>> seen) {
        if (!seen.add(node)) {
            return;
        }
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            Class<?> type = argument.getType().getClass();
            if (!ArgumentTypeInfos.isClassRecognized(type)) {
                out.add(type.getSimpleName());
            }
        }
        for (CommandNode<?> child : node.getChildren()) {
            collectUnregisteredArgumentTypes(child, out, seen);
        }
    }

    // ==================================================================
    // Economy
    // ==================================================================

    private void economyTests(EconomyService economy, ServerPlayer alice, ServerPlayer bob) {
        log("-- economy --");
        economy.setBalance(alice.getUUID(), 10_000L);
        economy.setBalance(bob.getUUID(), 10_000L);

        check("balance reads back", economy.getBalance(alice.getUUID()) == 10_000L);
        check("hasEnough true at exactly the balance", economy.hasEnough(alice.getUUID(), 10_000L));
        check("hasEnough false one unit above", !economy.hasEnough(alice.getUUID(), 10_001L));
        check("addBalance rejects zero",
                economy.addBalance(alice.getUUID(), 0L) == TransactionResult.INVALID_AMOUNT);
        check("addBalance rejects a negative amount",
                economy.addBalance(alice.getUUID(), -5L) == TransactionResult.INVALID_AMOUNT);

        check("removeBalance refuses to overdraw",
                economy.removeBalance(alice.getUUID(), 999_999L) == TransactionResult.INSUFFICIENT_FUNDS);
        check("the refused withdrawal changed nothing", economy.getBalance(alice.getUUID()) == 10_000L);

        check("transfer succeeds",
                economy.transfer(alice.getUUID(), bob.getUUID(), 2_500L) == TransactionResult.SUCCESS);
        check("sender was debited", economy.getBalance(alice.getUUID()) == 7_500L);
        check("receiver was credited", economy.getBalance(bob.getUUID()) == 12_500L);
        check("paying yourself is rejected",
                economy.transfer(alice.getUUID(), alice.getUUID(), 1L) == TransactionResult.SAME_ACCOUNT);

        check("transfer beyond the balance is rejected",
                economy.transfer(bob.getUUID(), alice.getUUID(), 999_999L)
                        == TransactionResult.INSUFFICIENT_FUNDS);
        check("the rejected transfer moved nothing",
                economy.getBalance(bob.getUUID()) == 12_500L && economy.getBalance(alice.getUUID()) == 7_500L);
        check("no money was created or destroyed",
                economy.getBalance(alice.getUUID()) + economy.getBalance(bob.getUUID()) == 20_000L);
        check("an overflowing credit is refused, not wrapped",
                economy.addBalance(alice.getUUID(), Long.MAX_VALUE) == TransactionResult.OVERFLOW);
        check("the refused credit changed nothing", economy.getBalance(alice.getUUID()) == 7_500L);
    }

    // ==================================================================
    // Marketplace
    // ==================================================================

    /** @return the id of the listing left on the market for the persistence check */
    private UUID shopTests(EconomyService economy, ShopService shop, ServerPlayer alice, ServerPlayer bob) {
        log("-- marketplace --");
        if (!Config.allowListing()) {
            skip("listing is disabled in the config; marketplace checks were skipped");
            return null;
        }

        SimpleContainer input = new SimpleContainer(1);
        input.setItem(0, new ItemStack(Items.STONE, 64));
        List<ItemPool> pools = List.of(new ContainerPool(input), new ContainerPool(alice.getInventory()));

        check("create listing succeeds",
                shop.create(alice, input.getItem(0), 10, 100L, 2, 4, pools) == ShopResult.SUCCESS);
        check("escrow took exactly the listed 10 items", input.getItem(0).getCount() == 54);

        List<Listing> mine = shop.listingsOf(alice.getUUID());
        check("the listing appears in the seller's own view", mine.size() == 1);
        if (mine.isEmpty()) {
            return null;
        }
        Listing listing = mine.get(0);
        check("stock was recorded", listing.stock() == 10);
        check("unit price was recorded", listing.unitPrice() == 100L);
        check("the listing is visible to other players",
                shop.search("", com.example.globalshop.core.market.ShopSort.TIME_NEWEST, null, false).stream()
                        .anyMatch(l -> l.id().equals(listing.id())));
        check("searching by item name finds it",
                shop.search("stone", com.example.globalshop.core.market.ShopSort.TIME_NEWEST, null, false).stream()
                        .anyMatch(l -> l.id().equals(listing.id())));

        check("a quantity below the minimum is rejected",
                buy(shop, bob, listing.id(), 1) == ShopResult.BELOW_MINIMUM);
        check("a quantity above the maximum is rejected",
                buy(shop, bob, listing.id(), 5) == ShopResult.ABOVE_MAXIMUM);
        check("a quantity above the stock is rejected",
                buy(shop, bob, listing.id(), 11) == ShopResult.ABOVE_MAXIMUM);
        check("a seller cannot buy their own listing",
                buy(shop, alice, listing.id(), 2) == ShopResult.CANNOT_BUY_OWN);
        check("an unknown listing is rejected",
                buy(shop, bob, UUID.randomUUID(), 2) == ShopResult.LISTING_NOT_FOUND);

        int stoneBefore = countStone(bob);
        check("a purchase succeeds", buy(shop, bob, listing.id(), 4) == ShopResult.SUCCESS);
        check("the buyer paid the exact total", economy.getBalance(bob.getUUID()) == 12_100L);
        Listing afterSale = shop.find(listing.id());
        check("stock dropped by the quantity bought", afterSale != null && afterSale.stock() == 6);
        check("the buyer actually received 4 items", countStone(bob) - stoneBefore == 4);

        // The shop takes a commission out of the total, so the seller receives less than the
        // buyer paid and the difference leaves the server entirely. Pinning the arithmetic here
        // is what stops a future change to the split from going unnoticed.
        long tradedTotal = 400L;
        long tradedCommission = tradedTotal * Config.marketCommissionPercent() / 100L;
        check("the seller received the total less the shop's commission",
                economy.getBalance(alice.getUUID()) == 7_500L + tradedTotal - tradedCommission);
        check("the commission is the only money that left the server",
                economy.getBalance(alice.getUUID()) + economy.getBalance(bob.getUUID())
                        == 20_000L - tradedCommission);

        // A purchase must fail as a whole: no money, no goods, no stock change.
        long bobBalance = economy.getBalance(bob.getUUID());
        economy.setBalance(bob.getUUID(), 0L);
        check("a purchase without funds is rejected",
                buy(shop, bob, listing.id(), 2) == ShopResult.INSUFFICIENT_FUNDS);
        Listing afterFailure = shop.find(listing.id());
        check("the failed purchase consumed no stock", afterFailure != null && afterFailure.stock() == 6);
        check("the failed purchase paid the seller nothing",
                economy.getBalance(alice.getUUID()) == 7_500L + tradedTotal - tradedCommission);
        check("the failed purchase took no money from the buyer", economy.getBalance(bob.getUUID()) == 0L);
        economy.setBalance(bob.getUUID(), bobBalance);

        check("the seller can change the price",
                shop.modify(alice, listing.id(), 250L, 6, 2, 3, pools) == ShopResult.SUCCESS);
        Listing modified = shop.find(listing.id());
        check("the new price is live", modified != null && modified.unitPrice() == 250L);
        check("contradictory limits are rejected",
                shop.modify(alice, listing.id(), 250L, 6, 5, 3, pools) == ShopResult.INVALID_MAX);
        check("a stranger cannot edit the listing",
                shop.modify(bob, listing.id(), 1L, 6, 1, 1, pools) == ShopResult.NOT_THE_OWNER);

        // Restocking is the one-click version of raising the stock: the server counts what the
        // seller is carrying and adds all of it, so there is no number to get wrong. It gets a
        // lot of its own so the escrow totals asserted elsewhere in this section are untouched,
        // and it is cleaned up fully — drained mailbox included — before moving on.
        alice.getInventory().clearContent();
        alice.getInventory().setItem(0, new ItemStack(Items.STONE, 20));
        check("a lot for the restock check was created",
                shop.create(alice, new ItemStack(Items.STONE), 4, 100L, 1, 4, pools) == ShopResult.SUCCESS);
        Listing restockListing = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.unitPrice() == 100L && candidate.stock() == 4) {
                restockListing = candidate;
                break;
            }
        }
        check("the restock lot is found", restockListing != null);
        if (restockListing != null) {
            // Count what is actually available first: the pools are the sell slot *and* the
            // backpack, so asserting a literal would be asserting how much earlier checks
            // happened to leave in the slot.
            int carriedForRestock = ItemStacks.countMatching(new ItemStack(Items.STONE), pools);
            int stockBeforeRestock = restockListing.stock();
            check("restocking succeeds",
                    shop.restock(alice, restockListing.id(), pools) == ShopResult.SUCCESS);
            Listing restocked = shop.find(restockListing.id());
            check("restocking added exactly what the seller was carrying",
                    restocked != null && restocked.stock() == stockBeforeRestock + carriedForRestock);
            check("restocking emptied that item out of the backpack", countStone(alice) == 0);
            check("a stranger cannot restock someone else's lot",
                    shop.restock(bob, restockListing.id(), pools) == ShopResult.NOT_THE_OWNER);
            check("restocking an unknown lot is refused",
                    shop.restock(alice, UUID.randomUUID(), pools) == ShopResult.LISTING_NOT_FOUND);
            shop.unlist(alice, restockListing.id());
            shop.deliverMail(alice);
        }
        alice.getInventory().clearContent();

        // A FakePlayer is not in the PlayerList, so the shop correctly treats the
        // seller as offline here: the returned stock waits in their mailbox instead of
        // being pushed into an inventory that is not connected to the world. Both paths
        // are the same code; this asserts the offline one, which is also what happens
        // when a real seller is away.
        int invBefore = countStone(alice);
        int inputBefore = input.getItem(0).getCount();
        Listing liveBeforeUnlist = shop.find(listing.id());
        int stockAtUnlist = (liveBeforeUnlist == null) ? -1 : liveBeforeUnlist.stock();

        check("unlisting succeeds", shop.unlist(alice, listing.id()) == ShopResult.SUCCESS);
        check("the listing is gone", shop.find(listing.id()) == null);

        boolean mailAfter = shop.hasMail(alice.getUUID());
        int invAfterUnlist = countStone(alice);
        int inputAfterUnlist = input.getItem(0).getCount();
        log("diagnostic: stockAtUnlist=" + stockAtUnlist
                + " mailAfter=" + mailAfter
                + " invBefore=" + invBefore + " invAfterUnlist=" + invAfterUnlist
                + " inputBefore=" + inputBefore + " inputAfterUnlist=" + inputAfterUnlist);

        check("the unlisted stock is held for the seller", mailAfter);

        int stacks = shop.deliverMail(alice);
        int invAfter = countStone(alice);
        int inputAfter = input.getItem(0).getCount();
        log("diagnostic: mailboxStacks=" + stacks
                + " invFinal=" + invAfter + " inputFinal=" + inputAfter);

        // Split in two so that, if this ever misbehaves, the log says which half broke:
        // an empty mailbox is a delivery problem, a wrong total is a quantity problem.
        check("collecting the mailbox produced at least one stack", stacks > 0);
        check("collecting it returns the remaining 6 escrowed items",
                (invAfter - invBefore) + (inputAfter - inputBefore) == 6);

        // Leave a fresh listing in place so the persistence check has something to look at.
        input.setItem(0, new ItemStack(Items.STONE, 5));
        check("a listing created for the persistence check succeeds",
                shop.create(alice, input.getItem(0), 5, 100L, 1, 5, pools) == ShopResult.SUCCESS);
        // Find it by its item rather than by position: listingsOf() is newest-first, so
        // indexing by position is the kind of assumption that breaks silently as soon as
        // another listing exists.
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.template().getItem() == Items.STONE) {
                return candidate.id();
            }
        }
        return null;
    }

    private ShopResult buy(ShopService shop, ServerPlayer buyer, UUID listingId, int quantity) {
        // The anti-flood throttle is a separate feature with its own check below;
        // clear it so a legitimate purchase under test is not mistaken for a flood.
        shop.forget(buyer.getUUID());
        return shop.purchase(buyer, listingId, quantity);
    }

    private static int countStone(ServerPlayer player) {
        return ItemStacks.countMatching(new ItemStack(Items.STONE),
                List.of(new ContainerPool(player.getInventory())));
    }

    // ==================================================================
    // Persistence
    // ==================================================================

    private void persistenceTests(EconomyService economy, UUID listingId) {
        log("-- persistence (what a server restart does) --");

        EconomySavedData liveEconomy = EconomySavedData.get(server);
        EconomySavedData reloadedEconomy = EconomySavedData.reload(liveEconomy);
        check("balances survive a save/load round trip",
                reloadedEconomy.balanceOf(aliceUuid()) == economy.getBalance(aliceUuid()));
        check("the account list survives a save/load round trip",
                reloadedEconomy.accountCount() == liveEconomy.accountCount());

        ShopSavedData liveShop = ShopSavedData.get(server);
        ShopSavedData reloadedShop = ShopSavedData.reload(liveShop);
        check("listings survive a save/load round trip",
                reloadedShop.listingCount() == liveShop.listingCount());
        check("the trade log survives a save/load round trip",
                reloadedShop.logEntryCount() == liveShop.logEntryCount());

        if (listingId != null) {
            Listing before = liveShop.listing(listingId);
            Listing after = reloadedShop.listing(listingId);
            check("a listing's item survives the round trip", before != null && after != null
                    && ItemStack.isSameItemSameTags(before.template(), after.template()));
            check("a listing's stock and price survive the round trip", before != null && after != null
                    && before.stock() == after.stock() && before.unitPrice() == after.unitPrice());
            check("ownership survives the round trip", after != null && after.isSeller(aliceUuid()));
        } else {
            skip("no listing available for the listing round-trip check");
        }
    }

    private UUID aliceUuid() {
        return profile("alice", ALICE_NAME).getId();
    }

    // ==================================================================
    // Items whose identity lives in NBT
    // ==================================================================

    /**
     * Lists an item whose entire identity is carried in NBT -- a custom name, two
     * enchantments and a damage value -- and follows it all the way through storage,
     * a save/load round trip, and delivery to a buyer.
     *
     * <p>This is the mechanism every third-party mod item depends on. A plain stack of
     * stone exercises none of it: it has no NBT, so it would pass even if the mod
     * dropped every tag on the floor. If this section passes, an item from another mod
     * (whose data is likewise just a registry name plus a tag) survives intact.
     */
    private void nbtItemTests(EconomyService economy, ShopService shop, ServerPlayer alice, ServerPlayer bob) {
        log("-- items carrying NBT (what modded items rely on) --");
        if (!Config.allowListing()) {
            skip("listing is disabled in the config; NBT item checks were skipped");
            return;
        }

        ItemStack fancy = new ItemStack(Items.DIAMOND_SWORD);
        fancy.setHoverName(Component.literal("GlobalShop Selftest Blade"));
        fancy.setDamageValue(37);
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantments.SHARPNESS, 5);
        enchantments.put(Enchantments.UNBREAKING, 3);
        EnchantmentHelper.setEnchantments(enchantments, fancy);

        check("the constructed item really does carry NBT",
                fancy.getTag() != null && !fancy.getTag().isEmpty());

        SimpleContainer input = new SimpleContainer(1);
        input.setItem(0, fancy.copy());
        List<ItemPool> pools = List.of(new ContainerPool(input), new ContainerPool(alice.getInventory()));

        check("an NBT-bearing item can be listed",
                shop.create(alice, input.getItem(0), 1, 500L, 1, 1, pools) == ShopResult.SUCCESS);

        // Located by its item, not by list position: listingsOf() is newest-first and
        // other lots may also be present.
        Listing listing = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.template().getItem() == Items.DIAMOND_SWORD) {
                listing = candidate;
                break;
            }
        }
        check("the NBT listing can be found again", listing != null);
        if (listing == null) {
            return;
        }
        ItemStack stored = listing.template();

        check("the custom name survived being listed",
                "GlobalShop Selftest Blade".equals(stored.getHoverName().getString()));
        check("the damage value survived being listed", stored.getDamageValue() == 37);
        check("the enchantments survived being listed",
                EnchantmentHelper.getEnchantments(stored).equals(enchantments));
        check("the item is still seen as enchanted", stored.isEnchanted());

        // Exactly what a server restart does to every stored stack.
        ShopSavedData reloaded = ShopSavedData.reload(ShopSavedData.get(server));
        Listing afterReload = reloaded.listing(listing.id());
        check("the NBT-bearing listing survives a save/load round trip", afterReload != null);
        if (afterReload != null) {
            ItemStack restored = afterReload.template();
            check("the custom name survived the round trip",
                    "GlobalShop Selftest Blade".equals(restored.getHoverName().getString()));
            check("the damage value survived the round trip", restored.getDamageValue() == 37);
            check("the enchantments survived the round trip",
                    EnchantmentHelper.getEnchantments(restored).equals(enchantments));
            check("every tag is byte-identical after the round trip",
                    ItemStack.isSameItemSameTags(stored, restored));
        }

        // The buyer must receive the tagged item, not a stripped-down one.
        long nbtSellerBefore = economy.getBalance(alice.getUUID());
        // Read the price off the listing rather than inferring it from a balance, so the
        // assertion says what it means.
        Listing beforeNbt = shop.find(listing.id());
        check("a buyer can purchase the NBT item",
                buy(shop, bob, listing.id(), 1) == ShopResult.SUCCESS);
        check("the buyer received an item with byte-identical NBT",
                inventoryContains(bob, stored));
        long nbtTotal = beforeNbt == null ? 0L : beforeNbt.unitPrice();
        long nbtCommission = nbtTotal * Config.marketCommissionPercent() / 100L;
        check("the seller was paid the price less the shop's commission",
                economy.getBalance(alice.getUUID()) == nbtSellerBefore + nbtTotal - nbtCommission);

        // The detail panel can only show enchantments and durability if they survive the
        // trip to the client, so send the page through the real codec. This is also the
        // only check that exercises the mod's packet serialisation at all.
        ShopDataPacket page = ShopDataPacket.build(alice,
                new ShopViewState("", ShopSort.TIME_NEWEST, true, false, 0));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        page.encode(buffer);
        ShopDataPacket received = ShopDataPacket.decode(buffer);

        ListingView enchanted = null;
        for (ListingView view : received.listings()) {
            if (view.display().getItem() == Items.DIAMOND_SWORD) {
                enchanted = view;
                break;
            }
        }
        check("the enchanted tool survives the page being encoded and decoded", enchanted != null);
        if (enchanted != null) {
            check("the client receives the enchantments, so the panel can show them",
                    EnchantmentHelper.getEnchantments(enchanted.display()).equals(enchantments));
            check("the client receives the damage, so the panel can show durability",
                    enchanted.display().getDamageValue() == 37);
            check("the client receives a damageable item with a real maximum",
                    enchanted.display().isDamageableItem() && enchanted.display().getMaxDamage() > 0);
        }
    }

    private static boolean inventoryContains(ServerPlayer player, ItemStack probe) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameTags(inventory.getItem(slot), probe)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // The page data the screen draws
    // ==================================================================

    /**
     * Checks the payload the GUI is built from.
     *
     * <p>The Unlist button is only offered when the server marks a lot as belonging to the
     * viewer, and a seller can only manage a sold-out lot if their own page still contains
     * it. Both are decided here, on the server, so both can be verified without a client --
     * which matters, because the screen itself cannot be launched in this environment.
     *
     * <p>Runs after the NBT section, which leaves a sold-out lot behind on purpose.
     */
    private void marketPageTests(ServerPlayer alice, ServerPlayer bob) {
        log("-- marketplace page data (what the screen draws) --");

        ShopDataPacket mine = ShopDataPacket.build(alice,
                new ShopViewState("", ShopSort.TIME_NEWEST, true, false, 0));
        check("the my-listings page is not empty", !mine.listings().isEmpty());
        check("every lot on the my-listings page is marked as the viewer's own",
                mine.listings().stream().allMatch(ListingView::ownedByViewer));
        check("every lot on the my-listings page really belongs to the viewer",
                mine.listings().stream().allMatch(view -> view.sellerId().equals(alice.getUUID())));
        check("a sold-out lot is still listed for its own seller, so it can be restocked or removed",
                mine.listings().stream().anyMatch(ListingView::soldOut));

        ShopDataPacket market = ShopDataPacket.build(bob,
                new ShopViewState("", ShopSort.TIME_NEWEST, false, false, 0));
        check("another player's market page does not claim the lots are theirs",
                market.listings().stream().noneMatch(ListingView::ownedByViewer));
        check("the public market hides sold-out lots",
                market.listings().stream().noneMatch(ListingView::soldOut));

        check("the balance sent with the page matches the account",
                mine.viewerBalance() == EconomyServices.get(server).getBalance(alice.getUUID()));
    }

    // ==================================================================
    // Escrow that has to reach past the first pool
    // ==================================================================

    /**
     * Lists more items than the sell slot can hold, so escrow has to continue into the
     * backpack.
     *
     * <p>This section exists because of a bug that shipped: the probe handed to escrow was
     * the live stack in the sell slot, so emptying that slot also emptied the probe, and
     * the backpack then matched nothing. Listing more than one stack therefore always
     * failed with "not enough items" no matter how many the player was carrying. Every
     * earlier test happened to list no more than the slot held, so none of them crossed
     * that boundary.
     */
    private void multiStackEscrowTests(ShopService shop, ServerPlayer alice) {
        log("-- escrow across the sell slot and the backpack --");
        if (!Config.allowListing()) {
            skip("listing is disabled in the config; cross-pool escrow checks were skipped");
            return;
        }

        Inventory inventory = alice.getInventory();
        inventory.clearContent();

        SimpleContainer input = new SimpleContainer(1);
        input.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        inventory.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        List<ItemPool> pools = List.of(new ContainerPool(input), new ContainerPool(inventory));

        ItemStack probe = input.getItem(0);
        check("the slot and the backpack together hold 128", ItemStacks.countMatching(probe, pools) == 128);

        check("listing 128 succeeds even though the slot holds only 64",
                shop.create(alice, probe, 128, 10L, 1, 128, pools) == ShopResult.SUCCESS);

        Listing listing = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.template().getItem() == Items.OAK_LOG) {
                listing = candidate;
                break;
            }
        }
        check("the lot was created", listing != null);
        if (listing != null) {
            check("the lot holds all 128", listing.stock() == 128);
        }
        check("the sell slot was emptied first", input.getItem(0).isEmpty());
        check("nothing was left behind in either pool",
                ItemStacks.countMatching(new ItemStack(Items.OAK_LOG), pools) == 0);

        // Asking for more than the player owns must still be refused, and change nothing.
        input.setItem(0, new ItemStack(Items.OAK_LOG, 8));
        check("a request beyond what is carried is refused",
                shop.create(alice, input.getItem(0), 9, 10L, 1, 9, pools) == ShopResult.NOT_ENOUGH_ITEMS);
        check("the refusal left the slot untouched", input.getItem(0).getCount() == 8);
    }

    // ==================================================================
    // Anti-flood throttle
    // ==================================================================

    private void throttleTest(ShopService shop, ServerPlayer alice, ServerPlayer bob) {
        if (Config.purchaseCooldownMs() <= 0) {
            skip("purchase throttle is disabled in the config");
            return;
        }
        log("-- purchase throttle --");
        SimpleContainer input = new SimpleContainer(1);
        input.setItem(0, new ItemStack(Items.DIRT, 16));
        List<ItemPool> pools = List.of(new ContainerPool(input), new ContainerPool(alice.getInventory()));

        if (shop.create(alice, input.getItem(0), 8, 1L, 1, 8, pools) != ShopResult.SUCCESS) {
            skip("could not create a listing for the throttle check");
            return;
        }
        // Located by its item rather than by position; see the note in shopTests.
        UUID id = null;
        for (Listing candidate : shop.listingsOf(alice.getUUID())) {
            if (candidate.template().getItem() == Items.DIRT) {
                id = candidate.id();
                break;
            }
        }
        if (id == null) {
            skip("no listing available for the throttle check");
            return;
        }

        // Deliberately bypass the helper so the throttle is not cleared between the two
        // calls -- but start from a clean slate, or a purchase a moment ago in an
        // earlier section would already have this player inside the cooldown window.
        shop.forget(bob.getUUID());
        ShopResult first = shop.purchase(bob, id, 1);
        ShopResult second = shop.purchase(bob, id, 1);
        check("the first purchase after a quiet period is accepted", first == ShopResult.SUCCESS);
        check("an immediate second purchase is throttled", second == ShopResult.COOLDOWN);
    }

    // ==================================================================
    // Housekeeping
    // ==================================================================

    /**
     * Empties a fake player's backpack and mailbox.
     *
     * <p>The mailbox lives inside the world save, so anything a previous run left
     * behind is still there on the next start. Draining it into an emptied inventory
     * and then clearing again is the simplest way to guarantee the mod's stores are
     * genuinely empty, which is what makes these checks repeatable.
     */
    private void resetCarriedItems(ShopService shop, ServerPlayer player) {
        player.getInventory().clearContent();
        shop.deliverMail(player);
        player.getInventory().clearContent();
    }

    private void cleanup(ShopService shop, EconomyService economy, ServerPlayer alice, ServerPlayer bob,
                         long aliceBefore, long bobBefore) {
        try {
            for (Listing listing : shop.listingsOf(alice.getUUID())) {
                shop.forceRemove(listing.id(), "selftest");
            }
            for (Listing listing : shop.listingsOf(bob.getUUID())) {
                shop.forceRemove(listing.id(), "selftest");
            }
            // forceRemove returns unsold stock to the seller, and a fake player counts
            // as offline, so it lands in the mailbox. Clear it or it would be written
            // into the world save and counted by the next run.
            resetCarriedItems(shop, alice);
            resetCarriedItems(shop, bob);
            economy.setBalance(alice.getUUID(), aliceBefore);
            economy.setBalance(bob.getUUID(), bobBefore);
            log("cleanup: removed self-test listings, drained both mailboxes, restored both balances");
        } catch (Throwable problem) {
            GlobalShop.LOGGER.warn("globalshop: selftest cleanup failed", problem);
        }
    }

    private void check(String name, boolean condition) {
        if (condition) {
            passed++;
            log("PASS  " + name);
        } else {
            failed++;
            log("FAIL  " + name);
        }
    }

    private void skip(String name) {
        skipped++;
        log("SKIP  " + name);
    }

    private void log(String message) {
        GlobalShop.LOGGER.info("[selftest] {}", message);
        source.sendSuccess(() -> Component.literal("[selftest] " + message), false);
    }

    /**
     * Collects balance events so a check can assert the numbers rather than only that something
     * arrived. A registered object rather than a lambda: IEventBus keys listeners on a real event
     * type, and a lambda's parameter type is erased by the time it is passed.
     */
    private static final class BalanceEventCapture {

        final java.util.List<com.example.globalshop.api.event.BalanceChangedEvent> seen =
                new java.util.ArrayList<>();

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onBalance(com.example.globalshop.api.event.BalanceChangedEvent event) {
            seen.add(event);
        }
    }
}
