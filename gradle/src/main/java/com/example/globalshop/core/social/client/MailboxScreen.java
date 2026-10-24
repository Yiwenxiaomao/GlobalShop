package com.example.globalshop.core.social.client;

import com.example.globalshop.core.social.net.MailView;
import com.example.globalshop.core.social.net.MailboxActionPacket;
import com.example.globalshop.core.social.net.MailboxDataPacket;
import com.example.globalshop.core.network.ModNetwork;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A player's mailbox.
 *
 * <p>A plain {@link Screen}: mail is claimed by the server straight into the backpack, so there is
 * nothing to drag and no reason to put the player's own items on screen where they could be
 * clicked by accident.
 *
 * <p>Left-click claims, right-click deletes. The right-click is deliberately the destructive one
 * and the left-click the useful one, so a stray click does the thing the player wanted rather than
 * destroying a delivery. Lapsed mail shows as lapsed and only offers the delete.
 */
public class MailboxScreen extends Screen {

    private static final int WIN_W = 320;
    private static final int WIN_H = 230;
    /**
     * 11, not 12: the rows start at {@link #FIRST_ROW_Y} and are {@link #ROW_H} apart, so twelve of
     * them would run to y=192 while the footer starts at {@link #FOOTER_Y}. The last row would sit
     * on top of the buttons.
     */
    private static final int ROWS_PER_PAGE = 11;
    private static final int ROW_H = 12;
    private static final int HEADER_Y = 34;
    private static final int FIRST_ROW_Y = 48;
    /** The click legend's own band, between the last row and the buttons. */
    private static final int LEGEND_Y = 194;
    private static final int FOOTER_Y = 206;
    private static final int PAD = 10;
    private static final int ICON_DX = PAD;
    private static final int TEXT_DX = PAD + 22;
    private static final int RIGHT_X = WIN_W - PAD;

    private final List<MailView> mails;

    private int page;
    private int left;
    private int top;
    private Button prevButton;
    private Button nextButton;

    public MailboxScreen(MailboxDataPacket data) {
        super(Component.translatable("globalshop.mail.title"));
        this.mails = data.mails();
    }

    private int pageCount() {
        return Math.max(1, (mails.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        int footer = top + FOOTER_Y;
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

    /** Which mail a screen position is over, or {@code null}. */
    private MailView mailAt(double mouseX, double mouseY) {
        int relX = (int) mouseX - left - PAD;
        int relY = (int) mouseY - top - FIRST_ROW_Y;
        if (relX < 0 || relX > WIN_W - PAD * 2 || relY < 0) {
            return null;
        }
        int row = relY / ROW_H;
        if (row >= ROWS_PER_PAGE) {
            return null;
        }
        int index = page * ROWS_PER_PAGE + row;
        return index < mails.size() ? mails.get(index) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MailView mail = mailAt(mouseX, mouseY);
        if (mail != null && (button == 0 || button == 1)) {
            // Right-click deletes. It is only allowed to delete, never to claim and delete at
            // once, so the two gestures can never mean the same thing.
            ModNetwork.sendToServer(new MailboxActionPacket(mail.id(), button == 1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + WIN_W, top + WIN_H, 0xF0101010);
        graphics.renderOutline(left, top, WIN_W, WIN_H, 0xFF7A7A7A);

        graphics.drawCenteredString(this.font, this.title, left + WIN_W / 2, top + 8, 0xFFFFD54F);
        graphics.drawCenteredString(this.font, Component.translatable("globalshop.mail.subtitle"),
                left + WIN_W / 2, top + 20, 0xFF9E9E9E);

        graphics.drawString(this.font, Component.translatable("globalshop.mail.what"),
                left + TEXT_DX, top + HEADER_Y, 0xFFBDBDBD, false);
        Component expiry = Component.translatable("globalshop.mail.expiry");
        graphics.drawString(this.font, expiry, left + RIGHT_X - this.font.width(expiry),
                top + HEADER_Y, 0xFFBDBDBD, false);
        graphics.fill(left + PAD, top + HEADER_Y + 10, left + WIN_W - PAD, top + HEADER_Y + 11,
                0xFF555555);

        if (mails.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("globalshop.mail.empty"),
                    left + WIN_W / 2, top + FIRST_ROW_Y + 8, 0xFF9E9E9E);
        }

        long now = System.currentTimeMillis();
        int first = page * ROWS_PER_PAGE;
        for (int i = 0; i < ROWS_PER_PAGE && first + i < mails.size(); i++) {
            MailView mail = mails.get(first + i);
            int y = top + FIRST_ROW_Y + i * ROW_H;
            boolean lapsed = mail.expiredAt(now);
            // Lapsed mail is greyed, because the one thing it still supports is deletion.
            int colour = lapsed ? 0xFF6E6E6E : 0xFFEEEEEE;

            graphics.renderItem(mail.display(), left + ICON_DX, y - 4);

            Component what = Component.translatable("globalshop.mail.line",
                    mail.totalCount(), mail.display().getHoverName(), mail.sourceKey());
            Component age = lapsed
                    ? Component.translatable("globalshop.mail.lapsed")
                    : Component.translatable("globalshop.mail.left",
                            Math.max(0L, (mail.expiresAt() - now) / 86_400_000L));
            int ageColour = lapsed ? 0xFFFF5555 : 0xFF9E9E9E;
            int ageWidth = this.font.width(age);
            graphics.drawString(this.font,
                    Component.literal(this.font.plainSubstrByWidth(what.getString(),
                            Math.max(16, RIGHT_X - ageWidth - 6 - TEXT_DX))),
                    left + TEXT_DX, y, colour, false);
            graphics.drawString(this.font, age, left + RIGHT_X - ageWidth, y, ageColour, false);
        }

        // ---- the click legend ----
        // Deliberately loud, on its own band, and colour-matched to the consequence. An
        // unexplained right-click in a list of somebody's purchases deletes one, and that is not
        // something a player should have to discover by trying it.
        graphics.fill(left + PAD, top + LEGEND_Y - 7, left + WIN_W - PAD, top + LEGEND_Y - 6,
                0xFF555555);
        Component claimLegend = Component.translatable("globalshop.mail.legend.claim");
        Component deleteLegend = Component.translatable("globalshop.mail.legend.delete");
        int gap = 28;
        int legendWidth = this.font.width(claimLegend) + gap + this.font.width(deleteLegend);
        int legendX = left + (WIN_W - legendWidth) / 2;
        graphics.drawString(this.font, claimLegend, legendX, top + LEGEND_Y, 0xFF3FBF3F, false);
        graphics.drawString(this.font, deleteLegend,
                legendX + this.font.width(claimLegend) + gap, top + LEGEND_Y, 0xFFFF5555, false);

        graphics.drawString(this.font, Component.literal((page + 1) + " / " + pageCount()),
                left + PAD, top + FOOTER_Y + 5, 0xFFBDBDBD, false);

        // The held mail's full tooltip, so a multi-stack delivery can be inspected before it is
        // claimed — claiming is what a left-click does, so this must be readable first.
        if (this.minecraft != null) {
            MailView hovered = mailAt(mouseX, mouseY);
            if (hovered != null) {
                java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
                lines.add(hovered.display().getHoverName());
                lines.add(Component.translatable("globalshop.mail.total", hovered.totalCount()));
                graphics.renderTooltip(this.font, lines, hovered.display().getTooltipImage(),
                        mouseX, mouseY);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
