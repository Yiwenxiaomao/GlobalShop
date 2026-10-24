package com.example.globalshop.api.feature.net;

import com.example.globalshop.api.event.FeaturePlaceUsedEvent;
import com.example.globalshop.api.feature.FeaturePlace;
import com.example.globalshop.api.feature.FeaturePlaces;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: the player clicked one of page two's places.
 *
 * <p>Only the index travels. Everything else is looked up here, on the server, so a client cannot
 * claim to be using a place that does not exist, or one belonging to somebody else, or a place at
 * all when it has registered nothing.
 *
 * <p>The event is fired before the place runs and can cancel it, which is how an addon refuses
 * something without the place having to know it might be refused.
 */
public class FeaturePlaceUsePacket {

    private final int index;

    public FeaturePlaceUsePacket(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    public static FeaturePlaceUsePacket decode(FriendlyByteBuf buf) {
        return new FeaturePlaceUsePacket(buf.readVarInt());
    }

    public static void handle(FeaturePlaceUsePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            Optional<FeaturePlace> place = FeaturePlaces.at(message.index);
            if (place.isEmpty()) {
                // A place that is not registered, or was registered after this client last heard.
                // Nothing to say and nobody to blame: the window refills the list when it opens.
                return;
            }
            FeaturePlaceUsedEvent event = new FeaturePlaceUsedEvent(sender, message.index, place.get());
            if (MinecraftForge.EVENT_BUS.post(event)) {
                return;
            }
            if (!place.get().isAvailable(sender)) {
                return;
            }
            place.get().use(sender);
        });
        context.setPacketHandled(true);
    }
}
