package com.example.globalshop.network.packet;

import com.example.globalshop.client.ClientServerShopCache;
import com.example.globalshop.economy.EconomyServices;
import com.example.globalshop.menu.ServerShopLayout;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.ServerProductView;
import com.example.globalshop.servershop.ServerShopEntry;
import com.example.globalshop.servershop.ServerShopService;
import com.example.globalshop.servershop.ServerShopServices;
import com.example.globalshop.servershop.ServerShopTab;
import com.example.globalshop.shop.ContainerPool;
import com.example.globalshop.shop.ItemStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client: one page of the server shop, for one page of the window.
 *
 * <p>Carries everything that screen draws in one go — the products, the viewer's balance, the
 * state of their daily allowance, which page they are on, and whether they may manage the
 * catalogue. Sending them together means the window can never show a price from one moment
 * beside a quota from another.
 *
 * <p>The tab is sent back with the data rather than assumed, because the server is what decides
 * which tab a viewer may have: a non-operator asking for the management page is given the sale
 * page instead, and the client has to be told that rather than quietly drawing the wrong one.
 */
public class ServerShopDataPacket {

    /** Products per page, taken from the layout so the two cannot disagree. */
    public static final int PAGE_SIZE = ServerShopLayout.ROWS;

    private final List<ServerProductView> products;
    private final long balance;
    private final int recycledToday;
    private final int dailyLimit;
    private final int page;
    private final int pageCount;
    private final int totalProducts;
    private final ServerShopTab tab;
    private final boolean operator;

    public ServerShopDataPacket(List<ServerProductView> products, long balance, int recycledToday,
                                int dailyLimit, int page, int pageCount, int totalProducts,
                                ServerShopTab tab, boolean operator) {
        this.products = products;
        this.balance = balance;
        this.recycledToday = recycledToday;
        this.dailyLimit = dailyLimit;
        this.page = page;
        this.pageCount = pageCount;
        this.totalProducts = totalProducts;
        this.tab = tab;
        this.operator = operator;
    }

    public List<ServerProductView> products() {
        return products;
    }

    public long balance() {
        return balance;
    }

    public int recycledToday() {
        return recycledToday;
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    /** Items the viewer may still sell back today. */
    public int remainingToday() {
        return Math.max(0, dailyLimit - recycledToday);
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        return pageCount;
    }

    public int totalProducts() {
        return totalProducts;
    }

    /** The page the server actually served, which may differ from the one that was asked for. */
    public ServerShopTab tab() {
        return tab;
    }

    /** Whether the viewer may edit the catalogue. Drives whether the third tab is drawn. */
    public boolean operator() {
        return operator;
    }

    // ------------------------------------------------------------------

    /**
     * Builds a page from the server's own data.
     *
     * <p>Both the tab and the page number are clamped rather than trusted: a catalogue shrinks
     * when an operator removes a line, and a client that asked for page 4 of what is now 2 pages
     * would otherwise be shown an empty list with no way back.
     */
    public static ServerShopDataPacket build(ServerPlayer player, ServerShopTab requestedTab,
                                             int requestedPage) {
        ServerShopService shop = ServerShopServices.get(player);
        boolean operator = shop.isOperator(player);
        ServerShopTab tab = requestedTab.forViewer(operator);

        List<ServerShopEntry> entries = shop.entriesFor(tab);
        int pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(pageCount - 1, requestedPage));

        List<ServerProductView> products = new ArrayList<>();
        int from = page * PAGE_SIZE;
        for (int i = from; i < Math.min(from + PAGE_SIZE, entries.size()); i++) {
            ServerShopEntry entry = entries.get(i);
            int carried = entry.allowBuyback()
                    ? ItemStacks.countMatching(entry.template(),
                            List.of(new ContainerPool(player.getInventory())))
                    : 0;
            products.add(new ServerProductView(entry.template(), entry.sellPrice(),
                    entry.buybackPrice(), entry.bundleSize(), entry.allowBuyback(),
                    entry.purchaseLimit(), entry.purchasedBy(player.getUUID()), carried));
        }

        return new ServerShopDataPacket(products,
                EconomyServices.get(player).getBalance(player.getUUID()),
                shop.recycledToday(player.getUUID()), shop.dailyLimit(), page, pageCount,
                entries.size(), tab, operator);
    }

    /** Builds and sends, which is how every server-side re-sync ends. */
    public static void send(ServerPlayer player, ServerShopTab tab, int page) {
        ModNetwork.sendToPlayer(player, build(player, tab, page));
    }

    // ------------------------------------------------------------------

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(products.size());
        for (ServerProductView product : products) {
            product.encode(buf);
        }
        buf.writeLong(balance);
        buf.writeVarInt(recycledToday);
        buf.writeVarInt(dailyLimit);
        buf.writeVarInt(page);
        buf.writeVarInt(pageCount);
        buf.writeVarInt(totalProducts);
        buf.writeVarInt(tab.ordinal());
        buf.writeBoolean(operator);
    }

    public static ServerShopDataPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ServerProductView> products = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            products.add(ServerProductView.decode(buf));
        }
        return new ServerShopDataPacket(products, buf.readLong(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                ServerShopTab.byOrdinal(buf.readVarInt()), buf.readBoolean());
    }

    public static void handle(ServerShopDataPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientServerShopCache.accept(message));
        context.setPacketHandled(true);
    }
}
