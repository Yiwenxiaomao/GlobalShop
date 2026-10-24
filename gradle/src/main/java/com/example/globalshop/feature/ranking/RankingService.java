package com.example.globalshop.feature.ranking;

import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The money ranking: every account on file, richest first.
 *
 * <p>Rebuilt at most once every {@link #REFRESH_MILLIS}. The requirement is a ranking that
 * updates every five minutes. Doing that by refreshing a cache rather than by pushing updates
 * from the server means a hundred players opening the window cause one rebuild rather than a
 * hundred, nobody is sent anything they did not ask for, and a player who never opens the
 * window costs nothing at all.
 */
public final class RankingService {

    public static final long REFRESH_MILLIS = 5L * 60L * 1000L;

    /**
     * A ceiling on how many rows are built and sent.
     *
     * <p>The ranking covers every account ever created, so on a long-lived server it grows without
     * bound. The cap keeps one opened window from becoming a multi-megabyte packet. Raise it if
     * the server genuinely has more players worth naming than this.
     */
    public static final int MAX_ROWS = 500;

    private final MinecraftServer server;

    private List<Row> cache = List.of();
    private long builtAt;
    private boolean built;

    public RankingService(MinecraftServer server) {
        this.server = server;
    }

    /** One line of the ranking. */
    public record Row(int position, UUID playerId, String name, long balance) {
    }

    /** The ranking, rebuilt first if the five minutes are up. */
    public synchronized List<Row> rows() {
        long now = System.currentTimeMillis();
        if (!built || now - builtAt >= REFRESH_MILLIS) {
            cache = build();
            builtAt = now;
            built = true;
        }
        return cache;
    }

    /** When the current ranking was built, so the screen can say how old it is. */
    public synchronized long builtAt() {
        return builtAt;
    }

    /** Forces the next read to rebuild. Used by the reload command and by the self test. */
    public synchronized void invalidate() {
        built = false;
    }

    private List<Row> build() {
        EconomyService economy = EconomyServices.get(server);
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>(economy.allBalances().entrySet());
        // Most money first. Ties break on the name rather than being left to the map's order: a
        // leaderboard that reorders players who hold the same amount looks like it is making the
        // numbers up, even when every figure in it is right.
        sorted.sort(Comparator
                .<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(entry -> economy.getLastKnownName(entry.getKey()).orElse("")));

        List<Row> rows = new ArrayList<>(Math.min(sorted.size(), MAX_ROWS));
        int position = 1;
        for (Map.Entry<UUID, Long> entry : sorted) {
            if (rows.size() >= MAX_ROWS) {
                break;
            }
            UUID id = entry.getKey();
            String name = economy.getLastKnownName(id)
                    .orElseGet(() -> id.toString().substring(0, 8));
            rows.add(new Row(position++, id, name, entry.getValue()));
        }
        return List.copyOf(rows);
    }
}
