package com.example.globalshop.client;

import com.example.globalshop.network.packet.RankingDataPacket;
import com.example.globalshop.network.packet.TradeLogDataPacket;
import net.minecraft.client.Minecraft;

/**
 * Opens the mod's plain screens.
 *
 * <p>Kept in the {@code client} package on purpose. The packets that call these live in common
 * code and are loaded on a dedicated server too; only the method bodies here name client classes,
 * and the JVM resolves those lazily, so a server never loads them. Both callers additionally
 * check the side first, so this is belt and braces rather than the only guard.
 */
public final class ClientScreens {

    private ClientScreens() {
    }

    public static void openRanking(RankingDataPacket data) {
        Minecraft.getInstance().setScreen(new RankingScreen(data));
    }

    public static void openTradeLog(TradeLogDataPacket data) {
        Minecraft.getInstance().setScreen(new TradeLogScreen(data));
    }

    public static void openMailbox(com.example.globalshop.network.packet.MailboxDataPacket data) {
        Minecraft.getInstance().setScreen(new MailboxScreen(data));
    }

    /**
     * Feeds a player-list page to the trade window that is already open.
     *
     * <p>The window is a container screen opened by the server, so it does not get "opened" by a
     * packet — the page is handed to whatever is on screen. If the player has closed it in the
     * meantime there is nothing to update, and dropping the page is correct.
     */
    public static void acceptPlayerList(
            com.example.globalshop.network.packet.PlayerListDataPacket data) {
        if (Minecraft.getInstance().screen instanceof TradeScreen screen) {
            screen.acceptPlayerList(data);
        }
    }

    public static void acceptConversation(
            com.example.globalshop.network.packet.ConversationDataPacket data) {
        if (Minecraft.getInstance().screen instanceof TradeScreen screen) {
            // Only if it is the conversation being shown. An offer changing pushes a fresh
            // conversation to both players, and the one reading a different conversation must not be
            // dragged into this one by it.
            screen.acceptConversationIfShowing(data);
        }
    }

    /**
     * Shows the membership status: opens the window, or updates the one already open.
     *
     * <p>Both cases matter — the first reply opens it, and every later one (after buying, or after
     * toggling auto-renew) has to land in the window the player is still looking at.
     */
    public static void showVipStatus(
            com.example.globalshop.network.packet.VipStatusDataPacket data) {
        if (Minecraft.getInstance().screen instanceof VipScreen open) {
            open.accept(data);
        } else {
            Minecraft.getInstance().setScreen(new VipScreen(data));
        }
    }
}
