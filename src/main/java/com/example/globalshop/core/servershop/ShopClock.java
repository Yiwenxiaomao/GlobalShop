package com.example.globalshop.core.servershop;

import com.example.globalshop.GlobalShop;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/**
 * The source of "what day is it" for the server shop's daily recycling quota.
 *
 * <h2>Why this is its own class</h2>
 * The reset must follow the <em>real-world</em> date, per the requirement, and must keep working
 * when the server was switched off across midnight, when the machine's time zone is not UTC,
 * and when an optional network time source is unreachable. Those are four different concerns
 * that all collapse into one question, so they live in one place instead of being spread across
 * the service.
 *
 * <h2>Never on the trading path</h2>
 * A network lookup is never performed while a player is recycling. Asking the network inside a
 * trade would let a slow or unreachable time server stall the server thread, and a failure
 * there would risk losing a trade. Instead the offset between the online clock and the system
 * clock is measured in the background and cached; {@link #nowMillis()} only reads it. If the
 * lookup has never succeeded the offset is zero, which means the system clock is used —
 * exactly the required fallback.
 *
 * <h2>Game time is not involved</h2>
 * Nothing here reads the world's day, the tick count or the uptime. Those stop advancing while
 * the server is down, which is precisely the case the requirement calls out.
 */
public final class ShopClock {

    /** Seconds between the NTP epoch (1900-01-01) and the Unix epoch (1970-01-01). */
    private static final long NTP_TO_UNIX_SECONDS = 2_208_988_800L;

    /** Client request packet: leap indicator 0, version 3, mode 3 (client). */
    private static final byte NTP_REQUEST = 0x1B;
    private static final int NTP_PACKET_SIZE = 48;
    private static final int NTP_TRANSMIT_TIME_OFFSET = 40;

    private static volatile ZoneId zone = ZoneId.systemDefault();
    private static volatile boolean useOnlineTime = false;
    private static volatile String ntpHost = "pool.ntp.org";
    private static volatile int timeoutMs = 3000;

    /**
     * Online clock minus system clock, in milliseconds.
     *
     * <p>Zero means "use the system clock", which is both the configured default and the
     * state after any failure, so there is no separate "online time is broken" branch to get
     * wrong.
     */
    private static final AtomicLong offsetMillis = new AtomicLong(0L);

    /** Guards against overlapping refreshes when one lookup is slow. */
    private static final AtomicBoolean refreshing = new AtomicBoolean(false);

    /** Set only by the self test, so crossing midnight can be exercised without touching the OS. */
    @Nullable
    private static volatile LocalDate dateOverride;

    /**
     * The same hook as an instant.
     *
     * <p>Pinning only the calendar date was not enough. Expiry compares instants, so a pinned
     * date left the real clock deciding whether a lot had timed out — and the market stamped its
     * listings with {@code System.currentTimeMillis()} while reading them back through here, which
     * is two clocks for one question. Now the date pins both, and the market stamps its listings
     * with {@link #nowMillis()}, so a pinned date means the same moment everywhere.
     */
    @Nullable
    private static volatile Long millisOverride;

    private ShopClock() {
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    public static void configure(String timezone, boolean onlineTime, String host, int timeout) {
        ZoneId resolved;
        try {
            resolved = (timezone == null || timezone.isBlank())
                    ? ZoneId.systemDefault()
                    : ZoneId.of(timezone.trim());
        } catch (RuntimeException unknownZone) {
            // A typo in the config must not take the recycling quota down with it.
            GlobalShop.LOGGER.warn("globalshop: unknown timezone '{}' in config; using the system zone {}",
                    timezone, ZoneId.systemDefault(), unknownZone);
            resolved = ZoneId.systemDefault();
        }
        zone = resolved;
        useOnlineTime = onlineTime;
        ntpHost = (host == null || host.isBlank()) ? "pool.ntp.org" : host.trim();
        timeoutMs = Math.max(250, timeout);
    }

    public static ZoneId zone() {
        return zone;
    }

    public static boolean usesOnlineTime() {
        return useOnlineTime;
    }

    public static long offsetMillis() {
        return offsetMillis.get();
    }

    /** True when the last online lookup succeeded, so the offset is meaningful. */
    public static boolean onlineTimeActive() {
        return offsetMillis.get() != 0L || lastLookupSucceeded;
    }

    private static volatile boolean lastLookupSucceeded = false;

    // ------------------------------------------------------------------
    // Reading the date
    // ------------------------------------------------------------------

    /** Current instant: the system clock plus the online offset, or a pinned test instant. */
    public static long nowMillis() {
        Long override = millisOverride;
        return override != null ? override : System.currentTimeMillis() + offsetMillis.get();
    }

    /**
     * Today's real-world date, in the configured zone.
     *
     * <p>This is the only thing the quota compares, and it is a calendar date rather than a
     * day number so a stored value stays readable in the save file and in a bug report.
     */
    public static LocalDate today() {
        LocalDate override = dateOverride;
        if (override != null) {
            return override;
        }
        return Instant.ofEpochMilli(nowMillis()).atZone(zone).toLocalDate();
    }

    /** Today as {@code yyyy-MM-dd}, the form persisted per player. */
    public static String todayIso() {
        return today().toString();
    }

    // ------------------------------------------------------------------
    // Optional online time
    // ------------------------------------------------------------------

    /**
     * Measures the online clock in the background and caches the offset.
     *
     * <p>Returns immediately; the result arrives later, or never. A failure leaves the offset
     * untouched (zero on a fresh server), so trading continues on the system clock and the only
     * consequence is a log line.
     */
    public static void refreshOnlineTimeAsync() {
        if (!useOnlineTime) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(ShopClock::refreshOnlineTimeNow, "globalshop-ntp");
        worker.setDaemon(true);
        worker.start();
    }

    /** Blocking lookup. Public so the self test can drive it; normal code uses the async form. */
    public static boolean refreshOnlineTimeNow() {
        refreshing.set(true);
        try {
            Long online = queryNtp(ntpHost, timeoutMs);
            if (online == null) {
                GlobalShop.LOGGER.warn(
                        "globalshop: online time lookup against {} failed; falling back to the system clock",
                        ntpHost);
                lastLookupSucceeded = false;
                offsetMillis.set(0L);
                return false;
            }
            long offset = online - System.currentTimeMillis();
            offsetMillis.set(offset);
            lastLookupSucceeded = true;
            GlobalShop.LOGGER.info("globalshop: online time from {} differs from the system clock by {} ms",
                    ntpHost, offset);
            return true;
        } catch (RuntimeException unexpected) {
            // Belt and braces: a time source must never be able to break trading.
            GlobalShop.LOGGER.warn("globalshop: online time lookup raised; using the system clock",
                    unexpected);
            lastLookupSucceeded = false;
            offsetMillis.set(0L);
            return false;
        } finally {
            refreshing.set(false);
        }
    }

    /**
     * Minimal SNTP client: one 48-byte request, read the transmit timestamp from the reply.
     *
     * <p>Written by hand rather than pulling in a library because this is the whole protocol
     * for the one field needed, and a dependency for twenty lines would not be worth it.
     *
     * @return the server's clock in Unix milliseconds, or {@code null} on any failure
     */
    @Nullable
    private static Long queryNtp(String host, int timeout) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeout);
            InetAddress address = InetAddress.getByName(host);

            byte[] request = new byte[NTP_PACKET_SIZE];
            request[0] = NTP_REQUEST;
            socket.send(new DatagramPacket(request, request.length, address, 123));

            byte[] response = new byte[NTP_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            if (packet.getLength() < NTP_PACKET_SIZE) {
                return null;
            }

            long seconds = readUnsignedInt(response, NTP_TRANSMIT_TIME_OFFSET);
            long fraction = readUnsignedInt(response, NTP_TRANSMIT_TIME_OFFSET + 4);
            if (seconds == 0L) {
                // A zero transmit timestamp means the server refused or is unsynchronised.
                return null;
            }
            long unixSeconds = seconds - NTP_TO_UNIX_SECONDS;
            long millis = unixSeconds * 1000L + (fraction * 1000L) / 0x1_0000_0000L;
            return millis;
        } catch (Exception failure) {
            // UnknownHostException, SocketTimeoutException, IOException, SecurityException --
            // every one of them means the same thing here.
            GlobalShop.LOGGER.warn("globalshop: NTP query to {} failed: {}", host, failure.toString());
            return null;
        }
    }

    private static long readUnsignedInt(byte[] buffer, int offset) {
        return ((long) (buffer[offset] & 0xFF) << 24)
                | ((long) (buffer[offset + 1] & 0xFF) << 16)
                | ((long) (buffer[offset + 2] & 0xFF) << 8)
                | (buffer[offset + 3] & 0xFF);
    }

    // ------------------------------------------------------------------
    // Test hooks
    // ------------------------------------------------------------------

    /**
     * Forces the date the quota sees.
     *
     * <p>The requirement asks for proof that the tally resets when the real date changes. The
     * self test cannot wait for midnight and must not change the host's clock, so it pins the
     * date instead — the comparison being tested is the same one production runs.
     */
    public static void setDateOverrideForTest(@Nullable LocalDate date) {
        dateOverride = date;
        millisOverride = date == null ? null : date.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Clears any measured offset, so a test starts from the system clock. */
    public static void resetOffsetForTest() {
        offsetMillis.set(0L);
        lastLookupSucceeded = false;
    }

    /**
     * Pins the clock to an exact instant, for tests.
     *
     * <p>{@link #setDateOverrideForTest} pins a whole day, which is enough for "has the recycling
     * quota reset" and not enough for anything that cares about seconds. The auction's anti-sniping
     * rule and its countdown do, and a test that could not place a bid ten seconds before the end
     * could not prove the rule works at all. The calendar date is kept in step, so the two hooks
     * can never disagree about what day it is.
     */
    public static void setMillisOverrideForTest(@Nullable Long millis) {
        millisOverride = millis;
        dateOverride = millis == null
                ? null
                : Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }
}
