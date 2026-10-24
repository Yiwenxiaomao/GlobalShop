package com.example.globalshop.core.client;

import com.example.globalshop.core.market.net.ShopViewState;
import com.example.globalshop.core.market.net.ShopActionResultPacket;
import com.example.globalshop.core.market.net.ShopDataPacket;
import com.example.globalshop.core.market.ShopResult;
import java.util.List;
import javax.annotation.Nullable;
import com.example.globalshop.core.market.net.ListingView;

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
        // A real list is the server's own account of the balance, so it replaces any pushed value.
        balanceOverride = -1L;
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

    public static List<com.example.globalshop.core.market.net.ListingView> listings() {
        ShopDataPacket snapshot = data;
        return snapshot == null ? List.of() : snapshot.listings();
    }

    public static ShopViewState view() {
        ShopDataPacket snapshot = data;
        return snapshot == null ? ShopViewState.INITIAL : snapshot.view();
    }

    /**
     * A balance that arrived on its own, which wins over the last list until a new list arrives.
     *
     * <p>Negative means "no override". A full list stays authoritative — it is the server's own
     * account of the balance — so receiving one clears this rather than being overruled by it. The
     * reason it exists: an auction settles while the seller is looking at the market, the money
     * arrives, and the figure on screen would otherwise keep showing the old total until they
     * pressed refresh. Being told you were paid should not need a button.
     */
    private static long balanceOverride = -1L;

    public static long balance() {
        if (balanceOverride >= 0L) {
            return balanceOverride;
        }
        ShopDataPacket snapshot = data;
        return snapshot == null ? 0L : snapshot.viewerBalance();
    }

    /** Applies a balance pushed on its own. */
    public static void overrideBalance(long value) {
        balanceOverride = value;
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
