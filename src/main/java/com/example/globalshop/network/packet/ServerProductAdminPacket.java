package com.example.globalshop.network.packet;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.menu.ServerShopMenu;
import com.example.globalshop.servershop.ServerShopResult;
import com.example.globalshop.servershop.ServerShopService;
import com.example.globalshop.servershop.ServerShopServices;
import com.example.globalshop.servershop.ServerShopTab;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: write a line into the catalogue, or take one out.
 *
 * <h2>What the client is not allowed to say</h2>
 * For an addition it does <em>not</em> send the item at all: the server reads it from the
 * operator's own held item. The client sends only the numbers. For a removal it sends the item,
 * because that is what identifies the line — but the line still has to be in the server's own
 * catalogue to be found, so an invented one resolves to nothing.
 *
 * <h2>Permission</h2>
 * Checked here, on the server, every time — regardless of whether the management page was ever
 * drawn for this player. That is the point of checking it here rather than only hiding a tab:
 * hiding a button is a courtesy, refusing the request is the rule.
 */
public class ServerProductAdminPacket {

    /** What to do. Ordinal-encoded on the wire. */
    public enum Action {
        ADD,
        REMOVE;

        static Action byOrdinal(int ordinal) {
            Action[] values = values();
            return (ordinal < 0 || ordinal >= values.length) ? ADD : values[ordinal];
        }
    }

    private final Action action;
    /** Identifies the line for {@link Action#REMOVE}; ignored for {@link Action#ADD}. */
    private final ItemStack target;
    private final long sellPrice;
    private final long buybackPrice;
    private final int bundleSize;
    private final int purchaseLimit;
    private final int page;

    public ServerProductAdminPacket(Action action, ItemStack target, long sellPrice,
                                    long buybackPrice, int bundleSize, int purchaseLimit, int page) {
        this.action = action;
        this.target = target;
        this.sellPrice = sellPrice;
        this.buybackPrice = buybackPrice;
        this.bundleSize = bundleSize;
        this.purchaseLimit = purchaseLimit;
        this.page = page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action.ordinal());
        buf.writeItem(target);
        buf.writeLong(sellPrice);
        buf.writeLong(buybackPrice);
        buf.writeVarInt(bundleSize);
        buf.writeVarInt(purchaseLimit);
        buf.writeVarInt(page);
    }

    public static ServerProductAdminPacket decode(FriendlyByteBuf buf) {
        return new ServerProductAdminPacket(Action.byOrdinal(buf.readVarInt()), buf.readItem(),
                buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ServerProductAdminPacket message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ServerShopMenu menu)) {
                return;
            }
            ServerShopService shop = ServerShopServices.get(sender);
            if (!shop.isOperator(sender)) {
                GlobalShop.LOGGER.warn("globalshop: refusing a server shop catalogue edit from {} (not an operator)",
                        sender.getGameProfile().getName());
                ServerShopActionResultPacket.sendAdmin(sender, ServerShopResult.INTERNAL_ERROR, null,
                        Component.empty());
                return;
            }

            ServerShopResult result;
            String successKey;
            ItemStack shown;
            if (message.action == Action.ADD) {
                // The item comes from the menu the server owns, never from the packet. An
                // operator who kept it in hand rather than dragging it in is accepted too, so
                // neither route is a dead end. It is only read: stocking never consumes it.
                ItemStack template = menu.templateStack();
                if (template.isEmpty()) {
                    template = sender.getMainHandItem();
                }
                shown = template.copy();
                // A buy-back price of zero means "the server does not buy this back", which is
                // how the management page expresses the recycling switch without a second control.
                boolean allowBuyback = message.buybackPrice > 0L;
                result = shop.add(template, message.sellPrice, message.buybackPrice,
                        message.bundleSize, allowBuyback, message.purchaseLimit);
                successKey = "globalshop.servershop.admin_added";
            } else {
                shown = message.target.copy();
                result = shop.remove(message.target);
                successKey = "globalshop.servershop.admin_removed";
            }

            ServerShopActionResultPacket.sendAdmin(sender, result, successKey,
                    shown.isEmpty() ? Component.empty() : shown.getHoverName());
            ServerShopDataPacket.send(sender, ServerShopTab.MANAGE, message.page);
        });
        context.setPacketHandled(true);
    }
}
