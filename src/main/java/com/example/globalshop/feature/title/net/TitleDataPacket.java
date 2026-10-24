package com.example.globalshop.feature.title.net;

import com.example.globalshop.Config;
import com.example.globalshop.api.economy.EconomyServices;
import com.example.globalshop.feature.title.TitlePricing;
import com.example.globalshop.feature.title.TitleService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import com.example.globalshop.core.client.ClientScreens;

/**
 * Server to client: everything the title window needs to draw itself.
 *
 * <h2>Why the prices are in here</h2>
 * The window prices what is being typed as it is typed, so it needs the two prices. Reading them
 * from the client's own config would be wrong the moment a server sets something different from the
 * file sitting on the player's disk — the window would quote one number and the server would charge
 * another. They travel with the rest of the state, so the quote on screen is always the server's.
 *
 * @param title          the title this player is wearing, or an empty string
 * @param maxLength      the character limit, from the server
 * @param ordinaryPrice  price of one ordinary character, in the smallest unit
 * @param specialPrice   price of one special character
 * @param balance        the player's balance when this was built
 */
public record TitleDataPacket(String title, int maxLength, long ordinaryPrice, long specialPrice,
                              long balance) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(title, TitleActionPacket.MAX_TEXT);
        buf.writeVarInt(maxLength);
        buf.writeLong(ordinaryPrice);
        buf.writeLong(specialPrice);
        buf.writeLong(balance);
    }

    public static TitleDataPacket decode(FriendlyByteBuf buf) {
        return new TitleDataPacket(buf.readUtf(TitleActionPacket.MAX_TEXT), buf.readVarInt(),
                buf.readLong(), buf.readLong(), buf.readLong());
    }

    public static void handle(TitleDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.example.globalshop.core.client.ClientScreens.showTitle(message);
            }
        });
        context.setPacketHandled(true);
    }

    /** Prices text with the prices this packet carries. Used by the window as the player types. */
    public TitlePricing.Quote quote(String text) {
        return TitlePricing.quote(text, ordinaryPrice, specialPrice);
    }

    /** Builds the current state for one player. Server side only. */
    public static TitleDataPacket build(TitleService titles, ServerPlayer viewer) {
        String current = titles.titleOf(viewer.getUUID());
        return new TitleDataPacket(current == null ? "" : current, TitleService.MAX_LENGTH,
                Config.titleOrdinaryPrice(), Config.titleSpecialPrice(),
                EconomyServices.get(viewer.getServer()).getBalance(viewer.getUUID()));
    }
}
