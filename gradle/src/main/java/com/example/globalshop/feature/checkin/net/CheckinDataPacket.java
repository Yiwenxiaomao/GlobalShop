package com.example.globalshop.feature.checkin.net;

import com.example.globalshop.feature.checkin.CheckinSavedData;
import com.example.globalshop.feature.checkin.CheckinService;
import com.example.globalshop.api.economy.EconomyServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.core.client.ClientScreens;

/**
 * Server to client: the state of this week's check-in.
 *
 * <p>Two bit masks rather than a list of seven states. Which day is today is a single number, and
 * the four states a day can be in are all derivable from it: collected, today, missed, or still to
 * come. Sending the derivation instead would be seven chances to disagree with the server.
 *
 * @param today       1 for Monday through 7 for Sunday
 * @param claimedMask bit {@code day - 1} set when that day has been collected
 * @param madeUpMask  bit {@code day - 1} set when it was collected by paying the fee
 * @param dailyReward what day 1 pays; day N pays N times this
 * @param makeupCost  the flat fee for a missed day
 * @param balance     the player's balance when this was built
 */
public record CheckinDataPacket(int today, int claimedMask, int madeUpMask, long dailyReward,
                                long makeupCost, long balance) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(today);
        buf.writeVarInt(claimedMask);
        buf.writeVarInt(madeUpMask);
        buf.writeLong(dailyReward);
        buf.writeLong(makeupCost);
        buf.writeLong(balance);
    }

    public static CheckinDataPacket decode(FriendlyByteBuf buf) {
        return new CheckinDataPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readLong(), buf.readLong(), buf.readLong());
    }

    public static void handle(CheckinDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.core.client.ClientScreens.showCheckin(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Whether a day has been collected. */
    public boolean claimed(int day) {
        return (claimedMask & (1 << (day - 1))) != 0;
    }

    /** Whether a day was collected by paying the make-up fee. */
    public boolean madeUp(int day) {
        return (madeUpMask & (1 << (day - 1))) != 0;
    }

    /** What a day pays. */
    public long reward(int day) {
        return dailyReward * Math.max(1, Math.min(CheckinService.DAYS, day));
    }

    /**
     * What a made-up day pays: half. The other half is destroyed with the fee, which is what stops
     * missing the best days of the week from being worth more than attending them.
     */
    public long makeUpReward(int day) {
        return reward(day) / 2;
    }

    /** Whether collecting this day now would cost the fee. */
    public boolean costsFee(int day) {
        return day < today;
    }

    /** Whether this day can be collected right now, for either price. */
    public boolean collectable(int day) {
        return day <= today && !claimed(day);
    }

    public static CheckinDataPacket build(CheckinService checkin, ServerPlayer viewer) {
        CheckinSavedData.Record record = checkin.record(viewer);
        return new CheckinDataPacket(CheckinService.dayOf(checkin.today()),
                record.claimedMask(), record.madeUpMask(), checkin.rewardFor(1),
                checkin.makeupCost(),
                EconomyServices.get(viewer.getServer()).getBalance(viewer.getUUID()));
    }
}
