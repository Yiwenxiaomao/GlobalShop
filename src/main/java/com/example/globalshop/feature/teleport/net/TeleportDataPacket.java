package com.example.globalshop.feature.teleport.net;

import com.example.globalshop.feature.teleport.TeleportService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.core.client.ClientScreens;

/**
 * Server to client: the state of the teleport window.
 *
 * <p>Both directions of a pending request are here, because a player can be on either end of one and
 * the window has to show whichever it is: waiting to be let in, or deciding whether to let somebody
 * in. Times are seconds remaining rather than deadlines, since the client only counts down and the
 * server is the one that decides when a request has lapsed.
 *
 * @param incomingFrom    who is asking to come to you, or empty
 * @param incomingSeconds how long that request has left
 * @param outgoingTo      who you have asked to visit, or empty
 * @param outgoingSeconds how long that request has left
 * @param cooldownSeconds seconds until you may jump randomly again
 * @param minDistance     the closest a random jump lands
 * @param maxDistance     the furthest a random jump lands
 * @param online          the players who are online, for the picker
 */
public record TeleportDataPacket(String incomingFrom, long incomingSeconds,
                                 String outgoingTo, long outgoingSeconds,
                                 long cooldownSeconds, long targetCooldownSeconds, long price,
                                 int minDistance, int maxDistance,
                                 List<String> online) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(incomingFrom, 64);
        buf.writeVarLong(incomingSeconds);
        buf.writeUtf(outgoingTo, 64);
        buf.writeVarLong(outgoingSeconds);
        buf.writeVarLong(cooldownSeconds);
        buf.writeVarLong(targetCooldownSeconds);
        buf.writeLong(price);
        buf.writeVarInt(minDistance);
        buf.writeVarInt(maxDistance);
        buf.writeVarInt(online.size());
        for (String name : online) {
            buf.writeUtf(name, TeleportActionPacket.MAX_NAME);
        }
    }

    public static TeleportDataPacket decode(FriendlyByteBuf buf) {
        String incomingFrom = buf.readUtf(64);
        long incomingSeconds = buf.readVarLong();
        String outgoingTo = buf.readUtf(64);
        long outgoingSeconds = buf.readVarLong();
        long cooldownSeconds = buf.readVarLong();
        long targetCooldownSeconds = buf.readVarLong();
        long price = buf.readLong();
        int minDistance = buf.readVarInt();
        int maxDistance = buf.readVarInt();
        int count = buf.readVarInt();
        List<String> online = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            online.add(buf.readUtf(TeleportActionPacket.MAX_NAME));
        }
        return new TeleportDataPacket(incomingFrom, incomingSeconds, outgoingTo, outgoingSeconds,
                cooldownSeconds, targetCooldownSeconds, price, minDistance, maxDistance, online);
    }

    public static void handle(TeleportDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.core.client.ClientScreens.showTeleport(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the state for one viewer. Server side only. */
    public static TeleportDataPacket build(TeleportService teleports, ServerPlayer viewer) {
        TeleportService.Request in = teleports.incoming(viewer.getUUID());
        TeleportService.Request out = teleports.outgoing(viewer.getUUID());
        long now = System.currentTimeMillis();
        List<String> online = new ArrayList<>();
        for (ServerPlayer other : viewer.getServer().getPlayerList().getPlayers()) {
            if (!other.getUUID().equals(viewer.getUUID())) {
                online.add(other.getGameProfile().getName());
            }
        }
        return new TeleportDataPacket(
                in == null ? "" : in.fromName(), in == null ? 0L : in.secondsLeft(now),
                out == null ? "" : nameOf(viewer, out), out == null ? 0L : out.secondsLeft(now),
                teleports.randomCooldownLeft(viewer.getUUID()),
                teleports.targetCooldownLeft(viewer.getUUID()), teleports.targetPrice(),
                teleports.minDistance(), teleports.maxDistance(), online);
    }

    private static String nameOf(ServerPlayer viewer, TeleportService.Request request) {
        ServerPlayer target = viewer.getServer().getPlayerList().getPlayer(request.toId());
        return target == null ? "?" : target.getGameProfile().getName();
    }
}
