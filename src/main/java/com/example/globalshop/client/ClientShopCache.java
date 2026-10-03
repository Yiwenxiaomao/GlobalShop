package com.example.globalshop.client;

import com.example.globalshop.network.ShopViewState;
import com.example.globalshop.network.packet.ShopActionResultPacket;
import com.example.globalshop.network.packet.ShopDataPacket;
import com.example.globalshop.shop.ShopResult;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The client's cache of the last page the server sent.
 *
 * <p>This is a <em>display cache</em> and nothing more. It is written only from
 * packets the server produced, it is never consulted to decide what a player may do,
 * and it is discarded on disconnect so a reconnect always starts from the server's
 * state. Every purchase, edit and listing goes to the server and comes back with a
 * fresh page, which replaces this cache wholesale.
 */
public final class ClientShopCache {

    private static volatile ShopDataPacket data;

    /** Translation key suffix of the request in flight, used to phrase the result. */
    private static volatile String pendingAction;

    private static volatile String statusKey;

    /**
     * Whether the last outcome was accepted.
     *
     * <p>A flag rather than a comparison against {@code "globalshop.result.success"}: on success
     * the key is built per action ({@code globalshop.status.purchase.ok}), so comparing it with
     * the generic result key silently never matched and every outcome — success included — was
     * treated as a failure. The packet already carries the result enum; this keeps it.
     */
    private static volatile boolean statusSuccess;

    private static volatile long statusExpiresAt;

    private ClientShopCache() {
    }

    /** Called from the packet handler for a page. */
    public static void accept(ShopDataPacket packet) {
        data = packet;
    }

    /** Called from the packet handler for an action result. */
    public static void acceptResult(ShopActionResultPacket packet) {
        data = packet.refresh();
        ShopResult result = packet.result();
        String action = (pendingAction == null) ? "action" : pendingAction;
        statusKey = result.isSuccess()
                ? "globalshop.status." + action + ".ok"
                : result.translationKey();
        statusSuccess = result.isSuccess();
        statusExpiresAt = System.currentTimeMillis() + 6000L;
        pendingAction = null;
    }

    /** Records which request was just sent, so a success can be worded precisely. */
    public static void expect(String actionKey) {
        pendingAction = actionKey;
    }

    @Nullable
    public static ShopDataPacket data() {
        return data;
    }

    public static List<com.example.globalshop.network.ListingView> listings() {
        ShopDataPacket snapshot = data;
        return snapshot == null ? List.of() : snapshot.listings();
    }

    public static ShopViewState view() {
        ShopDataPacket snapshot = data;
        return snapshot == null ? ShopViewState.INITIAL : snapshot.view();
    }

    public static long balance() {
        ShopDataPacket snapshot = data;
        return snapshot == null ? 0L : snapshot.viewerBalance();
    }

    /** @return the status message to show, or {@code null} once it has expired. */
    @Nullable
    public static String statusKey() {
        String key = statusKey;
        if (key == null || System.currentTimeMillis() > statusExpiresAt) {
            return null;
        }
        return key;
    }

    /** Whether the outcome behind {@link #statusKey()} was accepted. */
    public static boolean statusSuccess() {
        return statusSuccess;
    }

    /** Drops everything, for example when leaving the world. */
    public static void reset() {
        data = null;
        pendingAction = null;
        statusKey = null;
        statusSuccess = false;
        statusExpiresAt = 0L;
    }
}
