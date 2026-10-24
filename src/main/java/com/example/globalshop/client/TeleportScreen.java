package com.example.globalshop.client;

import com.example.globalshop.economy.MoneyFormat;
import com.example.globalshop.network.ModNetwork;
import com.example.globalshop.network.packet.TeleportActionPacket;
import com.example.globalshop.network.packet.TeleportDataPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The teleport window: a random jump, or asking to visit a named player.
 *
 * <h2>The clocks tick locally</h2>
 * The packet carries seconds remaining at the moment it was built, and this screen remembers when
 * that was. Every countdown is then recomputed from the difference on each frame, so the numbers
 * actually move instead of freezing at whatever they were when the window opened. Nothing is asked of
 * the server to make that happen — a cooldown is a local display of a decision the server has already
 * made, and the server refuses a request that is still cooling down whatever this screen shows.
 *
 * <h2>The player list is paged</h2>
 * A list that only scrolls is no use in a window with no scrollbar, so the names are paged with the
 * arrows and the page number beside them. Six a page, which is what fits between the random button
 * and the close button.
 */
public class TeleportScreen extends Screen {

    private static final int WIN_W = 300;
    private static final int WIN_H = 212;
    private static final int ROW_H = 14;
    private static final int LIST_Y = 78;
    private static final int PAGE_SIZE = 6;

    private static final int C_TITLE = 0xFFFFD54F;
    private static final int C_TEXT = 0xFFEEEEEE;
    private static final int C_DIM = 0xFF9E9E9E;
    private static final int C_GOOD = 0xFF7FD07F;
    private static final int C_BAD = 0xFFFF9E9E;
    private static final int C_MONEY = 0xFFFFD700;
    private static final int C_PANEL = 0xF0121218;

    private final TeleportDataPacket data;
    /** When this screen was built, so the packet's seconds can be counted down from there. */
    private final long arrivedAt = System.currentTimeMillis();
    private int left;
    private int top;
    private int page;

    public TeleportScreen(TeleportDataPacket data) {
        super(Component.translatable("globalshop.teleport.window"));
        this.data = data;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        this.page = 0;

        addRenderableWidget(Button.builder(
                        Component.translatable("globalshop.teleport.random",
                                data.minDistance(), data.maxDistance()),
                        b -> send(TeleportActionPacket.RANDOM, ""))
                .bounds(left + 8, top + 26, WIN_W - 16, 18).build());

        int pager = top + LIST_Y + PAGE_SIZE * ROW_H + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + 8, pager, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + WIN_W / 2 - 24, pager, 16, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.teleport.close"),
                        b -> onClose())
                .bounds(left + 8, top + WIN_H - 24, WIN_W - 16, 18).build());
    }

    private void send(int action, String target) {
        ModNetwork.sendToServer(new TeleportActionPacket(action, target));
    }

    private void turnPage(int delta) {
        int pages = pageCount();
        page = Math.max(0, Math.min(pages - 1, page + delta));
    }

    private int pageCount() {
        return Math.max(1, (data.online().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** Turns a packet's "seconds left" into the seconds left right now. */
    private long live(long secondsAtBuild) {
        long elapsed = (System.currentTimeMillis() - arrivedAt) / 1000L;
        return Math.max(0L, secondsAtBuild - elapsed);
    }

    private List<String> pageNames() {
        List<String> online = data.online();
        int from = Math.min(page * PAGE_SIZE, online.size());
        int to = Math.min(from + PAGE_SIZE, online.size());
        return online.subList(from, to);
    }

    /** Which name the mouse is over, or -1. */
    private int nameAt(double mouseX, double mouseY) {
        int relY = (int) mouseY - top - LIST_Y;
        if (mouseX < left + 8 || mouseX > left + WIN_W / 2 - 6 || relY < 0) {
            return -1;
        }
        int index = relY / ROW_H;
        return index < pageNames().size() ? index : -1;
    }

    /** The request panel's clickable bands: the accept/refuse or cancel line. */
    private boolean overRequestActions(double mouseX, double mouseY) {
        int x = left + WIN_W / 2 + 6;
        int y = top + 52 + ROW_H * 2 - 2;
        return mouseX >= x && mouseY >= y - 2 && mouseY <= y + ROW_H
                && (!data.incomingFrom().isEmpty() || !data.outgoingTo().isEmpty());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = nameAt(mouseX, mouseY);
            if (index >= 0) {
                if (live(data.targetCooldownSeconds()) > 0L) {
                    // Refused here as well as on the server, so the click does nothing rather than
                    // sending a request that is certain to be turned down.
                    return true;
                }
                send(TeleportActionPacket.REQUEST, pageNames().get(index));
                return true;
            }
            if (overRequestActions(mouseX, mouseY)) {
                if (!data.incomingFrom().isEmpty()) {
                    boolean accept = mouseX < left + WIN_W / 2 + 76;
                    send(accept ? TeleportActionPacket.ACCEPT : TeleportActionPacket.DENY, "");
                } else {
                    send(TeleportActionPacket.CANCEL, "");
                }
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

        long randomLeft = live(data.cooldownSeconds());
        long targetLeft = live(data.targetCooldownSeconds());
        if (randomLeft > 0L) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("globalshop.teleport.cooldown", randomLeft),
                    left + WIN_W / 2, top + 48, C_BAD);
        } else if (targetLeft > 0L) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("globalshop.teleport.target_cooldown", targetLeft),
                    left + WIN_W / 2, top + 48, C_BAD);
        }

        // What visiting somebody costs, so the price is known before a name is clicked.
        graphics.drawString(this.font,
                Component.translatable("globalshop.teleport.pick",
                        MoneyFormat.formatWithName(data.price())),
                left + 8, top + 64, C_DIM, false);

        drawNames(graphics, mouseX, mouseY);
        drawRequest(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawNames(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> names = pageNames();
        if (names.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.teleport.alone"),
                    left + 10, top + LIST_Y + 2, C_DIM, false);
            return;
        }
        int hovered = nameAt(mouseX, mouseY);
        boolean cooling = live(data.targetCooldownSeconds()) > 0L;
        for (int i = 0; i < names.size(); i++) {
            int y = top + LIST_Y + i * ROW_H;
            boolean over = hovered == i && !cooling;
            if (over) {
                graphics.fill(left + 8, y - 1, left + WIN_W / 2 - 8, y + ROW_H - 3, 0x50FFD54F);
            }
            graphics.drawString(this.font, Component.literal(names.get(i)), left + 12, y + 2,
                    cooling ? C_DIM : (over ? C_TITLE : C_TEXT), false);
        }
        // The page number, between the two arrows, so a full server is navigable without a wheel.
        Component pages = Component.literal((page + 1) + "/" + pageCount());
        graphics.drawString(this.font, pages,
                left + 8 + 16 + 4, top + LIST_Y + PAGE_SIZE * ROW_H + 7, C_DIM, false);
        if (data.online().size() > PAGE_SIZE) {
            graphics.drawString(this.font,
                    Component.translatable("globalshop.teleport.total", data.online().size()),
                    left + WIN_W / 2 - 6, top + LIST_Y + PAGE_SIZE * ROW_H + 7, C_DIM, false);
        }
    }

    /** The right-hand panel: whoever is waiting on an answer, in either direction. */
    private void drawRequest(GuiGraphics graphics) {
        int x = left + WIN_W / 2 + 6;
        int y = top + 52;
        if (data.incomingFrom().isEmpty() && data.outgoingTo().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.teleport.none"),
                    x, y + 2, C_DIM, false);
            return;
        }
        if (!data.incomingFrom().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("globalshop.teleport.wants_in", data.incomingFrom()),
                    x, y, C_TEXT, false);
            graphics.drawString(this.font,
                    Component.translatable("globalshop.teleport.expires",
                            live(data.incomingSeconds())),
                    x, y + ROW_H - 2, C_DIM, false);
            graphics.drawString(this.font, Component.translatable("globalshop.teleport.accept"),
                    x, y + ROW_H * 2 - 2, C_GOOD, false);
            graphics.drawString(this.font, Component.translatable("globalshop.teleport.deny"),
                    x + 70, y + ROW_H * 2 - 2, C_BAD, false);
            return;
        }
        graphics.drawString(this.font,
                Component.translatable("globalshop.teleport.waiting", data.outgoingTo()),
                x, y, C_TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("globalshop.teleport.expires",
                        live(data.outgoingSeconds())),
                x, y + ROW_H - 2, C_DIM, false);
        graphics.drawString(this.font, Component.translatable("globalshop.teleport.cancel"),
                x, y + ROW_H * 2 - 2, C_BAD, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
