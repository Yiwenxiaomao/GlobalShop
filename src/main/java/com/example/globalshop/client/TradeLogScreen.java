package com.example.globalshop.client;

import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.network.packet.TradeLogDataPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The viewer's own player-to-player trades, newest first.
 *
 * <p>A plain {@link Screen}: it is a read-only record, so it has no slots and no reason to expose
 * the player's inventory.
 *
 * <p>The amount column is the <em>gross</em> transaction amount, and says so. The log records
 * what the buyer paid; the shop's commission is taken afterwards and is not part of this record,
 * so labelling the column "income" would quietly overstate what a seller received.
 */
public class TradeLogScreen extends Screen {

    private static final int WIN_W = 320;
    private static final int WIN_H = 230;
    /** 11, not 12: twelve rows would reach the footer. See MailboxScreen for the arithmetic. */
    private static final int ROWS_PER_PAGE = 11;
    private static final int ROW_H = 12;
    private static final int HEADER_Y = 34;
    private static final int FIRST_ROW_Y = 48;
    private static final int PAD = 10;
    private static final int AMOUNT_RIGHT_DX = WIN_W - PAD;

    private final List<TradeLogDataPacket.Line> lines;

    private int page;
    private int left;
    private int top;
    private Button prevButton;
    private Button nextButton;

    public TradeLogScreen(TradeLogDataPacket data) {
        super(Component.translatable("globalshop.log.title"));
        this.lines = data.lines();
    }

    private int pageCount() {
        return Math.max(1, (lines.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
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
        graphics.drawCenteredString(this.font, Component.translatable("globalshop.log.subtitle"),
                left + WIN_W / 2, top + 20, 0xFF9E9E9E);

        graphics.drawString(this.font, Component.translatable("globalshop.log.when"),
                left + PAD, top + HEADER_Y, 0xFFBDBDBD, false);
        graphics.drawString(this.font, Component.translatable("globalshop.log.what"),
                left + PAD + 92, top + HEADER_Y, 0xFFBDBDBD, false);
        Component amountHeading = Component.translatable("globalshop.log.amount");
        graphics.drawString(this.font, amountHeading,
                left + AMOUNT_RIGHT_DX - this.font.width(amountHeading), top + HEADER_Y,
                0xFFBDBDBD, false);
        graphics.fill(left + PAD, top + HEADER_Y + 10, left + WIN_W - PAD, top + HEADER_Y + 11,
                0xFF555555);

        if (lines.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("globalshop.log.empty"),
                    left + WIN_W / 2, top + FIRST_ROW_Y + 8, 0xFF9E9E9E);
        }

        int first = page * ROWS_PER_PAGE;
        for (int i = 0; i < ROWS_PER_PAGE && first + i < lines.size(); i++) {
            TradeLogDataPacket.Line line = lines.get(first + i);
            int y = top + FIRST_ROW_Y + i * ROW_H;
            // A sale reads green, a purchase red, matching how the money moved for the reader.
            int colour = line.wasSeller() ? 0xFF9BE79B : 0xFFFF9E9E;
            String sign = line.wasSeller() ? "+" : "-";

            Component when = Component.literal(GuiFormat.shortTime(line.timestamp()));
            graphics.drawString(this.font, when, left + PAD, y, 0xFF9E9E9E, false);

            Component what = line.wasSeller()
                    ? Component.translatable("globalshop.log.sold_to",
                            line.quantity(), line.itemName(), line.counterparty())
                    : Component.translatable("globalshop.log.bought_from",
                            line.quantity(), line.itemName(), line.counterparty());
            Component amount = Component.literal(sign + MoneyFormat.formatCompact(line.total()));
            int amountWidth = this.font.width(amount);
            int room = AMOUNT_RIGHT_DX - amountWidth - 6 - (PAD + 92);
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(what.getString(), Math.max(12, room))),
                    left + PAD + 92, y, colour, false);
            graphics.drawString(this.font, amount, left + AMOUNT_RIGHT_DX - amountWidth, y, colour, false);
        }

        graphics.drawString(this.font, Component.literal((page + 1) + " / " + pageCount()),
                left + PAD + 50, top + WIN_H - 19, 0xFFBDBDBD, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
