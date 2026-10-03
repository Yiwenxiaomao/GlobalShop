package com.example.globalshop.network;

import com.example.globalshop.shop.ShopSort;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;

/**
 * What the player is currently looking at: search text, ordering, tab and page.
 *
 * <p>This is a <em>display preference</em>, not data. The client sends it along with
 * every request so the server can rebuild exactly the page the player is looking at
 * and echo it back after a purchase. The server validates and clamps every field and
 * never treats it as fact -- the listings it returns always come from the server's
 * own storage.
 *
 * <p>{@link #blacklist} is what makes the operator's blacklist editor work without a second
 * request/response pair: it says "send me the blacklist instead of a market page", so the
 * ordinary page refresh after an edit already carries the new list.
 */
public record ShopViewState(String query, ShopSort sort, boolean mineOnly, boolean blacklist, int page) {

    /** Server-side bound so a malformed or hostile packet cannot ask for a huge string. */
    public static final int MAX_QUERY_LENGTH = 64;

    public static final ShopViewState INITIAL = new ShopViewState("", ShopSort.DEFAULT, false, false, 0);

    /** Removes control characters and clamps the length. */
    public static String sanitizeQuery(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(Math.min(raw.length(), MAX_QUERY_LENGTH));
        for (int i = 0; i < raw.length() && cleaned.length() < MAX_QUERY_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c >= ' ' && c != '\u007F') {
                cleaned.append(c);
            }
        }
        return cleaned.toString().trim();
    }

    /** Never null, never a negative page, never an out-of-range ordering. */
    public ShopViewState validated() {
        return new ShopViewState(
                sanitizeQuery(query),
                sort == null ? ShopSort.DEFAULT : sort,
                mineOnly,
                blacklist,
                Math.max(0, page));
    }

    public ShopViewState withPage(int newPage) {
        return new ShopViewState(query, sort, mineOnly, blacklist, Math.max(0, newPage));
    }

    public ShopViewState withSort(ShopSort newSort) {
        return new ShopViewState(query, newSort, mineOnly, blacklist, 0);
    }

    /** Switches between the operator's blacklist editor and the ordinary market page. */
    public ShopViewState withBlacklist(boolean on) {
        return new ShopViewState(query, sort, mineOnly, on, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(query == null ? "" : query, MAX_QUERY_LENGTH);
        buf.writeUtf(sort == null ? ShopSort.DEFAULT.id() : sort.id(), 32);
        buf.writeBoolean(mineOnly);
        buf.writeBoolean(blacklist);
        buf.writeInt(page);
    }

    public static ShopViewState decode(FriendlyByteBuf buf) {
        String query = buf.readUtf(MAX_QUERY_LENGTH);
        String sortId = buf.readUtf(32).toLowerCase(Locale.ROOT);
        boolean mineOnly = buf.readBoolean();
        boolean blacklist = buf.readBoolean();
        int page = buf.readInt();
        return new ShopViewState(query, ShopSort.byId(sortId), mineOnly, blacklist, page).validated();
    }
}
