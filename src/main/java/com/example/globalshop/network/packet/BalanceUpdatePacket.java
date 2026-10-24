package com.example.globalshop.network.packet;

import com.example.globalshop.economy.EconomyServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: your balance changed, redraw the number.
 *
 * <h2>Why this is its own packet</h2>
 * The obvious way to refresh a balance would be to push a fresh {@code ShopDataPacket}. That packet
 * carries the whole market page — the query, the sort, the page number — and it is built from what
 * the <em>client</em> asked for. The server does not remember that, so pushing one would silently
 * reset the player's search and throw them back to page one, in the middle of what they were doing.
 *
 * <p>This carries the one number that changed. The client treats it as an override until the next
 * real list arrives, which is authoritative and replaces it — so a pushed balance can never win
 * over the server's own account of itself.
 *
 * <p>The case that made this necessary: an auction settles while the seller is looking at the
 * market, the money arrives, and the figure on screen keeps showing the old total until the player
 * presses refresh. Having to press refresh to be told you were paid is not acceptable.
 */
public class BalanceUpdatePacket {

    private final long balance;

    public BalanceUpdatePacket(long balance) {
        this.balance = balance;
    }

    public long balance() {
        return balance;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(balance);
    }

    public static BalanceUpdatePacket decode(FriendlyByteBuf buf) {
        return new BalanceUpdatePacket(buf.readLong());
    }

    public static void handle(BalanceUpdatePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.client.ClientScreens.acceptBalance(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Sends this player their current balance. Safe to call for somebody who is offline. */
    public static void push(ServerPlayer player) {
        if (player == null) {
            return;
        }
        long balance = EconomyServices.get(player.getServer()).getBalance(player.getUUID());
        com.example.globalshop.network.ModNetwork.sendToPlayer(player,
                new BalanceUpdatePacket(balance));
    }
}
