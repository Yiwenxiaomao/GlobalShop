package com.example.globalshop.core.servershop.net;

import com.example.globalshop.core.servershop.ServerShopTab;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "send me this page of this tab".
 *
 * <p>Sent when the screen opens, when the tab or page changes, and after every trade. Both
 * values are display preferences and nothing more; the server clamps them and rebuilds the page
 * from its own catalogue and the player's own balance and permissions.
 */
public class RequestServerShopDataPacket {

    private final ServerShopTab tab;
    private final int page;

    public RequestServerShopDataPacket(ServerShopTab tab, int page) {
        this.tab = tab;
        this.page = page;
    }

    public ServerShopTab tab() {
        return tab;
    }

    public int page() {
        return page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(tab.ordinal());
        buf.writeVarInt(page);
    }

    public static RequestServerShopDataPacket decode(FriendlyByteBuf buf) {
        return new RequestServerShopDataPacket(ServerShopTab.byOrdinal(buf.readVarInt()),
                buf.readVarInt());
    }

    public static void handle(RequestServerShopDataPacket message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                // Client side: a request packet must never be processed locally.
                return;
            }
            ServerShopDataPacket.send(sender, message.tab, message.page);
        });
        context.setPacketHandled(true);
    }
}
