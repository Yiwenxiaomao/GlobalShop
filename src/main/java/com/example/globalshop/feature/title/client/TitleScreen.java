package com.example.globalshop.feature.title.client;

import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.feature.title.net.TitleActionPacket;
import com.example.globalshop.feature.title.net.TitleDataPacket;
import com.example.globalshop.feature.title.TitlePricing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The title window: type what should appear in front of your name, and see what it costs.
 *
 * <p>The price is shown as it is typed, counted character by character, because the whole point of
 * pricing ordinary and special characters differently is that the player can see why one title
 * costs more than another. The numbers come from the server in {@link TitleDataPacket} rather than
 * from this client's config file, so the quote on screen is the price that will actually be charged.
 *
 * <p>Nothing is charged until save is pressed, and the server validates before it charges anything
 * — a title that is too long or contains an illegal character costs nothing and says so.
 */
public class TitleScreen extends Screen {

    private static final int WIN_W = 264;
    private static final int WIN_H = 148;
    private static final int C_TITLE = 0xFFFFD54F;
    private static final int C_TEXT = 0xFFEEEEEE;
    private static final int C_DIM = 0xFF9E9E9E;
    private static final int C_MONEY = 0xFFFFD700;
    private static final int C_BAD = 0xFFFF5555;
    private static final int C_GOOD = 0xFF7FD07F;
    private static final int C_PANEL = 0xF0121218;

    private final TitleDataPacket data;
    private int left;
    private int top;

    private EditBox textBox;
    private Component hint = Component.empty();

    public TitleScreen(TitleDataPacket data) {
        super(Component.translatable("globalshop.title.window"));
        this.data = data;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;

        textBox = addRenderableWidget(new EditBox(this.font, left + 8, top + 48, WIN_W - 16, 18,
                Component.translatable("globalshop.title.field")));
        textBox.setMaxLength(TitleActionPacket.MAX_TEXT);
        textBox.setValue(data.title());
        textBox.setResponder(text -> hint = Component.empty());

        addRenderableWidget(Button.builder(Component.translatable("globalshop.title.save"),
                        b -> send(TitleActionPacket.SET))
                .bounds(left + 8, top + WIN_H - 26, 78, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.title.clear"),
                        b -> send(TitleActionPacket.CLEAR))
                .bounds(left + 92, top + WIN_H - 26, 78, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.title.close"),
                        b -> onClose())
                .bounds(left + 176, top + WIN_H - 26, 80, 18).build());
    }

    private void send(int action) {
        ModNetwork.sendToServer(new TitleActionPacket(action, textBox.getValue()));
    }

    @Override
    public void onClose() {
        // Nothing is sent here, and that is the fix for a window that could not be closed at all.
        //
        // An earlier version sent the OPEN action on close — copied from the auction window, where
        // closing does have to unregister the viewer. It does not apply here: the server answered
        // OPEN with the current state, the packet handler saw a title window was open and opened a
        // fresh one, and the screen came straight back. Pressing escape went through this method
        // too, so there was no way out at all. The title window holds no state on the server, so
        // there is nothing to tell it when the window closes.
        super.onClose();
    }

    /** Replaces the state after the server answered, keeping whatever is being typed. */
    public void accept(TitleDataPacket fresh) {
        // Only overwrite the box when it is untouched: the server's echo arrives after a save, and
        // stamping it over the field mid-edit would fight the player for the cursor.
        if (textBox.getValue().equals(data.title()) || textBox.getValue().isBlank()) {
            textBox.setValue(fresh.title());
        }
        this.hint = Component.empty();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new TitleScreen(fresh));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, C_PANEL);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);

        graphics.drawCenteredString(this.font, this.title, left + WIN_W / 2, top + 8, C_TITLE);
        Component current = data.title().isEmpty()
                ? Component.translatable("globalshop.title.none")
                : Component.literal("[" + data.title() + "]");
        graphics.drawCenteredString(this.font,
                Component.translatable("globalshop.title.current", current),
                left + WIN_W / 2, top + 24, data.title().isEmpty() ? C_DIM : C_TEXT);
        graphics.drawString(this.font, Component.translatable("globalshop.title.hint"),
                left + 8, top + 36, C_DIM, false);

        // Priced as it is typed, with the server's two prices.
        String typed = textBox.getValue().trim();
        TitlePricing.Quote quote = data.quote(typed);
        int y = top + 74;
        if (typed.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.title.enter_something"),
                    left + 8, y, C_DIM, false);
        } else {
            Component breakdown = Component.translatable("globalshop.title.breakdown",
                    quote.ordinary(), MoneyFormat.formatCompact(data.ordinaryPrice()),
                    quote.special(), MoneyFormat.formatCompact(data.specialPrice()));
            graphics.drawString(this.font, breakdown, left + 8, y, C_TEXT, false);
            boolean tooLong = quote.length() > data.maxLength();
            boolean tooPoor = quote.total() > data.balance();
            graphics.drawString(this.font,
                    Component.translatable("globalshop.title.cost",
                            MoneyFormat.formatWithName(quote.total())),
                    left + 8, y + 12, tooPoor ? C_BAD : C_MONEY, false);
            if (tooLong) {
                graphics.drawString(this.font,
                        Component.translatable("globalshop.title.too_long", data.maxLength()),
                        left + 8, y + 24, C_BAD, false);
            } else if (tooPoor) {
                graphics.drawString(this.font, Component.translatable("globalshop.title.too_poor"),
                        left + 8, y + 24, C_BAD, false);
            } else {
                graphics.drawString(this.font, Component.translatable("globalshop.title.affordable"),
                        left + 8, y + 24, C_GOOD, false);
            }
            graphics.drawCenteredString(this.font,
                    Component.literal("[" + typed + "] " + playerName()),
                    left + WIN_W / 2, y + 38, C_TEXT);
        }
        graphics.drawString(this.font, Component.translatable("globalshop.title.balance",
                        MoneyFormat.formatWithName(data.balance())),
                left + 8, top + WIN_H - 40, C_DIM, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String playerName() {
        return this.minecraft == null || this.minecraft.player == null
                ? ""
                : this.minecraft.player.getGameProfile().getName();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
