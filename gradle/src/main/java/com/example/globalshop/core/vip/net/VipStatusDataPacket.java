package com.example.globalshop.core.vip.net;

import com.example.globalshop.Config;
import com.example.globalshop.core.client.ClientScreens;
import com.example.globalshop.core.market.ShopService;
import com.example.globalshop.core.market.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: the viewer's own membership, and what it costs.
 *
 * <p>Everything the membership window draws is in here — whether it is running, when it ends,
 * whether it renews, and the price and period — so the window never has to reason about a config
 * file it cannot see. The price especially: it is the server's setting, and a client showing its own
 * would be showing a number the player will not be charged.
 */
public class VipStatusDataPacket {

    private final boolean active;
    private final long untilMillis;
    private final boolean autoRenew;
    private final long price;
    private final int days;

    public VipStatusDataPacket(boolean active, long untilMillis, boolean autoRenew, long price,
                               int days) {
        this.active = active;
        this.untilMillis = untilMillis;
        this.autoRenew = autoRenew;
        this.price = price;
        this.days = days;
    }

    public boolean active() {
        return active;
    }

    public long untilMillis() {
        return untilMillis;
    }

    public boolean autoRenew() {
        return autoRenew;
    }

    public long price() {
        return price;
    }

    public int days() {
        return days;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeLong(untilMillis);
        buf.writeBoolean(autoRenew);
        buf.writeLong(price);
        buf.writeVarInt(days);
    }

    public static VipStatusDataPacket decode(FriendlyByteBuf buf) {
        return new VipStatusDataPacket(buf.readBoolean(), buf.readLong(), buf.readBoolean(),
                buf.readLong(), buf.readVarInt());
    }

    public static void handle(VipStatusDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreens.showVipStatus(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Builds the viewer's own status. Server side only. */
    public static VipStatusDataPacket build(ShopService shop, ServerPlayer player) {
        long until = shop.vipUntil(player.getUUID());
        return new VipStatusDataPacket(shop.isVip(player.getUUID()), until,
                shop.vipAutoRenew(player.getUUID()), Config.vipPrice(), Config.vipDays());
    }
}
