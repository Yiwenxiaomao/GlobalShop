package com.example.globalshop.network.packet;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.bank.BankResult;
import com.example.globalshop.bank.BankService;
import com.example.globalshop.bank.BankServices;
import com.example.globalshop.economy.MoneyFormat;
import java.util.OptionalLong;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: everything the bank window can do.
 *
 * <p>One packet for five actions, the way the other windows do it. The amount travels as <em>text</em>
 * and is parsed with the server's own currency settings, so a client cannot send a number the server
 * would read differently — and it is never trusted: every route re-checks it against the wallet, the
 * deposit or the loan limit before anything moves.
 */
public class BankActionPacket {

    public static final int OPEN = 0;
    public static final int DEPOSIT = 1;
    public static final int WITHDRAW = 2;
    public static final int BORROW = 3;
    public static final int REPAY = 4;
    /** The three "everything" buttons. The server works the amount out; the client sends none. */
    public static final int DEPOSIT_ALL = 5;
    public static final int WITHDRAW_ALL = 6;
    public static final int REPAY_ALL = 7;

    public static final int MAX_AMOUNT_TEXT = 24;

    private final int action;
    private final String amount;

    public BankActionPacket(int action, String amount) {
        this.action = action;
        this.amount = amount == null ? "" : amount;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(amount, MAX_AMOUNT_TEXT);
    }

    public static BankActionPacket decode(FriendlyByteBuf buf) {
        return new BankActionPacket(buf.readVarInt(), buf.readUtf(MAX_AMOUNT_TEXT));
    }

    public static void handle(BankActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            BankService bank = BankServices.get(sender);
            if (message.action >= DEPOSIT_ALL) {
                long depositBefore = bank.depositOf(sender.getUUID());
                long loanBefore = bank.loanOf(sender.getUUID());
                BankResult result = switch (message.action) {
                    case DEPOSIT_ALL -> bank.depositAll(sender);
                    case WITHDRAW_ALL -> bank.withdrawAll(sender);
                    default -> bank.repayAll(sender);
                };
                // The same report as the typed route. It was missed here the first time round,
                // which is why the "all" buttons went on saying "Done." while the others said how
                // much had moved.
                sender.sendSystemMessage(result.isSuccess()
                        ? report(message.action, bank, sender, depositBefore, loanBefore)
                        : Component.translatable(result.translationKey()));
            } else if (message.action != OPEN) {
                long depositBefore = bank.depositOf(sender.getUUID());
                long loanBefore = bank.loanOf(sender.getUUID());
                OptionalLong parsed = MoneyFormat.parse(message.amount);
                boolean parsedOk = parsed.isPresent() && parsed.getAsLong() > 0L;
                BankResult result = parsedOk ? run(bank, sender, message.action, parsed.getAsLong())
                        : BankResult.INVALID_AMOUNT;
                if (result.isSuccess()) {
                    // Reported from what actually changed, not from what was asked for. The "all"
                    // buttons work their amount out on the server, so the request does not contain
                    // the figure the player needs to be told.
                    sender.sendSystemMessage(report(message.action, bank, sender,
                            depositBefore, loanBefore));
                } else {
                    sender.sendSystemMessage(Component.translatable(result.translationKey()));
                }
                // The wallet moved unless nothing did, and the window shows it either way. The
                // economy also announces the new balance on its own.
            }
            ModNetwork.sendToPlayer(sender, BankDataPacket.build(bank, sender));
        });
        context.setPacketHandled(true);
    }

    /**
     * What to tell the player, worked out from the before and after figures.
     *
     * <p>Deriving it rather than passing it around means the "all" routes need no special case: they
     * know the amount no better than the caller does, and the change in the account is the amount.
     */
    private static Component report(int action, BankService bank, ServerPlayer sender,
                                    long depositBefore, long loanBefore) {
        long depositAfter = bank.depositOf(sender.getUUID());
        long loanAfter = bank.loanOf(sender.getUUID());
        return switch (action) {
            case DEPOSIT, DEPOSIT_ALL -> Component.translatable("globalshop.bank.done.deposit",
                    MoneyFormat.formatWithName(depositAfter - depositBefore));
            case WITHDRAW, WITHDRAW_ALL -> Component.translatable("globalshop.bank.done.withdraw",
                    MoneyFormat.formatWithName(depositBefore - depositAfter));
            case BORROW -> Component.translatable("globalshop.bank.done.borrow",
                    MoneyFormat.formatWithName(loanAfter - loanBefore));
            default -> Component.translatable("globalshop.bank.done.repay",
                    MoneyFormat.formatWithName(loanBefore - loanAfter),
                    MoneyFormat.formatWithName(loanAfter));
        };
    }

    private static BankResult run(BankService bank, ServerPlayer sender, int action, long amount) {
        return switch (action) {
            case DEPOSIT -> bank.deposit(sender, amount);
            case WITHDRAW -> bank.withdraw(sender, amount);
            case BORROW -> bank.borrow(sender, amount);
            case REPAY -> bank.repay(sender, amount);
            default -> BankResult.INVALID_AMOUNT;
        };
    }
}
