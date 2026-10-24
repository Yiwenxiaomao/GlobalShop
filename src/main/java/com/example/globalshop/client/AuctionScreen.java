package com.example.globalshop.client;

import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.AuctionActionPacket;
import com.example.globalshop.network.packet.AuctionCreatePacket;
import com.example.globalshop.network.packet.AuctionDataPacket;
import com.example.globalshop.network.packet.AuctionToastPacket;
import com.example.globalshop.network.packet.RequestAuctionsPacket;
import com.example.globalshop.economy.MoneyFormat;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The auction house window.
 *
 * <p>A plain {@link Screen} rather than a container screen: nothing here is dragged, and putting the
 * player's backpack on screen would only invite clicking it. The one item that matters — the stack
 * being offered — is read from the player's own hand by the server, so there is no slot to fake and
 * nothing for the client to be trusted with.
 *
 * <p>The countdown is recomputed from {@link ClientAuction#serverNow()} on every frame, so it ticks
 * smoothly without asking the server for the time, and two players looking at the same auction
 * count down to the same instant even if their machine clocks disagree by hours.
 */
public class AuctionScreen extends Screen {

    private static final int WIN_W = 400;
    private static final int WIN_H = 220;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_H = 20;
    private static final int LIST_Y = 38;

    private static final int C_TITLE = 0xFFFFD54F;
    private static final int C_TEXT = 0xFFEEEEEE;
    private static final int C_DIM = 0xFF9E9E9E;
    private static final int C_MONEY = 0xFFFFD700;
    private static final int C_URGENT = 0xFFFF5555;
    private static final int C_MINE = 0xFF66D9FF;
    private static final int C_PANEL = 0xF0121218;
    private static final int C_ROW = 0x30FFFFFF;
    private static final int C_SELECTED = 0x60FFD54F;

    /**
     * Column positions, measured from the window's left edge.
     *
     * <p>These are named and kept in one place because the paging arrows share the header row. The
     * first attempt had the "ends in" column start at 292 and the arrows at 300 and 322, so the
     * column header ran straight underneath the back arrow. Every column now stops short of the
     * paging group, and the page number sits between the two arrows where a reader looks for it.
     */
    private static final int COL_ITEM = 8;
    private static final int COL_ICON = 9;
    private static final int COL_NAME = 30;
    private static final int COL_NAME_W = 84;
    private static final int COL_PRICE = 118;
    private static final int COL_BUYOUT = 180;
    private static final int COL_ENDS = 236;
    private static final int COL_SELLER = 292;
    private static final int COL_SELLER_W = 46;
    private static final int PAGING_BUTTON_W = 14;
    private static final int PAGING_PREV_X = 348;
    private static final int PAGING_NEXT_X = 384;
    /** The centre of the gap between the two arrows. */
    private static final int PAGING_TEXT_CENTRE = 373;

    private int left;
    private int top;

    private EditBox searchBox;
    private EditBox bidBox;

    // The create form, only built when that mode is entered.
    private EditBox startPriceBox;
    private EditBox incrementBox;
    private EditBox buyoutBox;
    private EditBox minutesBox;

    private Button sortEndingButton;
    private Button sortNewestButton;
    private Button bidButton;
    private Button buyoutButton;
    private Button sellButton;
    private Button refreshButton;
    private Button closeButton;
    private Button prevButton;
    private Button nextButton;
    private Button confirmButton;
    private Button backButton;

    private boolean creating;
    private int sort = AuctionDataPacket.AuctionSort.ENDING_SOONEST;
    private int page;
    @Nullable
    private UUID selected;
    @Nullable
    private Component toast;
    private long toastUntil;
    private long lastRequestAt;

    public AuctionScreen() {
        super(Component.translatable("globalshop.auction.title"));
    }

    // ==================================================================
    // Layout
    // ==================================================================

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;

        searchBox = addRenderableWidget(new EditBox(this.font, left + 6, top + 6, 138, 16,
                Component.translatable("globalshop.auction.search")));
        searchBox.setMaxLength(48);
        searchBox.setValue(ClientAuction.data() == null ? "" : ClientAuction.data().query());

        bidBox = addRenderableWidget(new EditBox(this.font, left + 6, top + WIN_H - 18, 88, 14,
                Component.translatable("globalshop.auction.bid")));
        bidBox.setMaxLength(AuctionActionPacket.MAX_AMOUNT_TEXT);

        startPriceBox = addRenderableWidget(new EditBox(this.font, left + 130, top + 60, 120, 16,
                Component.translatable("globalshop.auction.create.start")));
        startPriceBox.setMaxLength(AuctionCreatePacket.MAX_AMOUNT_TEXT);
        incrementBox = addRenderableWidget(new EditBox(this.font, left + 130, top + 84, 120, 16,
                Component.translatable("globalshop.auction.create.increment")));
        incrementBox.setMaxLength(AuctionCreatePacket.MAX_AMOUNT_TEXT);
        buyoutBox = addRenderableWidget(new EditBox(this.font, left + 130, top + 108, 120, 16,
                Component.translatable("globalshop.auction.create.buyout")));
        buyoutBox.setMaxLength(AuctionCreatePacket.MAX_AMOUNT_TEXT);
        minutesBox = addRenderableWidget(new EditBox(this.font, left + 130, top + 132, 120, 16,
                Component.translatable("globalshop.auction.create.minutes")));
        minutesBox.setMaxLength(6);
        minutesBox.setValue(String.valueOf(Math.max(1, com.example.globalshop.Config.auctionMaxMinutes())));

        int bar = top + WIN_H - 18;
        bidButton = barButton(96, bar, 44, "globalshop.auction.bid", b -> bid());
        buyoutButton = barButton(144, bar, 56, "globalshop.auction.buyout", b -> buyout());
        sellButton = barButton(204, bar, 44, "globalshop.auction.sell", b -> setCreating(true));
        refreshButton = barButton(252, bar, 48, "globalshop.auction.refresh", b -> request());
        closeButton = barButton(304, bar, 48, "globalshop.auction.close", b -> onClose());

        // Paging sits on the header row, with the page number between the arrows.
        prevButton = barButton(PAGING_PREV_X, 25, PAGING_BUTTON_W, "globalshop.auction.prev",
                b -> turnPage(-1));
        nextButton = barButton(PAGING_NEXT_X, 25, PAGING_BUTTON_W, "globalshop.auction.next",
                b -> turnPage(1));
        prevButton.setMessage(Component.literal("<"));
        nextButton.setMessage(Component.literal(">"));

        sortEndingButton = barButton(146, 6, 50, "globalshop.auction.sort.ending",
                b -> setSort(AuctionDataPacket.AuctionSort.ENDING_SOONEST));
        sortNewestButton = barButton(198, 6, 50, "globalshop.auction.sort.newest",
                b -> setSort(AuctionDataPacket.AuctionSort.NEWEST));

        confirmButton = barButton(130, top + 162 - top, 120, "globalshop.auction.create.confirm",
                b -> confirmCreate());
        backButton = barButton(258, 162, 60, "globalshop.auction.create.back",
                b -> setCreating(false));

        refreshWidgets();
        // First open, or re-open after closing: always a fresh list and a fresh clock reading.
        request();
    }

    private Button barButton(int x, int y, int width, String key,
                             Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.translatable(key), action)
                .bounds(left + x, top + y, width, 16).build());
    }

    private void refreshWidgets() {
        boolean listing = !creating;
        searchBox.setVisible(listing);
        bidBox.setVisible(listing);
        sortEndingButton.visible = listing;
        sortNewestButton.visible = listing;
        bidButton.visible = listing;
        buyoutButton.visible = listing;
        sellButton.visible = listing;
        refreshButton.visible = listing;
        closeButton.visible = true;
        prevButton.visible = listing;
        nextButton.visible = listing;

        startPriceBox.setVisible(creating);
        incrementBox.setVisible(creating);
        buyoutBox.setVisible(creating);
        minutesBox.setVisible(creating);
        confirmButton.visible = creating;
        backButton.visible = creating;

        // The bid box starts at the smallest amount that would be accepted, so the common case is
        // one click. It is a hint only: the server decides, and refuses anything below its minimum.
        AuctionDataPacket.Row row = selectedRow();
        if (listing && row != null && bidBox.getValue().isBlank()) {
            bidBox.setValue(MoneyFormat.format(minimumBid(row)));
        }
    }

    private void setCreating(boolean value) {
        this.creating = value;
        refreshWidgets();
    }

    private void setSort(int value) {
        this.sort = value;
        this.page = 0;
        request();
    }

    private void turnPage(int delta) {
        int pages = pageCount();
        page = Math.max(0, Math.min(pages - 1, page + delta));
    }

    private int pageCount() {
        int size = ClientAuction.rows().size();
        return Math.max(1, (size + ROWS_VISIBLE - 1) / ROWS_VISIBLE);
    }

    // ==================================================================
    // Talking to the server
    // ==================================================================

    /** Asks for the list. Also the heartbeat that tells the server this window is open. */
    private void request() {
        lastRequestAt = System.currentTimeMillis();
        ModNetwork.sendToServer(new RequestAuctionsPacket(searchBox.getValue(), sort, true));
    }

    private void bid() {
        AuctionDataPacket.Row row = selectedRow();
        if (row == null) {
            return;
        }
        ModNetwork.sendToServer(new AuctionActionPacket(AuctionActionPacket.BID, row.id(),
                bidBox.getValue()));
    }

    private void buyout() {
        AuctionDataPacket.Row row = selectedRow();
        if (row == null) {
            return;
        }
        ModNetwork.sendToServer(new AuctionActionPacket(AuctionActionPacket.BUYOUT, row.id(), ""));
    }

    private void confirmCreate() {
        long minutes;
        try {
            minutes = Long.parseLong(minutesBox.getValue().trim());
        } catch (NumberFormatException notANumber) {
            minutes = -1L;
        }
        ModNetwork.sendToServer(new AuctionCreatePacket(startPriceBox.getValue(),
                incrementBox.getValue(), buyoutBox.getValue(), minutes));
        setCreating(false);
    }

    @Override
    public void onClose() {
        // Tell the server to stop broadcasting to this player. A missed one costs nothing — the
        // set is a delivery hint and is cleared on disconnect anyway.
        ModNetwork.sendToServer(new RequestAuctionsPacket("", sort, false));
        super.onClose();
    }

    // ==================================================================
    // Data
    // ==================================================================

    @Nullable
    private AuctionDataPacket.Row selectedRow() {
        return selected == null ? null : ClientAuction.rowById(selected);
    }

    private List<AuctionDataPacket.Row> visibleRows() {
        List<AuctionDataPacket.Row> all = ClientAuction.rows();
        int from = Math.min(page * ROWS_VISIBLE, all.size());
        int to = Math.min(from + ROWS_VISIBLE, all.size());
        return all.subList(from, to);
    }

    /** The smallest bid that would be accepted, computed from the same numbers the server uses. */
    private static long minimumBid(AuctionDataPacket.Row row) {
        return row.hasBid() ? row.topBid() + row.minIncrement() : row.startPrice();
    }

    private int rowAt(double mouseX, double mouseY) {
        int relX = (int) mouseX - left - 6;
        int relY = (int) mouseY - top - LIST_Y;
        if (relX < 0 || relY < 0 || relX > WIN_W - 12) {
            return -1;
        }
        int index = relY / ROW_H;
        return index < visibleRows().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!creating && button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                selected = visibleRows().get(index).id();
                bidBox.setValue(MoneyFormat.format(minimumBid(visibleRows().get(index))));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ==================================================================
    // Drawing
    // ==================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, C_PANEL);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);

        if (creating) {
            drawCreate(graphics);
        } else {
            drawList(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        drawToast(graphics);
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        AuctionDataPacket data = ClientAuction.data();
        long balance = ClientAuction.balance();
        long held = ClientAuction.held();
        Component wallet = Component.translatable("globalshop.auction.wallet",
                MoneyFormat.formatCompact(balance), MoneyFormat.formatCompact(held));
        graphics.drawString(this.font, wallet, left + WIN_W - 8 - this.font.width(wallet), top + 8,
                held > 0L ? C_MINE : C_DIM, false);

        // Column headings.
        int headerY = top + 26;
        graphics.drawString(this.font, Component.translatable("globalshop.auction.col.item"),
                left + COL_ITEM, headerY, C_DIM, false);
        graphics.drawString(this.font, Component.translatable("globalshop.auction.col.price"),
                left + COL_PRICE, headerY, C_DIM, false);
        graphics.drawString(this.font, Component.translatable("globalshop.auction.col.buyout"),
                left + COL_BUYOUT, headerY, C_DIM, false);
        graphics.drawString(this.font, Component.translatable("globalshop.auction.col.ends"),
                left + COL_ENDS, headerY, C_DIM, false);
        // Centred in the gap between the arrows rather than drawn from a fixed left edge, so a
        // two-digit page count stays between them instead of running under the forward arrow.
        Component pages = Component.literal((page + 1) + "/" + pageCount());
        graphics.drawString(this.font, pages,
                left + PAGING_TEXT_CENTRE - this.font.width(pages) / 2, headerY, C_DIM, false);

        List<AuctionDataPacket.Row> rows = visibleRows();
        if (rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.auction.none"),
                    left + 8, top + LIST_Y + 6, C_DIM, false);
            return;
        }

        long now = ClientAuction.serverNow();
        for (int i = 0; i < rows.size(); i++) {
            AuctionDataPacket.Row row = rows.get(i);
            int y = top + LIST_Y + i * ROW_H;
            boolean isSelected = row.id().equals(selected);
            graphics.fill(left + 6, y - 1, left + WIN_W - 6, y + ROW_H - 3,
                    isSelected ? C_SELECTED : C_ROW);

            graphics.renderItem(row.item(), left + COL_ICON, y);
            // The count and durability overlay, drawn the way Minecraft draws it for an item in a
            // slot: over the icon's bottom-right corner. A stack of 64 had been shown with no
            // number at all, which made it look like one.
            graphics.renderItemDecorations(this.font, row.item(), left + COL_ICON, y);
            Component name = row.item().getHoverName();
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(name.getString(), COL_NAME_W)),
                    left + COL_NAME, y + 5, row.mine() ? C_MINE : C_TEXT, false);

            Component price = row.hasBid()
                    ? Component.literal(MoneyFormat.formatCompact(row.topBid()))
                    : Component.translatable("globalshop.auction.no_bid",
                            MoneyFormat.formatCompact(row.startPrice()));
            graphics.drawString(this.font, price, left + COL_PRICE, y + 5,
                    row.hasBid() ? C_MONEY : C_DIM, false);

            graphics.drawString(this.font, row.buyoutPrice() > 0L
                            ? Component.literal(MoneyFormat.formatCompact(row.buyoutPrice()))
                            : Component.literal("-"),
                    left + COL_BUYOUT, y + 5, row.buyoutPrice() > 0L ? C_MONEY : C_DIM, false);

            long remaining = row.endTimeMillis() - now;
            graphics.drawString(this.font, Component.literal(ClientAuction.formatRemaining(remaining)),
                    left + COL_ENDS, y + 5, remaining <= 30_000L ? C_URGENT : C_TEXT, false);

            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(row.sellerName(),
                            COL_SELLER_W)),
                    left + COL_SELLER, y + 5, C_DIM, false);
        }

        AuctionDataPacket.Row row = selectedRow();
        if (row != null) {
            long minimum = minimumBid(row);
            Component hint = Component.translatable("globalshop.auction.minimum",
                    MoneyFormat.formatWithName(minimum));
            graphics.drawString(this.font, hint, left + 6, top + WIN_H - 34, C_DIM, false);
        }
    }

    private void drawCreate(GuiGraphics graphics) {
        graphics.drawString(this.font, Component.translatable("globalshop.auction.create.title"),
                left + 130, top + 36, C_TITLE, false);
        graphics.drawString(this.font, Component.translatable("globalshop.auction.create.note"),
                left + 130, top + 48, C_DIM, false);

        label(graphics, "globalshop.auction.create.start", top + 64);
        label(graphics, "globalshop.auction.create.increment", top + 88);
        label(graphics, "globalshop.auction.create.buyout", top + 112);
        label(graphics, "globalshop.auction.create.minutes", top + 136);

        var held = this.minecraft == null || this.minecraft.player == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : this.minecraft.player.getMainHandItem();
        Component inHand = held.isEmpty()
                ? Component.translatable("globalshop.auction.create.empty_hand")
                : Component.translatable("globalshop.auction.create.in_hand",
                        held.getHoverName(), held.getCount());
        graphics.drawString(this.font, inHand, left + 130, top + 186,
                held.isEmpty() ? C_URGENT : C_TEXT, false);
        if (!held.isEmpty()) {
            graphics.renderItem(held, left + 108, top + 182);
        }
    }

    private void label(GuiGraphics graphics, String key, int y) {
        Component text = Component.translatable(key);
        graphics.drawString(this.font, text, left + 130 - this.font.width(text) - 6, y + 4, C_DIM,
                false);
    }

    /** The centred popup the requirement asks for when something is won while the window is open. */
    private void drawToast(GuiGraphics graphics) {
        if (toast == null || System.currentTimeMillis() > toastUntil) {
            toast = null;
            return;
        }
        int width = this.font.width(toast) + 24;
        int x = left + (WIN_W - width) / 2;
        int y = top + WIN_H / 2 - 14;
        graphics.fill(x, y, x + width, y + 28, 0xF0203010);
        graphics.renderOutline(x, y, width, 28, 0xFF7FD07F);
        graphics.drawCenteredString(this.font, toast, left + WIN_W / 2, y + 10, 0xFF9CFF9C);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================================================================
    // Server updates
    // ==================================================================

    public void accept(AuctionDataPacket packet) {
        // The query echoed back is the server's view of what was asked for; if the player has typed
        // since, the newer text stays and the next request will carry it.
        page = Math.min(page, pageCount() - 1);
        if (selected != null && ClientAuction.rowById(selected) == null) {
            selected = null;
        }
        refreshWidgets();
    }

    public void acceptUpdate() {
        refreshWidgets();
    }

    public void showToast(AuctionToastPacket packet) {
        this.toast = packet.message();
        this.toastUntil = System.currentTimeMillis() + 4_000L;
    }
}
