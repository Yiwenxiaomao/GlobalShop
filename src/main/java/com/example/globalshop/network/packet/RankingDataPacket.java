package com.example.globalshop.network.packet;

import com.example.globalshop.client.ClientScreens;
import com.example.globalshop.shop.RankingService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the money ranking, and when it was built.
 *
 * <p>The build time travels with the rows so the screen can say how old the figures are. The
 * ranking is rebuilt at most once every five minutes, so without that line a player looking at
 * a stale board would have no way to tell it apart from a live one.
 */
public class RankingDataPacket {

    /** One line, flattened for the wire. The player's UUID is deliberately not sent. */
    public record Row(int position, String name, long balance) {
    }

    private final List<Row> rows;
    private final long builtAt;

    public RankingDataPacket(List<Row> rows, long builtAt) {
        this.rows = List.copyOf(rows);
        this.builtAt = builtAt;
    }

    public List<Row> rows() {
        return rows;
    }

    public long builtAt() {
        return builtAt;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeVarInt(Math.max(0, row.position()));
            buf.writeUtf(row.name());
            buf.writeLong(row.balance());
        }
        buf.writeLong(builtAt);
    }

    public static RankingDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Row> rows = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            rows.add(new Row(buf.readVarInt(), buf.readUtf(), buf.readLong()));
        }
        return new RankingDataPacket(rows, buf.readLong());
    }

    public static void handle(RankingDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.openRanking(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the payload for a viewer. Server side only. */
    public static RankingDataPacket build(RankingService ranking) {
        List<Row> rows = new ArrayList<>();
        for (RankingService.Row row : ranking.rows()) {
            rows.add(new Row(row.position(), row.name(), row.balance()));
        }
        return new RankingDataPacket(rows, ranking.builtAt());
    }
}
