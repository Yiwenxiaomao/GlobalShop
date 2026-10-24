package com.example.globalshop.network.packet;

import com.example.globalshop.pvp.PvpService;
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
 * Server to client: the state of the PvP window.
 *
 * <p>The candidate list is built from players who have <em>turned PvP on</em>, not from everyone who
 * is online. Offering a name that cannot be challenged would be offering a button that does nothing,
 * and it would also leak nothing useful — that a player has opted in is exactly the fact this window
 * exists to show.
 *
 * @param willing       whether this viewer has agreed to be fightable
 * @param opponentName  who they are duelling, or empty
 * @param stake         the stake a duel is fought over
 * @param minPercent    the smallest fraction of it a loss can cost
 * @param maxPercent    the largest
 * @param candidates    the players who have PvP on and are not already duelling
 */
public record PvpDataPacket(boolean willing, String opponentName, long stake, int minPercent,
                            int maxPercent, List<String> candidates) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(willing);
        buf.writeUtf(opponentName, 64);
        buf.writeLong(stake);
        buf.writeVarInt(minPercent);
        buf.writeVarInt(maxPercent);
        buf.writeVarInt(candidates.size());
        for (String name : candidates) {
            buf.writeUtf(name, PvpActionPacket.MAX_NAME);
        }
    }

    public static PvpDataPacket decode(FriendlyByteBuf buf) {
        boolean willing = buf.readBoolean();
        String opponentName = buf.readUtf(64);
        long stake = buf.readLong();
        int minPercent = buf.readVarInt();
        int maxPercent = buf.readVarInt();
        int count = buf.readVarInt();
        List<String> candidates = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            candidates.add(buf.readUtf(PvpActionPacket.MAX_NAME));
        }
        return new PvpDataPacket(willing, opponentName, stake, minPercent, maxPercent, candidates);
    }

    public static void handle(PvpDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.client.ClientScreens.showPvp(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the state for one viewer. Server side only. */
    public static PvpDataPacket build(PvpService pvp, ServerPlayer viewer) {
        UUID opponentId = pvp.opponentOf(viewer.getUUID());
        ServerPlayer opponent = opponentId == null
                ? null
                : viewer.getServer().getPlayerList().getPlayer(opponentId);
        List<String> candidates = new ArrayList<>();
        for (ServerPlayer other : viewer.getServer().getPlayerList().getPlayers()) {
            if (other.getUUID().equals(viewer.getUUID())) {
                continue;
            }
            // Only players who have opted in, and only those not already busy.
            if (pvp.isWilling(other.getUUID()) && !pvp.isDuelling(other.getUUID())) {
                candidates.add(other.getGameProfile().getName());
            }
        }
        return new PvpDataPacket(pvp.isWilling(viewer.getUUID()),
                opponent == null ? "" : opponent.getGameProfile().getName(),
                pvp.stake(), pvp.lossMinPercent(), pvp.lossMaxPercent(), candidates);
    }
}
