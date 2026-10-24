package com.example.globalshop.core.market.net;

import com.example.globalshop.core.menu.ShopMenu;
import com.example.globalshop.core.market.net.ShopViewState;
import com.example.globalshop.core.market.ShopResult;
import com.example.globalshop.core.market.ShopServices;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "buy this many from this listing".
 *
 * <p>Carries no price. The server looks the listing up by id and uses the price,
 * stock and limits it has stored, so a modified client cannot buy at a price of its
 * own choosing.
 */
public class PurchasePacket {

    private final ShopViewState view;
    private final UUID listingId;
    private final int quantity;

    public PurchasePacket(ShopViewState view, UUID listingId, int quantity) {
        this.view = view;
        this.listingId = listingId;
        this.quantity = quantity;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeUUID(listingId);
        buf.writeVarInt(Math.max(0, quantity));
    }

    public static PurchasePacket decode(FriendlyByteBuf buf) {
        ShopViewState view = ShopViewState.decode(buf);
        UUID listingId = buf.readUUID();
        int quantity = buf.readVarInt();
        return new PurchasePacket(view, listingId, quantity);
    }

    public static void handle(PurchasePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            // Require the market to actually be open. A scripted client that never
            // opens the screen has no business transacting.
            if (!(sender.containerMenu instanceof ShopMenu)) {
                return;
            }
            ShopResult result = ShopServices.get(sender).purchase(sender, message.listingId, message.quantity);
            ShopActionResultPacket.send(sender, result, message.view);
        });
        context.setPacketHandled(true);
    }
}
