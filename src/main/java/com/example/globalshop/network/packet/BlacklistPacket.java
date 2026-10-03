package com.example.globalshop.network.packet;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.shop.ShopServices;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client to server: block or unblock one item.
 *
 * <p>Carries an item id and a direction, nothing else. The id is resolved against the server's
 * own item registry, so an id that names nothing is refused rather than stored — otherwise a
 * client could fill the blacklist with junk that no screen could ever display or remove.
 *
 * <p>Operator only, re-checked here. The button that sends this is hidden for everyone else, but
 * a hidden button is not a permission check.
 */
public class BlacklistPacket {

    private final String itemId;
    private final boolean add;

    public BlacklistPacket(String itemId, boolean add) {
        this.itemId = itemId;
        this.add = add;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(itemId, 128);
        buf.writeBoolean(add);
    }

    public static BlacklistPacket decode(FriendlyByteBuf buf) {
        return new BlacklistPacket(buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(BlacklistPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            // The authoritative check. Nothing about the client's state is trusted.
            if (!sender.hasPermissions(2)) {
                return;
            }
            ResourceLocation id = ResourceLocation.tryParse(message.itemId);
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                return;
            }
            if (message.add) {
                ShopServices.get(sender).blockItem(id.toString());
            } else {
                ShopServices.get(sender).unblockItem(id.toString());
            }
            // Echo a fresh page, which now carries the updated list.
            ModNetwork.sendToPlayer(sender, ShopDataPacket.build(sender,
                    com.example.globalshop.network.ShopViewState.INITIAL.withBlacklist(true)));
        });
        context.setPacketHandled(true);
    }
}
