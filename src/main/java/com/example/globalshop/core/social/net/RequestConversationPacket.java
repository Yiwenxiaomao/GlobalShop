package com.example.globalshop.core.social.net;

import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.social.SocialService;
import com.example.globalshop.core.social.SocialServices;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "open the conversation with this player".
 *
 * <p>The other player's id is the only thing that travels. Whether they are online, what was said
 * before and whether they are even a real account are all answered by the server.
 */
public class RequestConversationPacket {

    private final UUID partnerId;

    public RequestConversationPacket(UUID partnerId) {
        this.partnerId = partnerId;
    }

    public UUID partnerId() {
        return partnerId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(partnerId);
    }

    public static RequestConversationPacket decode(FriendlyByteBuf buf) {
        return new RequestConversationPacket(buf.readUUID());
    }

    public static void handle(RequestConversationPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            SocialService social = SocialServices.get(sender);
            ModNetwork.sendToPlayer(sender,
                    ConversationDataPacket.build(social, sender, message.partnerId));
        });
        context.setPacketHandled(true);
    }
}
