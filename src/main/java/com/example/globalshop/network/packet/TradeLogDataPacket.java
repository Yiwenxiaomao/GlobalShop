package com.example.globalshop.network.packet;

import com.example.globalshop.client.ClientScreens;
import com.example.globalshop.shop.ShopService;
import com.example.globalshop.shop.TradeLogEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the viewer's own player-to-player trades.
 *
 * <p>Only ever built for the player who asked, and there is no field identifying whose trades
 * these are — the packet cannot express "somebody else's log". Each line is already phrased from
 * the recipient's point of view (they bought it, or they sold it) so the screen does no
 * interpretation of its own.
 */
public class TradeLogDataPacket {

    /**
     * One trade, flattened and already oriented towards the reader.
     *
     * <p>{@code kind} is the enum constant's name — {@code PURCHASE}, {@code AUCTION},
     * {@code SERVER_SHOP}, {@code PRIVATE_TRADE} — so the screen can say how the item was bought.
     * Four ways of buying ending in one list is only useful if the list says which was which.
     */
    public record Line(long timestamp, String itemName, int quantity, long total,
                       String counterparty, boolean wasSeller, String kind) {
    }

    private final List<Line> lines;

    public TradeLogDataPacket(List<Line> lines) {
        this.lines = List.copyOf(lines);
    }

    public List<Line> lines() {
        return lines;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(lines.size());
        for (Line line : lines) {
            buf.writeLong(line.timestamp());
            buf.writeUtf(line.itemName());
            buf.writeVarInt(Math.max(0, line.quantity()));
            buf.writeLong(line.total());
            buf.writeUtf(line.counterparty());
            buf.writeBoolean(line.wasSeller());
            buf.writeUtf(line.kind());
        }
    }

    public static TradeLogDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Line> lines = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            lines.add(new Line(buf.readLong(), buf.readUtf(), buf.readVarInt(), buf.readLong(),
                    buf.readUtf(), buf.readBoolean(), buf.readUtf()));
        }
        return new TradeLogDataPacket(lines);
    }

    public static void handle(TradeLogDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.openTradeLog(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the viewer's own history. Server side only. */
    public static TradeLogDataPacket build(ShopService shop, UUID viewerId) {
        List<Line> lines = new ArrayList<>();
        for (TradeLogEntry entry : shop.tradesOf(viewerId, 200)) {
            boolean wasSeller = viewerId.equals(entry.sellerId());
            // A server-shop purchase has nobody on the other side, so it gets no name to read from.
            String counterparty = entry.kind() == TradeLogEntry.Kind.SERVER_SHOP
                    ? ""
                    : (wasSeller ? entry.actorName() : entry.sellerName());
            lines.add(new Line(entry.timestamp(),
                    entry.template().getHoverName().getString(),
                    entry.quantity(),
                    entry.totalPrice(),
                    counterparty == null ? "" : counterparty,
                    wasSeller,
                    entry.kind().name()));
        }
        return new TradeLogDataPacket(lines);
    }
}
