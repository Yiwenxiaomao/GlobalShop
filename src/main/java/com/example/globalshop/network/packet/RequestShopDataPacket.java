package com.example.globalshop.network.packet;

import com.example.globalshop.network.ShopViewState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "send me this page of the market".
 *
 * <p>Sent when the screen opens, when the player changes tab, sort, page or search
 * text, and after a reconnect. The view state is a display preference only; the
 * server rebuilds the page from its own storage and clamps whatever it was asked
 * for.
 */
public class RequestShopDataPacket {

    private final ShopViewState view;

    public RequestShopDataPacket(ShopViewState view) {
        this.view = view;
    }

    public ShopViewState view() {
        return view;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    public static RequestShopDataPacket decode(FriendlyByteBuf buf) {
        return new RequestShopDataPacket(ShopViewState.decode(buf));
    }

    public static void handle(RequestShopDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                // Client side: a request packet must never be processed locally.
                return;
            }
            ShopDataPacket.send(sender, message.view);
        });
        context.setPacketHandled(true);
    }
}
