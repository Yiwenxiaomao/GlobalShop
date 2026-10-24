package com.example.globalshop.core.social.net;

import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.market.ShopResult;
import com.example.globalshop.core.market.ShopService;
import com.example.globalshop.core.market.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: claim one mail, or throw it away.
 *
 * <p>The id is checked against the sender's own mailbox, so an id belonging to someone else simply
 * is not found — there is no separate ownership test to forget. The server replies with a fresh
 * mailbox either way, so what the screen shows next is what the server actually holds.
 */
public class MailboxActionPacket {

    private final long mailId;
    private final boolean delete;

    public MailboxActionPacket(long mailId, boolean delete) {
        this.mailId = mailId;
        this.delete = delete;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(mailId);
        buf.writeBoolean(delete);
    }

    public static MailboxActionPacket decode(FriendlyByteBuf buf) {
        return new MailboxActionPacket(buf.readVarLong(), buf.readBoolean());
    }

    public static void handle(MailboxActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ShopService shop = ShopServices.get(sender);
            if (message.delete) {
                boolean removed = shop.deleteMail(sender.getUUID(), message.mailId);
                if (removed) {
                    sender.sendSystemMessage(Component.translatable("globalshop.mail.deleted"));
                }
            } else {
                ShopResult result = shop.claimMail(sender, message.mailId);
                sender.sendSystemMessage(result.isSuccess()
                        ? Component.translatable("globalshop.mail.claimed")
                        : Component.translatable(result.translationKey()));
            }
            ModNetwork.sendToPlayer(sender,
                    MailboxDataPacket.build(shop, sender.getUUID()));
        });
        context.setPacketHandled(true);
    }
}
