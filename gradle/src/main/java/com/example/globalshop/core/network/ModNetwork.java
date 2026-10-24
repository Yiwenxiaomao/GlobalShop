package com.example.globalshop.core.network;

import com.example.globalshop.GlobalShop;
import com.example.globalshop.feature.bank.net.BankActionPacket;
import com.example.globalshop.feature.bank.net.BankDataPacket;
import com.example.globalshop.core.social.net.BlacklistPacket;
import com.example.globalshop.api.feature.net.FeaturePlaceUsePacket;
import com.example.globalshop.api.feature.net.FeaturePlacesDataPacket;
import com.example.globalshop.core.social.net.ConversationDataPacket;
import com.example.globalshop.feature.auction.net.AuctionActionPacket;
import com.example.globalshop.feature.auction.net.AuctionCreatePacket;
import com.example.globalshop.feature.auction.net.AuctionDataPacket;
import com.example.globalshop.feature.auction.net.AuctionToastPacket;
import com.example.globalshop.feature.auction.net.AuctionUpdatePacket;
import com.example.globalshop.internal.economy.net.BalanceUpdatePacket;
import com.example.globalshop.feature.checkin.net.CheckinActionPacket;
import com.example.globalshop.feature.checkin.net.CheckinDataPacket;
import com.example.globalshop.core.market.net.CreateListingPacket;
import com.example.globalshop.core.social.net.MailboxActionPacket;
import com.example.globalshop.core.social.net.MailboxDataPacket;
import com.example.globalshop.core.market.net.ModifyListingPacket;
import com.example.globalshop.core.social.net.OpenTradePacket;
import com.example.globalshop.core.market.net.PurchasePacket;
import com.example.globalshop.feature.pvp.net.PvpActionPacket;
import com.example.globalshop.feature.pvp.net.PvpDataPacket;
import com.example.globalshop.feature.ranking.net.RankingDataPacket;
import com.example.globalshop.feature.ranking.net.PlayerListDataPacket;
import com.example.globalshop.core.social.net.RequestConversationPacket;
import com.example.globalshop.feature.auction.net.RequestAuctionsPacket;
import com.example.globalshop.core.social.net.RequestMailboxPacket;
import com.example.globalshop.feature.ranking.net.RequestPlayerListPacket;
import com.example.globalshop.feature.ranking.net.RequestRankingPacket;
import com.example.globalshop.core.servershop.net.RequestServerShopDataPacket;
import com.example.globalshop.core.market.net.RequestShopDataPacket;
import com.example.globalshop.feature.tradelog.net.RequestTradeLogPacket;
import com.example.globalshop.core.market.net.RestockPacket;
import com.example.globalshop.core.social.net.SendDirectMessagePacket;
import com.example.globalshop.core.servershop.net.ServerBuyPacket;
import com.example.globalshop.core.servershop.net.ServerProductAdminPacket;
import com.example.globalshop.core.servershop.net.ServerRecyclePacket;
import com.example.globalshop.feature.teleport.net.TeleportActionPacket;
import com.example.globalshop.feature.teleport.net.TeleportDataPacket;
import com.example.globalshop.feature.title.net.TitleActionPacket;
import com.example.globalshop.feature.title.net.TitleDataPacket;
import com.example.globalshop.core.servershop.net.ServerShopActionResultPacket;
import com.example.globalshop.core.servershop.net.ServerShopDataPacket;
import com.example.globalshop.core.market.net.ShopActionResultPacket;
import com.example.globalshop.core.market.net.ShopDataPacket;
import com.example.globalshop.feature.tradelog.net.TradeLogDataPacket;
import com.example.globalshop.core.social.net.TradeOfferActionPacket;
import com.example.globalshop.core.market.net.UnlistPacket;
import com.example.globalshop.core.market.net.UnlistAllPacket;
import com.example.globalshop.core.vip.net.VipActionPacket;
import com.example.globalshop.core.vip.net.VipStatusDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import com.example.globalshop.core.market.ShopService;

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

    /**
     * The protocol version, checked by Forge on both ends.
     *
     * <p><b>Bump this whenever the shape of any packet changes</b> — a field added, removed or
     * reordered, on either side. It is the only thing that turns a mismatched client and server into
     * a refusal at login. Without a bump they negotiate happily, and the disagreement surfaces later
     * as the client reading one byte past the end of a packet and being dropped with an internal
     * exception — which is exactly what happened when the bank's packet gained its flag.
     *
     * <p>2: BankDataPacket carries whether the reply should open the window or only refresh it.
     */
    private static final String PROTOCOL_VERSION = "2";

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

        // The auction house, on the same channel with its own message types for the same reason.
        CHANNEL.registerMessage(nextMessageId++, RequestAuctionsPacket.class,
                RequestAuctionsPacket::encode, RequestAuctionsPacket::decode, RequestAuctionsPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, AuctionActionPacket.class,
                AuctionActionPacket::encode, AuctionActionPacket::decode, AuctionActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, AuctionCreatePacket.class,
                AuctionCreatePacket::encode, AuctionCreatePacket::decode, AuctionCreatePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, AuctionDataPacket.class,
                AuctionDataPacket::encode, AuctionDataPacket::decode, AuctionDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, AuctionUpdatePacket.class,
                AuctionUpdatePacket::encode, AuctionUpdatePacket::decode, AuctionUpdatePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, AuctionToastPacket.class,
                AuctionToastPacket::encode, AuctionToastPacket::decode, AuctionToastPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, BalanceUpdatePacket.class,
                BalanceUpdatePacket::encode, BalanceUpdatePacket::decode, BalanceUpdatePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, TitleActionPacket.class,
                TitleActionPacket::encode, TitleActionPacket::decode, TitleActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, TitleDataPacket.class,
                TitleDataPacket::encode, TitleDataPacket::decode, TitleDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, CheckinActionPacket.class,
                CheckinActionPacket::encode, CheckinActionPacket::decode, CheckinActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, CheckinDataPacket.class,
                CheckinDataPacket::encode, CheckinDataPacket::decode, CheckinDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, TeleportActionPacket.class,
                TeleportActionPacket::encode, TeleportActionPacket::decode,
                TeleportActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, TeleportDataPacket.class,
                TeleportDataPacket::encode, TeleportDataPacket::decode, TeleportDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, PvpActionPacket.class,
                PvpActionPacket::encode, PvpActionPacket::decode, PvpActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, PvpDataPacket.class,
                PvpDataPacket::encode, PvpDataPacket::decode, PvpDataPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, BankActionPacket.class,
                BankActionPacket::encode, BankActionPacket::decode, BankActionPacket::handle);
        CHANNEL.registerMessage(nextMessageId++, BankDataPacket.class,
                BankDataPacket::encode, BankDataPacket::decode, BankDataPacket::handle);
        // Appended, never inserted: message ids are positional, so a new packet goes on the end or
        // it renumbers every packet after it.
        CHANNEL.registerMessage(nextMessageId++, FeaturePlaceUsePacket.class,
                FeaturePlaceUsePacket::encode, FeaturePlaceUsePacket::decode,
                FeaturePlaceUsePacket::handle);
        CHANNEL.registerMessage(nextMessageId++, FeaturePlacesDataPacket.class,
                FeaturePlacesDataPacket::encode, FeaturePlacesDataPacket::decode,
                FeaturePlacesDataPacket::handle);

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
