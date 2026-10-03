package com.example.globalshop.network.packet;

import com.example.globalshop.menu.TradeMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "open the private-trade window".
 *
 * <p>Carries nothing. Which players exist, who is online and what was said are all answered by the
 * window's own requests, so nothing about the world is taken from the client.
 */
public class OpenTradePacket {

    public OpenTradePacket() {
    }

    public void encode(FriendlyByteBuf buf) {
        // Nothing to send.
    }

    public static OpenTradePacket decode(FriendlyByteBuf buf) {
        return new OpenTradePacket();
    }

    public static void handle(OpenTradePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            sender.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, player) -> new TradeMenu(windowId, inventory),
                    Component.translatable("globalshop.trade.title")));
        });
        context.setPacketHandled(true);
    }
}
