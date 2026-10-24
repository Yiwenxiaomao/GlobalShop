package com.example.globalshop.network.packet;

import com.example.globalshop.economy.MoneyFormat;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: show this in the middle of the auction window.
 *
 * <p>Only sent to players who have the window open — everyone else gets the same news as a chat
 * line, which is what the requirement asks for. Carrying a translation key and its arguments rather
 * than a finished sentence means the message is written in the reading player's own language; a
 * server-side {@code Component} would arrive in the server's.
 */
public class AuctionToastPacket {

    private final String key;
    private final String itemName;
    private final long amount;

    public AuctionToastPacket(String key, String itemName, long amount) {
        this.key = key;
        this.itemName = itemName == null ? "" : itemName;
        this.amount = amount;
    }

    public String key() {
        return key;
    }

    public String itemName() {
        return itemName;
    }

    public long amount() {
        return amount;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(key, 96);
        buf.writeUtf(itemName, 96);
        buf.writeLong(amount);
    }

    public static AuctionToastPacket decode(FriendlyByteBuf buf) {
        return new AuctionToastPacket(buf.readUtf(96), buf.readUtf(96), buf.readLong());
    }

    /** The text to draw. {@code amount} of zero means the message takes only the item name. */
    public Component message() {
        if (amount > 0L) {
            return Component.translatable(key, itemName, MoneyFormat.formatWithName(amount));
        }
        return Component.translatable(key, itemName);
    }

    public static void handle(AuctionToastPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.client.ClientScreens.showAuctionToast(message);
            }
        });
        context.setPacketHandled(true);
    }
}
