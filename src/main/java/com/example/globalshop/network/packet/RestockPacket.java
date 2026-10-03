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
 * Client to server: "put everything of that item I am carrying into this lot".
 *
 * <p>Carries only which listing and the player's current view, like every other request. The
 * items come from the seller's own inventory server-side, and the resulting stock is decided
 * there too — a client cannot name an amount, so it cannot inflate a lot beyond what it holds.
 *
 * <p>Refused unless the sender actually has the market open, and refused unless the lot is
 * theirs; both checks are on the server.
 */
public class RestockPacket {

    private final ShopViewState view;
    private final UUID listingId;

    public RestockPacket(ShopViewState view, UUID listingId) {
        this.view = view;
        this.listingId = listingId;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeUUID(listingId);
    }

    public static RestockPacket decode(FriendlyByteBuf buf) {
        return new RestockPacket(ShopViewState.decode(buf), buf.readUUID());
    }

    public static void handle(RestockPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ShopMenu menu)) {
                return;
            }
            ShopResult result = ShopServices.get(sender).restock(
                    sender, message.listingId, menu.escrowPools());
            // Sends the outcome and a fresh page, so the stock the player sees is the stock the
            // server now holds rather than the one it had before the click.
            ShopActionResultPacket.send(sender, result, message.view);
        });
        context.setPacketHandled(true);
    }
}
