package com.example.globalshop.client;

import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.menu.TradeLayout;
import com.example.globalshop.menu.TradeMenu;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.ConversationDataPacket;
import com.example.globalshop.network.packet.PlayerListDataPacket;
import com.example.globalshop.network.packet.RequestConversationPacket;
import com.example.globalshop.network.packet.RequestPlayerListPacket;
import com.example.globalshop.network.packet.SendDirectMessagePacket;
import com.example.globalshop.network.packet.TradeOfferActionPacket;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The private-trade window: a player directory, a conversation, a payment field, and the player's own
 * backpack as real slots.
 *
 * <h2>Two modes in one window</h2>
 * The list and the conversation share the window rather than being two screens, because the backpack
 * is a real container menu opened by the server: a second screen would mean a second menu, and the
 * player's items would appear to jump when it opened. Switching a client-side mode keeps one menu and
 * one set of slots.
 *
 * <h2>What the colours mean</h2>
 * A name is red with "(online)" when the server said that player is connected, and grey with
 * "(offline)" when it did not. Red is the colour that reads as "live" at a glance, which is the
 * question the list exists to answer.
 */
public class TradeScreen extends AbstractContainerScreen<TradeMenu> {

    private static final int COLOR_TITLE = 0xFFFFD54F;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_DIM = 0xFF9E9E9E;
    private static final int COLOR_ONLINE = 0xFFFF5555;
    private static final int COLOR_OFFLINE = 0xFF7A7A7A;
    private static final int COLOR_PANEL = 0xF0101010;
    private static final int COLOR_SEPARATOR = 0xFF555555;
    private static final int COLOR_MINE = 0xFF9BE79B;
    private static final int COLOR_THEIRS = 0xFFFFD0A0;

    private enum Mode {
        LIST,
        CONVERSATION
    }

    private Mode mode = Mode.LIST;
    /** Whether the list has ever been asked for, so the first page is requested exactly once. */
    private boolean requestedList;

    // ---- widgets ----
    private EditBox searchBox;
    private EditBox messageBox;
    /** The price box in the offer panel. Read-only until "change price" is pressed. */
    private EditBox priceBox;
    private Button inputButton;
    private Button prevButton;
    private Button nextButton;
    /** Context button: offer the item, or change the price, or publish the new one. */
    private Button offerButton;
    /** Context button: take the offer back (seller), or pay it (buyer). */
    private Button secondButton;
    private Button backButton;

    // ---- directory state ----
    private List<PlayerListDataPacket.Row> rows = List.of();
    private int page;
    private int pageCount = 1;
    private int totalMatches;
    @Nullable
    private PlayerListDataPacket.Row hoveredRow;

    // ---- conversation state ----
    @Nullable
    private UUID partnerId;
    private String partnerName = "";
    private boolean partnerOnline;
    private boolean partnerKnown = true;
    private List<ConversationDataPacket.Line> lines = List.of();
    private long viewerBalance;

    // ---- offer state ----
    private boolean hasOffer;
    @Nullable
    private UUID offerSellerId;
    private ItemStack offerItem = ItemStack.EMPTY;
    private long offerPrice;
    private boolean offerPriced;
    /**
     * Whether the price box is unlocked.
     *
     * <p>Read-only by default, so a price cannot be changed by a stray keystroke: the player presses
     * "change price", types, then confirms. That is the two-step the negotiation is meant to be.
     */
    private boolean priceEditable;
    /** The slot's last known contents, so the button state is recomputed when it changes. */
    private boolean lastSlotHadItem;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = TradeLayout.GUI_WIDTH;
        this.imageHeight = TradeLayout.GUI_HEIGHT;
        // The base class would draw its own "Inventory" label at y=146, on top of the paging row.
        this.inventoryLabelY = -10000;
    }

    // ==================================================================
    // Widgets
    // ==================================================================

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos;
        int top = this.topPos;

        searchBox = addRenderableWidget(new EditBox(this.font, left + TradeLayout.INPUT_X,
                top + TradeLayout.INPUT_Y, TradeLayout.INPUT_W, TradeLayout.INPUT_H,
                Component.translatable("globalshop.trade.search")));
        searchBox.setMaxLength(RequestPlayerListPacket.MAX_QUERY);
        searchBox.setHint(Component.translatable("globalshop.trade.search_hint"));

        messageBox = addRenderableWidget(new EditBox(this.font, left + TradeLayout.INPUT_X,
                top + TradeLayout.INPUT_Y, TradeLayout.INPUT_W, TradeLayout.INPUT_H,
                Component.translatable("globalshop.trade.message")));
        messageBox.setMaxLength(200);
        messageBox.setHint(Component.translatable("globalshop.trade.message_hint"));

        priceBox = addRenderableWidget(new EditBox(this.font, left + TradeLayout.PRICE_FIELD_X,
                top + TradeLayout.PRICE_FIELD_Y, TradeLayout.PRICE_FIELD_W,
                TradeLayout.PRICE_FIELD_H,
                Component.translatable("globalshop.trade.price_field")));
        priceBox.setMaxLength(TradeOfferActionPacket.MAX_AMOUNT_TEXT);

        inputButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.trade.search_button"), b -> submitInput())
                .bounds(left + TradeLayout.INPUT_BUTTON_X, top + TradeLayout.INPUT_Y,
                        TradeLayout.INPUT_BUTTON_W, TradeLayout.INPUT_BUTTON_H).build());

        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + TradeLayout.CONTENT_X, top + TradeLayout.NAV_Y,
                        TradeLayout.NAV_BUTTON_W, TradeLayout.NAV_BUTTON_H).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + TradeLayout.CONTENT_X + TradeLayout.NAV_BUTTON_W + 4,
                        top + TradeLayout.NAV_Y, TradeLayout.NAV_BUTTON_W,
                        TradeLayout.NAV_BUTTON_H).build());
        backButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.trade.back"), b -> showList())
                .bounds(left + TradeLayout.CONTENT_X + 60, top + TradeLayout.NAV_Y, 60,
                        TradeLayout.NAV_BUTTON_H).build());

        offerButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.trade.offer_button"),
                        b -> pressOfferButton())
                .bounds(left + TradeLayout.OFFER_BUTTON_X, top + TradeLayout.OFFER_BUTTON1_Y,
                        TradeLayout.OFFER_BUTTON_W, TradeLayout.OFFER_BUTTON_H).build());
        secondButton = addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.trade.withdraw_button"),
                        b -> pressSecondButton())
                .bounds(left + TradeLayout.OFFER_BUTTON_X, top + TradeLayout.OFFER_BUTTON2_Y,
                        TradeLayout.OFFER_BUTTON_W, TradeLayout.OFFER_BUTTON_H).build());

        if (!requestedList) {
            requestedList = true;
            requestPage(0);
        }
        refreshWidgets();
    }

    private void refreshWidgets() {
        boolean list = mode == Mode.LIST;
        boolean talking = mode == Mode.CONVERSATION && partnerId != null;

        searchBox.setVisible(list);
        // Buttons take the public field: AbstractWidget has no setVisible in 1.20.1 (EditBox does).
        inputButton.visible = list;
        inputButton.setMessage(Component.translatable("globalshop.trade.search_button"));
        prevButton.visible = list;
        nextButton.visible = list;
        backButton.visible = !list;

        messageBox.setVisible(talking);
        priceBox.setVisible(talking && hasOffer);
        // Editable only after "change price": the box is a display until then.
        priceBox.setEditable(talking && hasOffer && priceEditable);
        offerButton.visible = talking;
        secondButton.visible = talking;
        // The base class draws every slot whatever the mode, so the offer slot is hidden by hand.
        menu.setOfferSlotActive(talking);
        updateOfferButtons();

        if (!list) {
            for (var box : List.of(searchBox)) {
                box.setFocused(false);
            }
        }
    }

    // ==================================================================
    // Server updates
    // ==================================================================

    /**
     * Applies a conversation only if it is the one on screen.
     *
     * <p>An offer changing pushes a fresh conversation to both players. The one who is reading a
     * different conversation must not be dragged into this one by it.
     */
    public void acceptConversationIfShowing(ConversationDataPacket data) {
        if (mode != Mode.CONVERSATION || partnerId == null || !partnerId.equals(data.partnerId())) {
            return;
        }
        acceptConversation(data);
    }

    public void acceptPlayerList(PlayerListDataPacket data) {
        this.rows = data.rows();
        this.page = data.page();
        this.pageCount = data.pageCount();
        this.totalMatches = data.totalMatches();
        // Adopt the server's answer as the box's contents only when it differs, so the player's
        // cursor does not jump while they are still typing.
        if (mode == Mode.LIST && !data.query().equals(searchBox.getValue())) {
            searchBox.setValue(data.query());
        }
        refreshWidgets();
    }

    public void acceptConversation(ConversationDataPacket data) {
        this.partnerId = data.partnerId();
        this.partnerName = data.partnerName() == null ? "" : data.partnerName();
        this.partnerOnline = data.online();
        this.partnerKnown = data.known();
        this.lines = data.lines();
        this.viewerBalance = data.viewerBalance();
        this.hasOffer = data.hasOffer();
        this.offerSellerId = data.offerSellerId();
        this.offerItem = data.offerItem();
        this.offerPrice = data.offerPrice();
        this.offerPriced = data.offerPriced();
        // Every refresh re-locks the box. Otherwise a price could be changed by a keystroke that
        // happened to land while the window was open, which is not a negotiation.
        this.priceEditable = false;
        if (hasOffer) {
            priceBox.setValue(offerPriced ? MoneyFormat.format(offerPrice) : "");
        } else {
            priceBox.setValue("");
        }
        this.mode = Mode.CONVERSATION;
        refreshWidgets();
    }

    // ==================================================================
    // Actions
    // ==================================================================

    private void requestPage(int newPage) {
        ModNetwork.sendToServer(new RequestPlayerListPacket(searchBox.getValue(), newPage));
    }

    private void turnPage(int delta) {
        requestPage(Math.max(0, Math.min(pageCount - 1, page + delta)));
    }

    private void submitInput() {
        if (mode == Mode.LIST) {
            requestPage(0);
        } else {
            sendMessage();
        }
    }

    private void sendMessage() {
        if (partnerId == null) {
            return;
        }
        String text = messageBox.getValue();
        if (text.isBlank()) {
            return;
        }
        ModNetwork.sendToServer(new SendDirectMessagePacket(partnerId, text));
        // Cleared straight away: leaving the text in the box invites sending it twice, and the
        // server's answer refreshes the conversation either way.
        messageBox.setValue("");
    }

    /** Whether the viewer is the side that put the item up. */
    private boolean iAmSeller() {
        return this.minecraft != null && this.minecraft.player != null && offerSellerId != null
                && offerSellerId.equals(this.minecraft.player.getUUID());
    }

    /**
     * Keeps the offer button in step with the slot.
     *
     * <p>Its enabled state depends on whether the slot holds something, and that changes the moment
     * the player drops an item in. Recomputing only in {@code init()} — which is where it used to
     * happen — meant the button stayed dark until something else forced a re-init, which is why
     * resizing the window made it light up.
     */
    @Override
    public void containerTick() {
        super.containerTick();
        boolean slotHasItem = offerSlotHasItem();
        if (slotHasItem != lastSlotHadItem) {
            lastSlotHadItem = slotHasItem;
            updateOfferButtons();
        }
    }

    /** Whether the offer slot currently holds something to offer. */
    private boolean offerSlotHasItem() {
        return !menu.slots.isEmpty() && menu.slots.get(0).hasItem();
    }

    /**
     * The two offer buttons change meaning with the state, so their labels and enabled-ness are
     * decided in one place rather than being spread over the places that change the state.
     */
    private void updateOfferButtons() {
        if (mode != Mode.CONVERSATION) {
            return;
        }
        if (!hasOffer) {
            offerButton.setMessage(Component.translatable("globalshop.trade.offer_button"));
            offerButton.active = offerSlotHasItem();
            secondButton.visible = false;
            return;
        }
        secondButton.visible = true;
        offerButton.setMessage(Component.translatable(priceEditable
                ? "globalshop.trade.publish_button" : "globalshop.trade.modify_button"));
        offerButton.active = true;

        if (iAmSeller()) {
            secondButton.setMessage(Component.translatable("globalshop.trade.withdraw_button"));
            secondButton.active = true;
        } else {
            secondButton.setMessage(Component.translatable("globalshop.trade.pay_button",
                    offerPriced ? MoneyFormat.formatCompact(offerPrice) : "--"));
            secondButton.active = offerPriced;
        }
    }

    /** The first button: offer the item, unlock the price, or publish the new one. */
    private void pressOfferButton() {
        if (partnerId == null) {
            return;
        }
        if (!hasOffer) {
            sendOfferAction(TradeOfferActionPacket.OFFER, "");
            return;
        }
        if (!priceEditable) {
            // Unlock only. Nothing is sent until the player confirms the new number, which is the
            // two-step the requirement describes.
            priceEditable = true;
            refreshWidgets();
            return;
        }
        sendOfferAction(TradeOfferActionPacket.PUBLISH, priceBox.getValue());
        priceEditable = false;
        // Re-labelled straight away: the other writers of priceEditable all refresh, and without it
        // the button still reads "Set price" and the state line still says to type a new price.
        refreshWidgets();
    }

    /** The second button: take the offer back, or pay it. */
    private void pressSecondButton() {
        if (partnerId == null || !hasOffer) {
            return;
        }
        if (iAmSeller()) {
            sendOfferAction(TradeOfferActionPacket.WITHDRAW, "");
        } else {
            // The price on screen, formatted so the server reads back the same number. The server
            // refuses if the offer has moved since — that is what stops a last-instant price rise.
            sendOfferAction(TradeOfferActionPacket.PAY, MoneyFormat.format(offerPrice));
        }
    }

    private void sendOfferAction(int action, String amountText) {
        ModNetwork.sendToServer(new TradeOfferActionPacket(partnerId, action, amountText));
    }

    private void showList() {
        this.mode = Mode.LIST;
        this.partnerId = null;
        refreshWidgets();
        requestPage(page);
    }

    private void openConversation(PlayerListDataPacket.Row row) {
        // Shown immediately, so the window answers the click before the server's reply arrives.
        this.partnerId = row.id();
        this.partnerName = row.name();
        this.partnerOnline = row.online();
        this.partnerKnown = true;
        this.lines = List.of();
        // The offer belongs to the pair, so it is cleared here rather than left over from whoever was
        // open a moment ago. The server's reply fills in this pair's own offer, if there is one.
        this.hasOffer = false;
        this.offerSellerId = null;
        this.offerItem = ItemStack.EMPTY;
        this.offerPrice = 0L;
        this.offerPriced = false;
        this.priceEditable = false;
        this.mode = Mode.CONVERSATION;
        refreshWidgets();
        ModNetwork.sendToServer(new RequestConversationPacket(row.id()));
    }

    // ==================================================================
    // Input
    // ==================================================================

    /** Which directory row a screen position is over, or {@code null}. */
    @Nullable
    private PlayerListDataPacket.Row rowAt(double mouseX, double mouseY) {
        int relX = (int) mouseX - leftPos - TradeLayout.CONTENT_X;
        int relY = (int) mouseY - topPos - TradeLayout.CONTENT_Y;
        if (relX < 0 || relX > TradeLayout.CONTENT_W || relY < 0) {
            return null;
        }
        int index = relY / TradeLayout.ROW_H;
        if (index >= TradeLayout.ROWS) {
            return null;
        }
        return index < rows.size() ? rows.get(index) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.LIST && button == 0) {
            PlayerListDataPacket.Row row = rowAt(mouseX, mouseY);
            if (row != null) {
                openConversation(row);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (mode == Mode.LIST && searchBox.isFocused()) {
                requestPage(0);
                return true;
            }
            if (mode == Mode.CONVERSATION && messageBox.isFocused()) {
                sendMessage();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================================================================
    // Drawing
    // ==================================================================

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, COLOR_PANEL);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF7A7A7A);
        graphics.fill(leftPos + 6, topPos + TradeLayout.SEPARATOR_Y, leftPos + imageWidth - 6,
                topPos + TradeLayout.SEPARATOR_Y + 1, COLOR_SEPARATOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.minecraft == null) {
            return;
        }
        this.hoveredRow = mode == Mode.LIST ? rowAt(mouseX, mouseY) : null;

        if (mode == Mode.LIST) {
            drawList(graphics);
        } else {
            drawConversation(graphics);
        }
        drawOfferPanel(graphics);
    }

    private void drawList(GuiGraphics graphics) {
        if (rows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.trade.no_players"),
                    leftPos + TradeLayout.CONTENT_X, topPos + TradeLayout.CONTENT_Y + 8,
                    COLOR_DIM, false);
        }
        for (int i = 0; i < rows.size() && i < TradeLayout.ROWS; i++) {
            PlayerListDataPacket.Row row = rows.get(i);
            int y = topPos + TradeLayout.CONTENT_Y + i * TradeLayout.ROW_H;
            boolean hot = row == hoveredRow;
            if (hot) {
                graphics.fill(leftPos + TradeLayout.CONTENT_X - 2, y - 2,
                        leftPos + TradeLayout.CONTENT_X + TradeLayout.CONTENT_W + 2, y + 12,
                        0x40FFFFFF);
            }
            // Red for online, grey for offline: the question the list exists to answer.
            int colour = row.online() ? COLOR_ONLINE : COLOR_OFFLINE;
            String suffix = row.duplicateName()
                    // Two accounts, one name. Offline-mode ids are derived from the name, so this is
                    // how one person ends up with two accounts; showing the short id is the only
                    // thing on screen that tells them apart.
                    ? "  " + row.id().toString().substring(0, 8)
                    : "";
            Component name = Component.literal(row.name()
                    + Component.translatable(row.online()
                            ? "globalshop.social.online" : "globalshop.social.offline").getString()
                    + suffix);
            graphics.drawString(this.font, name, leftPos + TradeLayout.CONTENT_X + 4, y, colour,
                    false);

            Component money = Component.literal(MoneyFormat.formatCompact(row.balance()));
            graphics.drawString(this.font, money,
                    leftPos + TradeLayout.CONTENT_X + TradeLayout.CONTENT_W
                            - this.font.width(money) - 4, y, COLOR_DIM, false);
        }

        // The paging text and the hint share this row with the buttons. The hint used to be drawn
        // six pixels under the last list row, which put it straight on top of these buttons: the
        // row below the list *is* the paging row, so anything "just under the list" lands on it.
        Component paging = Component.literal((page + 1) + " / " + pageCount + "   (" + totalMatches + ")");
        graphics.drawString(this.font, paging,
                leftPos + TradeLayout.CONTENT_X + TradeLayout.NAV_BUTTON_W * 2 + 14,
                topPos + TradeLayout.NAV_Y + 5, COLOR_DIM, false);

        Component hint = Component.translatable(rows.stream().anyMatch(row -> !row.online())
                ? "globalshop.trade.offline_hint" : "globalshop.trade.online_hint");
        // Truncated to what is left of the row, so a wordy translation cannot run under the paging
        // text either.
        Component fitted = Component.literal(this.font.plainSubstrByWidth(hint.getString(), 168));
        graphics.drawString(this.font, fitted,
                leftPos + TradeLayout.CONTENT_X + TradeLayout.CONTENT_W - this.font.width(fitted),
                topPos + TradeLayout.NAV_Y + 5, COLOR_DIM, false);
    }

    private void drawConversation(GuiGraphics graphics) {
        Component status = Component.translatable(partnerOnline
                ? "globalshop.social.online" : "globalshop.social.offline");
        Component heading = Component.literal(partnerName).append(status);
        int headingColour = partnerOnline ? COLOR_ONLINE : COLOR_OFFLINE;
        graphics.drawString(this.font, heading,
                leftPos + TradeLayout.STATUS_RIGHT - this.font.width(heading),
                topPos + TradeLayout.TITLE_Y, headingColour, false);

        if (!partnerKnown) {
            graphics.drawString(this.font, Component.translatable("globalshop.result.unknown_player"),
                    leftPos + TradeLayout.CONTENT_X, topPos + TradeLayout.CONTENT_Y + 8,
                    COLOR_ONLINE, false);
            return;
        }
        if (!partnerOnline) {
            // Said plainly, because it changes what the player should expect: the message is kept,
            // and the other player is told about it when they next log in.
            graphics.drawString(this.font, Component.translatable("globalshop.trade.offline_note"),
                    leftPos + TradeLayout.CONTENT_X, topPos + TradeLayout.CONTENT_Y + 6,
                    COLOR_DIM, false);
        }

        int firstRow = topPos + TradeLayout.CONTENT_Y + (partnerOnline ? 4 : 18);
        for (int i = 0; i < lines.size() && i < TradeLayout.CHAT_LINES; i++) {
            ConversationDataPacket.Line line = lines.get(i);
            int y = firstRow + i * TradeLayout.CHAT_LINE_H;
            boolean mine = this.minecraft.player != null
                    && line.from().equals(this.minecraft.player.getUUID());
            // The player's own prefix comes from the language file like everything else. It was
            // a literal here, which every player saw in every language -- the one string in the
            // mod that a translation could not reach.
            Component text = mine
                    ? Component.translatable("globalshop.trade.me_prefix", line.text())
                    : Component.literal(line.fromName() + ": " + line.text());
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(text.getString(),
                            TradeLayout.CHAT_W - 8)),
                    leftPos + TradeLayout.CONTENT_X + 4, y, mine ? COLOR_MINE : COLOR_THEIRS, false);
        }
        if (lines.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.trade.no_messages"),
                    leftPos + TradeLayout.CONTENT_X + 4, firstRow, COLOR_DIM, false);
        }
    }

    /**
     * The offer panel: what is being sold, for how much, and which side the viewer is on.
     *
     * <p>Drawn beside the conversation so the negotiation and the thing being negotiated are visible
     * at once — a price you cannot see while reading the offer is not much use.
     */
    private void drawOfferPanel(GuiGraphics graphics) {
        if (mode != Mode.CONVERSATION) {
            return;
        }
        int px = leftPos + TradeLayout.PANEL_X;
        int py = topPos + TradeLayout.PANEL_Y;
        graphics.fill(px, py, px + TradeLayout.PANEL_W, py + TradeLayout.PANEL_H, 0x40000000);
        graphics.renderOutline(px, py, TradeLayout.PANEL_W, TradeLayout.PANEL_H, 0xFF555555);
        graphics.drawString(this.font, Component.translatable("globalshop.trade.panel"), px + 4,
                py + 3, COLOR_TITLE, false);

        if (!hasOffer) {
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(
                            Component.translatable("globalshop.trade.slot_hint").getString(), 96)),
                    leftPos + TradeLayout.OFFER_TEXT_X, topPos + TradeLayout.OFFER_NAME_Y + 4,
                    COLOR_DIM, false);
        } else {
            // The item itself is drawn by the real slot; this is the name and count beside it.
            Component summary = Component.literal(
                    offerItem.getHoverName().getString() + " x" + offerItem.getCount());
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(summary.getString(), 96)),
                    leftPos + TradeLayout.OFFER_TEXT_X, topPos + TradeLayout.OFFER_NAME_Y, COLOR_TEXT,
                    false);
            graphics.drawString(this.font, Component.translatable(iAmSeller()
                            ? "globalshop.trade.you_sell" : "globalshop.trade.you_buy"),
                    leftPos + TradeLayout.OFFER_TEXT_X, topPos + TradeLayout.OFFER_ROLE_Y,
                    iAmSeller() ? COLOR_THEIRS : COLOR_MINE, false);
        }

        Component price = !hasOffer
                ? Component.translatable("globalshop.trade.no_offer")
                : (offerPriced
                        ? Component.translatable("globalshop.trade.price",
                                MoneyFormat.formatCompact(offerPrice))
                        : Component.translatable("globalshop.trade.no_price"));
        graphics.drawString(this.font,
                Component.literal(this.font.plainSubstrByWidth(price.getString(),
                        TradeLayout.PANEL_W - 8)),
                px + 4, topPos + TradeLayout.OFFER_PRICE_Y,
                hasOffer && offerPriced ? COLOR_MINE : COLOR_DIM, false);

        // The state, so the two context buttons are never a guess.
        Component state = Component.translatable(!hasOffer
                ? "globalshop.trade.state_none"
                : (priceEditable ? "globalshop.trade.state_editing"
                        : (!offerPriced ? "globalshop.trade.state_unpriced"
                                : (iAmSeller()
                                        ? "globalshop.trade.state_seller"
                                        : "globalshop.trade.state_ready"))));
        graphics.drawString(this.font,
                Component.literal(this.font.plainSubstrByWidth(state.getString(),
                        TradeLayout.PANEL_W - 8)),
                px + 4, topPos + TradeLayout.OFFER_STATUS_Y,
                hasOffer && offerPriced ? COLOR_MINE : COLOR_DIM, false);

        Component balance = Component.translatable("globalshop.trade.my_balance",
                MoneyFormat.formatCompact(viewerBalance));
        graphics.drawString(this.font,
                Component.literal(this.font.plainSubstrByWidth(balance.getString(),
                        TradeLayout.BALANCE_MAX_W)),
                leftPos + TradeLayout.BALANCE_X, topPos + TradeLayout.BALANCE_Y, COLOR_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
