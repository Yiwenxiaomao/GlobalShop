package com.example.globalshop.core.social.net;

import com.example.globalshop.core.client.ClientScreens;
import com.example.globalshop.core.social.net.MailView;
import com.example.globalshop.core.social.Mail;
import com.example.globalshop.core.market.ShopService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: one player's mailbox.
 *
 * <p>Built from the sender's own deliveries and nothing else. There is no field naming whose
 * mailbox this is, so the packet cannot express "somebody else's mail" — the same shape as the
 * trade log, and for the same reason.
 */
public class MailboxDataPacket {

    /** How many mails are sent at most. The mailbox's own limit is the real bound. */
    public static final int MAX_SENT = 200;

    private final List<MailView> mails;

    public MailboxDataPacket(List<MailView> mails) {
        this.mails = List.copyOf(mails);
    }

    public List<MailView> mails() {
        return mails;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mails.size());
        for (MailView mail : mails) {
            mail.encode(buf);
        }
    }

    public static MailboxDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MailView> mails = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            mails.add(MailView.decode(buf));
        }
        return new MailboxDataPacket(mails);
    }

    public static void handle(MailboxDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.openMailbox(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the payload for one player. Server side only. */
    public static MailboxDataPacket build(ShopService shop, UUID playerId) {
        List<Mail> mails = shop.mailsOf(playerId);
        List<MailView> views = new ArrayList<>(Math.min(mails.size(), MAX_SENT));
        for (Mail mail : mails) {
            if (views.size() >= MAX_SENT) {
                break;
            }
            views.add(MailView.of(mail));
        }
        return new MailboxDataPacket(views);
    }
}
