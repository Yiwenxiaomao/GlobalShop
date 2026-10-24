package com.example.globalshop.client;

import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.CheckinActionPacket;
import com.example.globalshop.network.packet.CheckinDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The weekly check-in panel: seven days, Monday to Sunday.
 *
 * <p>Each row says what that day pays and where it stands, and clicking a row acts on it — collecting
 * today, or paying the fee to make up a day that was missed. One click target per row rather than
 * two buttons per row, because the two actions are mutually exclusive: a day is either today, or
 * behind, or ahead, and only one of those can be collected.
 *
 * <p>The fee is shown on every missed row, so the price of catching up is visible before it is paid.
 */
public class CheckinScreen extends Screen {

    private static final int WIN_W = 264;
    private static final int WIN_H = 186;
    private static final int ROW_H = 18;
    private static final int LIST_Y = 40;

    private static final int C_TITLE = 0xFFFFD54F;
    private static final int C_TEXT = 0xFFEEEEEE;
    private static final int C_DIM = 0xFF808080;
    private static final int C_MONEY = 0xFFFFD700;
    private static final int C_TODAY = 0xFF7FD07F;
    private static final int C_MISSED = 0xFFFF9E9E;
    private static final int C_PANEL = 0xF0121218;

    private final CheckinDataPacket data;
    private int left;
    private int top;

    public CheckinScreen(CheckinDataPacket data) {
        super(Component.translatable("globalshop.checkin.window"));
        this.data = data;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("globalshop.checkin.close"),
                        b -> onClose())
                .bounds(left + 8, top + WIN_H - 24, WIN_W - 16, 18).build());
    }

    /** Which row the mouse is over, or -1. Rows are the seven days, Monday first. */
    private int rowAt(double mouseX, double mouseY) {
        int relY = (int) mouseY - top - LIST_Y;
        if (mouseX < left + 8 || mouseX > left + WIN_W - 8 || relY < 0) {
            return -1;
        }
        int index = relY / ROW_H;
        return index < 7 ? index + 1 : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int day = rowAt(mouseX, mouseY);
            if (day > 0 && data.collectable(day)) {
                ModNetwork.sendToServer(new CheckinActionPacket(CheckinActionPacket.COLLECT, day));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, C_PANEL);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);

        graphics.drawCenteredString(this.font, this.title, left + WIN_W / 2, top + 8, C_TITLE);
        graphics.drawCenteredString(this.font,
                Component.translatable("globalshop.checkin.balance",
                        MoneyFormat.formatWithName(data.balance())),
                left + WIN_W / 2, top + 22, C_MONEY);

        int hovered = rowAt(mouseX, mouseY);
        for (int day = 1; day <= 7; day++) {
            int y = top + LIST_Y + (day - 1) * ROW_H;
            boolean claimed = data.claimed(day);
            boolean isToday = day == data.today();
            boolean missed = day < data.today() && !claimed;
            boolean collectable = data.collectable(day);

            // A row that can be acted on lights up under the cursor; the rest stay flat.
            if (collectable) {
                graphics.fill(left + 8, y - 1, left + WIN_W - 8, y + ROW_H - 3,
                        hovered == day ? 0x50FFD54F : 0x28FFFFFF);
            }
            int colour = claimed ? C_DIM : (isToday ? C_TODAY : (missed ? C_MISSED : C_DIM));
            graphics.drawString(this.font,
                    Component.translatable("globalshop.checkin.day" + day),
                    left + 12, y + 4, colour, false);
            graphics.drawString(this.font,
                    Component.literal(MoneyFormat.formatWithName(data.reward(day))),
                    left + 74, y + 4, claimed ? C_DIM : C_MONEY, false);

            Component state;
            if (claimed) {
                state = Component.translatable(data.madeUp(day)
                        ? "globalshop.checkin.state.made_up"
                        : "globalshop.checkin.state.claimed");
            } else if (isToday) {
                state = Component.translatable("globalshop.checkin.state.today");
            } else if (missed) {
                // Both numbers: a make-up pays half, so what it costs and what it returns are
                // different figures, and hiding either would be hiding the decision.
                state = Component.translatable("globalshop.checkin.state.missed",
                        MoneyFormat.formatWithName(data.makeupCost()),
                        MoneyFormat.formatWithName(data.makeUpReward(day)));
            } else {
                state = Component.translatable("globalshop.checkin.state.future");
            }
            graphics.drawString(this.font, state, left + 148, y + 4, colour, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
