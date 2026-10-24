package com.example.globalshop.feature.ranking.net;

import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.social.SocialService;
import com.example.globalshop.core.social.SocialServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: "show me the player list page matching this search".
 *
 * <p>The search text and page are display preferences, exactly like the market's view state: the
 * server clamps them and answers from its own data, so a hand-written client can ask for page 9999
 * and simply receives the last page.
 */
public class RequestPlayerListPacket {

    /** Server-side bound, so a malformed packet cannot ask for a huge string. */
    public static final int MAX_QUERY = 32;

    private final String query;
    private final int page;

    public RequestPlayerListPacket(String query, int page) {
        this.query = query == null ? "" : query;
        this.page = page;
    }

    public String query() {
        return query;
    }

    public int page() {
        return page;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(query, MAX_QUERY);
        buf.writeVarInt(Math.max(0, page));
    }

    public static RequestPlayerListPacket decode(FriendlyByteBuf buf) {
        return new RequestPlayerListPacket(buf.readUtf(MAX_QUERY), buf.readVarInt());
    }

    public static void handle(RequestPlayerListPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            SocialService social = SocialServices.get(sender);
            ModNetwork.sendToPlayer(sender,
                    PlayerListDataPacket.build(social, message.query, message.page));
        });
        context.setPacketHandled(true);
    }
}
