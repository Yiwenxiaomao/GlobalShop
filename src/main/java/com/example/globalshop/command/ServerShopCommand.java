package com.example.globalshop.command;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.servershop.ServerShopEntry;
import com.example.globalshop.servershop.ServerShopResult;
import com.example.globalshop.servershop.ServerShopSavedData;
import com.example.globalshop.servershop.ServerShopService;
import com.example.globalshop.servershop.ServerShopServices;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code /globalshop server} subtree.
 *
 * <h2>Permission levels</h2>
 * <ul>
 *   <li>{@code /globalshop server} -- level 0, opens the shop.</li>
 *   <li>{@code add|remove|list|reload|resetDaily|logs} -- level 2 (operator).</li>
 * </ul>
 *
 * <p>Kept in its own class rather than added to {@link ShopCommand} because the two shops are
 * separate features: everything that can change the server shop's catalogue or anyone's quota
 * lives here, which makes "what can an operator do to the official shop" answerable by reading
 * one file.
 *
 * <p>The catalogue is the thing that mints and destroys items, so nothing here is reachable by an
 * ordinary player. The self test walks the built command tree and asserts that (see
 * {@code SelfTest.permissionTests}).
 */
public final class ServerShopCommand {

    private ServerShopCommand() {
    }

    /** The {@code server} literal, to be attached under {@code /globalshop}. */
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext buildContext) {
        return Commands.literal("server")
                .executes(ServerShopCommand::open)
                .then(Commands.literal("open").executes(ServerShopCommand::open))
                .then(add(buildContext))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(ServerShopCommand::remove)))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> list(context, 20))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(context -> list(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("logs")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> logs(context, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(context -> logs(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ServerShopCommand::reload))
                .then(Commands.literal("resetDaily")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(ServerShopCommand::resetDaily)));
    }

    /**
     * {@code add <item> <sell> <buyback> <bundle> [limit] [recycle]}.
     *
     * <p>{@code limit} omitted means unlimited. {@code recycle} omitted means "accept this item
     * back if a buy-back price was given", which is the convenient reading; spelling it out is
     * for the case where an operator wants a buy-back price on the books but the line closed to
     * trade.
     *
     * <p>The item is an {@link ItemArgument}, so NBT can be included and an operator can stock
     * "a diamond sword with Sharpness V" as its own product, separate from a plain one.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> add(CommandBuildContext buildContext) {
        return Commands.literal("add")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.argument("sell", MoneyArgumentType.money())
                                .then(Commands.argument("buyback", MoneyArgumentType.money())
                                        .then(Commands.argument("bundle", IntegerArgumentType.integer(1, 4096))
                                                .executes(context -> add(context, 0,
                                                        MoneyArgumentType.getMoney(context, "buyback") > 0L))
                                                .then(Commands.argument("limit",
                                                                IntegerArgumentType.integer(0, 1_000_000))
                                                        .executes(context -> add(context,
                                                                IntegerArgumentType.getInteger(context, "limit"),
                                                                MoneyArgumentType.getMoney(context, "buyback") > 0L))
                                                        .then(Commands.argument("recycle", BoolArgumentType.bool())
                                                                .executes(context -> add(context,
                                                                        IntegerArgumentType.getInteger(context, "limit"),
                                                                        BoolArgumentType.getBool(context, "recycle")))))))));
    }

    // ==================================================================

    private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        GlobalShop.openServerShop(player);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context, int limit, boolean allowBuyback)
            throws CommandSyntaxException {
        ItemInput input = ItemArgument.getItem(context, "item");
        ItemStack template = input.createItemStack(1, false);
        long sell = MoneyArgumentType.getMoney(context, "sell");
        long buyback = MoneyArgumentType.getMoney(context, "buyback");
        int bundle = IntegerArgumentType.getInteger(context, "bundle");

        ServerShopService shop = ServerShopServices.get(context.getSource());
        ServerShopResult result = shop.add(template, sell, buyback, bundle, allowBuyback, limit);
        if (!result.isSuccess()) {
            context.getSource().sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.servershop.added",
                template.getHoverName(),
                com.example.globalshop.economy.MoneyFormat.formatWithName(sell),
                allowBuyback ? com.example.globalshop.economy.MoneyFormat.formatWithName(buyback)
                        : Component.translatable("globalshop.servershop.no_buyback"),
                bundle, limit <= 0 ? Component.translatable("globalshop.servershop.unlimited")
                        : Component.literal(Integer.toString(limit))), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemStack template = ItemArgument.getItem(context, "item").createItemStack(1, false);
        ServerShopResult result = ServerShopServices.get(context.getSource()).remove(template);
        if (!result.isSuccess()) {
            context.getSource().sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("globalshop.servershop.removed", template.getHoverName()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context, int count) {
        ServerShopService shop = ServerShopServices.get(context.getSource());
        List<ServerShopEntry> entries = shop.entries();
        if (entries.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("globalshop.servershop.list_empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("globalshop.servershop.list_header", entries.size()), false);
        int shown = Math.min(count, entries.size());
        for (int i = 0; i < shown; i++) {
            ServerShopEntry entry = entries.get(i);
            context.getSource().sendSuccess(() -> Component.translatable("globalshop.servershop.list_line",
                    entry.template().getHoverName(),
                    com.example.globalshop.economy.MoneyFormat.format(entry.sellPrice()),
                    entry.allowBuyback()
                            ? com.example.globalshop.economy.MoneyFormat.format(entry.buybackPrice())
                            : "-",
                    entry.bundleSize(),
                    entry.purchaseLimit() <= 0 ? "inf" : Integer.toString(entry.purchaseLimit())), false);
        }
        return shown;
    }

    private static int logs(CommandContext<CommandSourceStack> context, int count) {
        ServerShopService shop = ServerShopServices.get(context.getSource());
        List<ServerShopSavedData.LogLine> lines = shop.recentLog(count);
        if (lines.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("globalshop.servershop.logs_empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.servershop.logs_header",
                lines.size(), shop.logSize()), false);
        for (ServerShopSavedData.LogLine line : lines) {
            context.getSource().sendSuccess(() -> Component.translatable("globalshop.servershop.log_line",
                    Component.translatable(line.action().translationKey()),
                    line.playerName(),
                    line.item().getHoverName(),
                    line.items(),
                    com.example.globalshop.economy.MoneyFormat.formatWithName(line.money())), false);
        }
        return lines.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        ServerShopService shop = ServerShopServices.get(context.getSource());
        shop.reload();
        context.getSource().sendSuccess(() -> Component.translatable("globalshop.servershop.reloaded",
                shop.size(), shop.dailyLimit()), true);
        return 1;
    }

    /**
     * Clears one player's recycling tally for today.
     *
     * <p>A debugging tool: the quota is meant to reset itself from the real-world date, so a
     * correct server never needs this.
     */
    private static int resetDaily(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        ServerShopService shop = ServerShopServices.get(context.getSource());
        int reset = 0;
        for (GameProfile target : targets) {
            int before = shop.recycledToday(target.getId());
            shop.resetDaily(target.getId());
            reset++;
            context.getSource().sendSuccess(() -> Component.translatable("globalshop.servershop.reset_daily",
                    target.getName(), before, shop.dailyLimit()), true);
        }
        return reset;
    }
}
