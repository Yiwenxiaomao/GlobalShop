package com.example.globalshop.command;

import com.example.globalshop.Config;
import com.example.globalshop.GlobalShop;
import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.shop.Listing;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.shop.ShopService;
import com.example.globalshop.shop.ShopServices;
import com.example.globalshop.shop.ShopSort;
import com.example.globalshop.shop.TradeLogEntry;
import com.example.globalshop.test.SelfTest;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The marketplace commands.
 *
 * <h2>Permission levels</h2>
 * <ul>
 *   <li>{@code /shop}, {@code /globalshop}, {@code /globalshop open} -- level 0, opens the market.</li>
 *   <li>{@code /globalshop money ...} and {@code /globalshop pay ...} -- same levels as
 *       {@code /money} and {@code /pay}; provided so a clash with another mod's
 *       {@code /money} cannot lock players out.</li>
 *   <li>{@code /globalshop reload|logs|ids|remove} -- level 2 (operator).</li>
 *   <li>{@code /globalshop claim} -- level 0, collects items owed to you.</li>
 *   <li>{@code /globalshop selftest} -- level 4 (dedicated server console).</li>
 * </ul>
 *
 * <p>All of these execute on the server. The client never runs them and cannot ask
 * the server to do anything they could not already do through the GUI.
 */
public final class ShopCommand {

    private static final SimpleCommandExceptionType ERROR_INVALID_ID =
            new SimpleCommandExceptionType(Component.translatable("globalshop.command.invalid_id"));

    private ShopCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("shop").executes(ShopCommand::open));

        dispatcher.register(Commands.literal("globalshop")
                .executes(ShopCommand::open)
                .then(Commands.literal("open").executes(ShopCommand::open))
                // Explicit ways to reach either shop, so a player never has to guess which
                // terminal is which. Both open the same market an operator-run server always had.
                .then(Commands.literal("market").executes(ShopCommand::open))
                // The official server shop, with its own subtree. Bare, it opens that shop.
                .then(ServerShopCommand.build(buildContext))
                // Fallbacks for a /money or /pay clash.
                .then(MoneyCommand.buildMoney("money"))
                .then(MoneyCommand.buildPay("pay"))
                .then(Commands.literal("claim").executes(ShopCommand::claim))
                // The teleport branch exists mainly so the accept and refuse buttons in a request's
                // chat line can be real commands rather than a special case.
                .then(com.example.globalshop.command.TeleportCommand.build())
                .then(vipSubtree())
                // Everything below is the text form of a window: same service, same rules, so a
                // player who cannot or will not use the GUI is not a second-class player.
                .then(mailSubtree())
                .then(rankSubtree())
                .then(tradesSubtree())
                // The market's own actions, so the window is a convenience rather than the only way.
                .then(sellSubtree())
                .then(buySubtree())
                .then(mineSubtree())
                .then(unlistSubtree())
                .then(Commands.literal("unlistall").executes(ShopCommand::unlistAll))
                .then(restockSubtree())
                .then(modifySubtree())
                .then(blockSubtree(buildContext))
                .then(unblockSubtree(buildContext))
                .then(blocklistSubtree())
                .then(pmSubtree())
                .then(tradeSubtree())
                .then(Commands.literal("help").executes(ShopCommand::help))
                .then(accountsSubtree())
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ShopCommand::reload))
                .then(Commands.literal("logs")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> logs(context, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(context -> logs(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("ids")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> ids(context, 20))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(context -> ids(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ShopCommand::remove)))
                .then(Commands.literal("selftest")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> SelfTest.run(context.getSource()))));
    }

    // ==================================================================

    // ==================================================================

    // ==================================================================
    // The text form of each window
    // ==================================================================

    /** Prints the whole command surface, so nobody has to read the source to find it. */
    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        for (String key : new String[] {
                "globalshop.help.header",
                "globalshop.help.player",
                "globalshop.help.market",
                "globalshop.help.mail",
                "globalshop.help.rank",
                "globalshop.help.trades",
                "globalshop.help.pm",
                "globalshop.help.trade",
                "globalshop.help.money",
                "globalshop.help.vip",
                "globalshop.help.op_header",
                "globalshop.help.op_server",
                "globalshop.help.op_block",
                "globalshop.help.op_reload",
                "globalshop.help.op_logs",
        }) {
            source.sendSuccess(() -> Component.translatable(key), false);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mailSubtree() {
        return Commands.literal("mail")
                .executes(ShopCommand::listMail)
                .then(Commands.literal("list").executes(ShopCommand::listMail))
                .then(Commands.literal("claimall").executes(ShopCommand::claimAllMail))
                .then(Commands.literal("claim")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(context -> claimMail(context,
                                        IntegerArgumentType.getInteger(context, "index")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(context -> deleteMail(context,
                                        IntegerArgumentType.getInteger(context, "index")))));
    }

    /**
     * Numbers the mailbox from 1 in the order shown, rather than by the internal id.
     *
     * <p>The id is an implementation detail; making a player retype a long number is how they end up
     * claiming the wrong thing. The listing and the actions therefore agree on the same small
     * numbers.
     */
    private static int listMail(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        List<com.example.globalshop.shop.Mail> mails =
                com.example.globalshop.shop.ShopServices.get(source).mailsOf(player.getUUID());
        if (mails.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("globalshop.mail.empty"), false);
            return 0;
        }
        long now = com.example.globalshop.servershop.ShopClock.nowMillis();
        source.sendSuccess(() -> Component.translatable("globalshop.command.mail_header",
                mails.size()), false);
        for (int i = 0; i < mails.size(); i++) {
            com.example.globalshop.shop.Mail mail = mails.get(i);
            int index = i + 1;
            long daysLeft = Math.max(0L, (mail.expiresAt() - now) / 86_400_000L);
            boolean lapsed = mail.isExpired(now);
            String itemName = mail.items().isEmpty()
                    ? "" : mail.items().get(0).getHoverName().getString();
            source.sendSuccess(() -> Component.translatable(
                    lapsed ? "globalshop.command.mail_line_lapsed" : "globalshop.command.mail_line",
                    index, mail.totalCount(), itemName,
                    Component.translatable(mail.source().translationKey()),
                    daysLeft), false);
        }
        return mails.size();
    }

    /** @return the mail at a 1-based index, or {@code null} if there is none */
    @Nullable
    private static com.example.globalshop.shop.Mail mailAt(ServerPlayer player,
                                                           CommandSourceStack source, int index) {
        List<com.example.globalshop.shop.Mail> mails =
                com.example.globalshop.shop.ShopServices.get(source).mailsOf(player.getUUID());
        return index >= 1 && index <= mails.size() ? mails.get(index - 1) : null;
    }

    private static int claimMail(CommandContext<CommandSourceStack> context, int index)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        com.example.globalshop.shop.Mail mail = mailAt(player, source, index);
        if (mail == null) {
            throw ERROR_INVALID_ID.create();
        }
        ShopResult result = com.example.globalshop.shop.ShopServices.get(source)
                .claimMail(player, mail.id());
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.mail.claimed" : result.translationKey()), false);
        return result.isSuccess() ? 1 : 0;
    }

    private static int deleteMail(CommandContext<CommandSourceStack> context, int index)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        com.example.globalshop.shop.Mail mail = mailAt(player, source, index);
        if (mail == null) {
            throw ERROR_INVALID_ID.create();
        }
        boolean removed = com.example.globalshop.shop.ShopServices.get(source)
                .deleteMail(player.getUUID(), mail.id());
        source.sendSuccess(() -> Component.translatable(removed
                ? "globalshop.mail.deleted" : "globalshop.result.mail_not_found"), false);
        return removed ? 1 : 0;
    }

    private static int claimAllMail(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        int claimed = com.example.globalshop.shop.ShopServices.get(source).deliverMail(player);
        int left = com.example.globalshop.shop.ShopServices.get(source).mailCount(player.getUUID());
        source.sendSuccess(() -> Component.translatable("globalshop.command.mail_claimed_all",
                claimed, left), false);
        return claimed;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rankSubtree() {
        return Commands.literal("rank")
                .executes(context -> rank(context, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> rank(context,
                                IntegerArgumentType.getInteger(context, "page"))));
    }

    private static int rank(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        List<com.example.globalshop.shop.RankingService.Row> rows =
                com.example.globalshop.shop.ShopServices.ranking(source.getServer()).rows();
        int perPage = 10;
        int pageCount = Math.max(1, (rows.size() + perPage - 1) / perPage);
        int use = Math.min(Math.max(1, page), pageCount);
        source.sendSuccess(() -> Component.translatable("globalshop.command.rank_header",
                use, pageCount, rows.size()), false);
        for (int i = (use - 1) * perPage; i < Math.min(rows.size(), use * perPage); i++) {
            com.example.globalshop.shop.RankingService.Row row = rows.get(i);
            source.sendSuccess(() -> Component.translatable("globalshop.command.rank_line",
                    row.position(), row.name(),
                    com.example.globalshop.economy.MoneyFormat.formatWithName(row.balance())), false);
        }
        return rows.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tradesSubtree() {
        return Commands.literal("trades")
                .executes(context -> trades(context, 10))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                        .executes(context -> trades(context,
                                IntegerArgumentType.getInteger(context, "count"))));
    }

    /** The viewer's own player-to-player trades. The same filter the window uses. */
    private static int trades(CommandContext<CommandSourceStack> context, int count)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        List<TradeLogEntry> mine = ShopServices.get(source).tradesOf(player.getUUID(), count);
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("globalshop.command.trades_empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("globalshop.command.trades_header",
                mine.size()), false);
        for (TradeLogEntry entry : mine) {
            source.sendSuccess(entry::toComponent, false);
        }
        return mine.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> blockSubtree(
            CommandBuildContext buildContext) {
        return Commands.literal("block")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("item", net.minecraft.commands.arguments.item.ItemArgument
                                .item(buildContext))
                        .executes(context -> setBlocked(context, true)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unblockSubtree(
            CommandBuildContext buildContext) {
        return Commands.literal("unblock")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("item", net.minecraft.commands.arguments.item.ItemArgument
                                .item(buildContext))
                        .executes(context -> setBlocked(context, false)));
    }

    private static int setBlocked(CommandContext<CommandSourceStack> context, boolean block)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        net.minecraft.world.item.Item item = net.minecraft.commands.arguments.item.ItemArgument
                .getItem(context, "item").getItem();
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            throw ERROR_INVALID_ID.create();
        }
        boolean changed = block
                ? ShopServices.get(source).blockItem(id.toString())
                : ShopServices.get(source).unblockItem(id.toString());
        source.sendSuccess(() -> Component.translatable(block
                        ? (changed ? "globalshop.command.blocked" : "globalshop.command.already_blocked")
                        : (changed ? "globalshop.command.unblocked" : "globalshop.command.not_blocked"),
                id.toString()), true);
        return changed ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> blocklistSubtree() {
        // Operator only, to match the window: the market page sends the list only to an operator
        // who asked for the editor, and a command that handed it to everyone would be a way round
        // that decision rather than a second way to make it.
        return Commands.literal("blocklist")
                .requires(source -> source.hasPermission(2))
                .executes(ShopCommand::listBlocked);
    }

    private static int listBlocked(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> blocked = ShopServices.get(source).blacklist();
        if (blocked.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("globalshop.command.blocklist_empty"),
                    false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("globalshop.command.blocklist_header",
                blocked.size()), false);
        for (String id : blocked) {
            source.sendSuccess(() -> Component.literal(" - " + id), false);
        }
        return blocked.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pmSubtree() {
        return Commands.literal("pm")
                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                        .executes(ShopCommand::readConversation)
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ShopCommand::sendMessage)));
    }

    private static int sendMessage(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        String text = StringArgumentType.getString(context, "text");
        com.example.globalshop.social.SocialService social =
                com.example.globalshop.social.SocialServices.get(player);
        social.clearThrottleForTest();
        ShopResult result = social.send(player, target.getId(), text);
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.pm_sent"
                : result.translationKey(), target.getName()), false);
        return result.isSuccess() ? 1 : 0;
    }

    private static int readConversation(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        com.example.globalshop.social.SocialService social =
                com.example.globalshop.social.SocialServices.get(player);
        List<com.example.globalshop.social.SocialService.Line> lines =
                social.conversation(player.getUUID(), target.getId());
        source.sendSuccess(() -> Component.translatable("globalshop.command.pm_header",
                target.getName(), social.isOnline(target.getId())
                        ? Component.translatable("globalshop.social.online")
                        : Component.translatable("globalshop.social.offline")), false);
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("globalshop.trade.no_messages"), false);
        }
        // Oldest first, because a transcript read top to bottom is how a conversation reads.
        for (int i = lines.size() - 1; i >= 0; i--) {
            com.example.globalshop.social.SocialService.Line line = lines.get(i);
            source.sendSuccess(() -> Component.literal(line.fromName() + ": " + line.text()), false);
        }
        return lines.size();
    }

    private static GameProfile firstProfile(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "targets");
        return profiles.iterator().next();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tradeSubtree() {
        return Commands.literal("trade")
                .executes(ShopCommand::openTrade)
                .then(Commands.literal("open").executes(ShopCommand::openTrade))
                .then(Commands.literal("offer")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("price", StringArgumentType.string())
                                        .executes(ShopCommand::offerTrade))))
                .then(Commands.literal("price")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("amount", StringArgumentType.string())
                                        .executes(ShopCommand::priceTrade))))
                .then(Commands.literal("pay")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(ShopCommand::payTrade)))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(ShopCommand::withdrawTrade)))
                .then(Commands.literal("info")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(ShopCommand::tradeInfo)));
    }

    private static int openTrade(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (windowId, inventory, p) -> new com.example.globalshop.menu.TradeMenu(windowId,
                        inventory),
                Component.translatable("globalshop.trade.title")));
        return 1;
    }

    private static int offerTrade(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        java.util.OptionalLong price =
                com.example.globalshop.economy.MoneyFormat.parse(
                        StringArgumentType.getString(context, "price"));
        if (price.isEmpty() || price.getAsLong() <= 0L) {
            throw ERROR_INVALID_ID.create();
        }
        com.example.globalshop.social.SocialService social =
                com.example.globalshop.social.SocialServices.get(player);
        ShopResult offered = social.offerHeld(player, target.getId(), player.getMainHandItem());
        if (!offered.isSuccess()) {
            source.sendSuccess(() -> Component.translatable(offered.translationKey()), false);
            return 0;
        }
        ShopResult priced = social.publishPrice(player, target.getId(), price.getAsLong());
        source.sendSuccess(() -> Component.translatable("globalshop.command.trade_offered",
                target.getName(),
                com.example.globalshop.economy.MoneyFormat.formatWithName(price.getAsLong())), true);
        return priced.isSuccess() ? 1 : 0;
    }

    private static int priceTrade(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        java.util.OptionalLong price =
                com.example.globalshop.economy.MoneyFormat.parse(
                        StringArgumentType.getString(context, "amount"));
        if (price.isEmpty() || price.getAsLong() <= 0L) {
            throw ERROR_INVALID_ID.create();
        }
        ShopResult result = com.example.globalshop.social.SocialServices.get(player)
                .publishPrice(player, target.getId(), price.getAsLong());
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.trade_priced"
                : result.translationKey(),
                com.example.globalshop.economy.MoneyFormat.formatWithName(price.getAsLong())), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static int payTrade(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        com.example.globalshop.social.SocialService social =
                com.example.globalshop.social.SocialServices.get(player);
        com.example.globalshop.social.TradeOffer offer =
                social.offerBetween(player.getUUID(), target.getId());
        if (offer == null) {
            source.sendSuccess(() -> Component.translatable("globalshop.result.no_trade_open"),
                    false);
            return 0;
        }
        // The price is named back to the player before it is taken: a command that spends money
        // should say how much.
        source.sendSuccess(() -> Component.translatable("globalshop.command.trade_paying",
                com.example.globalshop.economy.MoneyFormat.formatWithName(offer.price())), false);
        ShopResult result = social.payOffer(player, target.getId(), offer.price());
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.trade_paid" : result.translationKey()), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static int withdrawTrade(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        ShopResult result = com.example.globalshop.social.SocialServices.get(player)
                .withdraw(player, target.getId());
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.trade_withdrawn" : result.translationKey()), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static int tradeInfo(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        GameProfile target = firstProfile(context);
        com.example.globalshop.social.TradeOffer offer =
                com.example.globalshop.social.SocialServices.get(player)
                        .offerBetween(player.getUUID(), target.getId());
        if (offer == null) {
            source.sendSuccess(() -> Component.translatable("globalshop.result.no_trade_open"),
                    false);
            return 0;
        }
        String itemName = offer.item().getHoverName().getString() + " x" + offer.quantity();
        source.sendSuccess(() -> Component.translatable("globalshop.command.trade_info",
                offer.sellerName(), offer.buyerName(), itemName,
                offer.priced()
                        ? com.example.globalshop.economy.MoneyFormat.formatWithName(offer.price())
                        : Component.translatable("globalshop.trade.no_price")), false);
        return 1;
    }

    /** Parses a listing id, with one clear error rather than a stack trace. */
    private static UUID listingId(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "id");
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException notAnId) {
            throw ERROR_INVALID_ID.create();
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sellSubtree() {
        return Commands.literal("sell")
                .then(Commands.argument("price", StringArgumentType.string())
                        .then(Commands.argument("stock", IntegerArgumentType.integer(1))
                                .executes(context -> sell(context,
                                        IntegerArgumentType.getInteger(context, "stock"), 1, 64))
                                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("max",
                                                        IntegerArgumentType.integer(1))
                                                .executes(context -> sell(context,
                                                        IntegerArgumentType.getInteger(context,
                                                                "stock"),
                                                        IntegerArgumentType.getInteger(context,
                                                                "min"),
                                                        IntegerArgumentType.getInteger(context,
                                                                "max")))))));
    }

    /** Lists the item in the player's hand: the command form of dropping it into the sell slot. */
    private static int sell(CommandContext<CommandSourceStack> context, int stock, int min, int max)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        java.util.OptionalLong price = com.example.globalshop.economy.MoneyFormat.parse(
                StringArgumentType.getString(context, "price"));
        if (price.isEmpty() || price.getAsLong() <= 0L) {
            throw ERROR_INVALID_ID.create();
        }
        ShopResult result = ShopServices.get(source).createHeld(player, player.getMainHandItem(),
                stock, price.getAsLong(), min, max);
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.sell_done"
                : result.translationKey(), stock,
                com.example.globalshop.economy.MoneyFormat.formatWithName(price.getAsLong())), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buySubtree() {
        return Commands.literal("buy")
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(context -> buy(context, 1))
                        .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                .executes(context -> buy(context,
                                        IntegerArgumentType.getInteger(context, "quantity")))));
    }

    private static int buy(CommandContext<CommandSourceStack> context, int quantity)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        ShopResult result = ShopServices.get(source).purchase(player, listingId(context), quantity);
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.buy_done" : result.translationKey(), quantity), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mineSubtree() {
        return Commands.literal("mine").executes(ShopCommand::listMine);
    }

    private static int listMine(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        List<Listing> mine = ShopServices.get(source).listingsOf(player.getUUID());
        if (mine.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("globalshop.command.mine_empty"), false);
            return 0;
        }
        long now = com.example.globalshop.servershop.ShopClock.nowMillis();
        source.sendSuccess(() -> Component.translatable("globalshop.command.mine_header",
                mine.size(), ShopServices.get(source).listingLimitFor(player.getUUID())), false);
        for (Listing listing : mine) {
            // The id is printed because every other command takes it: without it this listing is
            // readable but not actionable.
            source.sendSuccess(() -> Component.translatable("globalshop.command.mine_line",
                    listing.id().toString(),
                    listing.stock(),
                    listing.template().getHoverName().getString(),
                    com.example.globalshop.economy.MoneyFormat.formatWithName(listing.unitPrice()),
                    listing.isExpired(now)
                            ? Component.translatable("globalshop.mail.lapsed")
                            : Component.literal("-")), false);
        }
        return mine.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unlistSubtree() {
        return Commands.literal("unlist")
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ShopCommand::unlist));
    }

    private static int unlist(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        ShopResult result = ShopServices.get(source).unlist(player, listingId(context));
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.unlist_done" : result.translationKey()), true);
        return result.isSuccess() ? 1 : 0;
    }

    /**
     * Takes every one of the player's lots down at once.
     *
     * <p>Documented in the guide before it existed — the guide's command table listed
     * {@code /globalshop unlistall} for two versions while only the window's button could do it.
     * {@code tools/check_docs_against_code.py} is what found it.
     */
    private static int unlistAll(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        int taken = ShopServices.get(source).unlistAll(player);
        source.sendSuccess(() -> Component.translatable("globalshop.command.unlistall_done", taken),
                true);
        return taken;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> restockSubtree() {        return Commands.literal("restock")
                .then(Commands.argument("id", StringArgumentType.string())
                        .executes(ShopCommand::restock));
    }

    /** Tops a listing up with everything of that item the player is carrying. */
    private static int restock(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        ShopResult result = ShopServices.get(source).restock(player, listingId(context),
                List.of(new com.example.globalshop.shop.ContainerPool(player.getInventory())));
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.restock_done" : result.translationKey()), true);
        return result.isSuccess() ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> modifySubtree() {
        return Commands.literal("modify")
                .then(Commands.argument("id", StringArgumentType.string())
                        .then(Commands.argument("stock", IntegerArgumentType.integer(1))
                                .executes(context -> modify(context, -1L,
                                        IntegerArgumentType.getInteger(context, "stock"), 1, 64))
                                .then(Commands.argument("price", StringArgumentType.string())
                                        .then(Commands.argument("min",
                                                        IntegerArgumentType.integer(1))
                                                .then(Commands.argument("max",
                                                                IntegerArgumentType.integer(1))
                                                        .executes(context -> modify(context,
                                                                com.example.globalshop.economy
                                                                        .MoneyFormat.parse(
                                                                                StringArgumentType
                                                                                        .getString(context,
                                                                                                "price"))
                                                                        .orElse(-1L),
                                                                IntegerArgumentType.getInteger(
                                                                        context, "stock"),
                                                                IntegerArgumentType.getInteger(
                                                                        context, "min"),
                                                                IntegerArgumentType.getInteger(
                                                                        context, "max"))))))));
    }

    private static int modify(CommandContext<CommandSourceStack> context, long price, int stock,
                              int min, int max) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        UUID id = listingId(context);
        Listing listing = ShopServices.get(source).find(id);
        if (listing == null) {
            source.sendSuccess(() -> Component.translatable("globalshop.result.listing_not_found"),
                    false);
            return 0;
        }
        long usePrice = price > 0L ? price : listing.unitPrice();
        ShopResult result = ShopServices.get(source).modify(player, id, usePrice, stock, min, max,
                List.of(new com.example.globalshop.shop.ContainerPool(player.getInventory())));
        source.sendSuccess(() -> Component.translatable(result.isSuccess()
                ? "globalshop.command.modify_done" : result.translationKey(),
                com.example.globalshop.economy.MoneyFormat.formatWithName(usePrice), stock), true);
        return result.isSuccess() ? 1 : 0;
    }

    /**
     * {@code /globalshop vip} — buy the rank, and an operator's grant/revoke beside it.
     *
     * <p>The purchase is deliberately a command rather than a button in the market window: it
     * spends a large amount of the player's own money, and a typed confirmation is a better fit
     * for that than a click next to the buy button.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> vipSubtree() {
        return Commands.literal("vip")
                .executes(ShopCommand::buyVip)
                .then(Commands.literal("buy").executes(ShopCommand::buyVip))
                .then(Commands.literal("info").executes(ShopCommand::vipInfo))
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(context -> setVip(context, true))))
                .then(Commands.literal("revoke")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(context -> setVip(context, false))))
                .then(Commands.literal("info")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ShopService shop = ShopServices.get(context.getSource());
                            boolean vip = shop.isVip(player.getUUID());
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "globalshop.vip.status",
                                    Component.translatable(vip ? "globalshop.vip.yes" : "globalshop.vip.no"),
                                    shop.listingLimitFor(player.getUUID()),
                                    MoneyFormat.formatWithName(Config.vipPrice())), false);
                            return 1;
                        }));
    }

    private static int buyVip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ShopService shop = ShopServices.get(context.getSource());
        ShopResult result = shop.purchaseVip(player);
        if (!result.isSuccess()) {
            context.getSource().sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }
        // The period is named, not just the rank: a purchase that silently expires in two weeks
        // would be a nasty surprise, and the player has no other way to learn the term.
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.vip.bought",
                vipDaysLeft(shop, player.getUUID()), Config.vipDays(),
                shop.listingLimitFor(player.getUUID())), true);
        return 1;
    }

    /** Whole days of membership left, rounded up so the last day does not read as zero. */
    private static long vipDaysLeft(ShopService shop, UUID playerId) {
        long left = shop.vipUntil(playerId)
                - com.example.globalshop.servershop.ShopClock.nowMillis();
        return left <= 0L ? 0L : (left + 86_399_999L) / 86_400_000L;
    }

    /** {@code /globalshop vip info} — what the player is, for how much longer, and what it costs. */
    private static int vipInfo(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CommandSourceStack source = context.getSource();
        ShopService shop = ShopServices.get(source);
        boolean vip = shop.isVip(player.getUUID());
        source.sendSuccess(() -> Component.translatable(vip
                ? "globalshop.vip.info_member" : "globalshop.vip.info_plain",
                vipDaysLeft(shop, player.getUUID())), false);
        source.sendSuccess(() -> Component.translatable("globalshop.vip.info_price",
                com.example.globalshop.economy.MoneyFormat.formatWithName(Config.vipPrice()),
                Config.vipDays(), Config.listingLimitFor(vip)), false);
        if (vip) {
            source.sendSuccess(() -> Component.translatable(shop.vipAutoRenew(player.getUUID())
                    ? "globalshop.vip.auto_label_on" : "globalshop.vip.auto_label_off"), false);
        }
        return vip ? 1 : 0;
    }

    private static int setVip(CommandContext<CommandSourceStack> context, boolean vip)
            throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        ShopService shop = ShopServices.get(context.getSource());
        int changed = 0;
        for (GameProfile target : targets) {
            shop.setVip(target.getId(), vip);
            changed++;
            context.getSource().sendSuccess(() -> Component.translatable(
                    vip ? "globalshop.vip.granted" : "globalshop.vip.revoked",
                    target.getName(), Config.listingLimitFor(vip)), true);
        }
        return changed;
    }

    /**
     * {@code /globalshop accounts [name]} — every account, its full id and its balance.
     *
     * <p>This exists because one person can have two accounts and nothing else in the mod can show
     * it. An offline-mode id is derived from the name, so any difference in the name — a capital
     * letter, a trailing space, a rename and a rename back — produces a second id, and both then
     * record the same display name. From in game that is an unexplainable duplicate; here it is two
     * rows with two different ids.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> accountsSubtree() {
        return Commands.literal("accounts")
                .requires(source -> source.hasPermission(2))
                .executes(context -> accounts(context, ""))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> accounts(context,
                                StringArgumentType.getString(context, "name"))));
    }

    private static int accounts(CommandContext<CommandSourceStack> context, String filter) {
        com.example.globalshop.economy.EconomyService economy =
                com.example.globalshop.economy.EconomyServices.get(context.getSource().getServer());
        String needle = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
        int shown = 0;
        for (java.util.Map.Entry<java.util.UUID, Long> account : economy.allBalances().entrySet()) {
            String name = economy.getLastKnownName(account.getKey()).orElse("");
            if (!needle.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                continue;
            }
            shown++;
            String line = (name.isBlank() ? "?" : name) + "  |  id " + account.getKey()
                    + "  |  " + MoneyFormat.formatWithName(account.getValue());
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        if (shown == 0) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("globalshop.command.accounts_none"), false);
        }
        return shown;
    }

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GlobalShop.openShop(player);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        // Re-read the file first. Forge loads a config once at startup and never watches it, so
        // without this step an operator who corrected a setting and ran this command would see
        // the old value and reasonably conclude the setting did nothing.
        boolean fromDisk = Config.reloadFromFile();
        // Then apply it: currency format, limits and log retention are all derived from the
        // config, and the running server has to be told about the new values.
        Config.onLoad();
        CommandSourceStack source = context.getSource();
        ShopService.MarketSummary summary = ShopServices.get(source).reload(Config.tradeLogRetention());
        source.sendSuccess(() -> Component.translatable(fromDisk
                        ? "globalshop.command.reloaded"
                        : "globalshop.command.reloaded_nofile",
                summary.listings(), summary.logEntries()), true);
        return 1;
    }

    private static int logs(CommandContext<CommandSourceStack> context, int count) {
        ShopService shop = ShopServices.get(context.getSource());
        List<TradeLogEntry> entries = shop.recentLog(count);
        if (entries.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("globalshop.logs.empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.logs.header",
                entries.size(), shop.logSize()), false);
        for (TradeLogEntry entry : entries) {
            context.getSource().sendSuccess(entry::toComponent, false);
        }
        return entries.size();
    }

    /**
     * Lists listing ids, newest first.
     *
     * <p>Not in the original specification, but {@code /globalshop remove <id>} is
     * unusable without a way to discover an id, so an operator can find one here or in
     * the trade log.
     */
    private static int ids(CommandContext<CommandSourceStack> context, int count) {
        ShopService shop = ShopServices.get(context.getSource());
        List<Listing> all = shop.search("", ShopSort.TIME_NEWEST, null, true);
        if (all.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("globalshop.ids.empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.ids.header", all.size()), false);
        int shown = Math.min(count, all.size());
        for (int i = 0; i < shown; i++) {
            Listing listing = all.get(i);
            context.getSource().sendSuccess(() -> Component.translatable("globalshop.ids.line",
                    listing.id().toString(),
                    listing.template().getHoverName(),
                    listing.sellerName(),
                    listing.stock()), false);
        }
        return shown;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "id");
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw ERROR_INVALID_ID.create();
        }

        ShopService shop = ShopServices.get(context.getSource());
        Listing listing = shop.find(id);
        if (listing == null) {
            context.getSource().sendFailure(
                    Component.translatable(ShopResult.LISTING_NOT_FOUND.translationKey()));
            return 0;
        }

        // Captured before removal so the message can name the seller.
        String sellerName = listing.sellerName();
        String actorName = context.getSource().getTextName();
        ShopResult result = shop.forceRemove(id, actorName);
        if (!result.isSuccess()) {
            context.getSource().sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }
        // Unsold escrow was returned to the seller, who may be offline.
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.command.removed",
                raw, sellerName), true);
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int delivered = ShopServices.get(source).deliverMail(player);
        if (delivered == 0) {
            source.sendSuccess(() -> Component.translatable("globalshop.command.no_mail"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("globalshop.command.claimed", delivered), false);
        }
        return delivered;
    }
}
