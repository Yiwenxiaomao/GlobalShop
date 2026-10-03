package com.example.globalshop.client;

import com.example.globalshop.network.ServerProductView;
import com.example.globalshop.network.packet.ServerShopActionResultPacket;
import com.example.globalshop.network.packet.ServerShopDataPacket;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/**
 * The client's copy of the server shop, as last described by the server.
 *
 * <p>Not authoritative and not treated as such. It is filled only by packets, cleared on
 * disconnect (see {@code ClientEvents}), and never consulted to decide whether something is
 * allowed — the screen greys a button out for the player's benefit, and the server refuses
 * regardless of what the screen believed.
 */
public final class ClientServerShopCache {

    /** How long a trade result stays on screen. */
    private static final long STATUS_MILLIS = 6000L;

    @Nullable
    private static ServerShopDataPacket data;
    @Nullable
    private static Component status;
    @Nullable
    private static String statusKey;
    /** The verdict, taken from the packet rather than guessed from the key. */
    private static boolean statusSuccess;
    private static long statusAt;

    private ClientServerShopCache() {
    }

    /** Called by {@code ServerShopDataPacket} on the client. Public because the packet lives in
     * a different package; nothing else is expected to call it. */
    public static void accept(ServerShopDataPacket packet) {
        data = packet;
    }

    public static void acceptResult(ServerShopActionResultPacket packet) {
        statusKey = packet.statusKey();
        statusSuccess = packet.success();
        status = Component.Serializer.fromJson(packet.messageJson());
        statusAt = System.currentTimeMillis();
    }

    @Nullable
    public static ServerShopDataPacket data() {
        return data;
    }

    public static List<ServerProductView> products() {
        return data == null ? List.of() : data.products();
    }

    public static long balance() {
        return data == null ? 0L : data.balance();
    }

    public static int recycledToday() {
        return data == null ? 0 : data.recycledToday();
    }

    public static int dailyLimit() {
        return data == null ? 0 : data.dailyLimit();
    }

    public static int remainingToday() {
        return data == null ? 0 : data.remainingToday();
    }

    /** The last trade result, or {@code null} once it has aged out. */
    @Nullable
    public static Component status() {
        if (status == null || System.currentTimeMillis() - statusAt > STATUS_MILLIS) {
            return null;
        }
        return status;
    }

    public static boolean statusIsFailure() {
        return statusKey != null && !statusSuccess;
    }

    /**
     * The key of the last trade result, or {@code null} once it has aged out.
     *
     * <p>Used to tell one outcome from the next so a sound is played once per result rather than
     * once per tick.
     */
    @Nullable
    public static String statusKey() {
        if (statusKey == null || System.currentTimeMillis() - statusAt > STATUS_MILLIS) {
            return null;
        }
        return statusKey;
    }

    /** Whether the last trade was accepted. Drives the sound. */
    public static boolean statusSuccess() {
        return statusSuccess;
    }

    /**
     * Throws everything away.
     *
     * <p>Called when leaving a server. This is what makes "the cache is never authoritative"
     * enforceable rather than merely intended: after a disconnect there is nothing left to show,
     * so the next screen asks the server before drawing anything.
     */
    public static void reset() {
        data = null;
        status = null;
        statusKey = null;
        statusSuccess = false;
        statusAt = 0L;
    }
}
