package com.example.globalshop.api.feature;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The thirty-two places on the second page, and who owns them.
 *
 * <p>GlobalShop's own features claim places by index; an addon does the same. Registration happens
 * once, during mod setup, before any server exists — the registry is a plain static because a place
 * is not per-world state. Which <em>player</em> may use a place is decided by the place itself, at
 * the moment it is used.
 *
 * <p>Indexes run from 0 to {@link #COUNT} - 1, left to right and then top to bottom, matching how
 * the window draws them.
 *
 * <h2>Registering</h2>
 *
 * <pre>{@code
 * FeaturePlaces.register(3, FeaturePlaces.of(
 *         Component.translatable("mymod.place.auctions"),
 *         new ItemStack(Items.GOLD_INGOT),
 *         player -> player.sendSystemMessage(Component.literal("Hello"))));
 * }</pre>
 *
 * <p>The return value says whether it worked. A place that is already taken is not replaced: two
 * mods fighting over one index is a bug in one of them, and silently losing the loser's place would
 * hide it.
 *
 * @since 1.1.0
 */
public final class FeaturePlaces {

    /** How many places the second page has. */
    public static final int COUNT = 32;

    private static final Map<Integer, FeaturePlace> REGISTERED = new TreeMap<>();

    private static final FeaturePlaces INSTANCE = new FeaturePlaces();

    private FeaturePlaces() {
    }

    /** @return the registry itself, for {@link com.example.globalshop.api.GlobalShopApi}. */
    public static FeaturePlaces get() {
        return INSTANCE;
    }

    /**
     * Makes a place out of a title, an icon and an action, so an addon rarely needs a class.
     *
     * <p>All three may be {@code null}, in which case the place is a button that says nothing and
     * does nothing — which is exactly what GlobalShop's own unclaimed places are.
     */
    public static FeaturePlace of(Component title, ItemStack icon, Consumer<ServerPlayer> action) {
        Component safeTitle = title == null ? Component.empty() : title;
        ItemStack safeIcon = icon == null ? ItemStack.EMPTY : icon;
        Consumer<ServerPlayer> safeAction = action == null ? player -> { } : action;
        return new FeaturePlace() {
            @Override
            public Component title() {
                return safeTitle;
            }

            @Override
            public ItemStack icon() {
                return safeIcon;
            }

            @Override
            public void use(ServerPlayer player) {
                safeAction.accept(player);
            }
        };
    }

    /**
     * Claims a place.
     *
     * @param index 0 to {@link #COUNT} - 1, in the order the window draws them
     * @return whether the place was free and is now this place's
     */
    public static synchronized boolean register(int index, FeaturePlace place) {
        Objects.requireNonNull(place, "place");
        if (index < 0 || index >= COUNT || REGISTERED.containsKey(index)) {
            return false;
        }
        REGISTERED.put(index, place);
        return true;
    }

    /** @return whether a place has been claimed. */
    public static synchronized boolean isTaken(int index) {
        return REGISTERED.containsKey(index);
    }

    /** @return the place at an index, if one is registered. */
    public static synchronized Optional<FeaturePlace> at(int index) {
        return Optional.ofNullable(REGISTERED.get(index));
    }

    /**
     * Every registered place, by index and in index order.
     *
     * <p>Ordered because the client is sent this to draw the page: a map that reorders between calls
     * would put a feature under a different button than the one that was clicked.
     */
    public static synchronized Map<Integer, FeaturePlace> all() {
        return Collections.unmodifiableMap(new TreeMap<>(REGISTERED));
    }

    /** How many are claimed. */
    public static synchronized int claimed() {
        return REGISTERED.size();
    }

    /**
     * Forgets every place.
     *
     * <p>For the self-test, which registers places and must not leave them behind for the next run.
     * Not for normal use: a place that vanishes while a player is looking at the page is a bug.
     */
    public static synchronized void clear() {
        REGISTERED.clear();
    }
}
