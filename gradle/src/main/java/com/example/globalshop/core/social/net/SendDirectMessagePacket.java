package com.example.globalshop.core.social.net;

import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.market.ShopResult;
import com.example.globalshop.core.social.SocialService;
import com.example.globalshop.core.social.SocialServices;
import com.example.globalshop.core.social.DirectMessage;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: one private message.
 *
 * <p>Refused only for reasons the sender can fix — empty, too long, or addressed to somebody who is
 * not a player. Being offline is not a refusal: a message to somebody who is away is kept and
 * announced the next time they log in, which is the whole point of being able to leave one.
 */
public class SendDirectMessagePacket {

    private final UUID partnerId;
    private final String text;

    public SendDirectMessagePacket(UUID partnerId, String text) {
        this.partnerId = partnerId;
        this.text = text == null ? "" : text;
    }

    public UUID partnerId() {
        return partnerId;
    }

    public String text() {
        return text;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(partnerId);
        // Allowed a little over the limit so the server can tell "too long" from "empty" and say so,
        // rather than the text being silently cut to fit the buffer.
        buf.writeUtf(text, DirectMessage.MAX_LENGTH + 64);
    }

    public static SendDirectMessagePacket decode(FriendlyByteBuf buf) {
        return new SendDirectMessagePacket(buf.readUUID(), buf.readUtf(DirectMessage.MAX_LENGTH + 64));
    }

    public static void handle(SendDirectMessagePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            SocialService social = SocialServices.get(sender);
            ShopResult result = social.send(sender, message.partnerId, message.text);
            if (!result.isSuccess()) {
                sender.sendSystemMessage(Component.translatable(result.translationKey()));
            }
            // Either way the window is refreshed, so a refusal leaves the typed text question
            // answered rather than the screen silently unchanged.
            ModNetwork.sendToPlayer(sender,
                    ConversationDataPacket.build(social, sender, message.partnerId));
        });
        context.setPacketHandled(true);
    }
}
