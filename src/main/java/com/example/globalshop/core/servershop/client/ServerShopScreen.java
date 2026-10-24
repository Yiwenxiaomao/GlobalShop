package com.example.globalshop.core.servershop.client;

import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.menu.ServerShopLayout;
import com.example.globalshop.core.menu.ServerShopMenu;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.core.servershop.net.ServerProductView;
import com.example.globalshop.core.servershop.net.RequestServerShopDataPacket;
import com.example.globalshop.core.servershop.net.ServerBuyPacket;
import com.example.globalshop.core.servershop.net.ServerProductAdminPacket;
import com.example.globalshop.core.servershop.net.ServerRecyclePacket;
import com.example.globalshop.core.servershop.ServerShopTab;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.example.globalshop.core.client.ItemInfoText;
import com.example.globalshop.core.market.client.ShopScreen;

/**
 * The official server shop window.
 *
 * <h2>Three pages, because they are three jobs</h2>
 * {@link ServerShopTab#SALE} is buying from the server, which has no limit and costs money.
 * {@link ServerShopTab#RECYCLE} is selling back, which is capped by a daily allowance and earns
 * money. {@link ServerShopTab#MANAGE} is writing the catalogue, which is an operator's job rather
 * than a player's. An earlier version put buying and selling back on one page, which meant two
 * opposite transactions sharing a panel and a pair of buttons; separating them means each page
 * shows only the numbers that action needs.
 *
 * <h2>Not the market</h2>
 * A separate screen from {@code ShopScreen}, not a mode of it. What the two share is the economy:
 * the balance in the header is the same balance {@code /money} prints and the market spends,
 * because the server reads it from one {@code EconomyService} for all three.
 *
 * <h2>What this screen decides, and what it does not</h2>
 * It greys out buttons the player cannot use, and hides the management tab from non-operators —
 * both courtesies, so the reason is visible before the click. It never decides an outcome. Every
 * price, bundle size, limit and permission shown here came from the server, and the server
 * re-checks all of them when a request arrives.
 */
public class ServerShopScreen extends AbstractContainerScreen<ServerShopMenu> {

    // The same vanilla palette as the market window.
    private static final int C_BLACK = 0xFF000000;
    private static final int C_BODY = 0xFFC6C6C6;
    private static final int C_LIGHT = 0xFFFFFFFF;
    private static final int C_DARK = 0xFF555555;
    private static final int C_INSET = 0xFF8B8B8B;
    private static final int C_INSET_DARK = 0xFF373737;
    private static final int C_TEXT = 0xFF303030;
    private static final int C_TEXT_DIM = 0xFF5A5A5A;
    private static final int C_PRICE = 0xFF1B5E20;
    private static final int C_DANGER = 0xFF8B0000;
    private static final int C_SELECT = 0xFFFFD54F;
    private static final int C_HOVER = 0xFFFFFFFF;
    private static final int C_ROW_ALT = 0xFFB8B8B8;
    private static final int C_DISABLED = 0xFF7A7A7A;
    private static final int C_QUOTA_OK = 0xFF1B5E20;
    private static final int C_QUOTA_LOW = 0xFF8A6D00;
    private static final int C_QUOTA_NONE = 0xFF8B0000;
    private static final int C_ACCENT = 0xFF3C82BE;

    /** The largest multiplier the box accepts; the server bounds it again anyway. */
    private static final int MAX_MULTIPLIER = 64;

    // ---- widgets, all created once and shown per page -------------------
    private Button tabSale;
    private Button tabRecycle;
    private Button tabManage;
    private Button pagePrev;
    private Button pageNext;
    private Button refresh;
    private Button qtyMinus;
    private Button qtyPlus;
    private Button buyButton;
    private Button recycleOneButton;
    private Button recycleAllButton;
    private Button adminSaveButton;
    private Button adminRemoveButton;

    private EditBox qtyBox;
    private EditBox sellBox;
    private EditBox buybackBox;
    private EditBox bundleBox;
    private EditBox limitBox;

    // ---- state ---------------------------------------------------------
    private ServerShopTab tab = ServerShopTab.SALE;
    private int page;
    /** Index within the current page, or -1 when nothing is selected. */
    private int selected = -1;
    private int multiplier = 1;
    private int lastHoveredRow = -1;
    /** Which product the management fields were last filled from, so typing is not overwritten. */
    private int filledFrom = -2;
    /** The last status key a sound was played for, so one outcome makes one noise. */
    private String lastSoundKey;

    public ServerShopScreen(ServerShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = ServerShopLayout.GUI_WIDTH;
        this.imageHeight = ServerShopLayout.GUI_HEIGHT;
        this.titleLabelY = -10_000;
        this.inventoryLabelY = -10_000;
    }

    private int gx(int relative) {
        return leftPos + relative;
    }

    private int gy(int relative) {
        return topPos + relative;
    }

    // ==================================================================
    // Setup
    // ==================================================================

    @Override
    protected void init() {
        super.init();

        tabSale = addRenderableWidget(Button.builder(Component.translatable(ServerShopTab.SALE.translationKey()),
                b -> switchTab(ServerShopTab.SALE))
                .bounds(gx(ServerShopLayout.TAB_SALE_X), gy(ServerShopLayout.TAB_Y),
                        ServerShopLayout.TAB_SALE_W, ServerShopLayout.TAB_H).build());
        tabRecycle = addRenderableWidget(Button.builder(
                        Component.translatable(ServerShopTab.RECYCLE.translationKey()),
                b -> switchTab(ServerShopTab.RECYCLE))
                .bounds(gx(ServerShopLayout.TAB_RECYCLE_X), gy(ServerShopLayout.TAB_Y),
                        ServerShopLayout.TAB_RECYCLE_W, ServerShopLayout.TAB_H).build());
        tabManage = addRenderableWidget(Button.builder(
                        Component.translatable(ServerShopTab.MANAGE.translationKey()),
                b -> switchTab(ServerShopTab.MANAGE))
                .bounds(gx(ServerShopLayout.TAB_MANAGE_X), gy(ServerShopLayout.TAB_Y),
                        ServerShopLayout.TAB_MANAGE_W, ServerShopLayout.TAB_H).build());

        pagePrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(gx(ServerShopLayout.PAGE_PREV_X), gy(ServerShopLayout.TAB_Y),
                        ServerShopLayout.PAGE_BTN_W, ServerShopLayout.TAB_H).build());
        pageNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(gx(ServerShopLayout.PAGE_NEXT_X), gy(ServerShopLayout.TAB_Y),
                        ServerShopLayout.PAGE_BTN_W, ServerShopLayout.TAB_H).build());
        refresh = addRenderableWidget(Button.builder(Component.translatable("globalshop.button.refresh"),
                b -> requestPage()).bounds(gx(ServerShopLayout.REFRESH_X), gy(ServerShopLayout.TAB_Y),
                ServerShopLayout.REFRESH_W, ServerShopLayout.TAB_H).build());

        // ---- sale page ----
        qtyMinus = addRenderableWidget(Button.builder(Component.literal("-"), b -> stepMultiplier(-1))
                .bounds(gx(ServerShopLayout.QTY_MINUS_X), gy(ServerShopLayout.QTY_Y - 1),
                        ServerShopLayout.QTY_BTN_W, ServerShopLayout.QTY_BTN_W).build());
        qtyPlus = addRenderableWidget(Button.builder(Component.literal("+"), b -> stepMultiplier(1))
                .bounds(gx(ServerShopLayout.QTY_PLUS_X), gy(ServerShopLayout.QTY_Y - 1),
                        ServerShopLayout.QTY_BTN_W, ServerShopLayout.QTY_BTN_W).build());
        qtyBox = addRenderableWidget(new EditBox(this.font, gx(ServerShopLayout.QTY_BOX_X),
                gy(ServerShopLayout.QTY_Y), ServerShopLayout.QTY_BOX_W, ServerShopLayout.FIELD_H,
                Component.empty()));
        qtyBox.setMaxLength(3);
        qtyBox.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        qtyBox.setValue("1");
        qtyBox.setResponder(text -> parseMultiplier(text).ifPresent(value -> multiplier = value));

        buyButton = addRenderableWidget(Button.builder(Component.translatable("globalshop.servershop.buy"),
                b -> sendBuy()).bounds(gx(ServerShopLayout.BUY_X), gy(ServerShopLayout.BUTTON_Y),
                ServerShopLayout.BUY_W, ServerShopLayout.BUTTON_H).build());

        // ---- recycle page ----
        recycleOneButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.servershop.recycle_one"), b -> sendRecycle(false))
                .bounds(gx(ServerShopLayout.RECYCLE_ONE_X), gy(ServerShopLayout.BUTTON_Y),
                        ServerShopLayout.RECYCLE_ONE_W, ServerShopLayout.BUTTON_H).build());
        recycleAllButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.servershop.recycle_all"), b -> sendRecycle(true))
                .bounds(gx(ServerShopLayout.RECYCLE_ALL_X), gy(ServerShopLayout.BUTTON_Y),
                        ServerShopLayout.RECYCLE_ALL_W, ServerShopLayout.BUTTON_H).build());

        // ---- management page ----
        sellBox = moneyBox(ServerShopLayout.FIELD_SELL_Y);
        buybackBox = moneyBox(ServerShopLayout.FIELD_BUYBACK_Y);
        bundleBox = intBox(ServerShopLayout.FIELD_BUNDLE_Y, 4);
        limitBox = intBox(ServerShopLayout.FIELD_LIMIT_Y, 7);
        adminSaveButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.servershop.admin_save"), b -> sendAdminAdd())
                .bounds(gx(ServerShopLayout.ADMIN_SAVE_X), gy(ServerShopLayout.BUTTON_Y),
                        ServerShopLayout.ADMIN_SAVE_W, ServerShopLayout.BUTTON_H).build());
        adminRemoveButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.servershop.admin_remove"), b -> sendAdminRemove())
                .bounds(gx(ServerShopLayout.ADMIN_REMOVE_X), gy(ServerShopLayout.BUTTON_Y),
                        ServerShopLayout.ADMIN_REMOVE_W, ServerShopLayout.BUTTON_H).build());

        requestPage();
        refreshWidgets();
    }

    private EditBox moneyBox(int relY) {
        EditBox box = new EditBox(this.font, gx(ServerShopLayout.FIELD_X), gy(relY),
                ServerShopLayout.FIELD_W, ServerShopLayout.FIELD_H, Component.empty());
        box.setMaxLength(20);
        box.setFilter(text -> text.isEmpty() || text.matches("[0-9.]{0,20}"));
        return addRenderableWidget(box);
    }

    private EditBox intBox(int relY, int maxLength) {
        EditBox box = new EditBox(this.font, gx(ServerShopLayout.FIELD_X), gy(relY),
                ServerShopLayout.FIELD_W, ServerShopLayout.FIELD_H, Component.empty());
        box.setMaxLength(maxLength);
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        return addRenderableWidget(box);
    }

    // ==================================================================
    // State
    // ==================================================================

    private boolean operator() {
        var data = ClientServerShopCache.data();
        return data != null && data.operator();
    }

    private int pageCount() {
        var data = ClientServerShopCache.data();
        return data == null ? 1 : data.pageCount();
    }

    @Nullable
    private ServerProductView selectedProduct() {
        List<ServerProductView> products = ClientServerShopCache.products();
        if (selected < 0 || selected >= products.size()) {
            return null;
        }
        return products.get(selected);
    }

    private void switchTab(ServerShopTab next) {
        if (tab == next) {
            return;
        }
        tab = next;
        page = 0;
        selected = -1;
        // Reset the template fields so a value typed for one product is not silently applied to
        // another after a tab change.
        filledFrom = -2;
        refreshWidgets();
        requestPage();
    }

    private void changePage(int delta) {
        int next = Math.max(0, Math.min(pageCount() - 1, page + delta));
        if (next != page) {
            page = next;
            selected = -1;
            requestPage();
        }
    }

    private void requestPage() {
        ModNetwork.sendToServer(new RequestServerShopDataPacket(tab, page));
    }

    private void stepMultiplier(int delta) {
        multiplier = Math.max(1, Math.min(MAX_MULTIPLIER, multiplier + delta));
        qtyBox.setValue(Integer.toString(multiplier));
    }

    private int pendingItems(ServerProductView product) {
        return product.bundleSize() * multiplier;
    }

    private long pendingCost(ServerProductView product) {
        try {
            return Math.multiplyExact(product.sellPrice(), (long) pendingItems(product));
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    // ==================================================================
    // Requests
    // ==================================================================

    private void sendBuy() {
        ServerProductView product = selectedProduct();
        if (product != null) {
            ModNetwork.sendToServer(new ServerBuyPacket(product.item(), multiplier, page));
        }
    }

    private void sendRecycle(boolean all) {
        ServerProductView product = selectedProduct();
        if (product != null) {
            ModNetwork.sendToServer(new ServerRecyclePacket(product.item(), all, page));
        }
    }

    private void sendAdminAdd() {
        if (!operator()) {
            return;
        }
        OptionalLong sell = MoneyFormat.parse(sellBox.getValue());
        OptionalLong buyback = parseOptionalMoney(buybackBox.getValue());
        OptionalInt bundle = parsePositive(bundleBox.getValue(), 4096);
        OptionalInt limit = parsePositive(limitBox.getValue(), 1_000_000);
        if (sell.isEmpty() || buyback.isEmpty() || bundle.isEmpty() || limit.isEmpty()) {
            return;
        }
        // The item is not sent: the server takes it from the slot it owns.
        ModNetwork.sendToServer(new ServerProductAdminPacket(ServerProductAdminPacket.Action.ADD,
                ItemStack.EMPTY, sell.getAsLong(), buyback.getAsLong(), bundle.getAsInt(),
                limit.getAsInt(), page));
    }

    private void sendAdminRemove() {
        ServerProductView product = selectedProduct();
        if (product == null || !operator()) {
            return;
        }
        ModNetwork.sendToServer(new ServerProductAdminPacket(ServerProductAdminPacket.Action.REMOVE,
                product.item(), 0L, 0L, 1, 0, page));
    }

    // ==================================================================
    // Per-tick housekeeping
    // ==================================================================

    @Override
    public void containerTick() {
        super.containerTick();

        var data = ClientServerShopCache.data();
        if (data != null) {
            if (data.tab() != tab) {
                // The server refused the page — a non-operator asking for the management tab,
                // most likely. Follow the server rather than keep asking.
                tab = data.tab();
                selected = -1;
            }
            if (data.page() != page) {
                page = data.page();
                selected = -1;
            }
        }

        List<ServerProductView> products = ClientServerShopCache.products();
        if (products.isEmpty()) {
            selected = -1;
        } else if (selected < 0 || selected >= products.size()) {
            selected = Math.max(0, Math.min(products.size() - 1, selected));
        }

        if (tab == ServerShopTab.MANAGE) {
            fillFieldsFromSelection();
        }
        refreshWidgets();

        // The same sound the market plays, for the same reason: the verdict comes from the
        // server's result, so a trade that looked available but was refused sounds refused.
        String statusKey = ClientServerShopCache.statusKey();
        if (statusKey != null && !statusKey.equals(lastSoundKey)) {
            lastSoundKey = statusKey;
            playResultSound(ClientServerShopCache.statusSuccess());
        }
    }

    private void playResultSound(boolean success) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        this.minecraft.player.playSound(
                success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                0.7F, success ? 1.0F : 0.8F);
    }

    /**
     * Copies the selected line into the fields, so an operator can edit rather than retype.
     *
     * <p>Only done when the selection changes: re-filling every tick would erase what is being
     * typed the moment it differed from what is stored.
     */
    private void fillFieldsFromSelection() {
        if (filledFrom == selected) {
            return;
        }
        filledFrom = selected;
        ServerProductView product = selectedProduct();
        if (product == null) {
            return;
        }
        sellBox.setValue(MoneyFormat.format(product.sellPrice()));
        buybackBox.setValue(product.allowBuyback() ? MoneyFormat.format(product.buybackPrice()) : "0");
        bundleBox.setValue(Integer.toString(product.bundleSize()));
        limitBox.setValue(Integer.toString(Math.max(0, product.purchaseLimit())));
    }

    /** Shows exactly the widgets the current page needs. */
    private void refreshWidgets() {
        boolean canManage = operator();
        tabManage.visible = canManage;
        if (!canManage && tab == ServerShopTab.MANAGE) {
            // Cannot happen through the UI, but a stale tab from before a permission change
            // would otherwise draw a page with nothing on it.
            tab = ServerShopTab.SALE;
        }

        tabSale.active = tab != ServerShopTab.SALE;
        tabRecycle.active = tab != ServerShopTab.RECYCLE;
        tabManage.active = canManage && tab != ServerShopTab.MANAGE;

        ServerProductView product = selectedProduct();
        boolean has = product != null;

        boolean sale = tab == ServerShopTab.SALE;
        boolean recycle = tab == ServerShopTab.RECYCLE;
        boolean manage = tab == ServerShopTab.MANAGE;

        boolean affordable = false;
        boolean withinLimit = false;
        if (has) {
            affordable = ClientServerShopCache.balance() >= pendingCost(product);
            int remaining = product.remainingForViewer();
            withinLimit = remaining == Integer.MAX_VALUE || pendingItems(product) <= remaining;
        }
        boolean canRecycle = has && product.allowBuyback() && product.carried() >= 1
                && ClientServerShopCache.remainingToday() > 0;

        qtyBox.setVisible(sale);
        qtyMinus.visible = sale;
        qtyPlus.visible = sale;
        buyButton.visible = sale;
        buyButton.active = has && affordable && withinLimit;

        recycleOneButton.visible = recycle;
        recycleAllButton.visible = recycle;
        recycleOneButton.active = canRecycle;
        recycleAllButton.active = canRecycle;

        sellBox.setVisible(manage);
        buybackBox.setVisible(manage);
        bundleBox.setVisible(manage);
        limitBox.setVisible(manage);
        adminSaveButton.visible = manage;
        adminRemoveButton.visible = manage;
        adminSaveButton.active = manage;
        adminRemoveButton.active = manage && has;

        // A hidden box must not keep keyboard focus, or it would silently swallow typing.
        for (EditBox box : List.of(qtyBox, sellBox, buybackBox, bundleBox, limitBox)) {
            if (!box.isVisible()) {
                box.setFocused(false);
            }
        }

        pagePrev.active = page > 0;
        pageNext.active = page < pageCount() - 1;

        // The product slot and the hotbar row exist only on the management page, so a player has
        // nothing to click and no way to leave an item somewhere they cannot see it.
        menu.setSlotsActive(manage);
    }

    // ==================================================================
    // Input
    // ==================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                selected = row;
                multiplier = 1;
                qtyBox.setValue("1");
                refreshWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (rowAt(mouseX, mouseY) >= 0) {
            changePage(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Index into the current page for a screen position, or {@code -1}. */
    private int rowAt(double mouseX, double mouseY) {
        int relX = (int) mouseX - gx(ServerShopLayout.LIST_X);
        int relY = (int) mouseY - gy(ServerShopLayout.LIST_Y);
        if (relX < 0 || relX >= ServerShopLayout.LIST_W || relY < 0) {
            return -1;
        }
        int row = relY / ServerShopLayout.ROW_H;
        if (row >= ServerShopLayout.ROWS) {
            return -1;
        }
        return row < ClientServerShopCache.products().size() ? row : -1;
    }

    // ==================================================================
    // Drawing primitives
    // ==================================================================

    private void raised(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1 + 1, y1 + 1, C_BLACK);
        graphics.fill(x0 + 1, y0 + 1, x1, y1, C_BODY);
        graphics.fill(x0 + 1, y0 + 1, x1, y0 + 2, C_LIGHT);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1, C_LIGHT);
        graphics.fill(x0 + 1, y1 - 1, x1, y1, C_DARK);
        graphics.fill(x1 - 1, y0 + 1, x1, y1, C_DARK);
    }

    private void sunken(GuiGraphics graphics, int x0, int y0, int x1, int y1, int face) {
        graphics.fill(x0, y0, x1 + 1, y1 + 1, C_BLACK);
        graphics.fill(x0 + 1, y0 + 1, x1, y1, face);
        graphics.fill(x0 + 1, y0 + 1, x1, y0 + 2, C_INSET_DARK);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y1, C_INSET_DARK);
        graphics.fill(x0 + 1, y1 - 1, x1, y1, C_LIGHT);
        graphics.fill(x1 - 1, y0 + 1, x1, y1, C_LIGHT);
    }

    private void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1 + 1, y0 + 1, colour);
        graphics.fill(x0, y1, x1 + 1, y1 + 1, colour);
        graphics.fill(x0, y0, x0 + 1, y1 + 1, colour);
        graphics.fill(x1, y0, x1 + 1, y1 + 1, colour);
    }

    /** A vanilla-styled 18x18 slot frame, used as a list row's icon well. */
    private void slotCell(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, C_INSET_DARK);
        graphics.fill(x, y, x + 16, y + 16, C_INSET);
        graphics.fill(x, y, x + 16, y + 1, C_INSET_DARK);
        graphics.fill(x, y, x + 1, y + 16, C_INSET_DARK);
        graphics.fill(x, y + 15, x + 16, y + 16, C_LIGHT);
        graphics.fill(x + 15, y, x + 16, y + 16, C_LIGHT);
    }

    // ==================================================================
    // Background
    // ==================================================================

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        raised(graphics, gx(0), gy(0), gx(ServerShopLayout.GUI_WIDTH - 1),
                gy(ServerShopLayout.GUI_HEIGHT - 1));

        graphics.fill(gx(0), gy(ServerShopLayout.SEPARATOR_Y), gx(ServerShopLayout.GUI_WIDTH),
                gy(ServerShopLayout.SEPARATOR_Y + 1), C_DARK);
        graphics.fill(gx(0), gy(ServerShopLayout.SEPARATOR_Y + 1), gx(ServerShopLayout.GUI_WIDTH),
                gy(ServerShopLayout.SEPARATOR_Y + 2), C_LIGHT);

        int listBottom = ServerShopLayout.LIST_Y + ServerShopLayout.ROWS * ServerShopLayout.ROW_H;
        sunken(graphics, gx(ServerShopLayout.LIST_X - 3), gy(ServerShopLayout.LIST_Y - 3),
                gx(ServerShopLayout.LIST_X + ServerShopLayout.LIST_W + 1), gy(listBottom + 1), C_INSET);
        for (int row = 0; row < ServerShopLayout.ROWS; row++) {
            int y = gy(ServerShopLayout.LIST_Y + row * ServerShopLayout.ROW_H);
            slotCell(graphics, gx(ServerShopLayout.LIST_X) + ServerShopLayout.ROW_ICON_DX,
                    y + ServerShopLayout.ROW_ICON_DY);
        }

        int panelHeight = tab == ServerShopTab.MANAGE
                ? ServerShopLayout.MANAGE_PANEL_H
                : ServerShopLayout.PANEL_H;
        sunken(graphics, gx(ServerShopLayout.PANEL_X), gy(ServerShopLayout.PANEL_Y),
                gx(ServerShopLayout.PANEL_X + ServerShopLayout.PANEL_W - 1),
                gy(ServerShopLayout.PANEL_Y + panelHeight - 1), C_INSET);

        if (tab == ServerShopTab.MANAGE) {
            // The product slot and the hotbar row, both real slots drawn by the base class while
            // they are active. Only their wells are drawn here, so the two cannot drift apart.
            slotCell(graphics, gx(ServerShopLayout.TEMPLATE_SLOT_X),
                    gy(ServerShopLayout.TEMPLATE_SLOT_Y));
            outline(graphics, gx(ServerShopLayout.TEMPLATE_SLOT_X) - 1,
                    gy(ServerShopLayout.TEMPLATE_SLOT_Y) - 1,
                    gx(ServerShopLayout.TEMPLATE_SLOT_X) + 16,
                    gy(ServerShopLayout.TEMPLATE_SLOT_Y) + 16, C_ACCENT);
            for (int column = 0; column < ServerShopLayout.INV_COLUMNS; column++) {
                slotCell(graphics, gx(ServerShopLayout.INV_X + column * 18),
                        gy(ServerShopLayout.INV_ROW_Y));
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Intentionally empty: all text is drawn from render(), in one coordinate space.
    }

    /**
     * Suppressed, as in the market window.
     *
     * <p>{@link #drawHoverTooltip} answers for every tooltip this screen shows, so letting the
     * base class draw its own would stack a second identical box on top.
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
        if (this.minecraft == null) {
            return;
        }

        List<ServerProductView> products = ClientServerShopCache.products();
        lastHoveredRow = rowAt(mouseX, mouseY);

        draw(graphics, Component.translatable("globalshop.servershop.title"),
                ServerShopLayout.TITLE_X, ServerShopLayout.TITLE_Y, C_TEXT);
        drawRight(graphics, truncateComponent(
                        Component.translatable("globalshop.label.balance",
                                MoneyFormat.formatCompact(ClientServerShopCache.balance())),
                        ServerShopLayout.BALANCE_MAX_W),
                ServerShopLayout.BALANCE_RIGHT, ServerShopLayout.TITLE_Y, C_PRICE);

        drawList(graphics, products);
        draw(graphics, (page + 1) + "/" + pageCount(), ServerShopLayout.PAGE_TEXT_X,
                ServerShopLayout.TAB_Y + 4, C_TEXT);

        switch (tab) {
            case RECYCLE -> drawRecyclePanel(graphics, selectedProduct());
            case MANAGE -> drawManagePanel(graphics, selectedProduct());
            default -> drawSalePanel(graphics, selectedProduct());
        }

        // ---- outcome of the last trade or edit ----
        Component status = ClientServerShopCache.status();
        if (status != null) {
            Component fitted = truncateComponent(status, ServerShopLayout.GUI_WIDTH - 8);
            int colour = ClientServerShopCache.statusIsFailure() ? 0xFFFF8080 : 0xFF80FF80;
            draw(graphics, fitted, ServerShopLayout.GUI_WIDTH / 2 - this.font.width(fitted) / 2,
                    ServerShopLayout.STATUS_Y, colour);
        }

        drawHoverTooltip(graphics, mouseX, mouseY);
    }

    private void drawList(GuiGraphics graphics, List<ServerProductView> products) {
        for (int row = 0; row < products.size() && row < ServerShopLayout.ROWS; row++) {
            ServerProductView product = products.get(row);
            int y = ServerShopLayout.LIST_Y + row * ServerShopLayout.ROW_H;
            int left = gx(ServerShopLayout.LIST_X);
            int right = left + ServerShopLayout.LIST_W;

            if (row == selected) {
                graphics.fill(left, gy(y), right, gy(y + ServerShopLayout.ROW_H), C_SELECT & 0x40FFFFFF);
                outline(graphics, left - 1, gy(y) - 1, right, gy(y + ServerShopLayout.ROW_H), C_SELECT);
            } else if (row == lastHoveredRow) {
                graphics.fill(left, gy(y), right, gy(y + ServerShopLayout.ROW_H), C_HOVER & 0x30FFFFFF);
            } else if (row % 2 == 1) {
                graphics.fill(left, gy(y), right, gy(y + ServerShopLayout.ROW_H), C_ROW_ALT);
            }

            int iconX = left + ServerShopLayout.ROW_ICON_DX;
            int iconY = gy(y + ServerShopLayout.ROW_ICON_DY);
            graphics.renderItem(product.item(), iconX, iconY);
            graphics.renderItemDecorations(this.font, product.item(), iconX, iconY);

            // Each page shows the number that matters to it: what it costs to buy, or what the
            // server pays to buy it back.
            long shown = tab == ServerShopTab.RECYCLE ? product.buybackPrice() : product.sellPrice();
            String price = MoneyFormat.format(shown);
            int priceRight = left + ServerShopLayout.ROW_PRICE_RIGHT;
            int textX = left + ServerShopLayout.ROW_TEXT_DX;
            int nameMax = priceRight - this.font.width(price) - 4 - textX;
            boolean exhausted = tab == ServerShopTab.SALE && product.soldOutForViewer();
            draw(graphics, truncate(product.item().getHoverName().getString(), Math.max(20, nameMax)),
                    textX - left, y + ServerShopLayout.ROW_TEXT_DY, exhausted ? C_DISABLED : C_TEXT);
            drawRight(graphics, Component.literal(price),
                    priceRight - left, y + ServerShopLayout.ROW_TEXT_DY,
                    exhausted ? C_DISABLED : C_PRICE);
        }
    }

    /**
     * The panel header both trading pages share: the item, its durability, and what is on it.
     *
     * @return the y the calling page should start its own rows at
     */
    private void drawItemHeader(GuiGraphics graphics, ServerProductView product) {
        int left = ServerShopLayout.P_X;
        int right = ServerShopLayout.PANEL_RIGHT;
        ItemStack display = product.item();
        String durability = ItemInfoText.durability(display);

        int iconX = gx(ServerShopLayout.P_ICON_X);
        int iconY = gy(ServerShopLayout.P_ICON_Y);
        graphics.renderItem(display, iconX, iconY);
        graphics.renderItemDecorations(this.font, display, iconX, iconY);

        int nameMax = right - (left + ServerShopLayout.P_ICON_TEXT_DX) - 6
                - (durability == null ? 0 : this.font.width(durability));
        draw(graphics, truncate(display.getHoverName().getString(), Math.max(30, nameMax)),
                left + ServerShopLayout.P_ICON_TEXT_DX, ServerShopLayout.P_NAME_Y + 2, C_TEXT);
        if (durability != null) {
            drawRight(graphics, Component.literal(durability), right,
                    ServerShopLayout.P_NAME_Y + 2, ItemInfoText.durabilityColour(display));
        }
        String enchantments = ItemInfoText.enchantments(display, this.font,
                ServerShopLayout.PANEL_W - 30);
        if (!enchantments.isEmpty()) {
            draw(graphics, truncate(enchantments, ServerShopLayout.PANEL_W - 30),
                    left + ServerShopLayout.P_ICON_TEXT_DX,
                    ServerShopLayout.P_ENCH_Y, ItemInfoText.COLOUR_ENCHANT);
        }
    }

    private void drawEmptyPanel(GuiGraphics graphics) {
        draw(graphics, Component.translatable("globalshop.servershop.nothing_selected"),
                ServerShopLayout.P_X, ServerShopLayout.P_R1_Y + 12, C_TEXT_DIM);
    }

    /** Buying: price, bundle, the purchase limit, and the running total. */
    private void drawSalePanel(GuiGraphics graphics, @Nullable ServerProductView product) {
        int left = ServerShopLayout.P_X;
        int inner = ServerShopLayout.PANEL_INNER;
        if (product == null) {
            drawEmptyPanel(graphics);
            return;
        }
        drawItemHeader(graphics, product);

        draw(graphics, truncateComponent(Component.translatable("globalshop.servershop.sell_price",
                MoneyFormat.formatWithName(product.sellPrice())), inner),
                left, ServerShopLayout.P_R1_Y, C_PRICE);
        draw(graphics, Component.translatable("globalshop.servershop.bundle", product.bundleSize()),
                left, ServerShopLayout.P_R2_Y, C_TEXT);

        Component purchaseLine = product.purchaseLimit() <= 0
                ? Component.translatable("globalshop.servershop.purchased_unlimited", product.purchased())
                : Component.translatable("globalshop.servershop.purchased", product.purchased(),
                        product.purchaseLimit(), product.remainingForViewer());
        draw(graphics, truncateComponent(purchaseLine, inner), left, ServerShopLayout.P_R3_Y,
                product.soldOutForViewer() ? C_DANGER : C_TEXT);

        draw(graphics, Component.translatable("globalshop.servershop.carried", product.carried()),
                left, ServerShopLayout.P_R4_Y, C_TEXT_DIM);

        int items = pendingItems(product);
        draw(graphics, truncate(MoneyFormat.format(product.sellPrice()) + " x " + items + " = "
                + MoneyFormat.format(pendingCost(product)), inner),
                left, ServerShopLayout.P_R5_Y, C_PRICE);

        draw(graphics, Component.translatable("globalshop.servershop.multiplier"),
                ServerShopLayout.QTY_LABEL_X, ServerShopLayout.QTY_Y + 3, C_TEXT);
        draw(graphics, truncateComponent(saleHint(product), inner),
                left, ServerShopLayout.P_HINT_Y, C_TEXT_DIM);
    }

    /** Selling back: what it pays, how many are held, and the daily allowance. */
    private void drawRecyclePanel(GuiGraphics graphics, @Nullable ServerProductView product) {
        int left = ServerShopLayout.P_X;
        int inner = ServerShopLayout.PANEL_INNER;

        // The allowance describes the player, not the selection, so it is drawn either way.
        int limit = ClientServerShopCache.dailyLimit();
        int used = ClientServerShopCache.recycledToday();
        int remaining = ClientServerShopCache.remainingToday();
        int quotaColour = remaining <= 0 ? C_QUOTA_NONE
                : (remaining <= Math.max(1, limit / 4) ? C_QUOTA_LOW : C_QUOTA_OK);

        if (product == null) {
            drawEmptyPanel(graphics);
            draw(graphics, Component.translatable("globalshop.servershop.quota_used", used, limit),
                    left, ServerShopLayout.P_R5_Y, quotaColour);
            draw(graphics, Component.translatable("globalshop.servershop.quota_left", remaining),
                    left, ServerShopLayout.P_R1_Y + 12, quotaColour);
            return;
        }
        drawItemHeader(graphics, product);

        draw(graphics, truncateComponent(Component.translatable("globalshop.servershop.buyback_price",
                MoneyFormat.formatWithName(product.buybackPrice())), inner),
                left, ServerShopLayout.P_R1_Y, C_PRICE);
        draw(graphics, Component.translatable("globalshop.servershop.carried", product.carried()),
                left, ServerShopLayout.P_R2_Y, C_TEXT);
        draw(graphics, truncateComponent(
                        Component.translatable("globalshop.servershop.quota_used", used, limit), inner),
                left, ServerShopLayout.P_R3_Y, quotaColour);
        draw(graphics, truncateComponent(
                        Component.translatable("globalshop.servershop.quota_left", remaining), inner),
                left, ServerShopLayout.P_R4_Y, quotaColour);

        // What selling everything held would pay, capped at the allowance so the number is the
        // one the player would actually receive rather than a wish.
        int sellable = Math.min(product.carried(), remaining);
        long income;
        try {
            income = Math.multiplyExact(product.buybackPrice(), (long) sellable);
        } catch (ArithmeticException overflow) {
            income = Long.MAX_VALUE;
        }
        draw(graphics, truncateComponent(Component.translatable("globalshop.servershop.recycle_value",
                sellable, MoneyFormat.formatWithName(income)), inner),
                left, ServerShopLayout.P_R5_Y, C_PRICE);

        draw(graphics, truncateComponent(recycleHint(product, remaining), inner),
                left, ServerShopLayout.P_HINT_Y, C_TEXT_DIM);
    }

    /**
     * Writing the catalogue.
     *
     * <p>The item is taken from the slot rather than from the fields, so an operator describes a
     * real stack — including its enchantments and damage — instead of typing an id. Selecting a
     * line on the left fills the fields, so editing is a change rather than a retype.
     */
    private void drawManagePanel(GuiGraphics graphics, @Nullable ServerProductView product) {
        int left = ServerShopLayout.P_X;
        int inner = ServerShopLayout.PANEL_INNER;

        if (product != null) {
            draw(graphics, truncate(product.item().getHoverName().getString(), inner),
                    left, ServerShopLayout.P_NAME_Y, C_TEXT);
        } else {
            draw(graphics, Component.translatable("globalshop.servershop.admin_heading"),
                    left, ServerShopLayout.P_NAME_Y, C_TEXT);
        }
        // The slot draws the item; what is left to say is what to do with it.
        draw(graphics, truncateComponent(
                        Component.translatable("globalshop.servershop.admin_new_hint"), inner),
                left, ServerShopLayout.P_ENCH_Y, C_TEXT_DIM);

        draw(graphics, Component.translatable("globalshop.servershop.field_sell"),
                left, ServerShopLayout.FIELD_SELL_Y + 3, C_TEXT);
        draw(graphics, Component.translatable("globalshop.servershop.field_buyback"),
                left, ServerShopLayout.FIELD_BUYBACK_Y + 3, C_TEXT);
        draw(graphics, Component.translatable("globalshop.servershop.field_bundle"),
                left, ServerShopLayout.FIELD_BUNDLE_Y + 3, C_TEXT);
        draw(graphics, Component.translatable("globalshop.servershop.field_limit"),
                left, ServerShopLayout.FIELD_LIMIT_Y + 3, C_TEXT);

        // Two short lines rather than one long one: the labels beside the boxes leave barely
        // fifty pixels, and "Sell-back 0 = not bought back, limit 0 = no limit" does not fit it.
        draw(graphics, truncateComponent(
                        Component.translatable("globalshop.servershop.admin_zero_hint"), inner),
                left, ServerShopLayout.P_HINT2_Y, C_TEXT_DIM);
        draw(graphics, truncateComponent(
                        Component.translatable("globalshop.servershop.admin_limit_hint"), inner),
                left, ServerShopLayout.P_HINT_Y, C_TEXT_DIM);
    }

    /** Whether the cursor is over the product slot in the management panel. */
    private boolean templateSlotHovered(double mouseX, double mouseY) {
        int x = gx(ServerShopLayout.TEMPLATE_SLOT_X);
        int y = gy(ServerShopLayout.TEMPLATE_SLOT_Y);
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    private Component saleHint(ServerProductView product) {
        if (product.soldOutForViewer()) {
            return Component.translatable("globalshop.servershop.limit_reached");
        }
        if (pendingCost(product) > ClientServerShopCache.balance()) {
            return Component.translatable("globalshop.servershop.cannot_afford");
        }
        return Component.translatable("globalshop.servershop.sale_hint");
    }

    private Component recycleHint(ServerProductView product, int remaining) {
        if (remaining <= 0) {
            return Component.translatable("globalshop.servershop.quota_exhausted");
        }
        if (product.carried() <= 0) {
            return Component.translatable("globalshop.servershop.not_carrying");
        }
        if (product.carried() > remaining) {
            return Component.translatable("globalshop.servershop.recycle_partial", remaining);
        }
        return Component.translatable("globalshop.servershop.recycle_hint");
    }

    // ==================================================================
    // Tooltips
    // ==================================================================

    /**
     * The tooltip for whatever is under the cursor: a product row, or the panel's icon.
     *
     * <p>This screen owns its tooltips rather than leaving them to the base class, for the same
     * reason the market window does — see {@code ShopScreen}. The content is vanilla's own
     * {@code ItemStack.getTooltipLines}, so enchantments, durability, potion effects, lore and
     * anything a modded item adds all arrive for free.
     */
    private void drawHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ServerProductView> products = ClientServerShopCache.products();
        ItemStack stack = null;
        if (lastHoveredRow >= 0 && lastHoveredRow < products.size()) {
            stack = products.get(lastHoveredRow).item();
        } else if (panelIconHovered(mouseX, mouseY)) {
            ServerProductView product = selectedProduct();
            stack = product == null ? null : product.item();
        } else if (tab == ServerShopTab.MANAGE && this.hoveredSlot != null
                && this.hoveredSlot.hasItem()) {
            // The operator's own product slot or hotbar, so they can check what they are about
            // to stock.
            stack = this.hoveredSlot.getItem();
        }
        if (stack == null) {
            return;
        }
        TooltipFlag flag = this.minecraft.options.advancedItemTooltips
                ? TooltipFlag.Default.ADVANCED
                : TooltipFlag.Default.NORMAL;
        graphics.renderTooltip(this.font, stack.getTooltipLines(this.minecraft.player, flag),
                stack.getTooltipImage(), mouseX, mouseY);
    }

    private boolean panelIconHovered(double mouseX, double mouseY) {
        int x = gx(ServerShopLayout.P_ICON_X);
        int y = gy(ServerShopLayout.P_ICON_Y);
        return mouseX >= x && mouseX < x + ServerShopLayout.P_ICON_SIZE
                && mouseY >= y && mouseY < y + ServerShopLayout.P_ICON_SIZE;
    }

    // ==================================================================
    // Text helpers
    // ==================================================================

    private void draw(GuiGraphics graphics, Component text, int x, int y, int colour) {
        graphics.drawString(this.font, text, gx(x), gy(y), colour, false);
    }

    private void draw(GuiGraphics graphics, String text, int x, int y, int colour) {
        graphics.drawString(this.font, text, gx(x), gy(y), colour, false);
    }

    private void drawRight(GuiGraphics graphics, Component text, int right, int y, int colour) {
        graphics.drawString(this.font, text, gx(right) - this.font.width(text), gy(y), colour, false);
    }

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

    private Component truncateComponent(Component text, int maxWidth) {
        String flat = text.getString();
        return this.font.width(flat) <= maxWidth ? text : Component.literal(truncate(flat, maxWidth));
    }

    private static OptionalInt parseMultiplier(String text) {
        return parsePositive(text, MAX_MULTIPLIER);
    }

    private static OptionalInt parsePositive(String text, int max) {
        if (text == null || text.isBlank()) {
            // An empty box means the lower bound, which is what lets the management page say
            // "no limit" by simply being left alone.
            return OptionalInt.of(0);
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 0 ? OptionalInt.of(Math.min(max, value)) : OptionalInt.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    /** Like {@link MoneyFormat#parse} but treats blank as zero, for the optional buy-back price. */
    private static OptionalLong parseOptionalMoney(String text) {
        if (text == null || text.isBlank()) {
            return OptionalLong.of(0L);
        }
        return MoneyFormat.parse(text);
    }
}
