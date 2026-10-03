package com.example.globalshop.network.packet;

import com.example.globalshop.menu.ShopMenu;
import com.example.globalshop.network.ShopViewState;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "list the item in my input slot".
 *
 * <p>The item is not named in the packet. The server reads the input slot of the
 * player's own open {@link ShopMenu}, so the listing is always backed by an item the
 * player actually placed there. The stock is then escrowed from the input slot and
 * the backpack, and creation fails outright if the items are not really present.
 */
public class CreateListingPacket {

    private final ShopViewState view;
    private final long unitPrice;
    private final int stock;
    private final int minPerBuy;
    private final int maxPerBuy;

    public CreateListingPacket(ShopViewState view, long unitPrice, int stock, int minPerBuy, int maxPerBuy) {
        this.view = view;
        this.unitPrice = unitPrice;
        this.stock = stock;
        this.minPerBuy = minPerBuy;
        this.maxPerBuy = maxPerBuy;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeLong(unitPrice);
        buf.writeVarInt(Math.max(0, stock));
        buf.writeVarInt(Math.max(0, minPerBuy));
        buf.writeVarInt(Math.max(0, maxPerBuy));
    }

    public static CreateListingPacket decode(FriendlyByteBuf buf) {
        ShopViewState view = ShopViewState.decode(buf);
        long unitPrice = buf.readLong();
        int stock = buf.readVarInt();
        int minPerBuy = buf.readVarInt();
        int maxPerBuy = buf.readVarInt();
        return new CreateListingPacket(view, unitPrice, stock, minPerBuy, maxPerBuy);
    }

    public static void handle(CreateListingPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ShopMenu menu)) {
                return;
            }
            ShopResult result = ShopServices.get(sender).create(
                    sender,
                    menu.inputStack(),
                    message.stock,
                    message.unitPrice,
                    message.minPerBuy,
                    message.maxPerBuy,
                    menu.escrowPools());
            ShopActionResultPacket.send(sender, result, message.view);
        });
        context.setPacketHandled(true);
    }
}
