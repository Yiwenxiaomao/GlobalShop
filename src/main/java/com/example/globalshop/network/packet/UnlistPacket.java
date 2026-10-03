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
 * Client to server: "take my listing down".
 *
 * <p>The server verifies ownership (or operator permission) itself; the client's
 * belief about who owns a listing is never consulted. Unsold escrow returns to the
 * seller, who may be offline, in which case it waits in their mailbox.
 */
public class UnlistPacket {

    private final ShopViewState view;
    private final UUID listingId;

    public UnlistPacket(ShopViewState view, UUID listingId) {
        this.view = view;
        this.listingId = listingId;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
        buf.writeUUID(listingId);
    }

    public static UnlistPacket decode(FriendlyByteBuf buf) {
        ShopViewState view = ShopViewState.decode(buf);
        return new UnlistPacket(view, buf.readUUID());
    }

    public static void handle(UnlistPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ShopMenu)) {
                return;
            }
            ShopResult result = ShopServices.get(sender).unlist(sender, message.listingId);
            ShopActionResultPacket.send(sender, result, message.view);
        });
        context.setPacketHandled(true);
    }
}
