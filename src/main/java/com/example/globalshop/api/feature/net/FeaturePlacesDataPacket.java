package com.example.globalshop.api.feature.net;

import com.example.globalshop.api.feature.FeaturePlace;
import com.example.globalshop.api.feature.FeaturePlaces;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.api.feature.client.ClientFeaturePlaces;
import com.example.globalshop.core.network.ModNetwork;

/**
 * Server to client: which of page two's places have been claimed, and what they look like.
 *
 * <p>Sent when the window opens rather than on login, because the only thing that needs it is the
 * window — and because a client that has not opened it has nothing to draw anyway.
 *
 * <p>The icons travel as item stacks, so a place can show what it is about without an addon having
 * to ship a texture and register a menu for it.
 */
public class FeaturePlacesDataPacket {

    /** One claimed place: where it is and how to draw it. */
    public record Entry(int index, Component title, ItemStack icon) {
    }

    private final List<Entry> entries;

    public FeaturePlacesDataPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public List<Entry> entries() {
        return entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeVarInt(entry.index());
            buf.writeComponent(entry.title());
            buf.writeItem(entry.icon());
        }
    }

    public static FeaturePlacesDataPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readVarInt(), buf.readComponent(), buf.readItem()));
        }
        return new FeaturePlacesDataPacket(entries);
    }

    public static void handle(FeaturePlacesDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.api.feature.client.ClientFeaturePlaces.accept(message.entries());
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the list from the registry. Server side only. */
    public static FeaturePlacesDataPacket build() {
        List<Entry> entries = new ArrayList<>();
        FeaturePlaces.all().forEach((index, place) ->
                entries.add(new Entry(index, place.title(), place.icon())));
        return new FeaturePlacesDataPacket(entries);
    }

    /**
     * Sends the current registry to one player.
     *
     * <p>Send this after an addon registers a place while a player already has the window open. At
     * startup it is not needed: registration happens before any server exists.
     */
    public static void send(ServerPlayer player) {
        com.example.globalshop.core.network.ModNetwork.sendToPlayer(player, build());
    }
}
