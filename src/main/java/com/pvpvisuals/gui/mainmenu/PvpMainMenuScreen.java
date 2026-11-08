package com.pvpvisuals.gui.mainmenu;

import com.pvpvisuals.PvpVisualsClient;
import com.pvpvisuals.gui.echo.EchoScreen;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.echo.LiquidGlassRenderer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Zenith-style main-menu port for PvP Visuals.
 *
 * The layout follows the supplied reference: centered brand, glass account badge,
 * left visual selector and bottom navigation dock. Navigation delegates to the
 * vanilla world, multiplayer and options screens, while Mods opens PvP Visuals'
 * own Click GUI.
 */
public final class PvpMainMenuScreen extends Screen {
    private static final String[] BACKDROP_NAMES = {
            "Sadnes пейзаж", "Небесный пейзаж", "Космическая туманность",
            "Полярное сияние", "Киберпанк неон", "Закатный мираж",
            "Тёмный обсидиан", "Цвета темы"
    };
    private static final String[] MAIN_LABELS = {"Одиночная", "Сетевая игра", "Аккаунты", "Моды"};
    private static final String[] MAIN_GLYPHS = {"◈", "⌁", "♙", "▦"};
    private static final String[] ACTION_GLYPHS = {"⚙", "⏻"};
    private static final int GLASS_TOP = 0x751C202B;
    private static final int GLASS_BOTTOM = 0x6810141D;
    private static final int GLASS_BORDER = 0x28FFFFFF;

    private static int backdropIndex;
    private static long backdropToastAt;
    private final float[] dockHover = new float[6];
    private float sidebarHover;
    private float parallaxX;
    private float parallaxY;
    private boolean accountsPage;
    private long lastFrameNs;

    public PvpMainMenuScreen() {
        super(Component.literal("Zenith"));
    }

    /** Replaces only the vanilla title screen; all other Minecraft screens remain untouched. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                client.setScreen(new PvpMainMenuScreen());
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 16f : Math.min(50f, (now - lastFrameNs) / 1_000_000f);
        lastFrameNs = now;

        updateParallax(mouseX, mouseY, dt);
        drawBackdrop(graphics, now);
        LiquidGlassRenderer.captureBackground();

        Gfx.alpha = 1f;
        if (accountsPage) {
            drawAccountsPage(graphics, mouseX, mouseY, dt);
        } else {
            drawBrand(graphics);
        }
        drawUserBadge(graphics);
        drawSidebar(graphics, mouseX, mouseY, dt, now);
        drawDock(graphics, mouseX, mouseY, dt);
        Gfx.alpha = 1f;
    }

    private void updateParallax(float mouseX, float mouseY, float dt) {
        float tx = width <= 0 ? 0f : Math.max(-1f, Math.min(1f, (mouseX - width / 2f) / (width / 2f)));
        float ty = height <= 0 ? 0f : Math.max(-1f, Math.min(1f, (mouseY - height / 2f) / (height / 2f)));
        float speed = Math.max(0.04f, Math.min(0.25f, dt * 0.008f));
        parallaxX += (tx - parallaxX) * speed;
        parallaxY += (ty - parallaxY) * speed;
    }

    /**
     * Procedural fallback backdrop. The source repository's PNG files are binary assets
     * and are not readable through the connected GitHub text-file API, so this keeps the
     * screen valid rather than referencing missing textures.
     */
    private void drawBackdrop(GuiGraphics g, long now) {
        float t = now / 1_000_000_000f;
        g.fill(0, 0, width, height, 0xFF080B16);
        float drift = (float) Math.sin(t * 0.12f) * 10f;
        float cx = width * 0.52f + parallaxX * 16f + drift;
        float cy = height * 0.40f + parallaxY * 12f;
        int[] glows = {0x182D4F9D, 0x143A2F75, 0x12244773, 0x0E7D3B91};
        float[] radii = {Math.max(width, height) * 0.47f, Math.max(width, height) * 0.34f,
                Math.max(width, height) * 0.24f, Math.max(width, height) * 0.16f};
        for (int i = 0; i < glows.length; i++) {
            float r = radii[i];
            Gfx.circle(g, cx - i * 24f, cy + i * 13f, r, glows[i]);
        }
        // Fixed deterministic star field with a slow parallax shift.
        for (int i = 0; i < 74; i++) {
            int seed = i * 1103515245 + 12345;
            float x = Math.floorMod(seed, 10007) / 10007f * width;
            float y = Math.floorMod(seed >>> 8, 10009) / 10009f * height;
            float pulse = 0.55f + 0.45f * (float) Math.sin(t * 0.55f + i * 1.7f);
            int alpha = Math.round((12f + (i % 5) * 5f) * pulse);
            int color = (alpha << 24) | (0xAFC5E8 + (i % 3) * 0x050407);
            float size = i % 13 == 0 ? 1.35f : 0.8f;
            g.fill(Math.round(x + parallaxX * (i % 4 + 1) * 0.7f),
                    Math.round(y + parallaxY * (i % 3 + 1) * 0.5f),
                    Math.round(x + parallaxX * (i % 4 + 1) * 0.7f) + Math.max(1, Math.round(size)),
                    Math.round(y + parallaxY * (i % 3 + 1) * 0.5f) + Math.max(1, Math.round(size)), color);
        }
        g.fill(0, 0, width, height, 0x18000000);
    }

    private void drawBrand(GuiGraphics g) {
        String title = "Zenith";
        String subtitle = "Welcome back";
        float titleSize = 40f;
        float subtitleSize = 13f;
        float titleWidth = Gfx.widthAtSize(font, title, titleSize);
        float subtitleWidth = Gfx.widthAtSize(font, subtitle, subtitleSize);
        float titleY = height * 0.35f;
        Gfx.textAtSize(g, font, title, (width - titleWidth) / 2f, titleY,
                titleSize, 0xFFF3F5FF, false);
        Gfx.textAtSize(g, font, subtitle, (width - subtitleWidth) / 2f,
                titleY + 39f, subtitleSize, 0xBFC1C9DC, false);
    }

    private void drawUserBadge(GuiGraphics g) {
        String username = Minecraft.getInstance().getUser().getName();
        if (username == null || username.isBlank()) username = "Zenith User";
        String value = "◉  " + username;
        int textWidth = Gfx.widthAtSize(font, value, 7.2f);
        float w = Math.max(72f, textWidth + 18f);
        float x = width - w - 16f;
        float y = 16f;
        Gfx.glass(g, x, y, w, 28f, 10f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);
        Gfx.textAtSize(g, font, value, x + 9f, y + 9f, 7.2f, 0xFFE6E9F5, false);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY, float dt, long now) {
        float x = 16f;
        float y = (height - 32f) / 2f;
        boolean over = Gfx.hit(mouseX, mouseY, 16, Math.round(y + 3), 26, 26);
        sidebarHover += ((over ? 1f : 0f) - sidebarHover) * Math.min(1f, dt / 90f);
        Gfx.glass(g, x, y, 26f, 32f, 11f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);
        int iconColor = lerp(0xFFC1C6D8, EchoTheme.ACCENT_TEXT, sidebarHover);
        Gfx.textAtSize(g, font, "✦", x + 8f - sidebarHover, y + 8f - sidebarHover,
                12f + sidebarHover * 2f, iconColor, false);

        long elapsed = now / 1_000_000L - backdropToastAt;
        if (backdropToastAt != 0L && elapsed >= 0L && elapsed < 2400L) {
            float alpha = elapsed > 1800L ? 1f - (elapsed - 1800L) / 600f : 1f;
            String label = "Фон: " + BACKDROP_NAMES[backdropIndex];
            float w = Gfx.widthAtSize(font, label, 7f) + 20f;
            float tx = x + 36f;
            float ty = y + 5f;
            Gfx.alpha = alpha;
            Gfx.glass(g, tx, ty, w, 22f, 6f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);
            Gfx.textAtSize(g, font, label, tx + 9f, ty + 7f, 7f, 0xFFF0F2FA, false);
            Gfx.alpha = 1f;
        }
    }

    private void drawDock(GuiGraphics g, int mouseX, int mouseY, float dt) {
        float dockH = 36f;
        float btnW = 46f;
        float btnH = 28f;
        float actionW = 26f;
        float gap = 2f;
        float separatorGap = 6f;
        float padX = 7f;
        float dockW = padX * 2f + btnW * 4f + gap * 3f + separatorGap * 2f + 1f + actionW * 2f + gap;
        float dockX = (width - dockW) / 2f;
        float dockY = height - dockH - 22f;
        Gfx.glass(g, dockX, dockY, dockW, dockH, 11f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);

        float x = dockX + padX;
        float y = dockY + (dockH - btnH) / 2f;
        for (int i = 0; i < 4; i++) {
            boolean active = i == 2 && accountsPage;
            boolean over = Gfx.hit(mouseX, mouseY, Math.round(x), Math.round(y), Math.round(btnW), Math.round(btnH));
            dockHover[i] += ((over ? 1f : 0f) - dockHover[i]) * Math.min(1f, dt / 70f);
            float h = dockHover[i];
            if (active || h > 0.02f) {
                Gfx.rrect(g, x, y, btnW, btnH, 7f,
                        alpha(active ? EchoTheme.NEON : 0x26FFFFFF, active ? 0.20f : h * 0.55f),
                        alpha(active ? EchoTheme.NEON : 0x12FFFFFF, active ? 0.12f : h * 0.35f),
                        alpha(active ? EchoTheme.NEON : 0x00FFFFFF, active ? 0.45f : h * 0.25f),
                        active ? 0.8f : 0f);
            }
            String glyph = MAIN_GLYPHS[i];
            float gs = 11f + h * 2f;
            float gw = Gfx.widthAtSize(font, glyph, gs);
            Gfx.textAtSize(g, font, glyph, x + (btnW - gw) / 2f, y + 2.5f - h,
                    gs, active ? EchoTheme.ACCENT_TEXT : lerp(0xC3BFC5D8, 0xFFFFFFFF, h), false);
            float lw = Gfx.widthAtSize(font, MAIN_LABELS[i], 6.5f);
            Gfx.textAtSize(g, font, MAIN_LABELS[i], x + (btnW - lw) / 2f, y + 18f,
                    6.5f, active ? 0xFFFFFFFF : lerp(0xB7B9C0D0, 0xFFF1F2FA, h), false);
            x += btnW + gap;
        }

        x += separatorGap - gap;
        g.fill(Math.round(x), Math.round(dockY + 11f), Math.round(x + 1f), Math.round(dockY + 25f), 0x24FFFFFF);
        x += 1f + separatorGap;
        for (int i = 4; i < 6; i++) {
            boolean over = Gfx.hit(mouseX, mouseY, Math.round(x), Math.round(y), Math.round(actionW), Math.round(btnH));
            dockHover[i] += ((over ? 1f : 0f) - dockHover[i]) * Math.min(1f, dt / 70f);
            float h = dockHover[i];
            String glyph = ACTION_GLYPHS[i - 4];
            float gs = 12f + h * 2f;
            float gw = Gfx.widthAtSize(font, glyph, gs);
            int base = i == 5 ? 0xFFC3BFCB : 0xFFC1C6D8;
            int hover = i == 5 ? 0xFFFF858B : 0xFFFFFFFF;
            Gfx.textAtSize(g, font, glyph, x + (actionW - gw) / 2f, y + 7f - h,
                    gs, lerp(base, hover, h), false);
            x += actionW + gap;
        }
    }

    private void drawAccountsPage(GuiGraphics g, int mouseX, int mouseY, float dt) {
        float w = Math.min(402f, width - 36f);
        float h = 206f;
        float x = (width - w) / 2f;
        float y = (height - h) / 2f - 8f;
        Gfx.glass(g, x, y, w, h, 12f, 0x85202634, 0x7610141D, 0x37FFFFFF);
        Gfx.textAtSize(g, font, "Аккаунты", x + 18f, y + 16f, 15f, 0xFFF0F2FA, false);
        Gfx.textAtSize(g, font, "Текущий профиль Minecraft", x + 18f, y + 43f,
                7.4f, 0xFFAEB5C8, false);

        float rowY = y + 66f;
        Gfx.glass(g, x + 14f, rowY, w - 28f, 52f, 8f, 0x522B3342, 0x40131822, 0x24FFFFFF);
        String username = Minecraft.getInstance().getUser().getName();
        Gfx.circle(g, x + 34f, rowY + 26f, 10f, 0x558C9EFF);
        Gfx.textAtSize(g, font, "♙", x + 30f, rowY + 20f, 10f, 0xFFE7EAFA, false);
        Gfx.textAtSize(g, font, username, x + 52f, rowY + 13f, 8f, 0xFFF0F2FA, false);
        Gfx.textAtSize(g, font, "Активная игровая сессия", x + 52f, rowY + 29f,
                6.2f, 0xFF9CA4B7, false);

        float buttonW = 120f;
        float buttonH = 27f;
        float by = y + h - 42f;
        float bx = x + 16f;
        boolean over = Gfx.hit(mouseX, mouseY, Math.round(bx), Math.round(by), Math.round(buttonW), Math.round(buttonH));
        Gfx.glass(g, bx, by, buttonW, buttonH, 7f, over ? 0xAA39445A : 0x63242B39,
                0x53151B27, 0x32FFFFFF);
        String copy = "Скопировать ник";
        Gfx.textAtSize(g, font, copy, bx + 11f, by + 9f, 6.5f, 0xFFE8EAF5, false);

        float backW = 72f;
        float backX = x + w - backW - 16f;
        boolean backOver = Gfx.hit(mouseX, mouseY, Math.round(backX), Math.round(by), Math.round(backW), Math.round(buttonH));
        Gfx.glass(g, backX, by, backW, buttonH, 7f, backOver ? 0xAA39445A : 0x63242B39,
                0x53151B27, 0x32FFFFFF);
        Gfx.textAtSize(g, font, "Назад", backX + 23f, by + 9f, 6.5f, 0xFFE8EAF5, false);
        Gfx.textAtSize(g, font, "Управление Microsoft-аккаунтами требует отдельной OAuth-настройки клиента.",
                x + 18f, y + h - 57f, 5.5f, 0xFF858DA1, false);
    }

    private void activateDock(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (index) {
            case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
            case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> accountsPage = !accountsPage;
            case 3 -> minecraft.setScreen(new EchoScreen());
            case 4 -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case 5 -> minecraft.stop();
            default -> { }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();

        if (accountsPage) {
            float w = Math.min(402f, width - 36f);
            float h = 206f;
            float x = (width - w) / 2f;
            float y = (height - h) / 2f - 8f;
            float by = y + h - 42f;
            if (Gfx.hit(mx, my, Math.round(x + 16f), Math.round(by), 120, 27)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(Minecraft.getInstance().getUser().getName());
                return true;
            }
            if (Gfx.hit(mx, my, Math.round(x + w - 88f), Math.round(by), 72, 27)) {
                accountsPage = false;
                return true;
            }
            return true;
        }

        float dockW = 271f;
        float dockX = (width - dockW) / 2f;
        float dockY = height - 58f;
        float x = dockX + 7f;
        for (int i = 0; i < 4; i++) {
            if (Gfx.hit(mx, my, Math.round(x), Math.round(dockY + 4f), 46, 28)) {
                activateDock(i);
                return true;
            }
            x += 48f;
        }
        x += 4f;
        if (Gfx.hit(mx, my, Math.round(x), Math.round(dockY + 4f), 26, 28)) {
            activateDock(4);
            return true;
        }
        x += 28f;
        if (Gfx.hit(mx, my, Math.round(x), Math.round(dockY + 4f), 26, 28)) {
            activateDock(5);
            return true;
        }

        float sideY = (height - 32f) / 2f;
        if (Gfx.hit(mx, my, 16, Math.round(sideY), 26, 32)) {
            backdropIndex = (backdropIndex + 1) % BACKDROP_NAMES.length;
            backdropToastAt = System.currentTimeMillis();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (accountsPage) {
                accountsPage = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int alpha(int color, float amount) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 255) * amount)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = Math.round(((from >>> 16) & 255) + (((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int gr = Math.round(((from >>> 8) & 255) + (((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }
}
