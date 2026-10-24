package com.example.globalshop.core.client;

import com.example.globalshop.feature.ranking.net.RankingDataPacket;
import com.example.globalshop.feature.tradelog.net.TradeLogDataPacket;
import net.minecraft.client.Minecraft;
import com.example.globalshop.feature.auction.client.AuctionScreen;
import com.example.globalshop.feature.auction.client.ClientAuction;
import com.example.globalshop.feature.auction.net.AuctionDataPacket;
import com.example.globalshop.feature.auction.net.AuctionToastPacket;
import com.example.globalshop.feature.auction.net.AuctionUpdatePacket;
import com.example.globalshop.feature.bank.client.BankScreen;
import com.example.globalshop.feature.bank.net.BankDataPacket;
import com.example.globalshop.feature.checkin.client.CheckinScreen;
import com.example.globalshop.feature.checkin.net.CheckinDataPacket;
import com.example.globalshop.feature.pvp.client.PvpScreen;
import com.example.globalshop.feature.pvp.net.PvpDataPacket;
import com.example.globalshop.feature.teleport.client.TeleportScreen;
import com.example.globalshop.feature.teleport.net.TeleportDataPacket;
import com.example.globalshop.feature.title.client.TitleScreen;
import com.example.globalshop.feature.title.net.TitleDataPacket;
import com.example.globalshop.core.social.client.MailboxScreen;
import com.example.globalshop.core.social.client.TradeScreen;
import com.example.globalshop.core.social.net.ConversationDataPacket;
import com.example.globalshop.core.social.net.MailboxDataPacket;
import com.example.globalshop.core.vip.client.VipScreen;
import com.example.globalshop.core.vip.net.VipStatusDataPacket;
import com.example.globalshop.feature.ranking.client.RankingScreen;
import com.example.globalshop.feature.ranking.net.PlayerListDataPacket;
import com.example.globalshop.feature.tradelog.client.TradeLogScreen;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;

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

    public static void openMailbox(com.example.globalshop.core.social.net.MailboxDataPacket data) {
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
            com.example.globalshop.feature.ranking.net.PlayerListDataPacket data) {
        if (Minecraft.getInstance().screen instanceof TradeScreen screen) {
            screen.acceptPlayerList(data);
        }
    }

    public static void acceptConversation(
            com.example.globalshop.core.social.net.ConversationDataPacket data) {
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
            com.example.globalshop.core.vip.net.VipStatusDataPacket data) {
        if (Minecraft.getInstance().screen instanceof VipScreen open) {
            open.accept(data);
        } else {
            Minecraft.getInstance().setScreen(new VipScreen(data));
        }
    }

    // ==================================================================
    // The auction house
    // ==================================================================

    /** Opens the auction window. */
    public static void openAuction() {
        Minecraft.getInstance().setScreen(new AuctionScreen());
    }

    /**
     * Stores a fresh auction list and lets the window redraw from it.
     *
     * <p>The cache is updated whether or not a window is open: the list also carries the server's
     * clock, and that reading is worth keeping even if the player closed the window in the moment
     * between asking and being answered.
     */
    public static void acceptAuctionList(
            com.example.globalshop.feature.auction.net.AuctionDataPacket data) {
        ClientAuction.accept(data);
        if (Minecraft.getInstance().screen instanceof AuctionScreen open) {
            open.accept(data);
        }
    }

    /** Applies a live bid: cache always, redraw only if the window is open. */
    public static void acceptAuctionUpdate(
            com.example.globalshop.feature.auction.net.AuctionUpdatePacket update) {
        ClientAuction.accept(update);
        if (Minecraft.getInstance().screen instanceof AuctionScreen open) {
            open.acceptUpdate();
        }
    }

    /** The centred popup. Only ever sent to players who have the window open. */
    public static void showAuctionToast(
            com.example.globalshop.feature.auction.net.AuctionToastPacket toast) {
        if (Minecraft.getInstance().screen instanceof AuctionScreen open) {
            open.showToast(toast);
        }
    }

    /**
     * A balance that changed on its own — an auction settling, a bid being refunded.
     *
     * <p>Both caches are told, because either window can be the one on screen, and the next real
     * list from the server replaces the pushed value.
     */
    public static void acceptBalance(
            com.example.globalshop.internal.economy.net.BalanceUpdatePacket packet) {
        ClientShopCache.overrideBalance(packet.balance());
        ClientAuction.overrideBalance(packet.balance());
    }

    /** Opens the title window, or updates the one already open. */
    public static void showTitle(com.example.globalshop.feature.title.net.TitleDataPacket data) {
        if (Minecraft.getInstance().screen instanceof TitleScreen open) {
            open.accept(data);
        } else {
            Minecraft.getInstance().setScreen(new TitleScreen(data));
        }
    }

    /**
     * Opens the check-in panel, or replaces the one already open.
     *
     * <p>Replaced rather than updated in place: unlike the title window there is nothing being typed
     * here, so the cheapest correct thing is to open the panel again with the new week in it.
     */
    public static void showCheckin(com.example.globalshop.feature.checkin.net.CheckinDataPacket data) {
        Minecraft.getInstance().setScreen(new CheckinScreen(data));
    }

    /** Opens the teleport window, or replaces the one already open with fresher state. */
    public static void showTeleport(
            com.example.globalshop.feature.teleport.net.TeleportDataPacket data) {
        Minecraft.getInstance().setScreen(new TeleportScreen(data));
    }

    /** Opens the PvP window, or replaces the one already open with fresher state. */
    public static void showPvp(com.example.globalshop.feature.pvp.net.PvpDataPacket data) {
        Minecraft.getInstance().setScreen(new PvpScreen(data));
    }

    /** Opens the bank, or replaces the one already open with fresher numbers. */
    /**
     * Updates an open bank window, and does nothing when there is none.
     *
     * <p>The interest sweep sends this to every player, so it has to be harmless for the ones
     * who are not looking at a bank.
     */
    public static void updateBank(com.example.globalshop.feature.bank.net.BankDataPacket data) {
        if (Minecraft.getInstance().screen instanceof BankScreen open) {
            open.apply(data);
        }
    }

    public static void showBank(com.example.globalshop.feature.bank.net.BankDataPacket data) {
        Minecraft.getInstance().setScreen(new BankScreen(data));
    }
}
