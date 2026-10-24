package com.example.globalshop.core.market.net;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.core.client.ClientShopCache;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.market.net.ShopViewState;
import com.example.globalshop.core.market.ShopResult;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the outcome of a request, with a refreshed page attached.
 *
 * <p>The refreshed page is included so the client does not need a second round trip
 * after every purchase or edit, and so what the player sees next is always what the
 * server actually stored.
 */
public class ShopActionResultPacket {

    private final ShopResult result;
    private final ShopDataPacket refresh;

    public ShopActionResultPacket(ShopResult result, ShopDataPacket refresh) {
        this.result = result;
        this.refresh = refresh;
    }

    public ShopResult result() {
        return result;
    }

    public ShopDataPacket refresh() {
        return refresh;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(result);
        refresh.encode(buf);
    }

    public static ShopActionResultPacket decode(FriendlyByteBuf buf) {
        ShopResult result = buf.readEnum(ShopResult.class);
        ShopDataPacket refresh = ShopDataPacket.decode(buf);
        return new ShopActionResultPacket(result, refresh);
    }

    public static void handle(ShopActionResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientShopCache.acceptResult(message));
        context.setPacketHandled(true);
    }

    /**
     * Reports an outcome and the resulting page state to the player. Server side only.
     *
     * <p>The result is logged as well as sent, so an operator reading the server log
     * can see refusals (a burst of {@code INSUFFICIENT_FUNDS} is worth noticing) even
     * though no state changed.
     */
    public static void send(ServerPlayer player, ShopResult result, ShopViewState view) {
        if (!result.isSuccess()) {
            GlobalShop.LOGGER.debug("globalshop: {} refused for {} ({})",
                    result, player.getGameProfile().getName(), view.query());
        }
        ModNetwork.sendToPlayer(player, new ShopActionResultPacket(result, ShopDataPacket.build(player, view)));
    }
}
