package com.example.globalshop.feature.pvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Who has agreed to fight.
 *
 * <h2>Off by default, and it stays off until they say otherwise</h2>
 * A player is not fightable because somebody else decided so. Being able to attack whoever you like
 * would make the setting meaningless, so the flag is per player, it defaults to false, and it is
 * saved — a player who has opted in should not have to opt in again every time they log in.
 *
 * <p>Who they are currently duelling is deliberately <em>not</em> saved. A duel is a moment, not a
 * setting: after a restart there is nobody standing in front of anybody, and restoring a duel would
 * mean two players who happen to log in at the same time could hit each other for no reason they
 * remember agreeing to.
 */
public class PvpSavedData extends SavedData {

    private static final String DATA_NAME = "globalshop_pvp";

    /** Players who have agreed to be fightable. */
    private final java.util.Set<UUID> willing = new java.util.HashSet<>();

    public boolean isWilling(UUID playerId) {
        return willing.contains(playerId);
    }

    /** @return true if the flag changed */
    public boolean setWilling(UUID playerId, boolean value) {
        boolean changed = value ? willing.add(playerId) : willing.remove(playerId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public int size() {
        return willing.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        int index = 0;
        for (UUID id : willing) {
            stored.putString("p" + index, id.toString());
            index++;
        }
        tag.put("willing", stored);
        return tag;
    }

    public static PvpSavedData load(CompoundTag tag) {
        PvpSavedData data = new PvpSavedData();
        CompoundTag stored = tag.getCompound("willing");
        for (String key : stored.getAllKeys()) {
            try {
                data.willing.add(UUID.fromString(stored.getString(key)));
            } catch (IllegalArgumentException notAUuid) {
                // One damaged entry is dropped rather than the whole file.
            }
        }
        return data;
    }

    public static PvpSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(PvpSavedData::load, PvpSavedData::new, DATA_NAME);
    }
}
