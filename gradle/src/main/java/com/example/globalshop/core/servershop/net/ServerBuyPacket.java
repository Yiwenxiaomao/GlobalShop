package com.example.globalshop.core.servershop.net;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.core.menu.ServerShopMenu;
import com.example.globalshop.core.servershop.ServerShopEntry;
import com.example.globalshop.core.servershop.ServerShopResult;
import com.example.globalshop.core.servershop.ServerShopService;
import com.example.globalshop.core.servershop.ServerShopServices;
import com.example.globalshop.core.servershop.ServerShopTab;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "buy this product, this many times".
 *
 * <p>Carries the product as an item stack, which is the catalogue's own key — so there is no id
 * for a client to invent, and an item that is not in the catalogue simply resolves to no entry.
 * Neither the price nor the bundle size is sent: both are read from the server's catalogue, so a
 * client cannot name its own price.
 *
 * <p>Refused unless the sender actually has the server shop open, which stops a modified client
 * from trading through a window it is not looking at.
 */
public class ServerBuyPacket {

    private final ItemStack product;
    private final int actions;
    private final int page;

    public ServerBuyPacket(ItemStack product, int actions, int page) {
        this.product = product;
        this.actions = actions;
        this.page = page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(product);
        buf.writeVarInt(actions);
        buf.writeVarInt(page);
    }

    public static ServerBuyPacket decode(FriendlyByteBuf buf) {
        return new ServerBuyPacket(buf.readItem(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ServerBuyPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            // The window must actually be open. This is the same gate the market uses: it is
            // what makes "the client only sends intent" true rather than aspirational.
            if (!(sender.containerMenu instanceof ServerShopMenu)) {
                GlobalShop.LOGGER.debug("globalshop: refusing a server-shop buy from {} (no shop open)",
                        sender.getGameProfile().getName());
                return;
            }
            ServerShopService shop = ServerShopServices.get(sender);
            ServerShopEntry entry = shop.find(message.product);
            ServerShopService.Outcome outcome = shop.buy(sender, entry, message.actions);

            String key = outcome.isSuccess() ? "globalshop.servershop.bought" : null;
            ServerShopActionResultPacket.send(sender, outcome.result(), key,
                    entry == null ? message.product.getHoverName() : entry.template().getHoverName(),
                    outcome.items(), outcome.money());
            // Re-sync unconditionally: a failed trade still changed nothing, but a successful one
            // changed the balance, the purchased count and possibly the carried count.
            ServerShopDataPacket.send(sender, ServerShopTab.SALE, message.page);
        });
        context.setPacketHandled(true);
    }
}
