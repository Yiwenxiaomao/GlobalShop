package com.example.globalshop.network.packet;

import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.menu.TradeMenu;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.shop.ItemPool;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.social.SocialService;
import com.example.globalshop.social.SocialServices;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: one of the four things you can do to an offer in a conversation.
 *
 * <p>One packet with an action rather than four packets, because the four share exactly the same
 * arguments — the other player, and possibly an amount — and splitting them would mean four copies
 * of the same validation and refresh logic, which is four places for it to drift.
 *
 * <p>Amounts travel as <em>text</em>, parsed with the server's own {@code MoneyFormat}. A client's
 * config need not match the server's, so a number the client worked out could differ from the one
 * the player read.
 */
public class TradeOfferActionPacket {

    /** Put whatever is in the offer slot up for sale to the other player. */
    public static final int OFFER = 0;
    /** Publish a price on the offer; either side may. */
    public static final int PUBLISH = 1;
    /** The seller takes the offer back. */
    public static final int WITHDRAW = 2;
    /** The buyer pays the published price. */
    public static final int PAY = 3;

    public static final int MAX_AMOUNT_TEXT = 24;

    private final UUID partnerId;
    private final int action;
    private final String amountText;

    public TradeOfferActionPacket(UUID partnerId, int action, String amountText) {
        this.partnerId = partnerId;
        this.action = action;
        this.amountText = amountText == null ? "" : amountText;
    }

    public UUID partnerId() {
        return partnerId;
    }

    public int action() {
        return action;
    }

    public String amountText() {
        return amountText;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(partnerId);
        buf.writeVarInt(action);
        buf.writeUtf(amountText, MAX_AMOUNT_TEXT);
    }

    public static TradeOfferActionPacket decode(FriendlyByteBuf buf) {
        return new TradeOfferActionPacket(buf.readUUID(), buf.readVarInt(),
                buf.readUtf(MAX_AMOUNT_TEXT));
    }

    public static void handle(TradeOfferActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            SocialService social = SocialServices.get(sender);
            // The offer slot lives in the window's menu, so the item being offered is read from
            // there rather than from anything the client said. Requiring the window itself is the
            // point: without it the ternary below was the only gate, and WITHDRAW, PUBLISH and PAY
            // would reach the service from a player who is not looking at the trade window at all —
            // letting a modified client change the price on, or pull back, an offer its owner
            // believed was being held for a buyer. The /globalshop trade commands take the same
            // service path deliberately: those are an explicit act by the same player, not a packet
            // claiming to be a window.
            if (!(sender.containerMenu instanceof TradeMenu menu)) {
                sender.sendSystemMessage(Component.translatable("globalshop.result.no_trade_open"));
                return;
            }
            List<ItemPool> pools = menu.offerPools();

            ShopResult result = switch (message.action) {
                case OFFER -> social.offerTo(sender, message.partnerId, pools);
                case WITHDRAW -> social.withdraw(sender, message.partnerId);
                case PUBLISH -> withParsedAmount(social, sender, message, false);
                case PAY -> withParsedAmount(social, sender, message, true);
                default -> ShopResult.INTERNAL_ERROR;
            };

            if (!result.isSuccess()) {
                sender.sendSystemMessage(Component.translatable(result.translationKey()));
                // The window is refreshed on failure only; on success the service has already told
                // both sides, which is the only way the other player's screen can update.
                ModNetwork.sendToPlayer(sender,
                        ConversationDataPacket.build(social, sender, message.partnerId));
            }
        });
        context.setPacketHandled(true);
    }

    /** Parses the amount with the server's settings and either publishes it or pays it. */
    private static ShopResult withParsedAmount(SocialService social, ServerPlayer sender,
                                               TradeOfferActionPacket message, boolean pay) {
        java.util.OptionalLong parsed = MoneyFormat.parse(message.amountText);
        if (parsed.isEmpty() || parsed.getAsLong() <= 0L) {
            return ShopResult.INVALID_PRICE;
        }
        return pay
                ? social.payOffer(sender, message.partnerId, parsed.getAsLong())
                : social.publishPrice(sender, message.partnerId, parsed.getAsLong());
    }
}
