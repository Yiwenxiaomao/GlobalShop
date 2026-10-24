package com.example.globalshop.network.packet;

import com.example.globalshop.auction.AuctionResult;
import com.example.globalshop.auction.AuctionService;
import com.example.globalshop.auction.AuctionServices;
import com.example.globalshop.economy.MoneyFormat;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: bid on an auction, or take its buyout.
 *
 * <p>One packet for both because they differ only in which service call they make, the way
 * {@code VipActionPacket} does. The amount travels as <em>text</em> and is parsed here with the
 * server's own currency settings, so a client cannot send a number the server would read
 * differently — and it is never treated as final: the service re-checks it against the standing
 * bid, the increment and the bidder's actual balance before anything moves.
 */
public class AuctionActionPacket {

    public static final int BID = 0;
    public static final int BUYOUT = 1;

    /** Matches the market's own limit on a typed amount. */
    public static final int MAX_AMOUNT_TEXT = 24;

    private final int action;
    private final UUID id;
    private final String amountText;

    public AuctionActionPacket(int action, UUID id, String amountText) {
        this.action = action;
        this.id = id;
        this.amountText = amountText == null ? "" : amountText;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUUID(id);
        buf.writeUtf(amountText, MAX_AMOUNT_TEXT);
    }

    public static AuctionActionPacket decode(FriendlyByteBuf buf) {
        return new AuctionActionPacket(buf.readVarInt(), buf.readUUID(),
                buf.readUtf(MAX_AMOUNT_TEXT));
    }

    public static void handle(AuctionActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            AuctionService auctions = AuctionServices.get(sender);
            AuctionResult result;
            if (message.action == BUYOUT) {
                result = auctions.buyout(sender, message.id);
            } else {
                OptionalLong amount = MoneyFormat.parse(message.amountText);
                if (amount.isEmpty() || amount.getAsLong() <= 0L) {
                    sender.sendSystemMessage(Component.translatable(
                            AuctionResult.INVALID_PRICE.translationKey()));
                    return;
                }
                result = auctions.bid(sender, message.id, amount.getAsLong());
            }
            if (!result.isSuccess()) {
                // Refusals are always reported: a bid that silently did nothing would look like lag.
                sender.sendSystemMessage(Component.translatable(result.translationKey()));
            }
            // No list is pushed here. On success the broadcast has already told everyone watching,
            // including this player, and on failure nothing changed — either way the window is
            // already right, and a full list per click would be the expensive way to say so.
        });
        context.setPacketHandled(true);
    }
}
