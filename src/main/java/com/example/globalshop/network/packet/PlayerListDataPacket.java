package com.example.globalshop.network.packet;

import com.example.globalshop.client.ClientScreens;
import com.example.globalshop.social.SocialService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: one page of the player directory.
 *
 * <p>Whether each player is online is decided on the server at the moment the page is built, so the
 * red/grey in the window is a fact about the server rather than a guess the client made from a stale
 * player list.
 */
public class PlayerListDataPacket {

    /**
     * One row. The id is needed so clicking it can name a recipient; the name is for the reader.
     *
     * @param duplicateName another account carries this same name, so the window shows a short id
     *                      as well — offline-mode ids are derived from the name, so any difference
     *                      in the name leaves one person with two accounts and one displayed name
     */
    public record Row(UUID id, String name, boolean online, long balance, boolean duplicateName) {
    }

    private final List<Row> rows;
    private final int page;
    private final int pageCount;
    private final int totalMatches;
    private final String query;

    public PlayerListDataPacket(List<Row> rows, int page, int pageCount, int totalMatches,
                                String query) {
        this.rows = List.copyOf(rows);
        this.page = page;
        this.pageCount = pageCount;
        this.totalMatches = totalMatches;
        this.query = query == null ? "" : query;
    }

    public List<Row> rows() {
        return rows;
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        return pageCount;
    }

    public int totalMatches() {
        return totalMatches;
    }

    public String query() {
        return query;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeUUID(row.id());
            buf.writeUtf(row.name(), 32);
            buf.writeBoolean(row.online());
            buf.writeLong(row.balance());
            buf.writeBoolean(row.duplicateName());
        }
        buf.writeVarInt(page);
        buf.writeVarInt(pageCount);
        buf.writeVarInt(totalMatches);
        buf.writeUtf(query, RequestPlayerListPacket.MAX_QUERY);
    }

    public static PlayerListDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Row> rows = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(32), buf.readBoolean(), buf.readLong(),
                    buf.readBoolean()));
        }
        return new PlayerListDataPacket(rows, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(RequestPlayerListPacket.MAX_QUERY));
    }

    public static void handle(PlayerListDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.acceptPlayerList(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds one page. Server side only. */
    public static PlayerListDataPacket build(SocialService social, String query, int page) {
        SocialService.Page found = social.directory(query, page);
        List<Row> rows = new ArrayList<>(found.entries().size());
        for (SocialService.Entry entry : found.entries()) {
            rows.add(new Row(entry.id(), entry.name(), entry.online(), entry.balance(),
                    entry.duplicateName()));
        }
        return new PlayerListDataPacket(rows, found.page(), found.pageCount(), found.totalMatches(),
                query);
    }
}
