package com.example.globalshop.network.packet;

import com.example.globalshop.bank.BankService;
import com.example.globalshop.economy.EconomyServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: both halves of the bank window at once.
 *
 * <p>One packet for both sides because the window shows them side by side and they are not
 * independent: what is owed counts against the limit, and what is deposited is what can be taken
 * out. Sending them apart would give the window two chances to be half right.
 *
 * <p>Rates travel as basis points, the same as the config, so the window can show "0.5% a day" and
 * "2% a game day" without either side doing a division that might disagree with the other.
 *
 * @param wallet                 the player's spendable balance
 * @param deposit                what is in the bank
 * @param loan                   what is owed
 * @param loanLimit              the ceiling on what may be owed at once
 * @param depositBasisPoints     what a deposit earns per real day
 * @param loanBasisPoints        what a loan charges per game day
 */
public record BankDataPacket(long wallet, long deposit, long loan, long loanLimit,
                             long depositBasisPoints, long loanBasisPoints) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(wallet);
        buf.writeLong(deposit);
        buf.writeLong(loan);
        buf.writeLong(loanLimit);
        buf.writeLong(depositBasisPoints);
        buf.writeLong(loanBasisPoints);
    }

    public static BankDataPacket decode(FriendlyByteBuf buf) {
        return new BankDataPacket(buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(),
                buf.readLong(), buf.readLong());
    }

    public static void handle(BankDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.client.ClientScreens.showBank(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** How much room is left under the limit. */
    public long borrowingRoom() {
        return Math.max(0L, loanLimit - loan);
    }

    /** Builds both halves for one viewer. Server side only. */
    public static BankDataPacket build(BankService bank, ServerPlayer viewer) {
        // Brought up to date first, so what the window shows includes interest earned while it was
        // closed. This is also what pays a player who never presses anything.
        bank.accrue(viewer.getUUID());
        return new BankDataPacket(
                EconomyServices.get(viewer.getServer()).getBalance(viewer.getUUID()),
                bank.depositOf(viewer.getUUID()), bank.loanOf(viewer.getUUID()),
                bank.loanLimit(), bank.depositInterestBasisPoints(), bank.loanInterestBasisPoints());
    }
}
