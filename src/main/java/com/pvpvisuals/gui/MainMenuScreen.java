package com.pvpvisuals.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.platform.NativeImage;
import com.pvpvisuals.PvpVisualsClient;
import com.pvpvisuals.account.OfflineAccountManager;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.echo.LiquidGlassRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Full-screen Liquid Glass title menu inspired by the supplied Vectra-style reference.
 * The wallpaper is a normal texture; all text, icons and buttons remain live GUI elements.
 */
public final class MainMenuScreen extends Screen {
    private static final String[] LABELS = {
            "Онлайн игра", "Одиночная игра", "Ваши аккаунты", "Настройки игры", "Выйти из игры"
    };
    private static final String[] HINTS = {
            "Подключайтесь к серверам и играйте вместе с другими",
            "", "", "", ""
    };

    private static final Identifier BACKGROUND_RESOURCE =
            Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "textures/gui/mainmenu_background.jpg");
    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "dynamic/main_menu_background");

    private static boolean backgroundAttempted;
    private static boolean backgroundLoaded;

    private int panelW;
    private int buttonX;
    private int buttonW;
    private int firstButtonY;
    private int[] buttonHeights = new int[5];
    private int buttonGap;

    public MainMenuScreen() {
        super(Component.literal("PvPVisuals"));
    }

    @Override
    protected void init() {
        layoutMenu();
    }

    private void layoutMenu() {
        // Proportional layout keeps the visual balance at every GUI scale and aspect ratio.
        panelW = Math.min(this.width, Math.round(this.width * 0.302f));
        buttonX = Math.round(this.width * 0.0393f);
        buttonW = Math.min(Math.round(this.width * 0.232f),
                Math.max(80, panelW - buttonX - Math.round(this.width * 0.030f)));
        firstButtonY = Math.round(this.height * 0.422f);
        buttonHeights[0] = Math.max(34, Math.round(this.height * 0.085f));
        for (int i = 1; i < buttonHeights.length; i++) {
            buttonHeights[i] = Math.max(26, Math.round(this.height * 0.056f));
        }
        buttonGap = Math.max(4, Math.round(this.height * 0.011f));
    }

    private static void ensureBackgroundLoaded() {
        if (backgroundAttempted) return;
        backgroundAttempted = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            try (InputStream input = mc.getResourceManager().getResource(BACKGROUND_RESOURCE)
                    .orElseThrow().open()) {
                BufferedImage decoded = ImageIO.read(input);
                if (decoded == null) return;
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                if (!ImageIO.write(decoded, "png", png)) return;
                NativeImage image = NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
                DynamicTexture texture = new DynamicTexture(() -> "pvpvisuals_main_menu_background", image);
                mc.getTextureManager().register(BACKGROUND_TEXTURE, texture);
                backgroundLoaded = true;
            }
        } catch (Throwable error) {
            System.err.println("[pvpvisuals] Main menu wallpaper could not be loaded: " + error);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EchoTheme.sync();
        layoutMenu();
        drawWallpaper(g);
        drawSidebar(g);
        drawHeader(g);
        drawButtons(g, mouseX, mouseY);
        drawFooter(g);
    }

    private void drawWallpaper(GuiGraphics g) {
        ensureBackgroundLoaded();
        if (backgroundLoaded) {
            g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, 0, 0, 0f, 0f,
                    this.width, this.height, 640, 360, 640, 360, 0xFFFFFFFF);
        } else {
            g.fillGradient(0, 0, this.width, this.height, 0xFF172838, 0xFF07111C);
        }

        // Gentle cinematic darkening across the scene; the left side gets a deeper glass veil.
        g.fill(0, 0, this.width, this.height, 0x35030A13);
        int fadeWidth = Math.max(12, Math.round(panelW * 0.30f));
        int solidWidth = Math.max(0, panelW - fadeWidth);
        g.fill(0, 0, solidWidth, this.height, 0xD809121E);
        int stripes = 36;
        for (int i = 0; i < stripes; i++) {
            float t = i / (float) stripes;
            int alpha = Math.round(0xD8 * (1f - t) * (1f - t));
            int x0 = solidWidth + Math.round(fadeWidth * i / (float) stripes);
            int x1 = solidWidth + Math.round(fadeWidth * (i + 1) / (float) stripes);
            g.fill(x0, 0, Math.max(x0 + 1, x1), this.height, (alpha << 24) | 0x09121E);
        }
        // Fine edge separation, barely visible over the background.
        g.fill(solidWidth + fadeWidth - 1, 0, solidWidth + fadeWidth, this.height, 0x2289B9D8);
    }

    private void drawSidebar(GuiGraphics g) {
        // Soft top and bottom shading makes the sidebar feel like smoked glass instead of a flat card.
        g.fillGradient(0, 0, panelW, this.height, 0x260B1A2A, 0x38020A12);
    }

    private void drawHeader(GuiGraphics g) {
        float w = this.width;
        float h = this.height;
        int left = Math.round(w * 0.0393f);

        float cx = w * 0.0492f;
        float cy = h * 0.184f;
        float radius = h * 0.0355f;
        Gfx.glow(g, cx - radius, cy - radius, radius * 2f, radius * 2f,
                radius, 0xB52C79FF, 5f);
        Gfx.circle(g, cx, cy, radius, 0xC91A3154);
        Gfx.ringCircle(g, cx, cy, radius - 1.5f, 1.2f, 0xC54C91F6);
        Gfx.textScaled(g, this.font, "V", cx - radius * 0.30f, cy - radius * 0.56f,
                1.8f, 0xFF62BDFF, false);

        Gfx.text(g, this.font, "PvPVisuals", left, Math.round(h * 0.247f), 0xFFC5D5E8);
        Gfx.text(g, this.font, "/", left + this.font.width("PvPVisuals") + 12,
                Math.round(h * 0.247f), 0xFF6F8298);
        Gfx.text(g, this.font, "Главная", left + this.font.width("PvPVisuals") + 27,
                Math.round(h * 0.247f), 0xFFB7C7D9);

        String player = OfflineAccountManager.get().currentName();
        Gfx.textScaled(g, this.font, "Рады видеть тебя,", left, h * 0.289f,
                2.05f, 0xFFF4F8FF, false);
        Gfx.textScaled(g, this.font, player, left, h * 0.334f,
                2.05f, 0xFFF4F8FF, false);
        Gfx.text(g, this.font, "Всё готово — выбирай режим и начинай играть.",
                left, Math.round(h * 0.383f), 0xFFA6B5C7);
    }

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        int y = firstButtonY;
        for (int i = 0; i < LABELS.length; i++) {
            int height = buttonHeights[i];
            boolean hovered = Gfx.hit(mouseX, mouseY, buttonX, y, buttonW, height);
            drawButton(g, i, y, height, hovered);
            y += height + buttonGap;
        }
    }

    private void drawButton(GuiGraphics g, int index, int y, int height, boolean hovered) {
        boolean primary = index == 0;
        int radius = Math.max(7, Math.round(height * 0.18f));
        if (primary) {
            Gfx.glow(g, buttonX, y, buttonW, height, radius, 0x9C247BFF, hovered ? 8f : 6f);
            Gfx.rrect(g, buttonX, y, buttonW, height, radius,
                    hovered ? 0xF12F7CF5 : 0xE92567E6,
                    hovered ? 0xE9235DCE : 0xD91C4CB9,
                    0xFF54C9FF, 1.25f);
            Gfx.rrect(g, buttonX + 2, y + 2, buttonW - 4, Math.max(3, height * 0.28f),
                    Math.max(5, radius - 2), 0x25FFFFFF, 0x00FFFFFF, 0, 0f);
        } else {
            int fill = hovered ? 0xB82A4259 : 0x87202D3D;
            int border = hovered ? 0xB18CC8E6 : 0x697F97B0;
            if (hovered) Gfx.glow(g, buttonX, y, buttonW, height, radius, 0x6C4CBAF4, 3f);
            LiquidGlassRenderer.surface(g, buttonX, y, buttonW, height, radius, fill, border);
        }

        float iconX = buttonX + buttonW * 0.064f;
        float iconY = y + height / 2f;
        int iconColor = primary ? 0xFFE7F7FF : 0xFFCEDBEC;
        drawIcon(g, index, iconX, iconY, iconColor);

        int textX = buttonX + Math.round(buttonW * 0.115f);
        if (index == 0) {
            Gfx.text(g, this.font, LABELS[index], textX, y + Math.round(height * 0.19f), 0xFFFFFFFF);
            Gfx.text(g, this.font, HINTS[index], textX, y + Math.round(height * 0.56f), 0xDDE9F8FF);
        } else {
            int textY = y + (height - 9) / 2;
            Gfx.text(g, this.font, LABELS[index], textX, textY, hovered ? 0xFFFFFFFF : 0xFFE3EAF4);
        }
        Gfx.text(g, this.font, "›", buttonX + buttonW - Math.round(buttonW * 0.055f),
                y + (height - 9) / 2, primary ? 0xFFFFFFFF : 0xFF79CFFF);
    }

    private void drawIcon(GuiGraphics g, int index, float cx, float cy, int color) {
        switch (index) {
            case 0 -> { // multiplayer: globe
                Gfx.ringCircle(g, cx, cy, 8.5f, 1.5f, color);
                Gfx.line(g, cx - 8f, cy, cx + 8f, cy, 1.2f, color);
                Gfx.line(g, cx, cy - 8f, cx, cy + 8f, 1.2f, color);
                Gfx.line(g, cx - 5f, cy - 6f, cx - 2f, cy + 6f, 0.9f, color);
                Gfx.line(g, cx + 5f, cy - 6f, cx + 2f, cy + 6f, 0.9f, color);
            }
            case 1 -> { // singleplayer: simple house
                Gfx.polyline(g, 1.7f, color, cx - 9f, cy - 1f, cx, cy - 9f,
                        cx + 9f, cy - 1f, cx + 7f, cy - 1f);
                Gfx.line(g, cx - 7f, cy - 1f, cx - 7f, cy + 8f, 1.5f, color);
                Gfx.line(g, cx + 7f, cy - 1f, cx + 7f, cy + 8f, 1.5f, color);
                Gfx.line(g, cx - 7f, cy + 8f, cx + 7f, cy + 8f, 1.5f, color);
                Gfx.rrect(g, cx - 2f, cy + 2f, 4f, 6f, 1f, color, color, 0, 0f);
            }
            case 2 -> { // accounts: head and shoulders
                Gfx.circle(g, cx, cy - 4.5f, 3.8f, color);
                Gfx.rrect(g, cx - 7f, cy + 1f, 14f, 8f, 4f, color, color, 0, 0f);
            }
            case 3 -> { // settings: gear-like ring and teeth
                Gfx.ringCircle(g, cx, cy, 7.5f, 2.4f, color);
                Gfx.circle(g, cx, cy, 2.3f, color);
                Gfx.line(g, cx, cy - 11f, cx, cy - 8f, 2f, color);
                Gfx.line(g, cx, cy + 8f, cx, cy + 11f, 2f, color);
                Gfx.line(g, cx - 11f, cy, cx - 8f, cy, 2f, color);
                Gfx.line(g, cx + 8f, cy, cx + 11f, cy, 2f, color);
            }
            case 4 -> { // exit: door and outward arrow
                Gfx.line(g, cx - 7f, cy - 9f, cx - 7f, cy + 9f, 1.7f, color);
                Gfx.line(g, cx - 7f, cy - 9f, cx + 1f, cy - 9f, 1.7f, color);
                Gfx.line(g, cx - 7f, cy + 9f, cx + 1f, cy + 9f, 1.7f, color);
                Gfx.line(g, cx - 1f, cy, cx + 9f, cy, 2f, color);
                Gfx.polyline(g, 2f, color, cx + 4f, cy - 5f, cx + 9f, cy, cx + 4f, cy + 5f);
            }
            default -> { }
        }
    }

    private void drawFooter(GuiGraphics g) {
        int left = Math.round(this.width * 0.0393f);
        int y = Math.round(this.height * 0.931f);
        int right = Math.min(panelW - Math.round(this.width * 0.055f), buttonX + buttonW);
        g.fill(left, y, Math.max(left + 10, right), y + 1, 0x507F9DB8);
        Gfx.circle(g, left + 5, y + 26, 2.5f, 0xFF54BDF3);
        Gfx.text(g, this.font, "PvPVisuals", left + 18, y + 22, 0xFF9FB3C8);
        int bulletX = left + 18 + this.font.width("PvPVisuals") + 10;
        Gfx.circle(g, bulletX, y + 26, 1.8f, 0xFF9FB3C8);
        Gfx.text(g, this.font, "Liquid Glass", bulletX + 10, y + 22, 0xFF9FB3C8);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        layoutMenu();
        int y = firstButtonY;
        for (int i = 0; i < LABELS.length; i++) {
            int height = buttonHeights[i];
            if (Gfx.hit(event.x(), event.y(), buttonX, y, buttonW, height)) {
                Minecraft mc = Minecraft.getInstance();
                switch (i) {
                    case 0 -> mc.setScreen(new JoinMultiplayerScreen(this));
                    case 1 -> mc.setScreen(new SelectWorldScreen(this));
                    case 2 -> mc.setScreen(new AltManagerScreen(this));
                    case 3 -> mc.setScreen(new OptionsScreen(this, mc.options));
                    case 4 -> mc.stop();
                    default -> { }
                }
                return true;
            }
            y += height + buttonGap;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}