package com.example.globalshop.core.servershop.net;

import com.example.globalshop.core.servershop.client.ClientServerShopCache;
import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.servershop.ServerShopResult;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: what a trade did.
 *
 * <p>The message is composed on the server and sent as a component rather than as a key the
 * client looks up, because these messages carry numbers the server computed — how many items
 * were taken, how much money moved — and a client that reassembled those from a key would be
 * second-guessing the outcome.
 *
 * <p>A failure carries the reason's translation key, which has no numbers in it, so it can be
 * sent as a key and translated in the player's own language.
 */
public class ServerShopActionResultPacket {

    private final boolean success;
    private final String statusKey;
    private final String messageJson;

    public ServerShopActionResultPacket(boolean success, String statusKey, String messageJson) {
        this.success = success;
        this.statusKey = statusKey;
        this.messageJson = messageJson;
    }

    /**
     * Whether the outcome was accepted.
     *
     * <p>Sent explicitly rather than inferred on the client by looking for {@code ".result."} in
     * the key. That guess happened to work here, but it is the same class of guess that made a
     * successful purchase sound like a refusal in the market window.
     */
    public boolean success() {
        return success;
    }

    public String statusKey() {
        return statusKey;
    }

    public String messageJson() {
        return messageJson;
    }

    /** Builds and sends the outcome of one trade. */
    public static void send(ServerPlayer player, ServerShopResult result, String successKey,
                            Component itemName, int items, long money) {
        Component message;
        String key;
        if (result.isSuccess() && successKey != null) {
            key = successKey;
            message = Component.translatable(successKey, items, itemName,
                    MoneyFormat.formatWithName(money));
        } else {
            key = result.translationKey();
            message = Component.translatable(key);
        }
        ModNetwork.sendToPlayer(player, new ServerShopActionResultPacket(result.isSuccess(), key,
                Component.Serializer.toJson(message)));
    }

    /**
     * Builds and sends the outcome of a catalogue edit.
     *
     * <p>Separate from {@link #send} because the message shape is different: a catalogue edit
     * moves no items and no money, so it names only the product.
     */
    public static void sendAdmin(ServerPlayer player, ServerShopResult result, String successKey,
                                 Component itemName) {
        boolean success = result.isSuccess() && successKey != null;
        String key = success ? successKey : result.translationKey();
        Component message = success
                ? Component.translatable(successKey, itemName)
                : Component.translatable(key);
        ModNetwork.sendToPlayer(player, new ServerShopActionResultPacket(result.isSuccess(), key,
                Component.Serializer.toJson(message)));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(statusKey);
        buf.writeUtf(messageJson);
    }

    public static ServerShopActionResultPacket decode(FriendlyByteBuf buf) {
        return new ServerShopActionResultPacket(buf.readBoolean(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(ServerShopActionResultPacket message,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientServerShopCache.acceptResult(message));
        context.setPacketHandled(true);
    }
}
