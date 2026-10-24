package com.example.globalshop.api.event;

import com.example.globalshop.api.feature.FeaturePlace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted when a player uses one of page two's places, before the place does anything.
 *
 * <p>Cancelable, so an addon can refuse — a place that teleports, or charges, can be held back by
 * something with a better idea. A canceled event means {@link FeaturePlace#use} is not called.
 *
 * <p>Fired on the server thread. The player is online and really did click.
 *
 * @since 1.1.0
 */
@Cancelable
public class FeaturePlaceUsedEvent extends Event {

    private final ServerPlayer player;
    private final int index;
    private final FeaturePlace place;

    public FeaturePlaceUsedEvent(ServerPlayer player, int index, FeaturePlace place) {
        this.player = player;
        this.index = index;
        this.place = place;
    }

    public ServerPlayer player() {
        return player;
    }

    /** Which of the thirty-two places, in the order the window draws them. */
    public int index() {
        return index;
    }

    public FeaturePlace place() {
        return place;
    }
}
