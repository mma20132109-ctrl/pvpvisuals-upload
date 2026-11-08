package com.pvpvisuals.gui.mainmenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Keeps Minecraft's complete world-selection workflow while applying the Zenith
 * backdrop, glass framing and persistent bottom navigation dock.
 */
public final class PvpWorldSelectScreen extends SelectWorldScreen {
    private final PvpMainMenuScreen menu;

    public PvpWorldSelectScreen(PvpMainMenuScreen menu) {
        super(menu);
        this.menu = menu;
    }

    @Override
    protected void init() {
        super.init();

        // Move the vanilla action row above the persistent bottom dock.
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.getY() > height - 44) {
                widget.setY(widget.getY() - 42);
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
        menu.renderWorldSelectDock(graphics, mouseX, mouseY, partialTick);
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
