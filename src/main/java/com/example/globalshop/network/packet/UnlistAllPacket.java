package com.example.globalshop.network.packet;

import com.example.globalshop.menu.ShopMenu;
import com.example.globalshop.network.ShopViewState;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "take every one of my lots off the market".
 *
 * <p>Carries nothing but the player's view — not a list of ids. Which lots are theirs is
 * something the server answers, so a client cannot ask for someone else's to be removed, and a
 * lot that disappeared between the click and the request simply is not there to remove.
 */
public class UnlistAllPacket {

    private final ShopViewState view;

    public UnlistAllPacket(ShopViewState view) {
        this.view = view;
    }

    public void encode(FriendlyByteBuf buf) {
        view.encode(buf);
    }

    public static UnlistAllPacket decode(FriendlyByteBuf buf) {
        return new UnlistAllPacket(ShopViewState.decode(buf));
    }

    public static void handle(UnlistAllPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (!(sender.containerMenu instanceof ShopMenu)) {
                return;
            }
            int removed = ShopServices.get(sender).unlistAll(sender);
            // The market's own result packet, not the server shop's: this is a market action and
            // the two shops deliberately have separate outcome enums.
            ShopActionResultPacket.send(sender,
                    removed > 0 ? ShopResult.SUCCESS : ShopResult.NOT_THE_OWNER,
                    message.view);
            ShopDataPacket.send(sender, message.view);
        });
        context.setPacketHandled(true);
    }
}
