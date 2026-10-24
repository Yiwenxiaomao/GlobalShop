package com.example.globalshop.feature.bank.client;

import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.feature.bank.net.BankActionPacket;
import com.example.globalshop.feature.bank.net.BankDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The bank window, split down the middle.
 *
 * <p>The left half is yours to put money into and the right half is the bank's to lend; they are side
 * by side because they are the same decision seen twice, and because a player who owes money should
 * not have to change windows to see how much they still have.
 *
 * <p>Every button acts on the amount in the box beside it, and every action is re-checked on the
 * server — the wallet, what is deposited, and the loan ceiling. The window's job is to show the
 * numbers and to make the wrong ones hard to ask for; it is never the thing that decides.
 */
public class BankScreen extends Screen {

    private static final int WIN_W = 320;
    private static final int WIN_H = 200;
    /**
     * Where each band of the window sits, named so the layout audit can read them.
     *
     * <p>The audit takes positions from this file; numbers written straight into a bounds() call are
     * invisible to it, which is how the close button came to sit on top of the two rate lines without
     * anything reporting it.
     */
    private static final int PAD = 8;
    private static final int HEADING_Y = 46;
    private static final int BOX_Y = 62;
    private static final int ACTION_Y = 86;
    private static final int ACTION2_Y = 108;
    private static final int TOTALS_Y = 132;
    private static final int RATE_Y = 144;
    private static final int NOTE_Y = 156;
    private static final int CLOSE_Y = WIN_H - 26;
    /** Two buttons across each half, with a gap between them. */
    private static final int HALF_W = (WIN_W / 2 - PAD * 2 - 4) / 2;
    private static final int C_TITLE = 0xFFFFD54F;
    private static final int C_TEXT = 0xFFEEEEEE;
    private static final int C_DIM = 0xFF9E9E9E;
    private static final int C_MONEY = 0xFFFFD700;
    private static final int C_BAD = 0xFFFF9E9E;
    private static final int C_GOOD = 0xFF7FD07F;
    private static final int C_PANEL = 0xF0121218;

    private BankDataPacket data;
    private int left;
    private int top;

    private EditBox depositBox;
    private EditBox loanBox;

    public BankScreen(BankDataPacket data) {
        super(Component.translatable("globalshop.bank.window"));
        this.data = data;
    }

    /**
     * Takes fresh figures without being rebuilt.
     *
     * <p>The periodic interest sweep pushes numbers to everybody; rebuilding the screen to show them
     * would clear whatever the player had typed into the amount box.
     */
    public void apply(BankDataPacket fresh) {
        this.data = fresh;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        int half = WIN_W / 2;

        depositBox = addRenderableWidget(new EditBox(this.font, left + PAD, top + BOX_Y,
                WIN_W / 2 - PAD * 2, 18, Component.translatable("globalshop.bank.amount")));
        depositBox.setMaxLength(BankActionPacket.MAX_AMOUNT_TEXT);

        loanBox = addRenderableWidget(new EditBox(this.font, left + half + PAD, top + BOX_Y,
                WIN_W / 2 - PAD * 2, 18, Component.translatable("globalshop.bank.amount")));
        loanBox.setMaxLength(BankActionPacket.MAX_AMOUNT_TEXT);

        // Each half has a typed amount on the left and "all of it" on the right. The three that
        // take an amount are halved to make room; borrowing is left full width, because there is no
        // such thing as borrowing everything.
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.deposit"),
                        b -> send(BankActionPacket.DEPOSIT, depositBox.getValue()))
                .bounds(left + PAD, top + ACTION_Y, HALF_W, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.deposit_all"),
                        b -> send(BankActionPacket.DEPOSIT_ALL, ""))
                .bounds(left + PAD + HALF_W + 4, top + ACTION_Y, HALF_W, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.withdraw"),
                        b -> send(BankActionPacket.WITHDRAW, depositBox.getValue()))
                .bounds(left + PAD, top + ACTION2_Y, HALF_W, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.withdraw_all"),
                        b -> send(BankActionPacket.WITHDRAW_ALL, ""))
                .bounds(left + PAD + HALF_W + 4, top + ACTION2_Y, HALF_W, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.borrow"),
                        b -> send(BankActionPacket.BORROW, loanBox.getValue()))
                .bounds(left + half + PAD, top + ACTION_Y, WIN_W / 2 - PAD * 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.repay"),
                        b -> send(BankActionPacket.REPAY, loanBox.getValue()))
                .bounds(left + half + PAD, top + ACTION2_Y, HALF_W, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.repay_all"),
                        b -> send(BankActionPacket.REPAY_ALL, ""))
                .bounds(left + half + PAD + HALF_W + 4, top + ACTION2_Y, HALF_W, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("globalshop.bank.close"),
                        b -> onClose())
                .bounds(left + PAD, top + CLOSE_Y, WIN_W - PAD * 2, 18).build());
    }

    private void send(int action, String amount) {
        ModNetwork.sendToServer(new BankActionPacket(action, amount));
    }

    /** The rates, phrased the way a player reads them: a fraction of a day. */
    private static String rateOf(long basisPoints) {
        return basisPoints % 100L == 0L
                ? (basisPoints / 100L) + "%"
                : (basisPoints / 100.0D) + "%";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, C_PANEL);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);
        // A line down the middle, so the two halves read as two halves.
        graphics.fill(left + WIN_W / 2, top + 26, left + WIN_W / 2 + 1, top + WIN_H - 32,
                0xFF555555);

        graphics.drawCenteredString(this.font, this.title, left + WIN_W / 2, top + 8, C_TITLE);
        graphics.drawCenteredString(this.font,
                Component.translatable("globalshop.bank.wallet",
                        MoneyFormat.formatWithName(data.wallet())),
                left + WIN_W / 2, top + 30, C_MONEY);

        int half = WIN_W / 2;
        // Left: what you have put away, and what it earns.
        graphics.drawString(this.font, Component.translatable("globalshop.bank.deposit_heading"),
                left + PAD, top + HEADING_Y, C_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("globalshop.bank.deposited",
                        MoneyFormat.formatWithName(data.deposit())),
                left + PAD, top + TOTALS_Y, C_MONEY, false);
        graphics.drawString(this.font,
                Component.translatable("globalshop.bank.deposit_rate",
                        rateOf(data.depositBasisPoints())),
                left + PAD, top + RATE_Y, C_GOOD, false);
        graphics.drawString(this.font, Component.translatable("globalshop.bank.deposit_note"),
                left + PAD, top + NOTE_Y, C_DIM, false);

        // Right: what you owe, and what it costs.
        graphics.drawString(this.font, Component.translatable("globalshop.bank.loan_heading"),
                left + half + PAD, top + HEADING_Y, C_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("globalshop.bank.owed",
                        MoneyFormat.formatWithName(data.loan())),
                left + half + PAD, top + TOTALS_Y, data.loan() > 0L ? C_BAD : C_MONEY, false);
        graphics.drawString(this.font,
                Component.translatable("globalshop.bank.loan_rate",
                        rateOf(data.loanBasisPoints())),
                left + half + PAD, top + RATE_Y, C_BAD, false);
        graphics.drawString(this.font,
                Component.translatable("globalshop.bank.room",
                        MoneyFormat.formatWithName(data.borrowingRoom())),
                left + half + PAD, top + NOTE_Y, C_DIM, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
