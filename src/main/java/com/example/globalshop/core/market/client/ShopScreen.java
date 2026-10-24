package com.example.globalshop.core.market.client;

import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.menu.ShopLayout;
import com.example.globalshop.core.menu.ShopMenu;
import com.example.globalshop.core.market.net.ListingView;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.vip.net.VipActionPacket;
import com.example.globalshop.core.market.net.ShopViewState;
import com.example.globalshop.core.market.net.CreateListingPacket;
import com.example.globalshop.core.market.net.ModifyListingPacket;
import com.example.globalshop.core.market.net.PurchasePacket;
import com.example.globalshop.core.social.net.BlacklistPacket;
import com.example.globalshop.core.social.net.OpenTradePacket;
import com.example.globalshop.core.social.net.RequestMailboxPacket;
import com.example.globalshop.feature.ranking.net.RequestRankingPacket;
import com.example.globalshop.core.market.net.RequestShopDataPacket;
import com.example.globalshop.feature.tradelog.net.RequestTradeLogPacket;
import com.example.globalshop.core.market.net.RestockPacket;
import com.example.globalshop.core.market.net.ShopDataPacket;
import com.example.globalshop.core.market.net.UnlistAllPacket;
import com.example.globalshop.core.market.net.UnlistPacket;
import com.example.globalshop.core.market.ShopSort;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.example.globalshop.feature.bank.net.BankActionPacket;
import com.example.globalshop.feature.checkin.net.CheckinActionPacket;
import com.example.globalshop.feature.pvp.net.PvpActionPacket;
import com.example.globalshop.feature.teleport.net.TeleportActionPacket;
import com.example.globalshop.feature.title.net.TitleActionPacket;
import com.example.globalshop.api.feature.client.ClientFeaturePlaces;
import com.example.globalshop.api.feature.net.FeaturePlaceUsePacket;
import com.example.globalshop.core.client.ClientScreens;
import com.example.globalshop.core.client.ClientShopCache;
import com.example.globalshop.core.market.ItemStacks;

/**
 * The marketplace screen.
 *
 * <p>Everything drawn here comes from {@link ClientShopCache}, which is filled only by
 * packets the server sent. The screen never decides an outcome: it asks the server and
 * displays the reply. The "unit price x quantity = total" line is a preview for the
 * player; the amount actually charged is computed server-side.
 *
 * <p>The market grid is drawn by hand rather than through slots, because a listing is not
 * a stack the player may pick up. Only the listing input slot is a real slot, so there is
 * no way to drag an item out of the market window without paying.
 *
 * <h2>Layout</h2>
 * Every coordinate comes from {@link ShopLayout}, shared with {@link ShopMenu}. The window
 * is a vanilla-style grey panel with proper bevels: raised for things you press, sunken for
 * wells that content sits in. Layout changes should be made in {@code tools/make_gui_preview.py}
 * first, which renders the window as an image; that is how the overlap bugs in the first
 * version of this screen were found.
 *
 * <p>All text is drawn from {@link #render} rather than {@code renderLabels}: the latter
 * runs inside a translation of the screen origin, and keeping every draw call in one
 * coordinate space makes the layout unambiguous.
 */
public class ShopScreen extends AbstractContainerScreen<ShopMenu> {

    // ------------------------------------------------------------------
    // The classic Minecraft window palette. Raising and sinking are done
    // with light/dark edges rather than a texture, so the drawing and the
    // slot positions can never drift apart.
    // ------------------------------------------------------------------
    private static final int C_BLACK = 0xFF000000;
    private static final int C_BODY = 0xFFC6C6C6;
    private static final int C_LIGHT = 0xFFFFFFFF;
    private static final int C_DARK = 0xFF555555;
    private static final int C_INSET = 0xFF8B8B8B;
    private static final int C_INSET_SOFT = 0xFFB2B2B2;
    private static final int C_INSET_DARK = 0xFF373737;
    private static final int C_TEXT = 0xFF303030;
    private static final int C_TEXT_DIM = 0xFF5A5A5A;
    private static final int C_PRICE = 0xFF1B5E20;
    private static final int C_DANGER = 0xFF8B0000;
    /** The result banner's own colours: green for accepted, red for refused. */
    private static final int C_STATUS_OK = 0xFF3FBF3F;
    private static final int C_STATUS_BAD = 0xFFFF5555;
    private static final int C_STATUS_BACKING = 0xE0101010;
    private static final int C_OWNED = 0xFF960096;
    /** VIP reads purple, everything else grey, so the rank is legible at a glance. */
    private static final int C_VIP = 0xFF9600C8;
    private static final int C_RANK_NORMAL = 0xFF7A7A7A;
    /** Half-transparent black laid over an expired lot's icon. */
    private static final int C_EXPIRED_VEIL = 0x99000000;
    private static final int C_SELECT = 0xFFFFD54F;
    private static final int C_HOVER = 0xFFFFFFFF;
    private static final int C_ACCENT = 0xFF3C82BE;
    private static final int C_STATUS = 0xFFFFE070;
    /** Enchantment names get their own colour so they are not mistaken for the item name. */
    private static final int C_ENCHANT = 0xFF5A3E9E;
    private static final int C_DURABLE_OK = 0xFF1B5E20;
    private static final int C_DURABLE_WARN = 0xFF8A6D00;
    private static final int C_DURABLE_LOW = 0xFF8B0000;

    private enum Tab {
        MARKET("globalshop.tab.market"),
        MINE("globalshop.tab.mine"),
        SELL("globalshop.tab.sell"),
        /**
         * The operator's item blacklist.
         *
         * <p>Deliberately absent from the tab row: that row has no space left, and a moderation
         * tool does not belong beside the shopping tabs. It is reached by its own button, which
         * only an operator is shown — and which the server re-checks, because a hidden button is
         * not a permission check.
         */
        BLACKLIST("globalshop.tab.blacklist");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable(key);
        }
    }

    // ---- widgets -----------------------------------------------------
    private EditBox searchBox;
    private EditBox qtyBox;
    private EditBox minePrice;
    private EditBox mineStock;
    private EditBox mineMin;
    private EditBox mineMax;
    private EditBox sellPrice;
    private EditBox sellStock;
    private EditBox sellMin;
    private EditBox sellMax;

    private Button buyButton;
    /** Replaces {@link #buyButton} when the selected lot is the viewer's own. */
    private Button marketUnlistButton;
    private Button qtyMinusButton;
    private Button qtyPlusButton;
    private Button mineSaveButton;
    private Button mineRestockButton;
    private Button mineUnlistButton;
    private Button mineUnlistAllButton;
    /** Window-level navigation, visible in every tab. */
    private Button rankingButton;
    private Button tradeLogButton;
    /** Operator only: opens the item blacklist editor. */
    private Button blacklistButton;
    /** Opens the mailbox, which both shops deliver into when a backpack is full. */
    private Button mailboxButton;
    private Button auctionButton;
    private Button titleButton;
    private Button checkinButton;
    private Button teleportButton;
    private Button pvpButton;
    private Button bankButton;
    private Button pagerBackButton;
    private Button pagerForwardButton;

    /** Which page of the side column is showing. Page one holds everything built so far. */
    private int columnPage;

    /**
     * The buttons that belong to page one of the side column.
     *
     * <p>Kept as a list because showing or hiding a page means touching all of them at once, and
     * nine separate assignments in the refresh method is nine chances to forget one.
     */
    private final java.util.List<Button> columnPageOne = new java.util.ArrayList<>();

    /**
     * What page two hid, so that exactly those can be put back.
     *
     * <p>Not "everything the screen renders": that is what page two hides, and while it is hidden
     * only this list knows which ones were visible beforehand. Without it the widgets this method
     * never touches — the tabs, the search box, the sort buttons — stayed hidden for good, and
     * returning to page one gave a window with no tabs and no search box, which is exactly what
     * happened.
     */
    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget>
            hiddenForPageTwo = new java.util.ArrayList<>();

    /**
     * The thirty-two places on page two, as buttons.
     *
     * <p>One per cell, each covering its cell and the pixel beneath it so no click slips between them
     * and reaches the backpack the cover is hiding. They do nothing yet on purpose: the page exists
     * so that features can be put in these places, and a place that cannot be clicked is not a place.
     */
    private final java.util.List<Button> pageTwoCells = new java.util.ArrayList<>();
    /** Opens the private-trade window: the player directory, conversations and payment. */
    private Button tradeButton;
    private Button blockAddButton;
    private Button blockRemoveButton;
    private Button sellButton;
    private Button tabMarketButton;
    private Button tabMineButton;
    private Button tabSellButton;

    private final List<EditBox> toggleableBoxes = new ArrayList<>();

    // ---- state -------------------------------------------------------
    private Tab tab = Tab.MARKET;
    private String query = "";
    private ShopSort sort = ShopSort.DEFAULT;
    private int page;
    @Nullable
    private UUID selectedId;
    private int quantity = 1;
    /** The blocked item id selected in the editor, which is a registry name rather than a lot. */
    @Nullable
    private String selectedBlocked;

    private String typedQuery = "";
    private long typedQueryAt;
    private ItemStack lastSellInput = ItemStack.EMPTY;

    /** The last status key a sound was played for, so one result makes one noise. */
    private String lastSoundKey;

    /**
     * Whether the server's view has been adopted for this screen.
     *
     * <p>{@link #init()} also runs on a window resize, and the server's view only records
     * whether the public tab or the my-listings tab was showing. Adopting it again on
     * resize would silently move a player sitting on the Sell tab back to Market.
     */
    private boolean adoptedServerView;

    public ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = ShopLayout.GUI_WIDTH;
        this.imageHeight = ShopLayout.GUI_HEIGHT;
        this.titleLabelY = -10_000;
        this.inventoryLabelY = -10_000;
    }

    // ==================================================================
    // Setup
    // ==================================================================

    @Override
    protected void init() {
        super.init();

        ShopDataPacket cached = ClientShopCache.data();
        if (!adoptedServerView && cached != null) {
            this.query = cached.view().query();
            this.sort = cached.view().sort();
            this.page = cached.view().page();
            this.tab = cached.view().mineOnly() ? Tab.MINE : Tab.MARKET;
            this.typedQuery = this.query;
            this.adoptedServerView = true;
        }

        searchBox = addRenderableWidget(new EditBox(this.font, gx(ShopLayout.SEARCH_X),
                gy(ShopLayout.ROW_A_Y), ShopLayout.SEARCH_W, ShopLayout.FIELD_H,
                Component.translatable("globalshop.search")));
        searchBox.setMaxLength(ShopViewState.MAX_QUERY_LENGTH);
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            typedQuery = text;
            typedQueryAt = System.currentTimeMillis();
        });

        tabMarketButton = addRenderableWidget(Button.builder(Tab.MARKET.label(), b -> switchTab(Tab.MARKET))
                .bounds(gx(ShopLayout.TAB_MARKET_X), gy(ShopLayout.ROW_B_Y),
                        ShopLayout.TAB_MARKET_W, ShopLayout.TAB_H).build());
        tabMineButton = addRenderableWidget(Button.builder(Tab.MINE.label(), b -> switchTab(Tab.MINE))
                .bounds(gx(ShopLayout.TAB_MINE_X), gy(ShopLayout.ROW_B_Y),
                        ShopLayout.TAB_MINE_W, ShopLayout.TAB_H).build());
        tabSellButton = addRenderableWidget(Button.builder(Tab.SELL.label(), b -> switchTab(Tab.SELL))
                .bounds(gx(ShopLayout.TAB_SELL_X), gy(ShopLayout.ROW_B_Y),
                        ShopLayout.TAB_SELL_W, ShopLayout.TAB_H).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(gx(ShopLayout.PAGE_PREV_X), gy(ShopLayout.ROW_B_Y),
                        ShopLayout.PAGE_BTN_W, ShopLayout.TAB_H).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(gx(ShopLayout.PAGE_NEXT_X), gy(ShopLayout.ROW_B_Y),
                        ShopLayout.PAGE_BTN_W, ShopLayout.TAB_H).build());

        addRenderableWidget(Button.builder(Component.translatable("globalshop.sort.short.price"),
                b -> togglePriceSort()).bounds(gx(ShopLayout.SORT_X), gy(ShopLayout.ROW_A_Y),
                ShopLayout.SORT_W, ShopLayout.FIELD_H).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.sort.short.time"),
                b -> toggleTimeSort()).bounds(gx(ShopLayout.SORT_X + ShopLayout.SORT_STEP),
                gy(ShopLayout.ROW_A_Y), ShopLayout.SORT_W, ShopLayout.FIELD_H).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.sort.short.seller"),
                b -> setSort(ShopSort.SELLER)).bounds(
                gx(ShopLayout.SORT_X + ShopLayout.SORT_STEP * 2), gy(ShopLayout.ROW_A_Y),
                ShopLayout.SORT_W, ShopLayout.FIELD_H).build());

        addRenderableWidget(Button.builder(Component.translatable("globalshop.button.refresh"),
                b -> requestData()).bounds(gx(ShopLayout.REFRESH_X), gy(ShopLayout.ROW_B_Y),
                ShopLayout.REFRESH_W, ShopLayout.TAB_H).build());

        // ---- market tab: quantity, and either Buy or Unlist ----
        qtyMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> stepQuantity(-1))
                .bounds(gx(ShopLayout.P_QTY_MINUS_X), gy(ShopLayout.P_QTY_Y - 1),
                        ShopLayout.P_QTY_BTN_W, ShopLayout.P_QTY_BTN_W).build());
        qtyBox = addRenderableWidget(intBox(ShopLayout.P_QTY_BOX_X, ShopLayout.P_QTY_Y,
                ShopLayout.P_QTY_BOX_W, 9));
        qtyBox.setValue("1");
        qtyBox.setResponder(text -> parsePositive(text).ifPresent(value -> quantity = clampToInt(value)));
        qtyPlusButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> stepQuantity(1))
                .bounds(gx(ShopLayout.P_QTY_PLUS_X), gy(ShopLayout.P_QTY_Y - 1),
                        ShopLayout.P_QTY_BTN_W, ShopLayout.P_QTY_BTN_W).build());

        buyButton = addRenderableWidget(Button.builder(Component.translatable("globalshop.button.buy"),
                b -> sendPurchase()).bounds(gx(ShopLayout.P_X), gy(ShopLayout.P_BUTTON_Y),
                ShopLayout.P_WIDE_BUTTON_W, ShopLayout.P_BUTTON_H).build());
        // Same bounds as Buy: only one of the two is ever visible, so the market tab
        // always offers exactly one obvious action.
        marketUnlistButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.unlist"), b -> sendUnlist())
                .bounds(gx(ShopLayout.P_X), gy(ShopLayout.P_BUTTON_Y),
                        ShopLayout.P_WIDE_BUTTON_W, ShopLayout.P_BUTTON_H).build());

        // ---- my-listings tab ----
        minePrice = addRenderableWidget(moneyBox(ShopLayout.FIELD_X, ShopLayout.F1_Y, ShopLayout.FIELD_W));
        mineStock = addRenderableWidget(intBox(ShopLayout.FIELD_X, ShopLayout.F2_Y, ShopLayout.FIELD_STOCK_W, 9));
        mineMin = addRenderableWidget(intBox(ShopLayout.ALT_X, ShopLayout.F2_Y, ShopLayout.ALT_W, 9));
        mineMax = addRenderableWidget(intBox(ShopLayout.FIELD_X, ShopLayout.F3_Y, ShopLayout.FIELD_W, 9));
        rankingButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.ranking"), b -> requestRanking())
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.RANKING_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        tradeLogButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.trade_log"), b -> requestTradeLog())
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.TRADE_LOG_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        blacklistButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.blacklist"), b -> toggleBlacklist())
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.BLACKLIST_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        mailboxButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.mailbox"), b -> requestMailbox())
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.MAILBOX_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        tradeButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.trade"), b -> requestTrade())
                .bounds(gx(ShopLayout.TRADE_BUTTON_X), gy(ShopLayout.TRADE_BUTTON_Y),
                        ShopLayout.TRADE_BUTTON_W, ShopLayout.TRADE_BUTTON_H).build());
        // The auction house is its own window rather than a tab of this one, so this only opens it.
        // Nothing is asked of the server to open it either: the new window requests its own list,
        // and carries the clock it needs to count down with.
        auctionButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.auction"),
                        b -> ClientScreens.openAuction())
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.AUCTION_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        // The title window, in the lower-right. It asks the server for the current state rather
        // than opening empty, so the field starts with whatever is already being worn.
        titleButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.title"),
                        b -> ModNetwork.sendToServer(new com.example.globalshop.feature.title.net.TitleActionPacket(
                                com.example.globalshop.feature.title.net.TitleActionPacket.OPEN, "")))
                .bounds(gx(ShopLayout.TITLE_BUTTON_X), gy(ShopLayout.TITLE_BUTTON_Y),
                        ShopLayout.TITLE_BUTTON_W, ShopLayout.TITLE_BUTTON_H).build());
        // The check-in, in the side column's sixth slot — the first of the five that were added
        // when the column was halved to make room for ten features.
        checkinButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.checkin"),
                        b -> ModNetwork.sendToServer(new com.example.globalshop.feature.checkin.net.CheckinActionPacket(
                                com.example.globalshop.feature.checkin.net.CheckinActionPacket.OPEN, 0)))
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.CHECKIN_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        // The teleport window, seventh in the column. Like the check-in it asks the server for its
        // state rather than opening empty, because what it shows is a request that already exists.
        teleportButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.teleport"),
                        b -> ModNetwork.sendToServer(new com.example.globalshop.feature.teleport.net.TeleportActionPacket(
                                com.example.globalshop.feature.teleport.net.TeleportActionPacket.OPEN, "")))
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.TELEPORT_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        // The eighth slot: PvP. It asks the server for its state, because the answer depends on
        // who else has opted in right now.
        pvpButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.pvp"),
                        b -> ModNetwork.sendToServer(new com.example.globalshop.feature.pvp.net.PvpActionPacket(
                                com.example.globalshop.feature.pvp.net.PvpActionPacket.OPEN, "")))
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.PVP_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        columnPageOne.add(pvpButton);
        // The ninth slot: the bank. It asks for its numbers rather than opening empty, because
        // interest may have accrued since the last time it was looked at.
        bankButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.bank"),
                        b -> ModNetwork.sendToServer(new com.example.globalshop.feature.bank.net.BankActionPacket(
                                com.example.globalshop.feature.bank.net.BankActionPacket.OPEN, "")))
                .bounds(gx(ShopLayout.SIDE_X), gy(ShopLayout.BANK_BUTTON_Y),
                        ShopLayout.SIDE_W, ShopLayout.SIDE_H).build());
        columnPageOne.add(bankButton);
        // The tenth slot: a pager split down the middle. Back on the left, forward on the right.
        pagerBackButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        b -> switchColumnPage(-1))
                .bounds(gx(ShopLayout.PAGER_BACK_X), gy(ShopLayout.PAGER_BUTTON_Y),
                        ShopLayout.PAGER_HALF_W, ShopLayout.SIDE_H).build());
        pagerForwardButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        b -> switchColumnPage(1))
                .bounds(gx(ShopLayout.PAGER_FORWARD_X), gy(ShopLayout.PAGER_BUTTON_Y),
                        ShopLayout.PAGER_HALF_W, ShopLayout.SIDE_H).build());
        columnPageOne.add(rankingButton);
        columnPageOne.add(tradeLogButton);
        columnPageOne.add(blacklistButton);
        columnPageOne.add(mailboxButton);
        columnPageOne.add(auctionButton);
        columnPageOne.add(checkinButton);
        columnPageOne.add(teleportButton);
        buildPageTwoCells();
        blockAddButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.block_add"), b -> blockFromSlot())
                .bounds(gx(ShopLayout.BLOCK_ADD_X), gy(ShopLayout.P_BUTTON_Y),
                        ShopLayout.BLOCK_ADD_W, ShopLayout.P_BUTTON_H).build());
        blockRemoveButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.block_remove"), b -> unblockSelected())
                .bounds(gx(ShopLayout.BLOCK_REMOVE_X), gy(ShopLayout.P_BUTTON_Y),
                        ShopLayout.BLOCK_REMOVE_W, ShopLayout.P_BUTTON_H).build());
        mineRestockButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.restock"), b -> sendRestock())
                .bounds(gx(ShopLayout.MINE_RESTOCK_X), gy(ShopLayout.P_BUTTON_Y),
                        ShopLayout.MINE_RESTOCK_W, ShopLayout.P_BUTTON_H).build());
        mineUnlistAllButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.button.unlist_all"), b -> sendUnlistAll())
                .bounds(gx(ShopLayout.MINE_UNLIST_ALL_X), gy(ShopLayout.P_BUTTON_Y),
                        ShopLayout.MINE_UNLIST_ALL_W, ShopLayout.P_BUTTON_H).build());
        mineSaveButton = addRenderableWidget(Button.builder(Component.translatable("globalshop.button.save"),
                b -> sendModify()).bounds(gx(ShopLayout.MINE_SAVE_X), gy(ShopLayout.P_BUTTON_Y),
                ShopLayout.MINE_SAVE_W, ShopLayout.P_BUTTON_H).build());
        mineUnlistButton = addRenderableWidget(Button.builder(Component.translatable("globalshop.button.unlist"),
                b -> sendUnlist()).bounds(gx(ShopLayout.MINE_UNLIST_X), gy(ShopLayout.P_BUTTON_Y),
                ShopLayout.MINE_UNLIST_W, ShopLayout.P_BUTTON_H).build());

        // ---- sell tab ----
        sellPrice = addRenderableWidget(moneyBox(ShopLayout.FIELD_X, ShopLayout.F1_Y, ShopLayout.FIELD_W));
        sellStock = addRenderableWidget(intBox(ShopLayout.FIELD_X, ShopLayout.F2_Y, ShopLayout.FIELD_STOCK_W, 9));
        sellMin = addRenderableWidget(intBox(ShopLayout.ALT_X, ShopLayout.F2_Y, ShopLayout.ALT_W, 9));
        sellMax = addRenderableWidget(intBox(ShopLayout.FIELD_X, ShopLayout.F3_Y, ShopLayout.FIELD_W, 9));
        sellButton = addRenderableWidget(Button.builder(Component.translatable("globalshop.button.list"),
                b -> sendCreate()).bounds(gx(ShopLayout.P_X), gy(ShopLayout.P_BUTTON_Y),
                ShopLayout.SELL_BUTTON_W, ShopLayout.P_BUTTON_H).build());

        toggleableBoxes.clear();
        toggleableBoxes.add(qtyBox);
        toggleableBoxes.add(minePrice);
        toggleableBoxes.add(mineStock);
        toggleableBoxes.add(mineMin);
        toggleableBoxes.add(mineMax);
        toggleableBoxes.add(sellPrice);
        toggleableBoxes.add(sellStock);
        toggleableBoxes.add(sellMin);
        toggleableBoxes.add(sellMax);

        if (cached == null) {
            requestData();
        }
        refreshWidgets();
    }

    private EditBox intBox(int relX, int relY, int width, int maxLength) {
        EditBox box = new EditBox(this.font, gx(relX), gy(relY), width,
                ShopLayout.FIELD_H_SMALL, Component.empty());
        box.setMaxLength(maxLength);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        return box;
    }

    private EditBox moneyBox(int relX, int relY, int width) {
        EditBox box = new EditBox(this.font, gx(relX), gy(relY), width,
                ShopLayout.FIELD_H_SMALL, Component.empty());
        box.setMaxLength(20);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9.,]{0,20}"));
        return box;
    }

    /** Last mouse position, for the header's hover highlight. */
    private int lastMouseX;
    private int lastMouseY;

    private int gx(int relative) {
        return leftPos + relative;
    }

    private int gy(int relative) {
        return topPos + relative;
    }

    // ==================================================================
    // State
    // ==================================================================

    private void switchTab(Tab newTab) {
        if (tab == newTab) {
            return;
        }
        tab = newTab;
        page = 0;
        refreshWidgets();
        requestData();
    }

    /**
     * Shows exactly the widgets the current tab and selection need.
     *
     * <p>In particular the market tab offers <em>either</em> Buy or Unlist: when the
     * selected lot belongs to the viewer, buying it is meaningless and taking it down is
     * the only sensible action, so the big obvious button becomes Unlist. Earlier versions
     * only offered unlisting from a separate tab, which left players who had just listed
     * something by mistake with no visible way to undo it.
     */
    /**
     * Page two of the side column: sixteen empty places for the next sixteen features.
     *
     * <p>Drawn as empty slots rather than as sixteen disabled buttons, because an empty slot is what
     * it is — somewhere a feature is going to go. A greyed-out button would claim there is something
     * there that cannot be pressed.
     */
    private void paintPageTwoCover(net.minecraft.client.gui.GuiGraphics graphics) {
        // Painted over, not merely un-widgetted. Hiding widgets handles the buttons and the boxes,
        // but the item grid, the listing panel and the backpack are drawn rather than being widgets,
        // so they survived the page turn and the second page came out as a jumble of both.
        //
        // The whole window except the two arrow buttons. An earlier version stopped at the pager's
        // row and left the bottom row of slots showing their top halves, because the hotbar sits a
        // little lower than the pager does; covering everything but the arrows cannot be wrong
        // whatever the slot positions turn out to be.
        int pagerTop = gy(ShopLayout.PAGER_BUTTON_Y) - 2;
        // The window's real bottom, not imageHeight's idea of it. The first version trusted that
        // field and the bottom row of slots kept showing, so it is clearly smaller than the window;
        // taking the larger of the two cannot be wrong either way.
        int pagerBottom = Math.max(topPos + this.imageHeight,
                gy(ShopLayout.PAGER_BUTTON_Y + ShopLayout.SIDE_H) + 2);
        graphics.fill(leftPos, topPos, leftPos + this.imageWidth, pagerTop, 0xFFC6C6C6);
        graphics.fill(leftPos, pagerTop, gx(ShopLayout.PAGER_BACK_X), pagerBottom, 0xFFC6C6C6);
        graphics.fill(gx(ShopLayout.PAGER_BACK_X + ShopLayout.PAGER_HALF_W), pagerTop,
                gx(ShopLayout.PAGER_FORWARD_X), pagerBottom, 0xFFC6C6C6);
        graphics.fill(gx(ShopLayout.PAGER_FORWARD_X + ShopLayout.PAGER_HALF_W), pagerTop,
                leftPos + this.imageWidth, pagerBottom, 0xFFC6C6C6);
        // Nothing is drawn for the places themselves: they are thirty-two real buttons and their
        // own appearance is the appearance. Painting cells over them is what made three rounds of
        // this confusing — the player was looking at rectangles and saying, correctly, that there
        // were no buttons.
    }

    /**
     * Puts the registry's titles on page two's buttons.
     *
     * <p>Called when the server sends the registry, which can be after these buttons were built: the
     * screen is made when the player opens the window, and an addon registers its place during mod
     * setup, so the order of those two is not something the screen can assume.
     */
    public void refreshPlaceLabels() {
        for (int i = 0; i < pageTwoCells.size(); i++) {
            ClientFeaturePlaces.Display display = ClientFeaturePlaces.at(i);
            pageTwoCells.get(i).setMessage(display == null ? Component.empty() : display.title());
        }
    }

    /**
     * A copy of a slot that reports itself inactive while page two is showing.
     *
     * <p>It reads and writes the same container at the same index, so the items are the same items.
     * The only thing it changes is whether the container draws and accepts them, which is what
     * {@code isActive} is consulted for — this project already relies on that for its tabs.
     */
    private static final class PageTwoSlot extends net.minecraft.world.inventory.Slot {

        private final net.minecraft.world.inventory.Slot original;
        private final boolean[] hidden;

        PageTwoSlot(net.minecraft.world.inventory.Slot original, boolean[] hidden) {
            super(original.container, original.getContainerSlot(), original.x, original.y);
            this.original = original;
            this.hidden = hidden;
        }

        @Override
        public boolean isActive() {
            return !hidden[0] && original.isActive();
        }

        @Override
        public net.minecraft.world.item.ItemStack getItem() {
            return original.getItem();
        }

        @Override
        public void set(net.minecraft.world.item.ItemStack stack) {
            original.set(stack);
        }

        @Override
        public void setChanged() {
            original.setChanged();
        }

        @Override
        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
            return original.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
            return original.mayPickup(player);
        }
    }

    /** Swapped in place of the real slots while page two shows; empty the rest of the time. */
    private final java.util.List<net.minecraft.world.inventory.Slot> realSlots =
            new java.util.ArrayList<>();
    private final boolean[] pageTwoHidingSlots = {false};

    /**
     * Hides or restores the backpack.
     *
     * <p>Neither painting over the slots nor moving them was available: the items are drawn by
     * AbstractContainerScreen's own private loop, after anything this class paints, and Slot.x and
     * Slot.y are final. Every slot is therefore replaced by a copy of itself that reports inactive,
     * which that same loop does consult. The copies share the container and the index, so the items
     * are untouched — only whether they are drawn, and whether a click can reach them.
     */
    private void setSlotsHidden(boolean hidden) {
        java.util.List<net.minecraft.world.inventory.Slot> slots = this.menu.slots;
        pageTwoHidingSlots[0] = hidden;
        if (hidden) {
            if (!realSlots.isEmpty()) {
                return;
            }
            for (int i = 0; i < slots.size(); i++) {
                realSlots.add(slots.get(i));
                slots.set(i, new PageTwoSlot(realSlots.get(i), pageTwoHidingSlots));
            }
            return;
        }
        if (realSlots.isEmpty()) {
            return;
        }
        for (int i = 0; i < realSlots.size() && i < slots.size(); i++) {
            slots.set(i, realSlots.get(i));
        }
        realSlots.clear();
    }

    /**
     * Lays out page two's places. Called once, from init: the grid does not move, so the buttons do
     * not need rebuilding when the page turns — only showing.
     */
    private void buildPageTwoCells() {
        for (int row = 0; row < ShopLayout.PAGE2_ROWS; row++) {
            for (int col = 0; col < ShopLayout.PAGE2_COLS; col++) {
                // Left to right, then top to bottom: the order the registry documents,
                // so a position in this list is the index an addon registered against.
                final int index = row * ShopLayout.PAGE2_COLS + col;
                int x = gx(ShopLayout.PAGE2_X + col * (ShopLayout.PAGE2_CELL_W + 1));
                int y = gy(ShopLayout.PAGE2_Y + row * (ShopLayout.PAGE2_CELL_H + 1));
                // No label and no action: the cell's own drawing is the appearance, and there is
                // nothing for a click to do until a feature is put here.
                Button cell = addRenderableWidget(Button.builder(Component.empty(), b -> {
                            // A place with nothing in it yet, and it says so. Client-side on purpose:
                            // there is no feature to ask the server about, and this is not a feature —
                            // it is what makes a place distinguishable from a decoration, which is the
                            // confusion that took several rounds to settle.
                            if (ClientFeaturePlaces.at(index) != null) {
                                // Somebody has claimed this place. Only the index goes: the
                                // server looks up what it is and runs it, so a place is written
                                // once, on the side that decides.
                                ModNetwork.sendToServer(
                                        new com.example.globalshop.api.feature.net.FeaturePlaceUsePacket(index));
                            } else if (net.minecraft.client.Minecraft.getInstance().player
                                    != null) {
                                // Nothing is here yet, and it says so. Not a feature: what makes
                                // a place distinguishable from a decoration.
                                net.minecraft.client.Minecraft.getInstance().player
                                        .displayClientMessage(
                                                Component.translatable("globalshop.page2.empty"),
                                                false);
                            }
                        })
                        .bounds(x, y, ShopLayout.PAGE2_CELL_W + 1, ShopLayout.PAGE2_CELL_H + 1)
                        .build());
                // Hidden from the start. refreshWidgets only ever speaks about these on page two, so
                // anything visible at creation is visible on page one too — which is exactly what
                // happened: thirty-two buttons drawn straight across the market window.
                cell.visible = false;
                pageTwoCells.add(cell);
            }
        }
    }

    /** Moves the side column one page, clamped at both ends. */
    private void switchColumnPage(int delta) {
        columnPage = Math.max(0, Math.min(ShopLayout.COLUMN_PAGES - 1, columnPage + delta));
        refreshWidgets();
    }

    private void refreshWidgets() {
        // Whatever page two hid comes back first, before anything decides what should be visible.
        // The rest of this method knows the widgets it manages; the ones it has never touched had
        // nothing to put them back, so page one came back without its tabs or its search box.
        for (net.minecraft.client.gui.components.AbstractWidget widget : hiddenForPageTwo) {
            widget.visible = true;
        }
        hiddenForPageTwo.clear();
        // The slots come home before anything decides what the page looks like.
        setSlotsHidden(false);
        // And page two's places go away again. Setting this only when they were built was not
        // enough: the page two branch shows them, and nothing on the way back said otherwise, so
        // one visit to page two left them across the market window for good. Done here, after the
        // restore loop, both paths agree every time — and the branch below re-shows them.
        for (Button cell : pageTwoCells) {
            cell.visible = false;
        }

        if (columnPage == 1) {
            // Page two is a menu of places, not a shop front: everything the market put on screen
            // belongs to page one, so it all goes, and only the pager comes back. Walking the list
            // rather than naming each widget means one added later is hidden without being listed.
            for (net.minecraft.client.gui.components.Renderable renderable : this.renderables) {
                if (renderable
                        instanceof net.minecraft.client.gui.components.AbstractWidget widget
                        && widget.visible) {
                    widget.visible = false;
                    hiddenForPageTwo.add(widget);
                }
            }
            pagerBackButton.visible = true;
            pagerForwardButton.visible = true;
            pagerBackButton.active = true;
            pagerForwardButton.active = false;
            // The places come back too: they were hidden by the loop above along with everything else.
            for (Button cell : pageTwoCells) {
                cell.visible = true;
            }
            setSlotsHidden(true);
            return;
        }
        ListingView selected = selectedListing();
        boolean market = tab == Tab.MARKET;
        boolean mine = tab == Tab.MINE;
        boolean sell = tab == Tab.SELL;
        boolean owned = selected != null && selected.ownedByViewer();
        boolean buyable = market && !owned;

        qtyBox.setVisible(buyable);
        qtyMinusButton.visible = buyable;
        qtyPlusButton.visible = buyable;
        buyButton.visible = buyable;
        marketUnlistButton.visible = market && owned;

        // The column is the first page's; each half of the pager is greyed at the end it cannot pass.
        for (Button button : columnPageOne) {
            button.visible = columnPage == 0;
        }
        pagerBackButton.active = columnPage > 0;
        pagerForwardButton.active = columnPage < ShopLayout.COLUMN_PAGES - 1;

        minePrice.setVisible(mine);
        mineStock.setVisible(mine);
        mineMin.setVisible(mine);
        mineMax.setVisible(mine);
        mineSaveButton.visible = mine;
        mineRestockButton.visible = mine;
        mineUnlistButton.visible = mine;
        mineUnlistAllButton.visible = mine;

        sellPrice.setVisible(sell);
        sellStock.setVisible(sell);
        sellMin.setVisible(sell);
        sellMax.setVisible(sell);
        sellButton.visible = sell;

        // The blacklist editor, which only the server can grant. The button is hidden entirely
        // for everyone else rather than shown greyed out: an unavailable moderation tool is not
        // something a player needs to know exists.
        ShopDataPacket pageData = ClientShopCache.data();
        boolean blockedMode = tab == Tab.BLACKLIST;
        blacklistButton.visible = pageData != null && pageData.operator();
        blockAddButton.visible = blockedMode;
        blockRemoveButton.visible = blockedMode;
        blockRemoveButton.active = blockedMode && selectedBlocked != null;

        // A hidden box must not keep keyboard focus, or it would silently swallow typing.
        for (EditBox box : toggleableBoxes) {
            if (!box.isVisible()) {
                box.setFocused(false);
            }
        }

        tabMarketButton.active = !market;
        tabMineButton.active = !mine;
        tabSellButton.active = !sell;
    }

    private void changePage(int delta) {
        ShopDataPacket data = ClientShopCache.data();
        int pageCount = (data == null) ? 1 : data.pageCount();
        int next = Math.max(0, Math.min(pageCount - 1, page + delta));
        if (next != page) {
            page = next;
            requestData();
        }
    }

    private void togglePriceSort() {
        setSort(sort == ShopSort.PRICE_ASC ? ShopSort.PRICE_DESC : ShopSort.PRICE_ASC);
    }

    private void toggleTimeSort() {
        setSort(sort == ShopSort.TIME_NEWEST ? ShopSort.TIME_OLDEST : ShopSort.TIME_NEWEST);
    }

    private void setSort(ShopSort newSort) {
        if (sort == newSort) {
            return;
        }
        sort = newSort;
        page = 0;
        requestData();
    }

    private ShopViewState currentView() {
        return new ShopViewState(query, sort, tab == Tab.MINE, tab == Tab.BLACKLIST, page);
    }

    private void requestData() {
        ModNetwork.sendToServer(new RequestShopDataPacket(currentView()));
    }

    private void stepQuantity(int delta) {
        ListingView listing = selectedListing();
        int lower = 1;
        int upper = Integer.MAX_VALUE;
        if (listing != null) {
            lower = Math.max(1, listing.minPerBuy());
            upper = Math.max(lower, listing.effectiveMaxPerBuy());
        }
        quantity = Math.max(lower, Math.min(upper, quantity + delta));
        qtyBox.setValue(Integer.toString(quantity));
    }

    // ==================================================================
    // Requests
    // ==================================================================

    private void sendPurchase() {
        ListingView listing = selectedListing();
        if (listing == null) {
            return;
        }
        int lower = Math.max(1, listing.minPerBuy());
        int upper = Math.max(lower, listing.effectiveMaxPerBuy());
        int amount = Math.max(lower, Math.min(upper, quantity));
        quantity = amount;
        qtyBox.setValue(Integer.toString(amount));
        ClientShopCache.expect("purchase");
        ModNetwork.sendToServer(new PurchasePacket(currentView(), listing.id(), amount));
    }

    private void sendUnlist() {
        ListingView listing = selectedListing();
        if (listing == null || !listing.ownedByViewer()) {
            return;
        }
        ClientShopCache.expect("unlist");
        ModNetwork.sendToServer(new UnlistPacket(currentView(), listing.id()));
    }

    /** Index into a grid page for a screen position, or {@code -1}. */
    private int gridIndexAt(double mouseX, double mouseY, int count) {
        int relX = (int) mouseX - gx(ShopLayout.GRID_X);
        int relY = (int) mouseY - gy(ShopLayout.GRID_Y);
        if (relX < 0 || relY < 0) {
            return -1;
        }
        int column = relX / ShopLayout.CELL;
        int row = relY / ShopLayout.CELL;
        if (column >= ShopLayout.COLS || row >= ShopLayout.ROWS) {
            return -1;
        }
        int index = row * ShopLayout.COLS + column;
        return index < count ? index : -1;
    }

    /** Index into the current market page. */
    private int cellIndexAt(double mouseX, double mouseY) {
        return gridIndexAt(mouseX, mouseY, ClientShopCache.listings().size());
    }

    /** Index into the blacklist grid, which is a different list in the same 6x6 cells. */
    private int blockedIndexAt(double mouseX, double mouseY) {
        return gridIndexAt(mouseX, mouseY, blockedIds().size());
    }

    /** The blocked item ids the last page carried. Empty for anyone but an operator. */
    private List<String> blockedIds() {
        ShopDataPacket data = ClientShopCache.data();
        return data == null ? List.of() : data.blacklist();
    }

    /**
     * A display stack for a blocked id.
     *
     * <p>Resolved on the client out of the item registry, so the wire only has to carry names.
     * Returns {@code null} for an id that no longer resolves — the mod that added it was removed
     * — which the grid draws as a marker rather than as an empty cell, so the entry can still be
     * found and taken out.
     */
    @Nullable
    private ItemStack stackForBlocked(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return null;
        }
        return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
    }

    /** The blocked items, in the same 6x6 grid the market uses. */
    private void drawBlockedGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> blocked = blockedIds();
        int hovered = blockedIndexAt(mouseX, mouseY);
        for (int index = 0;
             index < blocked.size() && index < ShopLayout.COLS * ShopLayout.ROWS;
             index++) {
            int cellX = gx(ShopLayout.GRID_X + (index % ShopLayout.COLS) * ShopLayout.CELL);
            int cellY = gy(ShopLayout.GRID_Y + (index / ShopLayout.COLS) * ShopLayout.CELL);
            ItemStack stack = stackForBlocked(blocked.get(index));
            if (stack == null) {
                graphics.fill(cellX + 2, cellY + 2, cellX + 14, cellY + 14, 0xFF7A2020);
            } else {
                graphics.renderItem(stack, cellX, cellY);
            }
            if (blocked.get(index).equals(selectedBlocked)) {
                outline(graphics, cellX - 1, cellY - 1, cellX + 16, cellY + 16, C_SELECT);
                outline(graphics, cellX - 2, cellY - 2, cellX + 17, cellY + 17, C_SELECT);
            } else if (index == hovered) {
                outline(graphics, cellX - 1, cellY - 1, cellX + 16, cellY + 16, C_HOVER);
            }
        }
    }

    /**
     * The blacklist editor's panel.
     *
     * <p>Adding works through the slot every other tab already uses: drop the item in, press the
     * button. The operator never has to know a registry name, and what gets blocked is the whole
     * item rather than the one stack they were holding — which is the point, since a rule that a
     * renamed copy walks around is not a rule.
     */
    private void drawBlacklistPanel(GuiGraphics graphics) {
        int left = ShopLayout.P_X;
        List<String> blocked = blockedIds();
        draw(graphics, Component.translatable("globalshop.blacklist.title"), left,
                ShopLayout.P_NAME_Y + 2, C_TEXT);
        draw(graphics, truncateComponent(Component.translatable("globalshop.blacklist.hint"),
                ShopLayout.PANEL_W - 10), left, ShopLayout.P_NAME_Y + 15, C_TEXT_DIM);
        draw(graphics, truncateComponent(Component.translatable("globalshop.blacklist.count",
                blocked.size()), ShopLayout.PANEL_W - 10), left, ShopLayout.P_NAME_Y + 28, C_TEXT_DIM);

        if (selectedBlocked == null) {
            draw(graphics, truncateComponent(
                            Component.translatable("globalshop.blacklist.none_selected"),
                            ShopLayout.PANEL_W - 10),
                    left, ShopLayout.P_NAME_Y + 48, C_TEXT_DIM);
        } else {
            ItemStack stack = stackForBlocked(selectedBlocked);
            if (stack != null) {
                graphics.renderItem(stack, gx(left), gy(ShopLayout.P_NAME_Y + 46));
            }
            draw(graphics, truncate(stack == null ? selectedBlocked : stack.getHoverName().getString(),
                    ShopLayout.PANEL_W - 30), left + 22, ShopLayout.P_NAME_Y + 50, C_DANGER);
            draw(graphics, truncate(selectedBlocked, ShopLayout.PANEL_W - 10), left,
                    ShopLayout.P_NAME_Y + 64, C_TEXT_DIM);
        }
        // The grid holds one page. Say how many are not on it rather than letting them look
        // missing — and it heals itself, because removing a visible entry reveals the next.
        int capacity = ShopLayout.COLS * ShopLayout.ROWS;
        if (blocked.size() > capacity) {
            draw(graphics, truncateComponent(Component.translatable("globalshop.blacklist.truncated",
                            blocked.size() - capacity), ShopLayout.PANEL_W - 10),
                    left, ShopLayout.P_NAME_Y + 80, C_DANGER);
        }
    }

    /** Opens or leaves the operator's blacklist editor. */
    private void toggleBlacklist() {
        switchTab(tab == Tab.BLACKLIST ? Tab.MARKET : Tab.BLACKLIST);
    }

    /** Blocks whatever is in the listing slot. */
    private void blockFromSlot() {
        // The listing slot is a real slot in this menu, so the item comes from the menu rather
        // than from anything the screen cached.
        ItemStack held = this.menu.inputStack();
        if (held.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (id != null) {
            sendBlacklist(id.toString(), true);
        }
    }

    private void unblockSelected() {
        if (selectedBlocked != null) {
            sendBlacklist(selectedBlocked, false);
        }
    }

    private void sendBlacklist(String itemId, boolean add) {
        ClientShopCache.expect(add ? "block" : "unblock");
        ModNetwork.sendToServer(new BlacklistPacket(itemId, add));
    }

    /** Asks the server to open the private-trade window. */
    private void requestTrade() {
        ModNetwork.sendToServer(new OpenTradePacket());
    }

    /** Asks the server for the viewer's mailbox; the reply opens the window. */
    private void requestMailbox() {
        ModNetwork.sendToServer(new RequestMailboxPacket());
    }

    /** Asks the server for the money ranking; the reply opens the window. */
    private void requestRanking() {
        ModNetwork.sendToServer(new RequestRankingPacket());
    }

    /** Asks the server for the viewer's own trades; the reply opens the window. */
    private void requestTradeLog() {
        ModNetwork.sendToServer(new RequestTradeLogPacket());
    }

    /**
     * Tops the selected lot up with everything of that item the player is carrying.
     *
     * <p>Only the listing id travels; the server counts the items itself and adds what it finds.
     */
    private void sendRestock() {
        ListingView listing = selectedListing();
        if (listing == null || !listing.ownedByViewer()) {
            return;
        }
        ClientShopCache.expect("restock");
        ModNetwork.sendToServer(new RestockPacket(currentView(), listing.id()));
    }

    /** Takes every one of the viewer's lots off the market in one request. */
    private void sendUnlistAll() {
        ClientShopCache.expect("unlist_all");
        ModNetwork.sendToServer(new UnlistAllPacket(currentView()));
    }

    private void sendModify() {
        ListingView listing = selectedListing();
        if (listing == null || !listing.ownedByViewer()) {
            return;
        }
        OptionalLong price = MoneyFormat.parse(minePrice.getValue());
        OptionalLong stock = parsePositive(mineStock.getValue());
        OptionalLong min = parsePositive(mineMin.getValue());
        OptionalLong max = parsePositive(mineMax.getValue());
        if (price.isEmpty() || stock.isEmpty() || min.isEmpty() || max.isEmpty()) {
            return;
        }
        ClientShopCache.expect("modify");
        ModNetwork.sendToServer(new ModifyListingPacket(currentView(), listing.id(), price.getAsLong(),
                clampToInt(stock.getAsLong()), clampToInt(min.getAsLong()), clampToInt(max.getAsLong())));
    }

    private void sendCreate() {
        OptionalLong price = MoneyFormat.parse(sellPrice.getValue());
        OptionalLong stock = parsePositive(sellStock.getValue());
        OptionalLong min = parsePositive(sellMin.getValue());
        OptionalLong max = parsePositive(sellMax.getValue());
        if (price.isEmpty() || stock.isEmpty() || min.isEmpty() || max.isEmpty()) {
            return;
        }
        ClientShopCache.expect("create");
        ModNetwork.sendToServer(new CreateListingPacket(currentView(), price.getAsLong(),
                clampToInt(stock.getAsLong()), clampToInt(min.getAsLong()), clampToInt(max.getAsLong())));
    }

    // ==================================================================
    // Per-tick housekeeping
    // ==================================================================

    @Override
    public void containerTick() {
        super.containerTick();

        // Debounced search: typing must not send one packet per keystroke.
        if (!typedQuery.equals(query) && System.currentTimeMillis() - typedQueryAt > 350L) {
            query = typedQuery;
            page = 0;
            requestData();
        }

        ShopDataPacket data = ClientShopCache.data();
        if (data != null && data.view().page() != page) {
            page = data.view().page();
        }

        List<ListingView> listings = ClientShopCache.listings();
        if (selectedId != null && listings.stream().noneMatch(l -> l.id().equals(selectedId))) {
            selectListing(null);
        }
        if (selectedId == null && !listings.isEmpty()) {
            selectListing(listings.get(0));
        }

        if (tab == Tab.SELL) {
            prefillSellFromInput();
        }

        // The tab and the selection both feed into which button is offered.
        refreshWidgets();

        // A sound for the outcome of the last action, played once when the result arrives.
        String key = ClientShopCache.statusKey();
        if (key != null && !key.equals(lastSoundKey)) {
            lastSoundKey = key;
            // The verdict comes from the server's result enum, not from the message key: the key
            // is built per action ("globalshop.status.purchase.ok"), so comparing it with the
            // generic success key never matched and every outcome sounded like a refusal.
            playResultSound(ClientShopCache.statusSuccess());
        }
    }

    /**
     * Plays the outcome of a trade.
     *
     * <p>Driven by the server's status key rather than by anything the screen decided, so a
     * refused purchase sounds like a refusal even though the button looked available.
     */
    private void playResultSound(boolean success) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.playSound(
                success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                0.7F, success ? 1.0F : 0.8F);
    }

    /**
     * Fills the selling form from whatever the player put in the sell slot.
     *
     * <p>The default stock is <em>everything</em> of that item the player is carrying, not
     * just the stack in the slot. A listing may draw from the backpack as well, so
     * pre-filling only the slot amount made "sell all of it" require manual arithmetic —
     * and made it look as though only one stack could be listed.
     */
    private void prefillSellFromInput() {
        ItemStack input = menu.inputStack();
        if (ItemStack.matches(input, lastSellInput)) {
            return;
        }
        lastSellInput = input.copy();
        if (input.isEmpty()) {
            return;
        }
        int available = countCarried(input);
        sellStock.setValue(Integer.toString(available));
        sellMin.setValue("1");
        sellMax.setValue(Integer.toString(available));
    }

    /** How many of this item the player is carrying: the sell slot plus the backpack. */
    private int countCarried(ItemStack probe) {
        int total = probe.getCount();
        if (this.minecraft == null || this.minecraft.player == null) {
            return total;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        // 36, not getContainerSize(): worn armour is not "carried". Counting it would let the sell
        // tab claim the player has more than they can actually trade away.
        int slots = com.example.globalshop.core.market.ItemStacks.placeableSlots(inventory);
        for (int slot = 0; slot < slots; slot++) {
            if (ItemStack.isSameItemSameTags(inventory.getItem(slot), probe)) {
                total += inventory.getItem(slot).getCount();
            }
        }
        return total;
    }

    private void selectListing(@Nullable ListingView listing) {
        selectedId = (listing == null) ? null : listing.id();
        if (listing == null) {
            quantity = 1;
            qtyBox.setValue("1");
            return;
        }
        int lower = Math.max(1, listing.minPerBuy());
        int upper = Math.max(lower, listing.effectiveMaxPerBuy());
        quantity = Math.min(upper, lower);
        qtyBox.setValue(Integer.toString(quantity));
        if (listing.ownedByViewer()) {
            minePrice.setValue(MoneyFormat.format(listing.unitPrice()));
            mineStock.setValue(Integer.toString(Math.max(1, listing.stock())));
            mineMin.setValue(Integer.toString(listing.minPerBuy()));
            mineMax.setValue(Integer.toString(Math.max(listing.minPerBuy(), listing.maxPerBuy())));
        }
    }

    @Nullable
    private ListingView selectedListing() {
        if (selectedId == null) {
            return null;
        }
        for (ListingView listing : ClientShopCache.listings()) {
            if (listing.id().equals(selectedId)) {
                return listing;
            }
        }
        return null;
    }

    @Nullable
    private ListingView listingAt(double mouseX, double mouseY) {
        int index = cellIndexAt(mouseX, mouseY);
        return index < 0 ? null : ClientShopCache.listings().get(index);
    }

    // ==================================================================
    // Input
    // ==================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // On page two the cells are offered the click first, and whatever misses them is swallowed
        // rather than reaching a backpack slot the cover is hiding. The widgets have to be asked
        // first — the earlier version returned before super.mouseClicked, which would have made the
        // cells themselves unclickable the moment they were given anything to do.
        if (columnPage == 1) {
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (button == 0 && tab == Tab.BLACKLIST) {
            List<String> blocked = blockedIds();
            int index = blockedIndexAt(mouseX, mouseY);
            if (index >= 0 && index < blocked.size()) {
                selectedBlocked = blocked.get(index);
                refreshWidgets();
                return true;
            }
        }
        if (button == 0 && overBadge(badgeBounds(fittedBalanceText()), (int) mouseX, (int) mouseY)) {
            // Ask the server rather than opening a window with guessed numbers: the price and the
            // period are the server's settings, so a client showing its own would be showing a
            // number the player is not going to be charged.
            ModNetwork.sendToServer(new VipActionPacket(VipActionPacket.STATUS));
            return true;
        }
        if (button == 0) {
            ListingView clicked = listingAt(mouseX, mouseY);
            if (clicked != null) {
                selectListing(clicked);
                refreshWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (listingAt(mouseX, mouseY) != null) {
            changePage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ==================================================================
    // Drawing primitives
    // ==================================================================

    /** A raised panel: what you press. Light top/left, dark bottom/right, black outline. */
    private void raised(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        raised(graphics, x0, y0, x1, y1, C_BODY);
    }

    private void raised(GuiGraphics graphics, int x0, int y0, int x1, int y1, int face) {
        graphics.fill(x0, y0, x1 + 1, y1 + 1, C_BLACK);
        graphics.fill(x0 + 1, y0 + 1, x1, y1, face);
        graphics.fill(x0 + 1, y0 + 1, x1, y0 + 2, C_LIGHT);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1, C_LIGHT);
        graphics.fill(x0 + 1, y1 - 1, x1, y1, C_DARK);
        graphics.fill(x1 - 1, y0 + 1, x1, y1, C_DARK);
    }

    /** A sunken well: where content sits. Dark top/left, light bottom/right. */
    private void sunken(GuiGraphics graphics, int x0, int y0, int x1, int y1, int face) {
        graphics.fill(x0, y0, x1 + 1, y1 + 1, C_BLACK);
        graphics.fill(x0 + 1, y0 + 1, x1, y1, face);
        graphics.fill(x0 + 1, y0 + 1, x1, y0 + 2, C_INSET_DARK);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1, C_INSET_DARK);
        graphics.fill(x0 + 1, y1 - 1, x1, y1, C_LIGHT);
        graphics.fill(x1 - 1, y0 + 1, x1, y1, C_LIGHT);
    }

    /** An 18x18 inventory slot, matching the vanilla three-tone inset. */
    private void slotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, C_INSET_DARK);
        graphics.fill(x, y, x + 16, y + 16, C_INSET);
        graphics.fill(x, y, x + 16, y + 1, C_INSET_DARK);
        graphics.fill(x, y, x + 1, y + 16, C_INSET_DARK);
        graphics.fill(x, y + 15, x + 16, y + 16, C_LIGHT);
        graphics.fill(x + 15, y, x + 16, y + 16, C_LIGHT);
    }

    private void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1 + 1, y0 + 1, colour);
        graphics.fill(x0, y1, x1 + 1, y1 + 1, colour);
        graphics.fill(x0, y0, x0 + 1, y1 + 1, colour);
        graphics.fill(x1, y0, x1 + 1, y1 + 1, colour);
    }

    // ==================================================================
    // Background
    // ==================================================================

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        raised(graphics, gx(0), gy(0), gx(ShopLayout.GUI_WIDTH - 1), gy(ShopLayout.GUI_HEIGHT - 1));

        // Groove under the header.
        graphics.fill(gx(0), gy(ShopLayout.SEPARATOR_Y), gx(ShopLayout.GUI_WIDTH),
                gy(ShopLayout.SEPARATOR_Y + 1), C_DARK);
        graphics.fill(gx(0), gy(ShopLayout.SEPARATOR_Y + 1), gx(ShopLayout.GUI_WIDTH),
                gy(ShopLayout.SEPARATOR_Y + 2), C_LIGHT);

        // Market grid well.
        sunken(graphics, gx(ShopLayout.GRID_X - 3), gy(ShopLayout.GRID_Y - 3),
                gx(ShopLayout.GRID_X + ShopLayout.COLS * ShopLayout.CELL + 1),
                gy(ShopLayout.GRID_Y + ShopLayout.ROWS * ShopLayout.CELL + 1), C_INSET);
        for (int row = 0; row < ShopLayout.ROWS; row++) {
            for (int column = 0; column < ShopLayout.COLS; column++) {
                slotBackground(graphics, gx(ShopLayout.GRID_X + column * ShopLayout.CELL),
                        gy(ShopLayout.GRID_Y + row * ShopLayout.CELL));
            }
        }

        // Detail / form well.
        sunken(graphics, gx(ShopLayout.PANEL_X), gy(ShopLayout.PANEL_Y),
                gx(ShopLayout.PANEL_X + ShopLayout.PANEL_W - 1),
                gy(ShopLayout.PANEL_Y + ShopLayout.PANEL_H - 1), C_INSET);

        // The listing input slot gets its own compartment so it reads as a purpose-built
        // place to put something, not as a stray inventory slot.
        sunken(graphics, gx(ShopLayout.INPUT_BOX_X), gy(ShopLayout.INPUT_BOX_Y),
                gx(ShopLayout.INPUT_BOX_X + ShopLayout.INPUT_BOX_W - 1),
                gy(ShopLayout.INPUT_BOX_Y + ShopLayout.INPUT_BOX_H - 1), C_INSET_SOFT);

        // The real slots: the listing input slot and the player's inventory.
        for (Slot slot : this.menu.slots) {
            slotBackground(graphics, gx(slot.x), gy(slot.y));
        }

        if (tab == Tab.SELL) {
            // Draw the eye to where the item has to go.
            outline(graphics, gx(ShopLayout.INPUT_BOX_X), gy(ShopLayout.INPUT_BOX_Y),
                    gx(ShopLayout.INPUT_BOX_X + ShopLayout.INPUT_BOX_W - 1),
                    gy(ShopLayout.INPUT_BOX_Y + ShopLayout.INPUT_BOX_H - 1), C_ACCENT);
        }
    
        if (columnPage == 1) {
            // Last thing in renderBg, so it covers this method's own drawing and the slot frames,
            // and so the container's widgets — the thirty-two places and the two arrows — are drawn
            // on top of it afterwards. Painting it any later puts it over the buttons.
            paintPageTwoCover(graphics);
        }
}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Intentionally empty: all text is drawn from render(), in one coordinate space.
    }

    /**
     * Suppressed on purpose.
     *
     * <p>{@link #drawHoverTooltip} already covers the hovered slot, so letting the base class
     * draw its own as well would stack two identical boxes on top of each other. This class
     * answers for every tooltip it shows.
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Intentionally empty: see drawHoverTooltip.
    }

    // ==================================================================
    // Foreground
    // ==================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (columnPage == 1) {
            // Page two's cover is painted in renderBg, before the container draws its widgets, so
            // that the thirty-two places and the arrows come out on top of it. Here, after the
            // container, there is nothing left to draw — the cover used to be painted at this point
            // and it went over the buttons, which is why the page came out blank.
            return;
        }

        // Kept for the header's hover highlight: the header is drawn by an inner call that has no
        // mouse position of its own.
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        if (this.minecraft == null) {
            return;
        }

        List<ListingView> listings = ClientShopCache.listings();
        ShopDataPacket data = ClientShopCache.data();
        int hovered = cellIndexAt(mouseX, mouseY);

        // ---- market grid ----
        // In the blacklist editor the same cells show blocked items instead, so the market grid
        // is skipped rather than drawn underneath and then covered.
        for (int index = 0;
             tab != Tab.BLACKLIST && index < listings.size()
                     && index < ShopLayout.COLS * ShopLayout.ROWS;
             index++) {
            int cellX = gx(ShopLayout.GRID_X + (index % ShopLayout.COLS) * ShopLayout.CELL);
            int cellY = gy(ShopLayout.GRID_Y + (index / ShopLayout.COLS) * ShopLayout.CELL);
            ListingView listing = listings.get(index);
            graphics.renderItem(listing.display(), cellX, cellY);
            graphics.renderItemDecorations(this.font, listing.display(), cellX, cellY);
            if (listing.expired()) {
                // Dimmed, so "this one is not for sale any more" reads at a glance rather than
                // only after selecting it and reading the panel.
                graphics.fill(cellX, cellY, cellX + 16, cellY + 16, C_EXPIRED_VEIL);
            }

            if (listing.id().equals(selectedId)) {
                outline(graphics, cellX - 1, cellY - 1, cellX + 16, cellY + 16, C_SELECT);
                outline(graphics, cellX - 2, cellY - 2, cellX + 17, cellY + 17, C_SELECT);
            } else if (index == hovered) {
                outline(graphics, cellX - 1, cellY - 1, cellX + 16, cellY + 16, C_HOVER);
            }
        }

        if (tab == Tab.BLACKLIST) {
            drawBlockedGrid(graphics, mouseX, mouseY);
        }

        // ---- header ----
        int pageCount = (data == null) ? 1 : data.pageCount();
        draw(graphics, (page + 1) + "/" + pageCount, ShopLayout.PAGE_TEXT_X,
                ShopLayout.ROW_B_Y + 4, C_TEXT);

        // The balance is abbreviated and then hard-capped, so no configuration and no
        // language can make it grow leftwards into the sort buttons.
        Component fittedBalance = fittedBalanceText();
        drawRight(graphics, fittedBalance, ShopLayout.BALANCE_RIGHT, ShopLayout.ROW_A_Y + 4, C_PRICE);
        // The viewer's own rank, immediately to the left of the balance. Purple for VIP, grey
        // otherwise, so the state is readable without reading the word — and clickable, because
        // this is where a player looks to find out what they are, and a label that answers a
        // question and then offers no action is a dead end.
        boolean viewerVip = viewerVip();
        int[] badge = badgeBounds(fittedBalance);
        boolean badgeHovered = overBadge(badge, this.lastMouseX, this.lastMouseY);
        if (badgeHovered) {
            graphics.fill(gx(badge[0] - 2), gy(badge[1] - 2), gx(badge[2] + 2), gy(badge[3] + 2),
                    0x35FFFFFF);
        }
        draw(graphics, rankBadge(viewerVip), badge[0], badge[1], rankColour(viewerVip));

        draw(graphics, Component.translatable("globalshop.label.sell_slot"),
                ShopLayout.INPUT_BOX_X + 4, ShopLayout.INPUT_LABEL_Y, C_TEXT_DIM);

        ListingView selected = selectedListing();
        drawPanel(graphics, selected);

        // ---- result banner ----
        // Inside the window, not above it: the window is as tall as the GUI area on the client
        // this was built against, so a message drawn at a negative y had never once been visible.
        // Coloured by outcome, because a refusal that looks exactly like a success does not read
        // as a refusal.
        String statusKey = ClientShopCache.statusKey();
        if (statusKey != null) {
            boolean ok = ClientShopCache.statusSuccess();
            Component message = Component.translatable(statusKey)
                    .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
            graphics.fill(gx(ShopLayout.SIDE_X), gy(ShopLayout.STATUS_BANNER_Y),
                    gx(ShopLayout.BALANCE_RIGHT),
                    gy(ShopLayout.STATUS_BANNER_Y + ShopLayout.STATUS_BANNER_H), C_STATUS_BACKING);
            draw(graphics, message, ShopLayout.GUI_WIDTH / 2 - this.font.width(message) / 2,
                    ShopLayout.STATUS_BANNER_Y + 3, ok ? C_STATUS_OK : C_STATUS_BAD);
        }

        // ---- hover tooltip, drawn last so nothing can cover it ----
        drawHoverTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Draws the tooltip for whatever is under the cursor.
     *
     * <p>This screen owns all three cases — a market lot, the detail panel's icon, and a real
     * inventory slot — rather than leaving slots to the base class. That is deliberate:
     * {@code AbstractContainerScreen} does not draw its slot tooltip from {@code render} in
     * this version, so relying on it meant market lots got a tooltip and the player's own
     * backpack silently did not. Routing everything through one method makes the three cases
     * behave identically, and makes the behaviour something this class can be reasoned about
     * instead of something inherited.
     */
    private void drawHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.minecraft == null) {
            return;
        }
        // The grid shows blocked items in the editor, not lots; leaving the listing hover in
        // place would describe a lot that is not on the screen.
        boolean blockedMode = tab == Tab.BLACKLIST;
        List<ListingView> listings = blockedMode ? List.of() : ClientShopCache.listings();
        int hovered = blockedMode ? -1 : cellIndexAt(mouseX, mouseY);
        ListingView selected = selectedListing();

        ItemStack stack = null;
        List<Component> lines = null;

        if (hovered >= 0 && hovered < listings.size()) {
            stack = listings.get(hovered).display();
        } else if (selected != null && panelIconHovered(mouseX, mouseY)) {
            stack = selected.display();
        } else if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            stack = this.hoveredSlot.getItem();
            // Ask the container for the lines, so any Forge hook a mod adds to slot tooltips
            // (previews, extra context) still applies to the player's own items.
            lines = this.getTooltipFromContainerItem(stack);
        }

        if (stack == null) {
            return;
        }
        if (lines == null) {
            // The same box the inventory shows, built the same way: name with its rarity and
            // enchantment styling, every enchantment, durability, attribute modifiers, and any
            // lines a modded item adds. This mod never has to know what an item wants to say.
            TooltipFlag flag = this.minecraft.options.advancedItemTooltips
                    ? TooltipFlag.Default.ADVANCED
                    : TooltipFlag.Default.NORMAL;
            lines = stack.getTooltipLines(this.minecraft.player, flag);
        }
        graphics.renderTooltip(this.font, lines, stack.getTooltipImage(), mouseX, mouseY);
    }

    private boolean panelIconHovered(double mouseX, double mouseY) {
        int x = gx(ShopLayout.P_ICON_X);
        int y = gy(ShopLayout.P_ICON_Y);
        return mouseX >= x && mouseX < x + ShopLayout.P_ICON_SIZE
                && mouseY >= y && mouseY < y + ShopLayout.P_ICON_SIZE;
    }

    private void drawPanel(GuiGraphics graphics, @Nullable ListingView selected) {
        int left = ShopLayout.P_X;

        if (tab == Tab.BLACKLIST) {
            drawBlacklistPanel(graphics);
            return;
        }

        if (tab == Tab.SELL) {
            draw(graphics, truncateComponent(Component.translatable("globalshop.sell.hint1"),
                    ShopLayout.PANEL_W - 10), left, ShopLayout.SELL_HINT1_Y, C_TEXT_DIM);
            drawFormRows(graphics);
            // Truncated: the English wording is wider than the panel, and a hint that runs
            // over the border looks broken in every language that happens to be wordy.
            draw(graphics, truncateComponent(Component.translatable("globalshop.sell.hint2"),
                    ShopLayout.PANEL_W - 10), left, ShopLayout.HINT_Y, C_TEXT_DIM);
            return;
        }

        if (selected == null) {
            draw(graphics, truncateComponent(Component.translatable("globalshop.label.nothing_selected"),
                    ShopLayout.PANEL_W - 10), left, ShopLayout.P_NAME_Y + 24, C_TEXT_DIM);
            return;
        }

        // The item, its remaining durability, and what is on it. For a tool, these three
        // things are what actually decide whether the lot is worth the price, so they are
        // shown in the panel rather than only in the hover tooltip.
        ItemStack display = selected.display();
        int rightEdge = ShopLayout.PANEL_X + ShopLayout.PANEL_W - 6;
        String durability = durabilityText(display);

        graphics.renderItem(display, gx(left), gy(ShopLayout.P_NAME_Y - 2));
        int nameMax = rightEdge - (left + 22) - 6
                - (durability == null ? 0 : this.font.width(durability));
        draw(graphics, truncate(display.getHoverName().getString(), Math.max(40, nameMax)),
                left + 22, ShopLayout.P_NAME_Y + 2, C_TEXT);
        if (durability != null) {
            drawRight(graphics, Component.literal(durability), rightEdge,
                    ShopLayout.P_NAME_Y + 2, durabilityColour(display));
        }
        // Indented past the icon so the two can never touch.
        drawEnchantments(graphics, display, left + 22, ShopLayout.P_ENCH_Y);

        if (tab == Tab.MINE) {
            // No seller row here: on your own listing the seller is you, and the tab is
            // already yours. Leaving it out also keeps it clear of the form's first row.
            drawFormRows(graphics);
            // An expired lot says so rather than showing the generic hint: it is the one state
            // where the only thing the seller can usefully do is take it down.
            Component footer;
            int colour;
            if (selected.expired()) {
                footer = Component.translatable("globalshop.label.expired");
                colour = C_DANGER;
            } else if (selected.soldOut()) {
                footer = Component.translatable("globalshop.label.sold_out");
                colour = C_DANGER;
            } else {
                footer = Component.translatable("globalshop.mine.hint");
                colour = C_TEXT_DIM;
            }
            draw(graphics, truncateComponent(footer, ShopLayout.PANEL_W - 10), left, ShopLayout.HINT_Y,
                    colour);
            return;
        }

        // ---- market tab ----
        // The seller, then that seller's rank straight after the name, and — for the viewer's own
        // lots — the owned badge on the right. All three share this row rather than the name row,
        // which already carries the durability readout.
        int badgeWidth = 0;
        if (selected.ownedByViewer()) {
            badgeWidth = this.font.width(Component.translatable("globalshop.label.owned")) + 6;
        }
        Component rank = rankBadge(selected.sellerVip());
        int rankWidth = this.font.width(rank) + 5;
        Component seller = truncateComponent(
                Component.translatable("globalshop.label.seller", selected.sellerName()),
                Math.max(40, rightEdge - left - badgeWidth - rankWidth));
        draw(graphics, seller, left, ShopLayout.P_SELLER_Y, C_TEXT);
        draw(graphics, rank, left + this.font.width(seller) + 5, ShopLayout.P_SELLER_Y,
                rankColour(selected.sellerVip()));
        if (selected.ownedByViewer()) {
            drawRight(graphics, Component.translatable("globalshop.label.owned"),
                    rightEdge, ShopLayout.P_SELLER_Y, C_OWNED);
        }

        draw(graphics, truncateComponent(Component.translatable("globalshop.label.unit_price",
                        MoneyFormat.formatWithName(selected.unitPrice())), ShopLayout.PANEL_W - 10),
                left, ShopLayout.P_PRICE_Y, C_PRICE);

        draw(graphics, Component.translatable("globalshop.label.stock", selected.stock()),
                left, ShopLayout.P_STOCK_Y, C_TEXT);
        draw(graphics, Component.translatable("globalshop.label.min", selected.minPerBuy()),
                left + 76, ShopLayout.P_STOCK_Y, C_TEXT_DIM);
        draw(graphics, Component.translatable("globalshop.label.max", selected.maxPerBuy()),
                left + 130, ShopLayout.P_STOCK_Y, C_TEXT_DIM);

        draw(graphics, Component.translatable("globalshop.label.listed_at", formatTime(selected.listedAt())),
                left, ShopLayout.P_TIME_Y, C_TEXT_DIM);

        if (selected.ownedByViewer()) {
            // Buying your own lot is refused by the server, so the panel explains what the
            // big button below is for instead of showing a quantity that cannot be used.
            draw(graphics, Component.translatable("globalshop.market.owned_hint"),
                    left, ShopLayout.P_QTY_Y - 2, C_TEXT_DIM);
            draw(graphics, Component.translatable("globalshop.market.owned_hint2"),
                    left, ShopLayout.P_QTY_Y + 10, C_TEXT_DIM);
            return;
        }

        draw(graphics, Component.translatable("globalshop.field.quantity"),
                left, ShopLayout.P_QTY_Y + 3, C_TEXT);

        int amount = quantity;
        long total;
        try {
            total = Math.multiplyExact(selected.unitPrice(), (long) amount);
        } catch (ArithmeticException overflow) {
            total = Long.MAX_VALUE;
        }
        // The requirement's "unit price x quantity = total", on one line. The currency name
        // is left out here to keep the whole equation in the panel's width.
        draw(graphics, MoneyFormat.format(selected.unitPrice()) + " x " + amount
                + " = " + MoneyFormat.format(total), left, ShopLayout.P_TOTAL_Y, C_PRICE);
    }

    private void drawFormRows(GuiGraphics graphics) {
        int left = ShopLayout.P_X;
        draw(graphics, Component.translatable("globalshop.field.price"), left, ShopLayout.F1_LABEL_Y, C_TEXT);
        draw(graphics, Component.translatable("globalshop.field.stock"), left, ShopLayout.F2_LABEL_Y, C_TEXT);
        draw(graphics, Component.translatable("globalshop.field.min"),
                ShopLayout.ALT_LABEL_X, ShopLayout.F2_LABEL_Y, C_TEXT);
        draw(graphics, Component.translatable("globalshop.field.max"), left, ShopLayout.F3_LABEL_Y, C_TEXT);
    }

    // ---- item details -------------------------------------------------

    /**
     * The {@code remaining/max} durability readout, or {@code null} when the item has none.
     *
     * <p>This is the number that decides whether a used tool is worth buying, so it is shown
     * outright rather than only in the hover tooltip. The grid icon also carries vanilla's
     * durability bar, but a bar alone cannot say "312 of 1561 left".
     */
    @Nullable
    private String durabilityText(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return null;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return null;
        }
        return Math.max(0, max - stack.getDamageValue()) + "/" + max;
    }

    private int durabilityColour(ItemStack stack) {
        int max = Math.max(1, stack.getMaxDamage());
        float remaining = Math.max(0, max - stack.getDamageValue()) / (float) max;
        if (remaining > 0.5F) {
            return C_DURABLE_OK;
        }
        return remaining > 0.2F ? C_DURABLE_WARN : C_DURABLE_LOW;
    }

    /**
     * Draws as many enchantments as fit, and says how many did not.
     *
     * <p>A bare "..." would leave the reader unable to tell a one-enchantment sword from a
     * five-enchantment one. Counting the remainder at least makes the truncation honest, and
     * the hover tooltip has the full list.
     *
     * <p>Sorted by name so the text does not reshuffle between frames — the enchantment map
     * has no meaningful order of its own.
     */
    private void drawEnchantments(GuiGraphics graphics, ItemStack stack, int x, int y) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.isEmpty()) {
            return;
        }
        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(enchantments.entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().getFullname(entry.getValue()).getString()));

        int maxWidth = ShopLayout.PANEL_X + ShopLayout.PANEL_W - 6 - x;
        StringBuilder fitted = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Enchantment, Integer> entry : entries) {
            String name = entry.getKey().getFullname(entry.getValue()).getString();
            String candidate = shown == 0 ? name : fitted + ", " + name;
            int hiddenIfAdded = entries.size() - (shown + 1);
            String suffix = hiddenIfAdded > 0 ? " " + moreText(hiddenIfAdded) : "";
            if (this.font.width(candidate + suffix) > maxWidth) {
                break;
            }
            fitted.setLength(0);
            fitted.append(candidate);
            shown++;
        }

        int hidden = entries.size() - shown;
        String text;
        if (shown == 0) {
            text = moreText(hidden);
        } else if (hidden > 0) {
            text = fitted + " " + moreText(hidden);
        } else {
            text = fitted.toString();
        }
        draw(graphics, truncate(text, maxWidth), x, y, C_ENCHANT);
    }

    private String moreText(int count) {
        return Component.translatable("globalshop.label.enchantments_more", count).getString();
    }

    // ---- text helpers -------------------------------------------------

    /** Whether the viewer owns the VIP rank, from the last page the server sent. */
    private boolean viewerVip() {
        ShopDataPacket data = ClientShopCache.data();
        return data != null && data.viewerVip();
    }

    /** "VIP" or "Normal". */
    private static Component rankBadge(boolean vip) {
        return Component.translatable(vip
                ? "globalshop.label.vip_badge"
                : "globalshop.label.normal_badge");
    }

    private static int rankColour(boolean vip) {
        return vip ? C_VIP : C_RANK_NORMAL;
    }

    /** The balance line as drawn: abbreviated, then hard-capped. */
    private Component fittedBalanceText() {
        return truncateComponent(Component.translatable("globalshop.label.balance",
                MoneyFormat.formatCompact(ClientShopCache.balance())),
                ShopLayout.BALANCE_MAX_W);
    }

    /**
     * The rank badge's rectangle.
     *
     * <p>Computed from the same numbers the drawing uses, and used by the click test: a hit box
     * derived separately from the drawing is how a button ends up not quite where it looks.
     */
    private int[] badgeBounds(Component fittedBalance) {
        Component badge = rankBadge(viewerVip());
        int right = ShopLayout.BALANCE_RIGHT - this.font.width(fittedBalance)
                - ShopLayout.BALANCE_BADGE_GAP;
        return new int[] {right - this.font.width(badge), ShopLayout.ROW_A_Y + 4, right,
                ShopLayout.ROW_A_Y + 12};
    }

    /**
     * Whether the mouse is over the badge.
     *
     * <p>The rectangle is in window space — it is what the drawing helper consumes — while the mouse
     * arrives in screen space, so the mouse is converted here, exactly as {@code gridIndexAt} does.
     * Comparing a screen position against a window position is how the badge ended up visible and
     * unclickable: it looked like it was at x=220 when the mouse had to be at x=273.
     */
    private boolean overBadge(int[] badge, double mouseX, double mouseY) {
        int relX = (int) mouseX - leftPos;
        int relY = (int) mouseY - topPos;
        return relX >= badge[0] - 2 && relX <= badge[2] + 2
                && relY >= badge[1] - 2 && relY <= badge[3] + 2;
    }

    private void draw(GuiGraphics graphics, Component text, int x, int y, int colour) {        graphics.drawString(this.font, text, gx(x), gy(y), colour, false);
    }

    private void draw(GuiGraphics graphics, String text, int x, int y, int colour) {
        graphics.drawString(this.font, text, gx(x), gy(y), colour, false);
    }

    private void drawRight(GuiGraphics graphics, Component text, int right, int y, int colour) {
        graphics.drawString(this.font, text, gx(right) - this.font.width(text), gy(y), colour, false);
    }

    /** Width-aware truncation; only relies on {@code Font.width}, which is stable API. */
    private String truncate(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (this.font.width(builder.toString() + text.charAt(i) + "...") > maxWidth) {
                break;
            }
            builder.append(text.charAt(i));
        }
        return builder + "...";
    }

    /**
     * As {@link #truncate}, for a translated component.
     *
     * <p>The component is flattened before measuring, which drops any per-argument styling;
     * that is acceptable for the strings this is used on, all of which are plain numbers
     * and names. It exists so that a long value can never push text outside its panel.
     */
    private Component truncateComponent(Component text, int maxWidth) {
        String flat = text.getString();
        return this.font.width(flat) <= maxWidth ? text : Component.literal(truncate(flat, maxWidth));
    }

    private static String formatTime(long epochMillis) {
        return DateTimeFormatter.ofPattern("MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(epochMillis));
    }

    private static OptionalLong parsePositive(String text) {
        if (text == null || text.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(text.trim());
            return value >= 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalLong.empty();
        }
    }

    private static int clampToInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}
