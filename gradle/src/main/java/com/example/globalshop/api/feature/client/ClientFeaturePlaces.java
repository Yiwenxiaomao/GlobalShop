package com.example.globalshop.api.feature.client;

import com.example.globalshop.api.feature.net.FeaturePlacesDataPacket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.example.globalshop.core.market.client.ShopScreen;

/**
 * The client's copy of page two's registry.
 *
 * <p>Only what the window needs to draw: a title and an icon per index. What a place <em>does</em> is
 * never here — the click sends an index and the server decides, which is what keeps a place from
 * having to be written twice.
 */
public final class ClientFeaturePlaces {

    /** What one place looks like. */
    public record Display(Component title, ItemStack icon) {
    }

    private static final Map<Integer, Display> PLACES = new HashMap<>();

    private ClientFeaturePlaces() {
    }

    /** Replaces the copy. Called when the server sends the registry. */
    public static void accept(List<FeaturePlacesDataPacket.Entry> entries) {
        PLACES.clear();
        for (FeaturePlacesDataPacket.Entry entry : entries) {
            PLACES.put(entry.index(), new Display(entry.title(), entry.icon()));
        }
        // A window that is already open has thirty-two buttons drawn with the old labels.
        if (net.minecraft.client.Minecraft.getInstance().screen
                instanceof ShopScreen screen) {
            screen.refreshPlaceLabels();
        }
    }

    /** @return the place at an index, or {@code null} when nothing is registered there. */
    public static Display at(int index) {
        return PLACES.get(index);
    }

    /** Forgets everything, for a disconnect. */
    public static void clear() {
        PLACES.clear();
    }
}
