package com.example.globalshop.client;

import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.VipActionPacket;
import com.example.globalshop.network.packet.VipStatusDataPacket;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The membership window: whether it is running, for how much longer, and the two things a player can
 * do about it.
 *
 * <p>Opened by clicking the rank badge beside the balance, which is where a player looks to find out
 * what they are. The badge used to be a label; a label that answers a question and then offers no
 * action is a dead end.
 *
 * <p>A plain {@link Screen}: there is nothing here to drag, and putting the player's backpack on
 * screen would only invite clicking it.
 */
public class VipScreen extends Screen {

    private static final int WIN_W = 240;
    private static final int WIN_H = 132;
    private static final int PAD = 12;
    private static final int COLOR_TITLE = 0xFFFFD54F;
    private static final int COLOR_TEXT = 0xFFEEEEEE;
    private static final int COLOR_DIM = 0xFF9E9E9E;
    private static final int COLOR_VIP = 0xFF9600C8;
    private static final int COLOR_OK = 0xFF3FBF3F;

    private VipStatusDataPacket status;
    private int left;
    private int top;
    private Button buyButton;
    private Button autoButton;

    public VipScreen(VipStatusDataPacket status) {
        super(Component.translatable("globalshop.vip.title"));
        this.status = status;
    }

    /** Applies a fresh status: the server's answer replaces whatever the window was showing. */
    public void accept(VipStatusDataPacket fresh) {
        this.status = fresh;
        if (this.minecraft != null) {
            refresh();
        }
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        int buttonY = top + WIN_H - 26;

        buyButton = addRenderableWidget(Button.builder(buyLabel(), b -> send(VipActionPacket.BUY))
                .bounds(left + PAD, top + 74, 100, 20).build());
        autoButton = addRenderableWidget(Button.builder(autoLabel(),
                        b -> send(VipActionPacket.TOGGLE_AUTO))
                .bounds(left + PAD + 108, top + 74, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + WIN_W - PAD - 60, buttonY, 60, 20).build());
    }

    private void send(int action) {
        ModNetwork.sendToServer(new VipActionPacket(action));
    }

    /** Re-labels both buttons from the current status. */
    private void refresh() {
        if (buyButton != null) {
            buyButton.setMessage(buyLabel());
        }
        if (autoButton != null) {
            autoButton.setMessage(autoLabel());
        }
    }

    private Component buyLabel() {
        return Component.translatable(status.active()
                ? "globalshop.vip.extend" : "globalshop.vip.buy",
                MoneyFormat.formatCompact(status.price()));
    }

    private Component autoLabel() {
        return Component.translatable(status.autoRenew()
                ? "globalshop.vip.auto_label_on" : "globalshop.vip.auto_label_off");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, 0xF0101010);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);
        graphics.drawCenteredString(this.font, this.title, left + WIN_W / 2, top + 8, COLOR_TITLE);

        Component state = status.active()
                ? Component.translatable("globalshop.vip.state_active", leftDays())
                : Component.translatable("globalshop.vip.state_none");
        graphics.drawCenteredString(this.font, state, left + WIN_W / 2, top + 28,
                status.active() ? COLOR_VIP : COLOR_DIM);

        Component price = Component.translatable("globalshop.vip.price_line",
                MoneyFormat.formatWithName(status.price()), status.days());
        graphics.drawCenteredString(this.font, price, left + WIN_W / 2, top + 44, COLOR_TEXT);

        // What auto-renew will actually do, in words, rather than only in the button's own label.
        graphics.drawCenteredString(this.font, Component.translatable(status.autoRenew()
                        ? "globalshop.vip.auto_note_on" : "globalshop.vip.auto_note_off",
                MoneyFormat.formatCompact(status.price())), left + WIN_W / 2, top + 58, COLOR_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Whole days left, rounded up so "1 day" does not show as "0 days" on the last day. */
    private long leftDays() {
        long millis = status.untilMillis() - System.currentTimeMillis();
        if (millis <= 0L) {
            return 0L;
        }
        return (millis + TimeUnit.DAYS.toMillis(1) - 1L) / TimeUnit.DAYS.toMillis(1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
