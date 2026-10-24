package com.example.globalshop.api.feature;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One of the thirty-two places on the second page of the market window's column.
 *
 * <p>A place is a label, an optional icon and something to do when it is clicked. The window draws
 * the label and the icon and sends the click to the server, where {@link #use} runs on the server
 * thread with the player who clicked — so a place never has to defend itself against a client that
 * lies about who it is, because the client never says.
 *
 * <p>Use {@link FeaturePlaces#of(Component, ItemStack, java.util.function.Consumer)} to make one
 * without writing a class.
 *
 * @since 1.1.0
 */
public interface FeaturePlace {

    /** What the place is called. Shown on the button, and on hover when there is more to say. */
    Component title();

    /**
     * Runs when a player uses this place.
     *
     * <p>On the server thread, with a player who is online and whose click this genuinely was. The
     * place is responsible for whatever it opens or changes; the registry only guarantees that the
     * call arrives.
     */
    void use(ServerPlayer player);

    /**
     * The icon drawn on the button, or {@link ItemStack#EMPTY} for a plain button.
     *
     * <p>Copied before it is drawn, so a place may return the same stack every time.
     */
    default ItemStack icon() {
        return ItemStack.EMPTY;
    }

    /**
     * Whether the place is usable right now, for a player.
     *
     * <p>Drawn dimmed when this is false, and not clickable. The default is always usable, which is
     * right for a place that has nothing to wait for.
     */
    default boolean isAvailable(ServerPlayer player) {
        return true;
    }
}
