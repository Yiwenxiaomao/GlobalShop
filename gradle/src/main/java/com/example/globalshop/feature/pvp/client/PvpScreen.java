package com.example.globalshop.feature.pvp.client;

import com.example.globalshop.api.economy.MoneyFormat;
import com.example.globalshop.core.network.ModNetwork;
import com.example.globalshop.feature.pvp.net.PvpActionPacket;
import com.example.globalshop.feature.pvp.net.PvpDataPacket;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The PvP window: whether you are fightable, and who you are fighting.
 *
 * <p>The switch comes first because nothing else can happen without it. The list underneath only ever
 * contains players who have turned it on themselves, so a name in it is an offer that can actually be
 * taken up — and its absence is the whole protection: there is no way to start a fight with somebody
 * who has not agreed.
 *
 * <p>The stake and the range are both on screen before anything is clicked. A duel with a hidden
 * price would be a trap, and the range is the interesting part: what losing costs is rolled at the
 * moment of death, so neither player knows the figure while they are fighting.
 */
public class PvpScreen extends Screen {

    private static final int WIN_W = 300;
    private static final int WIN_H = 212;
    private static final int ROW_H = 14;
    private static final int LIST_Y = 92;
    private static final int PAGE_SIZE = 6;

    private static final int C_TITLE = 0xFFFFD54F;
    private static final int C_TEXT = 0xFFEEEEEE;
    private static final int C_DIM = 0xFF9E9E9E;
    private static final int C_GOOD = 0xFF7FD07F;
    private static final int C_BAD = 0xFFFF9E9E;
    private static final int C_MONEY = 0xFFFFD700;
    private static final int C_PANEL = 0xF0121218;

    private final PvpDataPacket data;
    private int left;
    private int top;
    private int page;

    public PvpScreen(PvpDataPacket data) {
        super(Component.translatable("globalshop.pvp.window"));
        this.data = data;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIN_W) / 2;
        this.top = (this.height - WIN_H) / 2;
        this.page = 0;

        addRenderableWidget(Button.builder(toggleLabel(),
                        b -> ModNetwork.sendToServer(new PvpActionPacket(PvpActionPacket.TOGGLE, "")))
                .bounds(left + 8, top + 28, WIN_W - 16, 18).build());

        int pager = top + LIST_Y + PAGE_SIZE * ROW_H + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turnPage(-1))
                .bounds(left + 8, pager, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turnPage(1))
                .bounds(left + WIN_W / 2 - 24, pager, 16, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("globalshop.pvp.close"),
                        b -> onClose())
                .bounds(left + 8, top + WIN_H - 24, WIN_W - 16, 18).build());
    }

    private Component toggleLabel() {
        return Component.translatable("globalshop.pvp.toggle",
                Component.translatable(data.willing()
                        ? "globalshop.pvp.state_on"
                        : "globalshop.pvp.state_off"));
    }

    private void turnPage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
    }

    private int pageCount() {
        return Math.max(1, (data.candidates().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private List<String> pageNames() {
        List<String> all = data.candidates();
        int from = Math.min(page * PAGE_SIZE, all.size());
        return all.subList(from, Math.min(from + PAGE_SIZE, all.size()));
    }

    private int nameAt(double mouseX, double mouseY) {
        int relY = (int) mouseY - top - LIST_Y;
        if (mouseX < left + 8 || mouseX > left + WIN_W / 2 - 8 || relY < 0) {
            return -1;
        }
        int index = relY / ROW_H;
        return index < pageNames().size() ? index : -1;
    }

    /** Whether the mouse is over the "end the duel" line of the right-hand panel. */
    private boolean overEnd(double mouseX, double mouseY) {
        int x = left + WIN_W / 2 + 6;
        int y = top + 60 + ROW_H * 2 - 2;
        return !data.opponentName().isEmpty() && mouseX >= x && mouseY >= y - 2
                && mouseY <= y + ROW_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = nameAt(mouseX, mouseY);
            if (index >= 0 && data.willing() && data.opponentName().isEmpty()) {
                ModNetwork.sendToServer(new PvpActionPacket(PvpActionPacket.CHALLENGE,
                        pageNames().get(index)));
                return true;
            }
            if (overEnd(mouseX, mouseY)) {
                ModNetwork.sendToServer(new PvpActionPacket(PvpActionPacket.END, ""));
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
                Component.translatable("globalshop.pvp.stake",
                        MoneyFormat.formatWithName(data.stake()), data.minPercent(),
                        data.maxPercent()),
                left + WIN_W / 2, top + 50, C_MONEY);
        graphics.drawCenteredString(this.font,
                Component.translatable("globalshop.pvp.range",
                        MoneyFormat.formatWithName(data.stake() * data.minPercent() / 100L),
                        MoneyFormat.formatWithName(data.stake() * data.maxPercent() / 100L)),
                left + WIN_W / 2, top + 62, C_DIM);

        graphics.drawString(this.font, Component.translatable("globalshop.pvp.pick"),
                left + 8, top + 78, C_DIM, false);

        drawCandidates(graphics, mouseX, mouseY);
        drawDuel(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCandidates(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!data.willing()) {
            graphics.drawString(this.font, Component.translatable("globalshop.pvp.turn_on_first"),
                    left + 10, top + LIST_Y + 2, C_BAD, false);
            return;
        }
        List<String> names = pageNames();
        if (names.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.pvp.none"),
                    left + 10, top + LIST_Y + 2, C_DIM, false);
            return;
        }
        int hovered = nameAt(mouseX, mouseY);
        for (int i = 0; i < names.size(); i++) {
            int y = top + LIST_Y + i * ROW_H;
            boolean over = hovered == i && data.opponentName().isEmpty();
            if (over) {
                graphics.fill(left + 8, y - 1, left + WIN_W / 2 - 10, y + ROW_H - 3, 0x50FFD54F);
            }
            graphics.drawString(this.font, Component.literal(names.get(i)), left + 12, y + 2,
                    over ? C_TITLE : C_TEXT, false);
        }
        Component pages = Component.literal((page + 1) + "/" + pageCount());
        graphics.drawString(this.font, pages, left + 28,
                top + LIST_Y + PAGE_SIZE * ROW_H + 7, C_DIM, false);
    }

    private void drawDuel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + WIN_W / 2 + 6;
        int y = top + 60;
        if (data.opponentName().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("globalshop.pvp.not_duelling"),
                    x, y + 2, C_DIM, false);
            return;
        }
        graphics.drawString(this.font,
                Component.translatable("globalshop.pvp.duelling", data.opponentName()),
                x, y, C_TEXT, false);
        graphics.drawString(this.font, Component.translatable("globalshop.pvp.end"),
                x, y + ROW_H * 2 - 2, overEnd(mouseX, mouseY) ? C_TITLE : C_BAD, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
