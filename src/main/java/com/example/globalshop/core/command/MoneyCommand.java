package com.example.globalshop.core.command;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.api.economy.TransactionResult;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The economy commands.
 *
 * <h2>Permission levels</h2>
 * <ul>
 *   <li>{@code /money} -- level 0, anyone may check their own balance.</li>
 *   <li>{@code /pay <player> <amount>} -- level 0, anyone may pay someone else.</li>
 *   <li>{@code /money <player>} -- level 2 (operator).</li>
 *   <li>{@code /money give|take|set ...} -- level 2 (operator).</li>
 * </ul>
 *
 * <p>Every one of these runs on the server. There is no client-side command path
 * and no packet that can move money, so a modified client cannot call them.
 *
 * <p>The whole tree is also exposed under {@code /globalshop money ...} so a clash
 * with another mod's {@code /money} command cannot lock players out.
 *
 * <p>Targets are resolved with {@link GameProfileArgument}, which consults the
 * server's profile cache, so an operator can act on a player who is offline. Their
 * balance is keyed by UUID and is unaffected by not being connected.
 */
public final class MoneyCommand {

    private static final SimpleCommandExceptionType ERROR_SINGLE_TARGET =
            new SimpleCommandExceptionType(Component.translatable("globalshop.command.single_target"));

    private static final SimpleCommandExceptionType ERROR_SELF_PAY =
            new SimpleCommandExceptionType(Component.translatable("globalshop.command.self_pay"));

    private MoneyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(buildMoney("money"));
        dispatcher.register(buildPay("pay"));
    }

    /** The {@code /money} tree. Reused verbatim as {@code /globalshop money}. */
    public static LiteralArgumentBuilder<CommandSourceStack> buildMoney(String name) {
        return Commands.literal(name)
                .executes(MoneyCommand::showSelf)
                .then(adminBranch("give", MoneyCommand::give))
                .then(adminBranch("take", MoneyCommand::take))
                .then(adminBranch("set", MoneyCommand::set))
                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                        .requires(source -> source.hasPermission(2))
                        .executes(MoneyCommand::showOther));
    }

    /** The {@code /pay} tree. Reused verbatim as {@code /globalshop pay}. */
    public static LiteralArgumentBuilder<CommandSourceStack> buildPay(String name) {
        return Commands.literal(name)
                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", MoneyArgumentType.money())
                                .executes(MoneyCommand::pay)));
    }

    /** {@code <name> <targets> <amount>}, operator only. */
    private static LiteralArgumentBuilder<CommandSourceStack> adminBranch(
            String name, com.mojang.brigadier.Command<CommandSourceStack> action) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", MoneyArgumentType.money())
                                .executes(action)));
    }

    // ==================================================================
    // /money
    // ==================================================================

    private static int showSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        EconomyService economy = EconomyServices.get(context.getSource());
        // First touch creates the account, so a player who has never been seen still
        // gets the configured starting balance exactly once.
        economy.ensureAccount(player.getUUID(), player.getGameProfile().getName(), Config.initialBalance());
        long balance = economy.getBalance(player.getUUID());
        context.getSource().sendSuccess(
                () -> Component.translatable("globalshop.money.self", MoneyFormat.formatWithName(balance)), false);
        return 1;
    }

    private static int showOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        EconomyService economy = EconomyServices.get(context.getSource());
        int shown = 0;
        for (GameProfile target : targets) {
            long balance = economy.getBalance(target.getId());
            context.getSource().sendSuccess(
                    () -> Component.translatable("globalshop.money.other",
                            target.getName(), MoneyFormat.formatWithName(balance)), false);
            shown++;
        }
        return shown;
    }

    // ==================================================================
    // /money give | take | set
    // ==================================================================

    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        long amount = MoneyArgumentType.getMoney(context, "amount");
        EconomyService economy = EconomyServices.get(context.getSource());
        int applied = 0;
        for (GameProfile target : targets) {
            TransactionResult result =
                    economy.ensureAccount(target.getId(), target.getName(), Config.initialBalance());
            if (result.isSuccess()) {
                result = economy.addBalance(target.getId(), amount);
            }
            if (result.isSuccess()) {
                applied++;
                context.getSource().sendSuccess(
                        () -> Component.translatable("globalshop.money.gave",
                                target.getName(), MoneyFormat.formatWithName(amount)), true);
            } else {
                context.getSource().sendFailure(Component.translatable(result.translationKey()));
            }
        }
        return applied;
    }

    private static int take(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        long amount = MoneyArgumentType.getMoney(context, "amount");
        EconomyService economy = EconomyServices.get(context.getSource());
        int applied = 0;
        for (GameProfile target : targets) {
            economy.ensureAccount(target.getId(), target.getName(), Config.initialBalance());
            // removeBalance refuses to go below zero, so a balance can never be driven
            // negative by an operator command.
            TransactionResult result = economy.removeBalance(target.getId(), amount);
            if (result.isSuccess()) {
                applied++;
                context.getSource().sendSuccess(
                        () -> Component.translatable("globalshop.money.took",
                                MoneyFormat.formatWithName(amount), target.getName()), true);
            } else {
                context.getSource().sendFailure(Component.translatable(result.translationKey()));
            }
        }
        return applied;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        long amount = MoneyArgumentType.getMoney(context, "amount");
        EconomyService economy = EconomyServices.get(context.getSource());
        int applied = 0;
        for (GameProfile target : targets) {
            TransactionResult result =
                    economy.ensureAccount(target.getId(), target.getName(), Config.initialBalance());
            if (result.isSuccess()) {
                result = economy.setBalance(target.getId(), amount);
            }
            if (result.isSuccess()) {
                applied++;
                context.getSource().sendSuccess(
                        () -> Component.translatable("globalshop.money.set",
                                target.getName(), MoneyFormat.formatWithName(amount)), true);
            } else {
                context.getSource().sendFailure(Component.translatable(result.translationKey()));
            }
        }
        return applied;
    }

    // ==================================================================
    // /pay
    // ==================================================================

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        long amount = MoneyArgumentType.getMoney(context, "amount");

        if (targets.size() != 1) {
            // A selector that matches several players is ambiguous for a payment.
            throw ERROR_SINGLE_TARGET.create();
        }
        GameProfile target = targets.iterator().next();
        if (target.getId().equals(sender.getUUID())) {
            throw ERROR_SELF_PAY.create();
        }

        EconomyService economy = EconomyServices.get(context.getSource());
        economy.ensureAccount(sender.getUUID(), sender.getGameProfile().getName(), Config.initialBalance());

        // One atomic debit+credit. Every failure mode leaves both balances untouched.
        TransactionResult result = economy.transfer(sender.getUUID(), target.getId(), amount);
        if (!result.isSuccess()) {
            context.getSource().sendFailure(Component.translatable(result.translationKey()));
            return 0;
        }

        long balance = economy.getBalance(sender.getUUID());
        context.getSource().sendSuccess(
                () -> Component.translatable("globalshop.money.paid",
                        MoneyFormat.formatWithName(amount), target.getName(),
                        MoneyFormat.formatWithName(balance)), false);

        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(target.getId());
        if (online != null) {
            online.sendSystemMessage(Component.translatable("globalshop.money.received",
                    MoneyFormat.formatWithName(amount), sender.getGameProfile().getName()));
        }
        return 1;
    }

    /** Convenience for other code that wants to reuse the amount argument type. */
    public static ArgumentType<Long> amountArgument() {
        return MoneyArgumentType.money();
    }
}
