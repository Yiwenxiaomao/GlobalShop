package com.example.globalshop.feature.title;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyService;
import com.example.globalshop.api.economy.EconomyServices;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Titles: what they cost, what is allowed, and who is wearing one.
 *
 * <h2>Charging</h2>
 * Every save costs, priced per character by {@link TitlePricing}. Nothing is owned: a player who
 * changes their title twelve times pays twelve times. That is deliberate — a title is a decoration
 * that shows next to their name in every message they send, so it is exactly the kind of thing that
 * should drain money out of the economy rather than be bought once and kept forever. Clearing a
 * title is free, so nobody is ever trapped in one they paid for.
 *
 * <p>The money is destroyed, like the market's commission, rather than paid to anyone.
 */
public class TitleService {

    /** The most characters a title may have, before prices even come into it. */
    public static final int MAX_LENGTH = 7;

    private final MinecraftServer server;

    public TitleService(MinecraftServer server) {
        this.server = server;
    }

    private TitleSavedData data() {
        return TitleSavedData.get(server);
    }

    /** The title this player is wearing, or {@code null}. */
    @Nullable
    public String titleOf(java.util.UUID playerId) {
        return data().titleOf(playerId);
    }

    /** What this text would cost right now, and how long it is. */
    public TitlePricing.Quote quote(String text) {
        return TitlePricing.quote(text == null ? "" : text, Config.titleOrdinaryPrice(),
                Config.titleSpecialPrice());
    }

    /**
     * Checks a title without charging for it.
     *
     * <p>Split out from {@link #set} so the window can warn as the player types without the server
     * having to remember a draft, and so a refusal costs nothing.
     */
    public TitleResult validate(String text) {
        if (text == null) {
            return TitleResult.EMPTY;
        }
        int length = 0;
        boolean anyVisible = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            // Format and control characters are refused outright. A section sign would let a player
            // colour their own title and everything after it, and a control code can reorder a line
            // of chat — neither is a decoration.
            int type = Character.getType(codePoint);
            if (type == Character.CONTROL || type == Character.FORMAT
                    || type == Character.UNASSIGNED || type == Character.SURROGATE
                    || type == Character.PRIVATE_USE || codePoint == '\u00a7') {
                return TitleResult.INVALID_CHARACTER;
            }
            if (codePoint != ' ') {
                anyVisible = true;
            }
            length++;
            i += Character.charCount(codePoint);
        }
        if (!anyVisible) {
            return TitleResult.EMPTY;
        }
        if (length > MAX_LENGTH) {
            return TitleResult.TOO_LONG;
        }
        return TitleResult.SUCCESS;
    }

    /**
     * Sets a player's title, charging for it.
     *
     * <p>Validation happens before any money moves, and the charge happens before the title is
     * stored, so a failure at either step leaves the player with what they had.
     */
    public TitleResult set(ServerPlayer player, String text) {
        String trimmed = text == null ? "" : text.trim();
        TitleResult validation = validate(trimmed);
        if (!validation.isSuccess()) {
            return validation;
        }
        String current = titleOf(player.getUUID());
        if (trimmed.equals(current)) {
            // Asking for what they already have is not a purchase.
            return TitleResult.UNCHANGED;
        }
        long price = quote(trimmed).total();
        EconomyService economy = EconomyServices.get(server);
        economy.ensureAccount(player, Config.initialBalance());
        if (!economy.hasEnough(player.getUUID(), price)) {
            return TitleResult.INSUFFICIENT_FUNDS;
        }
        if (!economy.removeBalance(player.getUUID(), price).isSuccess()) {
            return TitleResult.INSUFFICIENT_FUNDS;
        }
        TitleSavedData data = data();
        synchronized (data) {
            data.setTitle(player.getUUID(), trimmed);
        }
        return TitleResult.SUCCESS;
    }

    /** Removes a title. Free, so nobody is stuck with one. */
    public TitleResult clear(ServerPlayer player) {
        if (titleOf(player.getUUID()) == null) {
            return TitleResult.UNCHANGED;
        }
        TitleSavedData data = data();
        synchronized (data) {
            data.setTitle(player.getUUID(), null);
        }
        return TitleResult.SUCCESS;
    }

    /**
     * The name to show for a player: their title in brackets, then their name.
     *
     * <p>Only ever called with a title that has already been validated, so the brackets are the only
     * thing this adds. Returns the plain name when there is no title, which is why callers can use
     * it unconditionally.
     */
    public Component decorate(ServerPlayer player, Component name) {
        String title = titleOf(player.getUUID());
        if (title == null || title.isEmpty()) {
            return name;
        }
        return Component.literal("[" + title + "] ").append(name);
    }
}
