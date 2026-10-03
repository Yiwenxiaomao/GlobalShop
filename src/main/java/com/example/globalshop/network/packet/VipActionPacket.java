package com.example.globalshop.network.packet;

import com.example.globalshop.Config;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.shop.ShopResult;
import com.example.globalshop.shop.ShopService;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client to server: open the membership window, buy a period, or change auto-renew.
 *
 * <p>One packet for all three because they share every argument (the player, and which of three
 * things they want). The reply is always the fresh status, so the window is redrawn from what the
 * server actually holds rather than from what the click was expected to do.
 */
public class VipActionPacket {

    /** Just asking for the current status, which is what opening the window does. */
    public static final int STATUS = 0;
    /** Buy one period. */
    public static final int BUY = 1;
    /** Turn auto-renew on or off. */
    public static final int TOGGLE_AUTO = 2;

    private final int action;

    public VipActionPacket(int action) {
        this.action = action;
    }

    public int action() {
        return action;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
    }

    public static VipActionPacket decode(FriendlyByteBuf buf) {
        return new VipActionPacket(buf.readVarInt());
    }

    public static void handle(VipActionPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            ShopService shop = ShopServices.get(sender);
            switch (message.action) {
                case BUY -> {
                    ShopResult result = shop.purchaseVip(sender);
                    if (result.isSuccess()) {
                        long left = shop.vipUntil(sender.getUUID())
                                - com.example.globalshop.servershop.ShopClock.nowMillis();
                        long days = left <= 0L ? 0L : (left + 86_399_999L) / 86_400_000L;
                        sender.sendSystemMessage(Component.translatable("globalshop.vip.bought",
                                days, Config.vipDays(),
                                shop.listingLimitFor(sender.getUUID())));
                    } else {
                        sender.sendSystemMessage(Component.translatable(result.translationKey()));
                    }
                }
                case TOGGLE_AUTO -> {
                    boolean now = !shop.vipAutoRenew(sender.getUUID());
                    shop.setVipAutoRenew(sender.getUUID(), now);
                    sender.sendSystemMessage(Component.translatable(now
                            ? "globalshop.vip.auto_on" : "globalshop.vip.auto_off"));
                }
                default -> {
                    // STATUS: nothing to change, only to report.
                }
            }
            ModNetwork.sendToPlayer(sender, VipStatusDataPacket.build(shop, sender));
        });
        context.setPacketHandled(true);
    }
}
