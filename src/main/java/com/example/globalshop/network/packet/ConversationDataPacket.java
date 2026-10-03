package com.example.globalshop.network.packet;

import com.example.globalshop.client.ClientScreens;
import com.example.globalshop.social.SocialService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: one conversation, and the sender's own balance.
 *
 * <p>The balance rides along because the payment field needs it and the window already refreshed
 * whenever anything changed. Sending it separately would mean a second round trip for a number that
 * is only ever displayed.
 *
 * <p>Only messages between the sender and {@code partnerId} are ever built into this packet: the
 * storage answers "the conversation between these two", and the first of those two is always the
 * player who asked. There is no field for whose messages to send.
 */
public class ConversationDataPacket {

    /** One message as the window draws it. */
    public record Line(UUID from, String fromName, String text, long time) {
    }

    private final UUID partnerId;
    private final String partnerName;
    private final boolean online;
    private final boolean known;
    private final List<Line> lines;
    private final long viewerBalance;

    // ---- the offer, if this pair has one ----
    // Carried with the conversation rather than fetched separately: the window draws both, and an
    // offer that arrived a moment after the messages would flicker in and out.
    private final boolean hasOffer;
    private final UUID offerSellerId;
    private final net.minecraft.world.item.ItemStack offerItem;
    private final long offerPrice;
    private final boolean offerPriced;

    public ConversationDataPacket(UUID partnerId, String partnerName, boolean online, boolean known,
                                  List<Line> lines, long viewerBalance, boolean hasOffer,
                                  UUID offerSellerId, net.minecraft.world.item.ItemStack offerItem,
                                  long offerPrice, boolean offerPriced) {
        this.partnerId = partnerId;
        this.partnerName = partnerName;
        this.online = online;
        this.known = known;
        this.lines = List.copyOf(lines);
        this.viewerBalance = viewerBalance;
        this.hasOffer = hasOffer;
        this.offerSellerId = offerSellerId;
        this.offerItem = offerItem == null
                ? net.minecraft.world.item.ItemStack.EMPTY : offerItem.copy();
        this.offerPrice = offerPrice;
        this.offerPriced = offerPriced;
    }

    public boolean hasOffer() {
        return hasOffer;
    }

    /** Who is selling. The buyer is whichever of the two is not this. */
    public UUID offerSellerId() {
        return offerSellerId;
    }

    public net.minecraft.world.item.ItemStack offerItem() {
        return offerItem.copy();
    }

    public long offerPrice() {
        return offerPrice;
    }

    /** Whether a price has been published yet. Nothing can be paid before it has. */
    public boolean offerPriced() {
        return offerPriced;
    }

    public UUID partnerId() {
        return partnerId;
    }

    public String partnerName() {
        return partnerName;
    }

    public boolean online() {
        return online;
    }

    /** Whether this id belongs to any account at all. */
    public boolean known() {
        return known;
    }

    /** Messages between the two players, newest first. */
    public List<Line> lines() {
        return lines;
    }

    public long viewerBalance() {
        return viewerBalance;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(partnerId);
        buf.writeUtf(partnerName == null ? "" : partnerName, 32);
        buf.writeBoolean(online);
        buf.writeBoolean(known);
        buf.writeLong(viewerBalance);
        buf.writeBoolean(hasOffer);
        if (hasOffer) {
            buf.writeUUID(offerSellerId);
            buf.writeItem(offerItem);
            buf.writeLong(offerPrice);
            buf.writeBoolean(offerPriced);
        }
        buf.writeVarInt(lines.size());
        for (Line line : lines) {
            buf.writeUUID(line.from());
            buf.writeUtf(line.fromName() == null ? "" : line.fromName(), 32);
            buf.writeUtf(line.text(), 200);
            buf.writeLong(line.time());
        }
    }

    public static ConversationDataPacket decode(FriendlyByteBuf buf) {
        UUID partnerId = buf.readUUID();
        String partnerName = buf.readUtf(32);
        boolean online = buf.readBoolean();
        boolean known = buf.readBoolean();
        long balance = buf.readLong();
        boolean hasOffer = buf.readBoolean();
        UUID offerSellerId = null;
        net.minecraft.world.item.ItemStack offerItem = net.minecraft.world.item.ItemStack.EMPTY;
        long offerPrice = 0L;
        boolean offerPriced = false;
        if (hasOffer) {
            offerSellerId = buf.readUUID();
            offerItem = buf.readItem();
            offerPrice = buf.readLong();
            offerPriced = buf.readBoolean();
        }
        int count = buf.readVarInt();
        List<Line> lines = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            lines.add(new Line(buf.readUUID(), buf.readUtf(32), buf.readUtf(200), buf.readLong()));
        }
        return new ConversationDataPacket(partnerId, partnerName, online, known, lines, balance,
                hasOffer, offerSellerId, offerItem, offerPrice, offerPriced);
    }

    public static void handle(ConversationDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.acceptConversation(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the conversation between the viewer and one partner. Server side only. */
    public static ConversationDataPacket build(SocialService social, ServerPlayer viewer,
                                               UUID partnerId) {
        List<SocialService.Line> found = social.conversation(viewer.getUUID(), partnerId);
        List<Line> lines = new ArrayList<>(found.size());
        for (SocialService.Line line : found) {
            lines.add(new Line(line.from(), line.fromName(), line.text(), line.time()));
        }
        com.example.globalshop.social.TradeOffer offer =
                social.offerBetween(viewer.getUUID(), partnerId);
        // The window's offer slot is brought in line here, so both sides see the held item without a
        // second message to keep it in step.
        if (viewer.containerMenu instanceof com.example.globalshop.menu.TradeMenu menu) {
            menu.showOffer(offer);
        }
        return new ConversationDataPacket(partnerId, social.nameOf(partnerId),
                social.isOnline(partnerId), social.isKnown(partnerId), lines,
                social.balanceOf(viewer.getUUID()), offer != null,
                offer == null ? null : offer.sellerId(),
                offer == null ? net.minecraft.world.item.ItemStack.EMPTY : offer.item(),
                offer == null ? 0L : offer.price(),
                offer != null && offer.priced());
    }
}
