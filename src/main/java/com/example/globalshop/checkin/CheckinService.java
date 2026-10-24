package com.example.globalshop.checkin;

import com.example.globalshop.Config;
import com.example.globalshop.economy.EconomyService;
import com.example.globalshop.economy.EconomyServices;
import com.example.globalshop.servershop.ShopClock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The weekly check-in: which day it is, what today pays, and what making up a missed day costs.
 *
 * <h2>The calendar is real, not in-game</h2>
 * Every day here is a real calendar day, taken from {@link ShopClock} — the same clock the market's
 * listing expiry and the auction's end times use, so there is one notion of "now" in the whole mod
 * and the test override that pins the date moves all of them together.
 *
 * <p>That is what makes a server that first opens on a Wednesday behave sensibly: Monday and Tuesday
 * of that week are already in the past, they show grey, and they can be made up for a fee. Nobody is
 * asked to have been online before the server existed.
 *
 * <h2>The fee is flat, and the payout is halved</h2>
 * Making up a day costs a flat {@code checkinMakeupCost} and pays only
 * {@link #makeUpReward(int) half} of what the day was worth. Both halves of that rule exist for the
 * same reason: a made-up day must never be worth more than it costs, or players would skip the
 * lucrative days on purpose and buy them back. With the default numbers the best day of the week
 * returns 175 against a 200 fee, so every day is a net loss to catch up on.
 */
public class CheckinService {

    /** The seven days, Monday first, matching {@link DayOfWeek#getValue()}. */
    public static final int DAYS = 7;

    private final MinecraftServer server;

    public CheckinService(MinecraftServer server) {
        this.server = server;
    }

    private CheckinSavedData data() {
        return CheckinSavedData.get(server);
    }

    /** Today's real date, from the mod's single clock. */
    public LocalDate today() {
        return ShopClock.today();
    }

    /**
     * The key for the week a date falls in: the Monday of that week.
     *
     * <p>Monday rather than the locale's first day of the week, because the panel is labelled
     * Monday to Sunday and the two must agree. A player in a country whose week starts on Sunday
     * would otherwise see the wrong column highlighted.
     */
    public static String weekKey(LocalDate date) {
        LocalDate monday = date.with(WeekFields.ISO.dayOfWeek(), 1L);
        return monday.toString();
    }

    /** 1 for Monday through 7 for Sunday, matching {@link DayOfWeek#getValue()}. */
    public static int dayOf(LocalDate date) {
        return date.getDayOfWeek().getValue();
    }

    /** What a given day of the week pays: day 1 pays one step, day 7 pays seven. */
    public long rewardFor(int day) {
        return Config.checkinDailyReward() * Math.max(1, Math.min(DAYS, day));
    }

    /**
     * What a made-up day actually pays: half of what it would have paid on the day.
     *
     * <p>The other half is destroyed along with the fee. That is what makes catching up a sink
     * rather than a trick: with a flat fee against a reward that grows by a step each day, the last
     * days of the week would otherwise pay more than the fee costs, and deliberately skipping
     * Sunday and buying it back would be profitable. Halving the payout closes that — the best day
     * of the week returns 175 against a 200 fee, so no day of the week is worth missing.
     */
    public long makeUpReward(int day) {
        return rewardFor(day) / 2;
    }

    /** The flat fee for making up a missed day. */
    public long makeupCost() {
        return Config.checkinMakeupCost();
    }

    /**
     * Clears a player's week, so every day is available again.
     *
     * <p>An operator tool rather than part of the normal flow: a week is meant to be spent once, and
     * this is for putting right a mistake.
     */
    public void reset(java.util.UUID playerId) {
        CheckinSavedData data = data();
        synchronized (data) {
            data.clear(playerId);
        }
    }

    /** This player's record for the current week. Never stored by reading it. */
    public CheckinSavedData.Record record(ServerPlayer player) {
        return data().record(player.getUUID(), weekKey(today()));
    }

    /**
     * Whether a day can still be collected.
     *
     * @return {@code SUCCESS} when it can, or {@code null} when it cannot, with the reason
     */
    public CheckinResult collectable(ServerPlayer player, int day) {
        if (day < 1 || day > DAYS) {
            return CheckinResult.NO_SUCH_DAY;
        }
        CheckinSavedData.Record record = record(player);
        if (record.isClaimed(day)) {
            return CheckinResult.ALREADY_CLAIMED;
        }
        int today = dayOf(today());
        if (day > today) {
            // A day that has not happened yet cannot be claimed, and cannot be made up either: the
            // fee buys a day that was missed, not a day brought forward.
            return CheckinResult.NOT_YET;
        }
        // A day that is neither today nor past is impossible, so anything left is either today's
        // (free) or a past day (fee). The caller decides which by comparing with today().
        return CheckinResult.SUCCESS;
    }

    /** Whether collecting this day costs the make-up fee. */
    public boolean costsFee(ServerPlayer player, int day) {
        return day < dayOf(today());
    }

    /**
     * Collects a day, charging the fee when it is a make-up.
     *
     * <p>Validation happens first and the money moves before the claim is recorded, so a failure at
     * either step leaves the player where they started rather than paying for nothing.
     */
    public CheckinResult collect(ServerPlayer player, int day) {
        CheckinResult allowed = collectable(player, day);
        if (!allowed.isSuccess()) {
            return allowed;
        }
        boolean makeUp = costsFee(player, day);
        long fee = makeUp ? makeupCost() : 0L;
        EconomyService economy = EconomyServices.get(server);
        economy.ensureAccount(player, Config.initialBalance());
        if (fee > 0L) {
            if (!economy.hasEnough(player.getUUID(), fee)) {
                return CheckinResult.CANNOT_AFFORD_MAKEUP;
            }
            if (!economy.removeBalance(player.getUUID(), fee).isSuccess()) {
                return CheckinResult.CANNOT_AFFORD_MAKEUP;
            }
        }
        long reward = makeUp ? makeUpReward(day) : rewardFor(day);
        if (!economy.addBalance(player.getUUID(), reward).isSuccess()) {
            // The reward could not be paid, so the fee must not be kept either.
            if (fee > 0L) {
                economy.addBalance(player.getUUID(), fee);
            }
            return CheckinResult.ECONOMY_ERROR;
        }
        CheckinSavedData data = data();
        synchronized (data) {
            data.markClaimed(player.getUUID(), weekKey(today()), day, makeUp);
        }
        return CheckinResult.SUCCESS;
    }
}
