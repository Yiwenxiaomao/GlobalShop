package com.example.globalshop.feature.title;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who is wearing which title.
 *
 * <p>One map in the overworld's saved data, the same shape as everything else in the mod: written
 * on every change, read on login and on request. A title is a single short string per player, so
 * there is nothing here that needs a database, and losing the file costs a decoration rather than
 * anything a player paid to keep.
 */
public class TitleSavedData extends SavedData {

    private static final String DATA_NAME = "globalshop_titles";

    private final Map<UUID, String> titles = new HashMap<>();

    /** The title this player is wearing, or {@code null} for none. */
    @Nullable
    public String titleOf(UUID playerId) {
        return titles.get(playerId);
    }

    /** Sets or clears a title. An empty value removes the entry rather than storing a blank one. */
    public void setTitle(UUID playerId, @Nullable String title) {
        if (title == null || title.isEmpty()) {
            if (titles.remove(playerId) != null) {
                setDirty();
            }
            return;
        }
        if (!title.equals(titles.put(playerId, title))) {
            setDirty();
        }
    }

    /** How many players are wearing something, for {@code /globalshop status}. */
    public int size() {
        return titles.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        for (Map.Entry<UUID, String> entry : titles.entrySet()) {
            stored.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("titles", stored);
        return tag;
    }

    public static TitleSavedData load(CompoundTag tag) {
        TitleSavedData data = new TitleSavedData();
        CompoundTag stored = tag.getCompound("titles");
        for (String key : stored.getAllKeys()) {
            try {
                data.titles.put(UUID.fromString(key), stored.getString(key));
            } catch (IllegalArgumentException notAUuid) {
                // A hand-edited or half-written file loses that one entry rather than the whole map.
            }
        }
        return data;
    }

    public static TitleSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(TitleSavedData::load, TitleSavedData::new, DATA_NAME);
    }
}
