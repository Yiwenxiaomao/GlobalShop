package com.example.globalshop.feature.ranking.client;

import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.feature.ranking.net.RankingDataPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.example.globalshop.core.social.client.MailboxScreen;

/**
 * The money ranking, as its own window.
 *
 * <p>A plain {@link Screen} rather than a container screen: there are no slots and nothing to
 * move, so giving it a menu would mean inventing an inventory it does not have and would put the
 * player's own items on screen where they could be clicked by accident.
 *
 * <p>The figures are rebuilt by the server at most once every five minutes, so the window states
 * how old they are instead of implying they are live. A board that silently shows five-minute-old
 * money is worse than one that admits it.
 */
public class RankingScreen extends Screen {

    private static final int WIN_W = 300;
    private static final int WIN_H = 230;
    /** 11, not 12: twelve rows would reach the footer. See MailboxScreen for the arithmetic. */
    private static final int ROWS_PER_PAGE = 11;
    private static final int ROW_H = 12;
    private static final int HEADER_Y = 34;
    private static final int FIRST_ROW_Y = 48;
    private static final int PAD = 10;
    /** Where the balance column's right edge sits inside the window. */
    private static final int BALANCE_RIGHT_DX = WIN_W - PAD;

    private final List<RankingDataPacket.Row> rows;
    private final long builtAt;

    private int page;
    private int left;
    private int top;
    private Button prevButton;
    private Button nextButton;

    public RankingScreen(RankingDataPacket data) {
        super(Component.translatable("globalshop.ranking.title"));
        this.rows = data.rows();
        this.builtAt = data.builtAt();
    }

    private int pageCount() {
        return Math.max(1, (rows.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        int footer = top + WIN_H - 24;
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + PAD, footer, 20, 18).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + PAD + 24, footer, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + WIN_W - PAD - 60, footer, 60, 18).build());
        updatePaging();
    }

    private void turnPage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        updatePaging();
    }

    private void updatePaging() {
        prevButton.active = page > 0;
        nextButton.active = page < pageCount() - 1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, 0xF0101010);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);

        graphics.drawCenteredString(this.font, this.title, left + WIN_W / 2, top + 8, 0xFFFFD54F);
        graphics.drawCenteredString(this.font, ageText(), left + WIN_W / 2, top + 20, 0xFF9E9E9E);

        // Column headings, so a bare number at the right edge is never a guess.
        graphics.drawString(this.font, Component.translatable("globalshop.ranking.rank"),
                left + PAD, top + HEADER_Y, 0xFFBDBDBD, false);
        graphics.drawString(this.font, Component.translatable("globalshop.ranking.player"),
                left + PAD + 30, top + HEADER_Y, 0xFFBDBDBD, false);
        Component balanceHeading = Component.translatable("globalshop.ranking.balance");
        graphics.drawString(this.font, balanceHeading,
                left + BALANCE_RIGHT_DX - this.font.width(balanceHeading), top + HEADER_Y,
                0xFFBDBDBD, false);
        graphics.fill(left + PAD, top + HEADER_Y + 10, left + WIN_W - PAD, top + HEADER_Y + 11,
                0xFF555555);

        if (rows.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("globalshop.ranking.empty"),
                    left + WIN_W / 2, top + FIRST_ROW_Y + 8, 0xFF9E9E9E);
        }

        int first = page * ROWS_PER_PAGE;
        for (int i = 0; i < ROWS_PER_PAGE && first + i < rows.size(); i++) {
            RankingDataPacket.Row row = rows.get(first + i);
            int y = top + FIRST_ROW_Y + i * ROW_H;
            // The top three are tinted, so "who is winning" reads before any of the numbers do.
            int nameColour = row.position() <= 3 ? 0xFFFFD54F : 0xFFEEEEEE;
            graphics.drawString(this.font, "#" + row.position(), left + PAD, y, 0xFF9E9E9E, false);

            Component balance = Component.literal(MoneyFormat.formatCompact(row.balance()));
            int balanceWidth = this.font.width(balance);
            int nameRoom = BALANCE_RIGHT_DX - balanceWidth - 6 - (PAD + 30);
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(row.name(), Math.max(12, nameRoom))),
                    left + PAD + 30, y, nameColour, false);
            graphics.drawString(this.font, balance,
                    left + BALANCE_RIGHT_DX - balanceWidth, y, 0xFF9BE79B, false);
        }

        Component paging = Component.literal((page + 1) + " / " + pageCount());
        graphics.drawString(this.font, paging, left + PAD + 50, top + WIN_H - 19, 0xFFBDBDBD, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** "updated N minutes ago", or "just now". */
    private Component ageText() {
        long ageMinutes = Math.max(0L, (System.currentTimeMillis() - builtAt) / 60_000L);
        if (ageMinutes <= 0L) {
            return Component.translatable("globalshop.ranking.age_now");
        }
        return Component.translatable("globalshop.ranking.age", ageMinutes);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
