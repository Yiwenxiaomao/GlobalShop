package com.example.globalshop.core.command;

import com.example.globalshop.api.economy.MoneyFormat;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * A Brigadier argument for money amounts.
 *
 * <p>Accepts the same notation the GUI does -- {@code 10}, {@code 12.34},
 * {@code .5}, {@code 1,234.50} -- and converts straight to the smallest currency
 * unit via {@link MoneyFormat}, which uses exact decimal arithmetic. The parsed
 * value is a {@code long} end to end, so a command can never introduce the rounding
 * error that a {@code double} argument would.
 */
public class MoneyArgumentType implements ArgumentType<Long> {

    private static final MoneyArgumentType INSTANCE = new MoneyArgumentType();

    private static final Collection<String> EXAMPLES = List.of("1", "10", "100", "12.50");

    private static final SimpleCommandExceptionType ERROR_INVALID_AMOUNT =
            new SimpleCommandExceptionType(Component.translatable("globalshop.command.invalid_amount"));

    private MoneyArgumentType() {
    }

    public static MoneyArgumentType money() {
        return INSTANCE;
    }

    /** Reads the amount in minor units. */
    public static long getMoney(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, Long.class);
    }

    @Override
    public Long parse(StringReader reader) throws CommandSyntaxException {
        String text = reader.readUnquotedString();
        OptionalLong parsed = MoneyFormat.parse(text);
        if (parsed.isEmpty()) {
            // Covers junk, negative values, and more precision than the currency has.
            throw ERROR_INVALID_AMOUNT.createWithContext(reader);
        }
        return parsed.getAsLong();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        for (String example : EXAMPLES) {
            if (example.startsWith(remaining)) {
                builder.suggest(example);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
