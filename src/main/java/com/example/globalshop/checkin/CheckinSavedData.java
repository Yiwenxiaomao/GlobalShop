package com.example.globalshop.checkin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What every player has claimed this week.
 *
 * <h2>One week, not a running streak</h2>
 * The check-in runs on the real calendar week, and each of its seven days stands alone. Missing
 * Monday and claiming Tuesday is fine: Monday simply stays grey and can be made up for a fee. There
 * is no streak to break, which is what the player asked for and also the kinder design — a server
 * that only opens on Wednesday should not punish anybody for Monday.
 *
 * <p>Only the current week is stored. Last week's record is not kept, because there is nothing to
 * do with it: the seven days it describes can no longer be claimed, and holding it would make the
 * file grow for a week nobody can reach.
 */
public class CheckinSavedData extends SavedData {

    private static final String DATA_NAME = "globalshop_checkin";

    /** One player's week. */
    public static final class Record {

        private final String week;
        private int claimedMask;
        private int madeUpMask;

        public Record(String week) {
            this.week = week;
        }

        /** Bit {@code day} - 1 is set when that day has been collected, by either route. */
        public boolean isClaimed(int day) {
            return (claimedMask & (1 << (day - 1))) != 0;
        }

        /** The raw mask, for sending to a client that derives the states itself. */
        public int claimedMask() {
            return claimedMask;
        }

        /** The raw mask of days collected by paying the fee. */
        public int madeUpMask() {
            return madeUpMask;
        }

        /** The week this record belongs to, as the Monday's date. */
        public String week() {
            return week;
        }

        public boolean isMadeUp(int day) {
            return (madeUpMask & (1 << (day - 1))) != 0;
        }

        public void markClaimed(int day, boolean wasMakeUp) {
            claimedMask |= 1 << (day - 1);
            if (wasMakeUp) {
                madeUpMask |= 1 << (day - 1);
            }
        }
    }

    private final Map<UUID, Record> records = new HashMap<>();

    /**
     * This player's record for the given week, or an empty one.
     *
     * <p>Returns a fresh record rather than storing it when the week has turned over, so reading
     * somebody's state never writes to the save file. A week that has moved on is simply not this
     * week's record.
     */
    public Record record(UUID playerId, String week) {
        Record existing = records.get(playerId);
        if (existing == null || !existing.week.equals(week)) {
            return new Record(week);
        }
        return existing;
    }

    /** Stores a claim. Creates the record if this is the player's first claim this week. */
    public void markClaimed(UUID playerId, String week, int day, boolean wasMakeUp) {
        Record existing = records.get(playerId);
        if (existing == null || !existing.week.equals(week)) {
            // The old week is dropped here rather than swept later: this is the moment a player's
            // history is known to be unreadable, and one entry per player is all that is kept.
            existing = new Record(week);
            records.put(playerId, existing);
        }
        existing.markClaimed(day, wasMakeUp);
        setDirty();
    }

    /** How many players have a record for this week, for {@code /globalshop status}. */
    public int size() {
        return records.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        for (Map.Entry<UUID, Record> entry : records.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("week", entry.getValue().week);
            one.putInt("claimed", entry.getValue().claimedMask);
            one.putInt("madeUp", entry.getValue().madeUpMask);
            stored.put(entry.getKey().toString(), one);
        }
        tag.put("records", stored);
        return tag;
    }

    public static CheckinSavedData load(CompoundTag tag) {
        CheckinSavedData data = new CheckinSavedData();
        CompoundTag stored = tag.getCompound("records");
        for (String key : stored.getAllKeys()) {
            try {
                CompoundTag one = stored.getCompound(key);
                Record record = new Record(one.getString("week"));
                record.claimedMask = one.getInt("claimed");
                record.madeUpMask = one.getInt("madeUp");
                data.records.put(UUID.fromString(key), record);
            } catch (IllegalArgumentException notAUuid) {
                // One damaged entry is dropped rather than the whole week's file.
            }
        }
        return data;
    }

    /**
     * Forgets a player's week entirely.
     *
     * <p>For an operator putting right a player who was wrongly denied a day, and for tests: the
     * record is saved, so without this a second run of the suite would find the first run's claims.
     */
    public void clear(UUID playerId) {
        if (records.remove(playerId) != null) {
            setDirty();
        }
    }

    /** @return the stored record or {@code null}, for tests that check the week turned over. */
    @Nullable
    public Record stored(UUID playerId) {
        return records.get(playerId);
    }

    public static CheckinSavedData get(net.minecraft.server.MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(CheckinSavedData::load, CheckinSavedData::new, DATA_NAME);
    }
}
