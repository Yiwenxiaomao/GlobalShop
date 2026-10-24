package com.example.globalshop.client;

import com.example.globalshop.network.packet.AuctionDataPacket;
import com.example.globalshop.network.packet.AuctionUpdatePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the auction window is showing, and the clock it counts down against.
 *
 * <h2>The offset is the whole design</h2>
 * Every list and every bid update carries the server's clock at the moment it was sent. On arrival
 * this class stores {@code serverNow - localNow} and throws away the old value. A countdown is then
 * {@code endTime - (localNow + offset)} — the difference between two clocks, applied as a
 * correction, rather than a comparison of the server's timeline with the client's.
 *
 * <p>That is what makes a wrong machine clock harmless. A player in another time zone, a player
 * whose clock is an hour slow, and a player who has just changed their system time all compute the
 * same remaining seconds, because the offset absorbs whatever their clock is doing and is
 * re-measured on every packet. It also means the countdown is recomputed locally every frame and
 * never polled from the server: one measurement per packet is enough to be right for as long as
 * the clocks stay in step, which they do.
 *
 * <p>The residual error is the one-way network latency of the packet that carried the timestamp,
 * typically a few tens of milliseconds. That is under the frame time of the window, so two players
 * looking at the same auction see the same number.
 */
public final class ClientAuction {

    /** Server clock minus local clock, in milliseconds. Re-measured on every packet. */
    private static long offsetMillis;

    private static AuctionDataPacket data;
    private static List<AuctionDataPacket.Row> rows = new ArrayList<>();

    private ClientAuction() {
    }

    /** The server's clock as this client currently understands it. */
    public static long serverNow() {
        return System.currentTimeMillis() + offsetMillis;
    }

    public static long offsetMillis() {
        return offsetMillis;
    }

    /** Re-measures the offset. Called from every packet that carries a server timestamp. */
    private static void calibrate(long serverNowMillis) {
        offsetMillis = serverNowMillis - System.currentTimeMillis();
    }

    public static AuctionDataPacket data() {
        return data;
    }

    public static List<AuctionDataPacket.Row> rows() {
        return rows;
    }

    /**
     * A balance pushed on its own, which wins over the last list until a new list arrives.
     *
     * <p>Same idea as the market's cache: a settlement can pay the seller while they are sitting in
     * this window, and the wallet line has to follow without a refresh. Negative means "no
     * override".
     */
    private static long balanceOverride = -1L;

    public static long balance() {
        if (balanceOverride >= 0L) {
            return balanceOverride;
        }
        return data == null ? 0L : data.viewerBalance();
    }

    public static void overrideBalance(long value) {
        balanceOverride = value;
    }

    /** The seller's held total, from the last list. */
    public static long held() {
        return data == null ? 0L : data.viewerHeld();
    }

    public static void accept(AuctionDataPacket packet) {
        calibrate(packet.serverNowMillis());
        data = packet;
        rows = new ArrayList<>(packet.rows());
        // The list is the server's own account of the balance, so it replaces any pushed value.
        balanceOverride = -1L;
    }

    /**
     * Applies a live bid.
     *
     * <p>If the auction is not in the list it is ignored rather than added: the player is looking at
     * a page this auction is not on — the result of a search, or simply a different page — and
     * inserting it would move every row under their cursor while they were reading it.
     */
    public static void accept(AuctionUpdatePacket update) {
        calibrate(update.serverNowMillis());
        if (update.ended()) {
            rows.removeIf(row -> row.id().equals(update.id()));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            AuctionDataPacket.Row row = rows.get(i);
            if (!row.id().equals(update.id())) {
                continue;
            }
            rows.set(i, new AuctionDataPacket.Row(row.id(), row.item(), row.sellerName(),
                    row.mine(), row.startPrice(), row.minIncrement(), update.topBid(),
                    update.topBidderName(), update.topBid() > 0L, row.buyoutPrice(),
                    update.endTimeMillis(), update.extensions()));
            return;
        }
    }

    /** Milliseconds left on one auction, or a negative number once it is over. */
    public static long remainingMillis(AuctionDataPacket.Row row) {
        return row.endTimeMillis() - serverNow();
    }

    /**
     * Formats a countdown.
     *
     * <p>Seconds below a minute, {@code m:ss} above it, and {@code h:mm:ss} past an hour — the
     * resolution the eye needs at each scale, rather than a fixed format that reads as noise.
     */
    public static String formatRemaining(long millis) {
        if (millis <= 0L) {
            return "0s";
        }
        long totalSeconds = millis / 1000L;
        if (totalSeconds < 60L) {
            return totalSeconds + "s";
        }
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes < 60L) {
            return String.format("%d:%02d", minutes, seconds);
        }
        return String.format("%d:%02d:%02d", minutes / 60L, minutes % 60L, seconds);
    }

    /** Forgets everything, so a new server session cannot inherit a stale offset. */
    public static void clear() {
        data = null;
        rows = new ArrayList<>();
        offsetMillis = 0L;
        balanceOverride = -1L;
    }

    /** Looks up a row by id, for the selection. */
    public static AuctionDataPacket.Row rowById(UUID id) {
        for (AuctionDataPacket.Row row : rows) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }
}
