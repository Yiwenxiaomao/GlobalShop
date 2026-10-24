package com.example.globalshop.core.servershop.net;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.core.menu.ServerShopMenu;
import com.example.globalshop.core.servershop.ServerShopEntry;
import com.example.globalshop.core.servershop.ServerShopService;
import com.example.globalshop.core.servershop.ServerShopServices;
import com.example.globalshop.core.servershop.ServerShopTab;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "sell this product back, one or all".
 *
 * <p>As with buying, the product is an item stack resolved against the server's catalogue and
 * the money comes from the server's buy-back price. The client cannot ask for a different amount
 * than "one" or "as many as allowed", which keeps the daily quota the only thing deciding how
 * many items actually change hands.
 */
public class ServerRecyclePacket {

    private final ItemStack product;
    private final boolean all;
    private final int page;

    public ServerRecyclePacket(ItemStack product, boolean all, int page) {
        this.product = product;
        this.all = all;
        this.page = page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(product);
        buf.writeBoolean(all);
        buf.writeVarInt(page);
    }

    public static ServerRecyclePacket decode(FriendlyByteBuf buf) {
        return new ServerRecyclePacket(buf.readItem(), buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(ServerRecyclePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ServerShopMenu)) {
                GlobalShop.LOGGER.debug("globalshop: refusing a server-shop recycle from {} (no shop open)",
                        sender.getGameProfile().getName());
                return;
            }
            ServerShopService shop = ServerShopServices.get(sender);
            ServerShopEntry entry = shop.find(message.product);
            ServerShopService.Outcome outcome = message.all
                    ? shop.recycleAll(sender, entry)
                    : shop.recycleOne(sender, entry);

            String key = outcome.isSuccess()
                    ? (message.all ? "globalshop.servershop.recycled_all" : "globalshop.servershop.recycled_one")
                    : null;
            ServerShopActionResultPacket.send(sender, outcome.result(), key,
                    entry == null ? message.product.getHoverName() : entry.template().getHoverName(),
                    outcome.items(), outcome.money());
            ServerShopDataPacket.send(sender, ServerShopTab.RECYCLE, message.page);
        });
        context.setPacketHandled(true);
    }
}
