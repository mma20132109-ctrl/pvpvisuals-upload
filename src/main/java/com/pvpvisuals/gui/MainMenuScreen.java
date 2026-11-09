package com.pvpvisuals.gui;

import com.pvpvisuals.account.OfflineAccountManager;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.echo.LiquidGlassRenderer;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.render.LogoRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

/** Main menu replacement using the project's Liquid Glass material system. */
public final class MainMenuScreen extends Screen {
    private static final String[] LABELS = {
            "Одиночная игра", "Сетевая игра", "Alt Manager", "Настройки", "Выйти"
    };
    private static final String[] HINTS = {
            "Создать мир или продолжить одиночную игру",
            "Подключиться к серверу",
            "Локальные оффлайн-профили",
            "Управление параметрами Minecraft",
            "Закрыть игру"
    };

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int buttonX;
    private int buttonW;
    private int buttonH;
    private int buttonGap;
    private int firstButtonY;

    public MainMenuScreen() {
        super(Component.literal("PvPVisuals"));
    }

    @Override
    protected void init() {
        layoutMenu();
    }

    private void layoutMenu() {
        float scale = Math.min(1f, Math.min((this.width - 24f) / 360f, (this.height - 24f) / 392f));
        scale = Math.max(0.72f, scale);
        panelW = Math.min(this.width - 16, Math.round(360f * scale));
        panelH = Math.min(this.height - 16, Math.round(392f * scale));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        buttonX = panelX + 22;
        buttonW = panelW - 44;
        buttonH = Math.max(29, Math.round(34f * scale));
        buttonGap = Math.max(4, Math.round(7f * scale));
        firstButtonY = panelY + Math.round(142f * scale);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EchoTheme.sync();
        layoutMenu();

        g.fillGradient(0, 0, this.width, this.height, 0xFF0B1B28, 0xFF050B13);
        Gfx.circle(g, this.width * 0.18f, this.height * 0.24f, Math.max(36f, this.width * 0.12f),
                0x1858D8F3);
        Gfx.circle(g, this.width * 0.82f, this.height * 0.76f, Math.max(42f, this.width * 0.15f),
                0x12719DFF);

        LiquidGlassRenderer.window(g, panelX, panelY, panelW, panelH, 16f);

        int logoH = 24;
        int logoW = LogoRenderer.hasLogo() ? LogoRenderer.crispWidth(logoH) : 0;
        if (logoW > 0) {
            LogoRenderer.renderCrisp(g, panelX + 24, panelY + 21, logoH, 1f);
            Gfx.textScaled(g, this.font, "PvPVisuals", panelX + 58, panelY + 23, 1.55f,
                    EchoTheme.TEXT, false);
        } else {
            Gfx.circle(g, panelX + 31, panelY + 33, 5, EchoTheme.NEON);
            Gfx.textScaled(g, this.font, "PvPVisuals", panelX + 44, panelY + 23, 1.55f,
                    EchoTheme.TEXT, false);
        }
        Gfx.text(g, this.font, "LIQUID GLASS CLIENT", panelX + 24, panelY + 57, EchoTheme.NEON);
        Gfx.text(g, this.font, "Рады видеть, " + OfflineAccountManager.get().currentName(),
                panelX + 24, panelY + 91, EchoTheme.TEXT);
        Gfx.text(g, this.font, "Выбери раздел для продолжения", panelX + 24, panelY + 108,
                EchoTheme.MUTED);

        for (int i = 0; i < LABELS.length; i++) {
            int y = firstButtonY + i * (buttonH + buttonGap);
            drawButton(g, mouseX, mouseY, i, y);
        }

        int footerY = panelY + panelH - 22;
        Gfx.text(g, this.font, "PvPVisuals  •  Cyan Liquid Glass",
                panelX + 24, footerY, EchoTheme.DIM);
    }

    private void drawButton(GuiGraphics g, int mouseX, int mouseY, int index, int y) {
        boolean hovered = Gfx.hit(mouseX, mouseY, buttonX, y, buttonW, buttonH);
        int tint = hovered ? EchoTheme.CARD_HOVER : EchoTheme.CARD;
        int border = hovered ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER;
        LiquidGlassRenderer.surface(g, buttonX, y, buttonW, buttonH, 9f, tint, border);
        int cy = y + buttonH / 2;
        Gfx.circle(g, buttonX + 16, cy, hovered ? 4 : 3, EchoTheme.NEON);
        Gfx.text(g, this.font, LABELS[index], buttonX + 30, y + 7, EchoTheme.TEXT);
        Gfx.text(g, this.font, HINTS[index], buttonX + 30, y + 20, EchoTheme.DIM);
        Gfx.text(g, this.font, "›", buttonX + buttonW - 18, y + 10, EchoTheme.NEON);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        layoutMenu();
        for (int i = 0; i < LABELS.length; i++) {
            int y = firstButtonY + i * (buttonH + buttonGap);
            if (!Gfx.hit(event.x(), event.y(), buttonX, y, buttonW, buttonH)) continue;
            Minecraft mc = Minecraft.getInstance();
            switch (i) {
                case 0 -> mc.setScreen(new SelectWorldScreen(this));
                case 1 -> mc.setScreen(new JoinMultiplayerScreen(this));
                case 2 -> mc.setScreen(new AltManagerScreen(this));
                case 3 -> mc.setScreen(new OptionsScreen(this, mc.options));
                case 4 -> mc.scheduleStop();
                default -> { }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Keep the main menu as the root screen; Escape must not return to the vanilla title menu.
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}