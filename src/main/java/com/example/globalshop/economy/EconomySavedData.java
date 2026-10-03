package com.example.globalshop.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side persistence for all balances.
 *
 * <p>This is a {@link SavedData}, so the server owns it: it lives in the world save
 * (for a dedicated server, {@code world/data/globalshop_economy.dat}) and travels
 * with world backups. It is never written to a client, a player's profile, or a
 * config directory, and the client has no API that can reach it.
 *
 * <p>Accounts are keyed by {@link UUID}. Names are recorded only for display and
 * are never used as an identity, so a player renaming cannot lose their money.
 *
 * <p>Access is expected to happen while holding {@code synchronized (data)}: see
 * {@link InternalEconomyService}. The map operations themselves are not
 * individually locked here because the service performs validation and mutation
 * together and must not be interleaved.
 */
public class EconomySavedData extends SavedData {

    /** File name (without extension) inside the world's {@code data} directory. */
    public static final String DATA_NAME = "globalshop_economy";

    private static final String TAG_ACCOUNTS = "Accounts";
    private static final String TAG_ID = "Id";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_NAME = "Name";

    /** uuid -> balance in minor units. An entry with 0 is still an existing account. */
    private final Map<UUID, Long> balances = new HashMap<>();

    /** uuid -> last seen name, for display only. */
    private final Map<UUID, String> names = new HashMap<>();

    public EconomySavedData() {
    }

    // ------------------------------------------------------------------
    // SavedData plumbing
    // ------------------------------------------------------------------

    /** Deserialises the saved file. Unknown or corrupt entries are skipped rather than crashing the load. */
    public static EconomySavedData load(CompoundTag tag) {
        EconomySavedData data = new EconomySavedData();
        ListTag accounts = tag.getList(TAG_ACCOUNTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < accounts.size(); i++) {
            CompoundTag entry = accounts.getCompound(i);
            if (!entry.contains(TAG_ID) || !entry.contains(TAG_AMOUNT, Tag.TAG_LONG)) {
                continue;
            }
            UUID id;
            try {
                id = entry.getUUID(TAG_ID);
            } catch (RuntimeException malformed) {
                continue;
            }
            if (id == null) {
                continue;
            }
            data.balances.put(id, entry.getLong(TAG_AMOUNT));
            if (entry.contains(TAG_NAME, Tag.TAG_STRING)) {
                data.names.put(id, entry.getString(TAG_NAME));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag accounts = new ListTag();
        // Stable ordering keeps the file diff-friendly across saves and backups.
        List<UUID> ids = new ArrayList<>(balances.keySet());
        ids.sort(Comparator.comparing(UUID::toString));
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_ID, id);
            entry.putLong(TAG_AMOUNT, balances.getOrDefault(id, 0L));
            String name = names.get(id);
            if (name != null && !name.isEmpty()) {
                entry.putString(TAG_NAME, name);
            }
            accounts.add(entry);
        }
        tag.put(TAG_ACCOUNTS, accounts);
        return tag;
    }

    /**
     * Fetches the instance for this server, creating an empty one on first use.
     *
     * <p>Always resolves through the overworld's data storage so that every
     * dimension and every player on the server shares one ledger, and so that the
     * data is stored in {@code world/data/}.
     */
    public static EconomySavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(EconomySavedData::load, EconomySavedData::new, DATA_NAME);
    }

    // ------------------------------------------------------------------
    // Read accessors. Public and safe: they cannot change anything.
    // ------------------------------------------------------------------

    /** @return the stored balance, or 0 when there is no account. */
    public long balanceOf(UUID id) {
        Long value = balances.get(id);
        return value == null ? 0L : value;
    }

    public boolean accountExists(UUID id) {
        return balances.containsKey(id);
    }

    public Optional<String> nameOf(UUID id) {
        return Optional.ofNullable(names.get(id));
    }

    /** Number of accounts on file. Used by diagnostics. */
    public int accountCount() {
        return balances.size();
    }

    /**
     * A copy of every account's balance, for the ranking.
     *
     * <p>A copy rather than the live map: the ranking is sorted and sent while other players may
     * be trading, and handing out the map itself would let that sort race a balance change.
     */
    public java.util.Map<UUID, Long> allBalances() {
        return new HashMap<>(balances);
    }

    /**
     * Serialises and re-reads this data, exactly as a server restart does.
     *
     * <p>Used by the self test to prove that balances really survive a save/load
     * cycle rather than merely looking right in memory.
     */
    public static EconomySavedData reload(EconomySavedData existing) {
        return load(existing.save(new CompoundTag()));
    }

    // ------------------------------------------------------------------
    // Mutators. Package private on purpose: only the economy implementation
    // may change a balance, and only while holding the monitor.
    // ------------------------------------------------------------------

    void putBalance(UUID id, long amount) {
        balances.put(id, amount);
    }

    void rememberName(UUID id, @Nullable String name) {
        if (name != null && !name.isBlank()) {
            names.put(id, name);
        }
    }
}
