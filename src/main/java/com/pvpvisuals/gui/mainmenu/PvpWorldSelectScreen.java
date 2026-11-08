package com.pvpvisuals.gui.mainmenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import com.pvpvisuals.gui.echo.Gfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Keeps Minecraft's complete world-selection workflow while applying the Zenith
 * backdrop, glass framing and persistent bottom navigation dock.
 */
public final class PvpWorldSelectScreen extends SelectWorldScreen {
    private final PvpMainMenuScreen menu;
    private final List<AbstractWidget> actionButtons = new ArrayList<>();
    private static final int ACTION_WIDTH = 108;
    private static final int ACTION_HEIGHT = 27;
    private static final int ACTION_GAP = 8;

    public PvpWorldSelectScreen(PvpMainMenuScreen menu) {
        super(menu);
        this.menu = menu;
    }

    @Override
    protected void init() {
        super.init();

        // Reflow the vanilla actions into one compact, centered row above the dock.
        actionButtons.clear();
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.getY() > height - 80) {
                actionButtons.add(widget);
            }
        }

        int count = actionButtons.size();
        if (count > 0) {
            int totalWidth = count * ACTION_WIDTH + (count - 1) * ACTION_GAP;
            int startX = (width - totalWidth) / 2;
            int rowY = height - 101;
            for (int i = 0; i < count; i++) {
                AbstractWidget widget = actionButtons.get(i);
                widget.setWidth(ACTION_WIDTH);
                widget.setHeight(ACTION_HEIGHT);
                widget.setX(startX + i * (ACTION_WIDTH + ACTION_GAP));
                widget.setY(rowY);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        menu.renderWorldSelectBackground(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMinimalActions(graphics, mouseX, mouseY);
        menu.renderWorldSelectDock(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMinimalActions(GuiGraphics graphics, int mouseX, int mouseY) {
        for (AbstractWidget widget : actionButtons) {
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            boolean hovered = widget.active && mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + h;
            String label = widget.getMessage().getString();

            int top = hovered ? 0xB83A4963 : 0x8830394D;
            int bottom = hovered ? 0xA51B2638 : 0x76151D2A;
            int border = hovered ? 0x668C9EFF : 0x32FFFFFF;
            Gfx.glass(graphics, x, y, w, h, 8f, top, bottom, border);

            int textWidth = Gfx.widthAtSize(font, label, 6.2f);
            float textX = x + (w - textWidth) / 2f;
            float textY = y + (h - 7f) / 2f;
            Gfx.textAtSize(graphics, font, label, textX, textY, 6.2f,
                    hovered ? 0xFFFFFFFF : 0xFFE4EAF6, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && menu.handleDockClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        super.removed();
        menu.setWorldSelectPage(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
