package com.example.globalshop.network;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.network.packet.BlacklistPacket;
import com.example.globalshop.network.packet.ConversationDataPacket;
import com.example.globalshop.network.packet.CreateListingPacket;
import com.example.globalshop.network.packet.MailboxActionPacket;
import com.example.globalshop.network.packet.MailboxDataPacket;
import com.example.globalshop.network.packet.ModifyListingPacket;
import com.example.globalshop.network.packet.OpenTradePacket;
import com.example.globalshop.network.packet.PurchasePacket;
import com.example.globalshop.network.packet.RankingDataPacket;
import com.example.globalshop.network.packet.PlayerListDataPacket;
import com.example.globalshop.network.packet.RequestConversationPacket;
import com.example.globalshop.network.packet.RequestMailboxPacket;
import com.example.globalshop.network.packet.RequestPlayerListPacket;
import com.example.globalshop.network.packet.RequestRankingPacket;
import com.example.globalshop.network.packet.RequestServerShopDataPacket;
import com.example.globalshop.network.packet.RequestShopDataPacket;
import com.example.globalshop.network.packet.RequestTradeLogPacket;
import com.example.globalshop.network.packet.RestockPacket;
import com.example.globalshop.network.packet.SendDirectMessagePacket;
import com.example.globalshop.network.packet.ServerBuyPacket;
import com.example.globalshop.network.packet.ServerProductAdminPacket;
import com.example.globalshop.network.packet.ServerRecyclePacket;
import com.example.globalshop.network.packet.ServerShopActionResultPacket;
import com.example.globalshop.network.packet.ServerShopDataPacket;
import com.example.globalshop.network.packet.ShopActionResultPacket;
import com.example.globalshop.network.packet.ShopDataPacket;
import com.example.globalshop.network.packet.TradeLogDataPacket;
import com.example.globalshop.network.packet.TradeOfferActionPacket;
import com.example.globalshop.network.packet.UnlistPacket;
import com.example.globalshop.network.packet.UnlistAllPacket;
import com.example.globalshop.network.packet.VipActionPacket;
import com.example.globalshop.network.packet.VipStatusDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's network channel.
 *
 * <h2>Direction and trust</h2>
 * Requests travel client to server and carry only <em>intent</em>: which listing,
 * how many, and the player's current search/sort/page. They never carry a price, a
 * seller, a balance or an item. Responses travel server to client and carry
 * read-only snapshots.
 *
 * <p>Consequently a compromised or hand-written client can ask the server to do
 * things, but it cannot make the server believe anything: every value that ends up
 * in the world save is produced by {@code ShopService} from the server's own data.
 *
 * <p>The protocol version string is checked by Forge on both ends, so a client and
 * server running different builds of this mod refuse to talk rather than
 * misinterpret each other's packets.
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            java.util.Objects.requireNonNull(
                    ResourceLocation.tryBuild(GlobalShop.MODID, "main"), "channel name"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextMessageId = 0;

    private ModNetwork() {
    }

    public static void register() {
        // client -> server
        CHANNEL.registerMessage(nextMessageId++, RequestShopDataPacket.class,
                RequestShopDataPacket::encode, RequestShopDataPacket::decode, RequestShopDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, PurchasePacket.class,
                PurchasePacket::encode, PurchasePacket::decode, PurchasePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, CreateListingPacket.class,
                CreateListingPacket::encode, CreateListingPacket::decode, CreateListingPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ModifyListingPacket.class,
                ModifyListingPacket::encode, ModifyListingPacket::decode, ModifyListingPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, UnlistPacket.class,
                UnlistPacket::encode, UnlistPacket::decode, UnlistPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RestockPacket.class,
                RestockPacket::encode, RestockPacket::decode, RestockPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, UnlistAllPacket.class,
                UnlistAllPacket::encode, UnlistAllPacket::decode, UnlistAllPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RequestRankingPacket.class,
                RequestRankingPacket::encode, RequestRankingPacket::decode, RequestRankingPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RankingDataPacket.class,
                RankingDataPacket::encode, RankingDataPacket::decode, RankingDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RequestTradeLogPacket.class,
                RequestTradeLogPacket::encode, RequestTradeLogPacket::decode, RequestTradeLogPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, TradeLogDataPacket.class,
                TradeLogDataPacket::encode, TradeLogDataPacket::decode, TradeLogDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, BlacklistPacket.class,
                BlacklistPacket::encode, BlacklistPacket::decode, BlacklistPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RequestMailboxPacket.class,
                RequestMailboxPacket::encode, RequestMailboxPacket::decode, RequestMailboxPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, MailboxDataPacket.class,
                MailboxDataPacket::encode, MailboxDataPacket::decode, MailboxDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, MailboxActionPacket.class,
                MailboxActionPacket::encode, MailboxActionPacket::decode, MailboxActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, OpenTradePacket.class,
                OpenTradePacket::encode, OpenTradePacket::decode, OpenTradePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RequestPlayerListPacket.class,
                RequestPlayerListPacket::encode, RequestPlayerListPacket::decode, RequestPlayerListPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, PlayerListDataPacket.class,
                PlayerListDataPacket::encode, PlayerListDataPacket::decode, PlayerListDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, RequestConversationPacket.class,
                RequestConversationPacket::encode, RequestConversationPacket::decode, RequestConversationPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ConversationDataPacket.class,
                ConversationDataPacket::encode, ConversationDataPacket::decode, ConversationDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, SendDirectMessagePacket.class,
                SendDirectMessagePacket::encode, SendDirectMessagePacket::decode, SendDirectMessagePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, TradeOfferActionPacket.class,
                TradeOfferActionPacket::encode, TradeOfferActionPacket::decode, TradeOfferActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, VipActionPacket.class,
                VipActionPacket::encode, VipActionPacket::decode, VipActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, VipStatusDataPacket.class,
                VipStatusDataPacket::encode, VipStatusDataPacket::decode, VipStatusDataPacket::handle);

        // The server shop speaks over the same channel but with its own messages, so a client
        // cannot reach one shop's handler through the other's packet type.
        CHANNEL.registerMessage(nextMessageId++, RequestServerShopDataPacket.class,
                RequestServerShopDataPacket::encode, RequestServerShopDataPacket::decode,
                RequestServerShopDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ServerBuyPacket.class,
                ServerBuyPacket::encode, ServerBuyPacket::decode, ServerBuyPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ServerRecyclePacket.class,
                ServerRecyclePacket::encode, ServerRecyclePacket::decode, ServerRecyclePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ServerProductAdminPacket.class,
                ServerProductAdminPacket::encode, ServerProductAdminPacket::decode,
                ServerProductAdminPacket::handle);

        // server -> client
        CHANNEL.registerMessage(nextMessageId++, ShopDataPacket.class,
                ShopDataPacket::encode, ShopDataPacket::decode, ShopDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ShopActionResultPacket.class,
                ShopActionResultPacket::encode, ShopActionResultPacket::decode, ShopActionResultPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ServerShopDataPacket.class,
                ServerShopDataPacket::encode, ServerShopDataPacket::decode, ServerShopDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, ServerShopActionResultPacket.class,
                ServerShopActionResultPacket::encode, ServerShopActionResultPacket::decode,
                ServerShopActionResultPacket::handle);
    }

    /** Server to one client. */
    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Client to the server. Only ever called from the client. */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
