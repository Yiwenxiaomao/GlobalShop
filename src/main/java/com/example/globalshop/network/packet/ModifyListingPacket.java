package com.example.globalshop.network.packet;

import com.example.globalshop.menu.ShopMenu;
import com.example.globalshop.network.ShopViewState;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.shop.ShopServices;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "change my listing".
 *
 * <p>Ownership is re-checked on the server. Raising the stock escrows the extra items
 * out of the player's input slot and backpack first, so a seller cannot inflate a
 * listing beyond the items they actually hand over.
 */
public class ModifyListingPacket {

    private final ShopViewState view;
    private final UUID listingId;
    private final long unitPrice;
    private final int stock;
    private final int minPerBuy;
    private final int maxPerBuy;

    public ModifyListingPacket(ShopViewState view, UUID listingId, long unitPrice, int stock,
                               int minPerBuy, int maxPerBuy) {
        this.view = view;
        this.listingId = listingId;
        this.unitPrice = unitPrice;
        this.stock = stock;
        this.minPerBuy = minPerBuy;
        this.maxPerBuy = maxPerBuy;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeUUID(listingId);
        buf.writeLong(unitPrice);
        buf.writeVarInt(Math.max(0, stock));
        buf.writeVarInt(Math.max(0, minPerBuy));
        buf.writeVarInt(Math.max(0, maxPerBuy));
    }

    public static ModifyListingPacket decode(FriendlyByteBuf buf) {
        ShopViewState view = ShopViewState.decode(buf);
        UUID listingId = buf.readUUID();
        long unitPrice = buf.readLong();
        int stock = buf.readVarInt();
        int minPerBuy = buf.readVarInt();
        int maxPerBuy = buf.readVarInt();
        return new ModifyListingPacket(view, listingId, unitPrice, stock, minPerBuy, maxPerBuy);
    }

    public static void handle(ModifyListingPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ShopMenu menu)) {
                return;
            }
            ShopResult result = ShopServices.get(sender).modify(
                    sender,
                    message.listingId,
                    message.unitPrice,
                    message.stock,
                    message.minPerBuy,
                    message.maxPerBuy,
                    menu.escrowPools());
            ShopActionResultPacket.send(sender, result, message.view);
        });
        context.setPacketHandled(true);
    }
}
