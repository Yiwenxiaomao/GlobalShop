package com.example.globalshop.feature.auction.net;

import com.example.globalshop.Config;
import com.example.globalshop.feature.auction.AuctionResult;
import com.example.globalshop.feature.auction.AuctionService;
import com.example.globalshop.feature.auction.AuctionServices;
import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.market.ContainerPool;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.core.market.ItemPool;
import com.example.globalshop.core.network.ModNetwork;

/**
 * Client to server: put the item I am holding up for auction, on these terms.
 *
 * <p>The item is <em>not</em> in this packet. The server reads it from the player's own hand, so
 * there is no way to offer something that is not there, and no way to describe a stack that never
 * existed. What the client chooses is the terms — the prices and the duration — and every one of
 * them is validated against the config before anything is escrowed.
 */
public class AuctionCreatePacket {

    public static final int MAX_AMOUNT_TEXT = 24;

    private final String startPrice;
    private final String minIncrement;
    private final String buyoutPrice;
    private final long minutes;

    public AuctionCreatePacket(String startPrice, String minIncrement, String buyoutPrice,
                               long minutes) {
        this.startPrice = startPrice == null ? "" : startPrice;
        this.minIncrement = minIncrement == null ? "" : minIncrement;
        this.buyoutPrice = buyoutPrice == null ? "" : buyoutPrice;
        this.minutes = minutes;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(startPrice, MAX_AMOUNT_TEXT);
        buf.writeUtf(minIncrement, MAX_AMOUNT_TEXT);
        buf.writeUtf(buyoutPrice, MAX_AMOUNT_TEXT);
        buf.writeVarLong(minutes);
    }

    public static AuctionCreatePacket decode(FriendlyByteBuf buf) {
        return new AuctionCreatePacket(buf.readUtf(MAX_AMOUNT_TEXT), buf.readUtf(MAX_AMOUNT_TEXT),
                buf.readUtf(MAX_AMOUNT_TEXT), buf.readVarLong());
    }

    public static void handle(AuctionCreatePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            AuctionService auctions = AuctionServices.get(sender);

            OptionalLong start = MoneyFormat.parse(message.startPrice);
            OptionalLong increment = MoneyFormat.parse(message.minIncrement);
            // A blank buyout means "none", which is different from a buyout of zero.
            OptionalLong buyout = message.buyoutPrice.isBlank()
                    ? OptionalLong.of(0L)
                    : MoneyFormat.parse(message.buyoutPrice);
            if (start.isEmpty() || increment.isEmpty() || buyout.isEmpty()) {
                sender.sendSystemMessage(Component.translatable(
                        AuctionResult.INVALID_PRICE.translationKey()));
                return;
            }

            ItemStack held = sender.getMainHandItem();
            // Named and counted before the call, not after. The service escrows out of this very
            // stack, so by the time it returns the hand is empty and reading the name from it
            // reports "Air" — which is exactly what a player saw after listing something.
            Component offered = Component.literal(held.getCount() > 1
                    ? held.getHoverName().getString() + " x" + held.getCount()
                    : held.getHoverName().getString());
            int count = held.getCount();
            List<com.example.globalshop.core.market.ItemPool> pools =
                    List.of(new ContainerPool(sender.getInventory()));
            AuctionResult result = auctions.list(sender, held, count, start.getAsLong(),
                    increment.getAsLong(), buyout.getAsLong(), message.minutes, pools);

            sender.sendSystemMessage(Component.translatable(result.isSuccess()
                    ? "globalshop.auction.listed"
                    : result.translationKey(), offered));
            // The window this player is looking at has to show the new auction, so the list is
            // rebuilt for them. Nobody else is pushed anything: a new listing does not change a
            // number somebody is watching, and they will see it when they next refresh.
            com.example.globalshop.core.network.ModNetwork.sendToPlayer(sender,
                    AuctionDataPacket.build(auctions, sender, "", 0));
        });
        context.setPacketHandled(true);
    }

    /** The longest duration the config allows, for the client's own validation. */
    public static long maxMinutes() {
        return Config.auctionMaxMinutes();
    }
}
