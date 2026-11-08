package com.pvpvisuals.gui.mainmenu;

import com.pvpvisuals.PvpVisualsClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import net.minecraft.client.input.CharacterEvent;
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
    private static final String SOURCE_ASSET_BASE =
            "https://raw.githubusercontent.com/mma20132109-ctrl/pvpvisuals-mainmenu-source/72f5841359a6c47d4d254d1accda8c5e80da4fe2/assets/menu/";
    private static final String[] IMAGE_BACKGROUNDS = {"sadnes.png", "sky.png"};
    private static final Identifier[] LOADED_BACKGROUNDS = new Identifier[IMAGE_BACKGROUNDS.length];
    private static final boolean[] BACKGROUND_REQUESTED = new boolean[IMAGE_BACKGROUNDS.length];
    private static final long[] BACKGROUND_RETRY_AFTER = new long[IMAGE_BACKGROUNDS.length];
    private static final ExecutorService ASSET_EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "pvpvisuals-menu-assets");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient ASSET_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
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
    private boolean accountInputFocused = true;
    private String accountInput = "";
    private long lastFrameNs;

    public PvpMainMenuScreen() {
        super(Component.literal("Zenith"));
    }

    /** Replaces only the vanilla title screen; all other Minecraft screens remain untouched. */
    public static void register() {
        PvpAccountManager.load();
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

    private static void ensureBackgroundDownloads() {
        for (int i = 0; i < IMAGE_BACKGROUNDS.length; i++) {
            final int index = i;
            synchronized (BACKGROUND_REQUESTED) {
                if (BACKGROUND_REQUESTED[index] || LOADED_BACKGROUNDS[index] != null
                        || System.nanoTime() < BACKGROUND_RETRY_AFTER[index]) continue;
                BACKGROUND_REQUESTED[index] = true;
            }
            ASSET_EXECUTOR.execute(() -> loadBackground(index));
        }
    }

    private static void loadBackground(int index) {
        String fileName = IMAGE_BACKGROUNDS[index];
        Path cache = FabricLoader.getInstance().getConfigDir()
                .resolve("pvpvisuals").resolve("menu-assets").resolve(fileName);
        try {
            byte[] bytes;
            if (Files.isRegularFile(cache) && Files.size(cache) > 8L) {
                bytes = Files.readAllBytes(cache);
            } else {
                HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE_ASSET_BASE + fileName))
                        .timeout(Duration.ofSeconds(25)).header("User-Agent", "PvPVisuals/1.0").GET().build();
                HttpResponse<byte[]> response = ASSET_HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200 || response.body().length < 8 || response.body().length > 12_000_000) {
                    throw new IllegalStateException("Unexpected HTTP response for " + fileName + ": " + response.statusCode());
                }
                bytes = response.body();
                if (bytes[0] != (byte) 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E || bytes[3] != 0x47) {
                    throw new IllegalStateException("Downloaded background is not a PNG: " + fileName);
                }
                try {
                    Files.createDirectories(cache.getParent());
                    Files.write(cache, bytes);
                } catch (Exception ignored) {
                    // A cache write failure must not prevent this session from showing the image.
                }
            }

            byte[] imageBytes = bytes;
            Minecraft.getInstance().execute(() -> {
                try {
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                    DynamicTexture texture = new DynamicTexture(() -> "pvpvisuals_mainmenu_" + fileName, image);
                    texture.upload();
                    Identifier id = Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "menu_runtime/" + fileName);
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                    LOADED_BACKGROUNDS[index] = id;
                } catch (Throwable error) {
                    synchronized (BACKGROUND_REQUESTED) {
                        BACKGROUND_REQUESTED[index] = false;
                        BACKGROUND_RETRY_AFTER[index] = System.nanoTime() + 60_000_000_000L;
                    }
                    System.err.println("[pvpvisuals] Не удалось загрузить фон меню " + fileName + ": " + error);
                }
            });
        } catch (Throwable error) {
            synchronized (BACKGROUND_REQUESTED) {
                BACKGROUND_REQUESTED[index] = false;
                BACKGROUND_RETRY_AFTER[index] = System.nanoTime() + 60_000_000_000L;
            }
            System.err.println("[pvpvisuals] Не удалось скачать фон меню " + fileName + ": " + error);
        }
    }

    /** Uses the exact background PNGs from the supplied source repository when available. */
    private void drawBackdrop(GuiGraphics g, long now) {
        ensureBackgroundDownloads();
        if (backdropIndex < LOADED_BACKGROUNDS.length && LOADED_BACKGROUNDS[backdropIndex] != null) {
            float maxOffset = 22f;
            float extra = maxOffset * 2f + 12f;
            float targetW = width + extra;
            float targetH = height + extra;
            float imageAspect = 1920f / 1080f;
            float screenAspect = targetW / Math.max(1f, targetH);
            float drawW;
            float drawH;
            if (screenAspect > imageAspect) {
                drawW = targetW;
                drawH = targetW / imageAspect;
            } else {
                drawH = targetH;
                drawW = targetH * imageAspect;
            }
            float offsetX = -parallaxX * maxOffset;
            float offsetY = -parallaxY * maxOffset;
            int x = Math.round((width - drawW) / 2f + offsetX);
            int y = Math.round((height - drawH) / 2f + offsetY);
            int w = Math.round(drawW);
            int h = Math.round(drawH);
            g.blit(RenderPipelines.GUI_TEXTURED, LOADED_BACKGROUNDS[backdropIndex],
                    x, y, 0f, 0f, w, h, 1920, 1080, 1920, 1080, 0xFFFFFFFF);
            g.fill(0, 0, width, height, 0x1E000000);
            return;
        }

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
        float h = Math.min(250f, height - 24f);
        float x = (width - w) / 2f;
        float y = (height - h) / 2f - 8f;
        Gfx.glass(g, x, y, w, h, 12f, 0x85202634, 0x7610141D, 0x37FFFFFF);
        Gfx.textAtSize(g, font, "Аккаунты", x + 18f, y + 15f, 15f, 0xFFF0F2FA, false);
        Gfx.textAtSize(g, font, "Оффлайн-профили Minecraft", x + 18f, y + 39f,
                7.2f, 0xFFAEB5C8, false);

        float inputX = x + 14f;
        float inputY = y + 56f;
        float inputW = w - 118f;
        Gfx.glass(g, inputX, inputY, inputW, 28f, 7f,
                accountInputFocused ? 0xAA303B51 : 0x63242B39, 0x53151B27,
                accountInputFocused ? 0x668C9EFF : 0x32FFFFFF);
        String shownInput = accountInput.isEmpty() ? "Введите ник (3–16 символов)" : accountInput;
        Gfx.textAtSize(g, font, shownInput, inputX + 9f, inputY + 9f, 6.3f,
                accountInput.isEmpty() ? 0xFF858DA1 : 0xFFF0F2FA, false);
        if (accountInputFocused && System.currentTimeMillis() % 1000L < 500L) {
            float caretX = inputX + 9f + Gfx.widthAtSize(font, accountInput, 6.3f) + 1f;
            g.fill(Math.round(caretX), Math.round(inputY + 8f), Math.round(caretX + 1f),
                    Math.round(inputY + 20f), 0xFFB8C4FF);
        }

        float addX = x + w - 96f;
        boolean addHover = Gfx.hit(mouseX, mouseY, Math.round(addX), Math.round(inputY), 82, 28);
        Gfx.glass(g, addX, inputY, 82f, 28f, 7f,
                addHover ? 0xAA39445A : 0x63242B39, 0x53151B27, 0x32FFFFFF);
        Gfx.textAtSize(g, font, "Добавить", addX + 20f, inputY + 9f, 6.5f, 0xFFE8EAF5, false);

        java.util.List<PvpAccountManager.Profile> profiles = PvpAccountManager.profiles();
        float rowY = y + 94f;
        int visible = Math.min(4, profiles.size());
        for (int i = 0; i < visible; i++) {
            PvpAccountManager.Profile profile = profiles.get(i);
            boolean active = PvpAccountManager.isActive(profile);
            Gfx.glass(g, x + 14f, rowY, w - 28f, 29f, 7f,
                    active ? 0x6C34405A : 0x492B3342, 0x36131822,
                    active ? 0x558C9EFF : 0x24FFFFFF);
            Gfx.circle(g, x + 28f, rowY + 14.5f, 4.2f, active ? EchoTheme.ACCENT_TEXT : 0xFF8790A4);
            Gfx.textAtSize(g, font, profile.name, x + 40f, rowY + 9f, 7f,
                    0xFFF0F2FA, false);
            Gfx.textAtSize(g, font, active ? "Текущий профиль" : "Оффлайн-профиль",
                    x + 40f, rowY + 19f, 5.4f, 0xFF929BAD, false);
            float selectX = x + w - 104f;
            float deleteX = x + w - 51f;
            boolean selectHover = Gfx.hit(mouseX, mouseY, Math.round(selectX), Math.round(rowY + 4f), 44, 21);
            Gfx.glass(g, selectX, rowY + 4f, 44f, 21f, 5f,
                    active ? 0x66343C50 : (selectHover ? 0xAA39445A : 0x53242B39),
                    0x40151B27, 0x28FFFFFF);
            Gfx.textAtSize(g, font, active ? "Активен" : "Выбрать",
                    selectX + (active ? 8f : 9f), rowY + 11f, 5.4f,
                    active ? EchoTheme.ACCENT_TEXT : 0xFFE8EAF5, false);
            boolean deleteHover = Gfx.hit(mouseX, mouseY, Math.round(deleteX), Math.round(rowY + 4f), 30, 21);
            Gfx.glass(g, deleteX, rowY + 4f, 30f, 21f, 5f,
                    deleteHover ? 0x88984B55 : 0x533B2730, 0x40151B27, 0x28FFFFFF);
            Gfx.textAtSize(g, font, "×", deleteX + 11f, rowY + 8f, 10f, 0xFFFFA5AA, false);
            rowY += 33f;
        }
        if (profiles.isEmpty()) {
            Gfx.textAtSize(g, font, "Список пуст. Добавь оффлайн-профиль выше.",
                    x + 18f, y + 105f, 6.2f, 0xFF9CA4B7, false);
        } else if (profiles.size() > visible) {
            Gfx.textAtSize(g, font, "+" + (profiles.size() - visible) + " ещё",
                    x + 18f, y + h - 31f, 5.6f, 0xFF858DA1, false);
        }
        Gfx.textAtSize(g, font, "Оффлайн-профили подходят для одиночной игры и серверов без online-mode.",
                x + 18f, y + h - 18f, 5.4f, 0xFF858DA1, false);
        float backX = x + w - 62f;
        Gfx.textAtSize(g, font, "Назад  ‹", backX, y + h - 18f, 5.8f, 0xFFD6DCEC, false);
    }

    private void activateDock(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (index) {
            case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
            case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> { accountsPage = !accountsPage; accountInputFocused = accountsPage; accountInput = ""; }
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
            float h = Math.min(250f, height - 24f);
            float x = (width - w) / 2f;
            float y = (height - h) / 2f - 8f;
            float inputX = x + 14f;
            float inputY = y + 56f;
            float inputW = w - 118f;
            if (Gfx.hit(mx, my, Math.round(inputX), Math.round(inputY), Math.round(inputW), 28)) {
                accountInputFocused = true;
                return true;
            }
            float addX = x + w - 96f;
            if (Gfx.hit(mx, my, Math.round(addX), Math.round(inputY), 82, 28)) {
                PvpAccountManager.addOffline(accountInput);
                accountInput = "";
                accountInputFocused = true;
                return true;
            }
            java.util.List<PvpAccountManager.Profile> profiles = PvpAccountManager.profiles();
            float rowY = y + 94f;
            int visible = Math.min(4, profiles.size());
            for (int i = 0; i < visible; i++) {
                float selectX = x + w - 104f;
                float deleteX = x + w - 51f;
                if (Gfx.hit(mx, my, Math.round(selectX), Math.round(rowY + 4f), 44, 21)) {
                    PvpAccountManager.select(i);
                    accountInputFocused = false;
                    return true;
                }
                if (Gfx.hit(mx, my, Math.round(deleteX), Math.round(rowY + 4f), 30, 21)) {
                    PvpAccountManager.remove(i);
                    accountInputFocused = false;
                    return true;
                }
                rowY += 33f;
            }
            float backX = x + w - 70f;
            if (Gfx.hit(mx, my, Math.round(backX), Math.round(y + h - 26f), 64, 22)) {
                accountsPage = false;
                accountInputFocused = false;
                return true;
            }
            accountInputFocused = false;
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
        x += 11f;
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
        if (accountsPage && accountInputFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!accountInput.isEmpty()) accountInput = accountInput.substring(0, accountInput.length() - 1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                PvpAccountManager.addOffline(accountInput);
                accountInput = "";
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                accountInputFocused = false;
                return true;
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (accountsPage) {
                accountsPage = false;
                accountInputFocused = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (accountsPage && accountInputFocused) {
            int codepoint = event.codepoint();
            if (accountInput.length() < 16 && (codepoint == '_' ||
                    (codepoint >= 'a' && codepoint <= 'z') ||
                    (codepoint >= 'A' && codepoint <= 'Z') ||
                    (codepoint >= '0' && codepoint <= '9'))) {
                accountInput += Character.toString(codepoint);
            }
            return true;
        }
        return super.charTyped(event);
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
