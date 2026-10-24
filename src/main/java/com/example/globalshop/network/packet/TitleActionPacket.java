package com.example.globalshop.network.packet;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.title.TitleResult;
import com.example.globalshop.title.TitleService;
import com.example.globalshop.title.TitleServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: open the title window, save a title, or clear one.
 *
 * <p>One packet for the three, the way {@code VipActionPacket} does, because they are three things
 * the same window does. The title travels as text and is validated on the server with the server's
 * own rules — the client's copy is only there to show a price while typing.
 */
public class TitleActionPacket {

    public static final int OPEN = 0;
    public static final int SET = 1;
    public static final int CLEAR = 2;

    /** Generous, because a refused title is refused for being long and the window must send it. */
    public static final int MAX_TEXT = 64;

    private final int action;
    private final String text;

    public TitleActionPacket(int action, String text) {
        this.action = action;
        this.text = text == null ? "" : text;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(text, MAX_TEXT);
    }

    public static TitleActionPacket decode(FriendlyByteBuf buf) {
        return new TitleActionPacket(buf.readVarInt(), buf.readUtf(MAX_TEXT));
    }

    public static void handle(TitleActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            TitleService titles = TitleServices.get(sender);
            if (message.action == SET) {
                TitleResult result = titles.set(sender, message.text);
                sender.sendSystemMessage(Component.translatable(result.translationKey()));
                // The balance moved unless nothing did, and the window shows it either way.
                BalanceUpdatePacket.push(sender);
            } else if (message.action == CLEAR) {
                TitleResult result = titles.clear(sender);
                if (result.isSuccess()) {
                    sender.sendSystemMessage(Component.translatable("globalshop.title.cleared"));
                }
            }
            ModNetwork.sendToPlayer(sender, TitleDataPacket.build(titles, sender));
            // Chat resolves the sender's display name when the message is built, so the next line
            // they type already carries the new title. Refreshing here covers the tab list too.
            sender.refreshDisplayName();
        });
        context.setPacketHandled(true);
    }
}
