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
    private int accountCarouselIndex;
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
        int[][] palettes = {
                {0xFF080B16, 0x183A5CC7, 0x162A4DAA, 0x123D2D91},
                {0xFF06131A, 0x1834B69B, 0x15268BA9, 0x103DDFB5},
                {0xFF090817, 0x1F8C6CF0, 0x1C3D72D8, 0x15451A8D},
                {0xFF061614, 0x1B10C890, 0x19409FCE, 0x1334D8BC},
                {0xFF100817, 0x1FF02C9C, 0x1B13B9EE, 0x174E28AF},
                {0xFF160B18, 0x1FEF793D, 0x1ACB3E54, 0x175F247C},
                {0xFF070A10, 0x24394B68, 0x1D18283F, 0x163B4863},
                {0xFF080B16, alpha(EchoTheme.ACCENT_A, 0.14f), alpha(EchoTheme.ACCENT_B, 0.10f), 0x143A3F73}
        };
        int[] palette = palettes[Math.min(7, Math.max(0, backdropIndex))];
        g.fill(0, 0, width, height, palette[0]);
        float drift = (float) Math.sin(t * 0.12f) * 10f;
        float cx = width * 0.52f + parallaxX * 16f + drift;
        float cy = height * 0.40f + parallaxY * 12f;
        float maxRadius = Math.max(width, height);
        for (int i = 0; i < 5; i++) {
            float radius = maxRadius * (0.46f - i * 0.065f);
            float ox = (float) Math.sin(t * (0.07f + i * 0.01f) + i) * (12f + i * 5f);
            float oy = (float) Math.cos(t * (0.09f + i * 0.01f) + i * 0.7f) * (8f + i * 4f);
            Gfx.circle(g, cx + ox, cy + oy, radius, palette[1 + i % 3]);
        }

        // The source offers animated shader families. These GPU-free equivalents preserve
        // their individual visual identities while remaining compatible with our renderer.
        if (backdropIndex == 3) {
            for (int band = 0; band < 5; band++) {
                float y = height * (0.31f + band * 0.075f);
                float[] curve = new float[26];
                for (int p = 0; p < 13; p++) {
                    float x = width * p / 12f;
                    float wave = (float) Math.sin(p * 0.45f + t * 0.32f + band * 0.8f);
                    curve[p * 2] = x;
                    curve[p * 2 + 1] = y + wave * (12f + band * 2f) + parallaxY * (band + 1);
                }
                Gfx.polyline(g, 1.2f + band * 0.3f, alpha(palette[2], 0.85f - band * 0.12f), curve);
            }
        } else if (backdropIndex == 4) {
            int grid = alpha(0xFF33D8F2, 0.14f);
            for (int i = 0; i <= 18; i++) {
                float x = width * i / 18f + parallaxX * 2f;
                Gfx.line(g, x, height * 0.32f, width / 2f + (x - width / 2f) * 1.55f,
                        height, 0.7f, grid);
            }
            for (int i = 0; i < 10; i++) {
                float f = i / 9f;
                float y = height * (0.36f + f * f * 0.64f);
                Gfx.line(g, 0f, y, width, y, 0.7f, grid);
            }
        } else if (backdropIndex == 5) {
            float sun = Math.min(width, height) * 0.18f;
            float sunX = width * 0.72f - parallaxX * 9f;
            float sunY = height * 0.43f - parallaxY * 6f;
            for (int i = 5; i >= 0; i--) {
                float r = sun * (1f + i * 0.21f);
                Gfx.circle(g, sunX, sunY, r, alpha(palette[2], 0.18f - i * 0.02f));
            }
            for (int i = 0; i < 6; i++) {
                float y = sunY + sun * (0.24f + i * 0.12f);
                Gfx.line(g, sunX - sun * 0.9f, y, sunX + sun * 0.9f, y,
                        1.4f + i * 0.15f, alpha(0xFF160B18, 0.68f));
            }
        } else if (backdropIndex == 6) {
            for (int i = 0; i < 8; i++) {
                float inset = 20f + i * 22f;
                Gfx.rrect(g, inset, inset, width - inset * 2f, height - inset * 2f,
                        24f + i * 3f, 0x00000000, 0x00000000,
                        alpha(palette[2], 0.12f - i * 0.01f), 0.7f);
            }
        }

        // Stable star field; density and brightness vary slightly by shader preset.
        int stars = backdropIndex == 6 ? 38 : 74;
        for (int i = 0; i < stars; i++) {
            int seed = i * 1103515245 + 12345;
            float x = Math.floorMod(seed, 10007) / 10007f * width;
            float y = Math.floorMod(seed >>> 8, 10009) / 10009f * height;
            float pulse = 0.55f + 0.45f * (float) Math.sin(t * 0.55f + i * 1.7f);
            int starAlpha = Math.round((10f + (i % 5) * 4f) * pulse);
            int color = (starAlpha << 24) | (0xAFC5E8 + (i % 3) * 0x050407);
            float size = i % 13 == 0 ? 1.35f : 0.8f;
            int px = Math.round(x + parallaxX * (i % 4 + 1) * 0.7f);
            int py = Math.round(y + parallaxY * (i % 3 + 1) * 0.5f);
            g.fill(px, py, px + Math.max(1, Math.round(size)), py + Math.max(1, Math.round(size)), color);
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
        float h = Math.min(270f, height - 24f);
        float x = (width - w) / 2f;
        float y = (height - h) / 2f - 8f;
        Gfx.glass(g, x, y, w, h, 12f, 0x85202634, 0x7610141D, 0x37FFFFFF);
        Gfx.textAtSize(g, font, "Аккаунты", x + 18f, y + 13f, 15f, 0xFFF0F2FA, false);
        Gfx.textAtSize(g, font, "Управление игровыми профилями", x + 18f, y + 36f,
                7.2f, 0xFFAEB5C8, false);

        java.util.List<PvpAccountManager.Profile> profiles = PvpAccountManager.profiles();
        if (profiles.isEmpty()) {
            accountCarouselIndex = 0;
        } else {
            accountCarouselIndex = Math.max(0, Math.min(profiles.size() - 1, accountCarouselIndex));
            float centerX = x + w / 2f;
            float centerY = y + 113f;
            java.util.List<Integer> order = new java.util.ArrayList<>();
            for (int i = 0; i < profiles.size(); i++) {
                if (Math.abs(i - accountCarouselIndex) <= 2) order.add(i);
            }
            order.sort((left, right) -> Float.compare(
                    Math.abs(right - accountCarouselIndex), Math.abs(left - accountCarouselIndex)));
            for (int index : order) {
                PvpAccountManager.Profile profile = profiles.get(index);
                float distance = index - accountCarouselIndex;
                float abs = Math.abs(distance);
                float scale = Math.max(0.68f, 1f - 0.16f * abs);
                float cardW = 94f * scale;
                float cardH = 124f * scale;
                float spacing = abs <= 1f ? abs * 78f : 78f + (abs - 1f) * 52f;
                float cardX = centerX + Math.signum(distance) * spacing - cardW / 2f;
                float cardY = centerY - cardH / 2f + abs * 4f;
                float fade = abs <= 1.5f ? 1f : Math.max(0.38f, 1f - (abs - 1.5f) * 0.35f);
                boolean active = PvpAccountManager.isActive(profile);
                Gfx.alpha = fade;
                Gfx.glow(g, cardX, cardY, cardW, cardH, 9f,
                        alpha(EchoTheme.NEON, active ? 0.20f : 0.08f), 4f);
                Gfx.glass(g, cardX, cardY, cardW, cardH, 9f,
                        active ? 0xA5384057 : 0x87232B39, 0x74101620,
                        active ? 0x668C9EFF : 0x35FFFFFF);
                Gfx.circle(g, cardX + cardW / 2f, cardY + 31f, 19f,
                        active ? 0x4C8C9EFF : 0x403F526E);
                Gfx.circle(g, cardX + cardW / 2f, cardY + 25f, 8f,
                        active ? 0xFFE2E8FF : 0xFFB8C1D5);
                Gfx.rrect(g, cardX + cardW / 2f - 13f, cardY + 34f, 26f, 16f,
                        8f, active ? 0xFFE2E8FF : 0xFFB8C1D5,
                        active ? 0xFFD1D9F1 : 0xFFA5B0C8, 0, 0f);
                String initial = profile.name.isEmpty() ? "?" : profile.name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
                float initialW = Gfx.widthAtSize(font, initial, 9f);
                Gfx.textAtSize(g, font, initial, cardX + (cardW - initialW) / 2f,
                        cardY + 22f, 9f, 0xFF263047, false);
                String name = Gfx.trimAtSize(font, profile.name, Math.round(cardW - 12f), 6.5f);
                float nameW = Gfx.widthAtSize(font, name, 6.5f);
                Gfx.textAtSize(g, font, name, cardX + (cardW - nameW) / 2f,
                        cardY + 61f, 6.5f, 0xFFF0F2FA, false);
                String type = active ? "ВЫБРАН" : "OFFLINE";
                float typeW = Gfx.widthAtSize(font, type, 4.8f);
                Gfx.textAtSize(g, font, type, cardX + (cardW - typeW) / 2f,
                        cardY + 75f, 4.8f, active ? EchoTheme.ACCENT_TEXT : 0xFF9CA5B8, false);
                if (index == accountCarouselIndex) {
                    Gfx.glass(g, cardX + 9f, cardY + cardH - 24f, cardW - 18f, 17f, 5f,
                            0x74475270, 0x50202A3A, 0x408C9EFF);
                    String select = active ? "Текущий" : "Выбрать";
                    float sw = Gfx.widthAtSize(font, select, 5.5f);
                    Gfx.textAtSize(g, font, select,
                            cardX + (cardW - sw) / 2f, cardY + cardH - 18f,
                            5.5f, 0xFFE8EEFF, false);
                }
                Gfx.alpha = 1f;
            }
            if (profiles.size() > 1) {
                String counter = (accountCarouselIndex + 1) + " / " + profiles.size();
                float cw = Gfx.widthAtSize(font, counter, 5.4f);
                Gfx.textAtSize(g, font, counter, centerX - cw / 2f, y + 183f,
                        5.4f, 0xFF8D97AC, false);
                Gfx.textAtSize(g, font, "‹", centerX - 72f, y + 100f, 15f, 0xFFBFC8DB, false);
                Gfx.textAtSize(g, font, "›", centerX + 64f, y + 100f, 15f, 0xFFBFC8DB, false);
            }
            boolean active = PvpAccountManager.isActive(profiles.get(accountCarouselIndex));
            float actionY = y + 193f;
            float actionW = 76f;
            float selectX = x + w / 2f - actionW - 4f;
            float deleteX = x + w / 2f + 4f;
            Gfx.glass(g, selectX, actionY, actionW, 22f, 6f,
                    active ? 0x66343C50 : 0x8C344A6D, 0x53151B27, 0x558C9EFF);
            Gfx.textAtSize(g, font, active ? "Выбран" : "Выбрать",
                    selectX + (active ? 22f : 21f), actionY + 7f, 5.8f,
                    active ? EchoTheme.ACCENT_TEXT : 0xFFE8EAF5, false);
            Gfx.glass(g, deleteX, actionY, actionW, 22f, 6f, 0x633B2730, 0x40151B27, 0x42D86C78);
            Gfx.textAtSize(g, font, "Удалить", deleteX + 21f, actionY + 7f,
                    5.8f, 0xFFFFB6BA, false);
        }

        float inputY = y + h - 38f;
        float inputX = x + 14f;
        float inputW = w - 162f;
        Gfx.glass(g, inputX, inputY, inputW, 26f, 7f,
                accountInputFocused ? 0xAA303B51 : 0x63242B39, 0x53151B27,
                accountInputFocused ? 0x668C9EFF : 0x32FFFFFF);
        String shownInput = accountInput.isEmpty() ? "Введите ник (3–16 символов)" : accountInput;
        Gfx.textAtSize(g, font, shownInput, inputX + 8f, inputY + 8f, 6.1f,
                accountInput.isEmpty() ? 0xFF858DA1 : 0xFFF0F2FA, false);
        if (accountInputFocused && System.currentTimeMillis() % 1000L < 500L) {
            float caretX = inputX + 8f + Gfx.widthAtSize(font, accountInput, 6.1f) + 1f;
            g.fill(Math.round(caretX), Math.round(inputY + 7f), Math.round(caretX + 1f),
                    Math.round(inputY + 19f), 0xFFB8C4FF);
        }
        float addX = x + w - 138f;
        boolean addHover = Gfx.hit(mouseX, mouseY, Math.round(addX), Math.round(inputY), 60, 26);
        Gfx.glass(g, addX, inputY, 60f, 26f, 7f,
                addHover ? 0xAA39445A : 0x63242B39, 0x53151B27, 0x32FFFFFF);
        Gfx.textAtSize(g, font, "Добавить", addX + 11f, inputY + 8f, 5.8f, 0xFFE8EAF5, false);
        float randomX = x + w - 72f;
        Gfx.glass(g, randomX, inputY, 26f, 26f, 7f, 0x63242B39, 0x53151B27, 0x32FFFFFF);
        Gfx.textAtSize(g, font, "↻", randomX + 8f, inputY + 7f, 10f, 0xFFE8EAF5, false);
        float clearX = x + w - 40f;
        Gfx.glass(g, clearX, inputY, 26f, 26f, 7f, 0x533B2730, 0x40151B27, 0x28FFFFFF);
        Gfx.textAtSize(g, font, "×", clearX + 8f, inputY + 6f, 11f, 0xFFFFB6BA, false);
    }

    private void activateDock(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (index) {
            case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
            case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> { accountsPage = !accountsPage; accountInputFocused = accountsPage; accountInput = ""; accountCarouselIndex = PvpAccountManager.activeIndex(); }
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
            float h = Math.min(270f, height - 24f);
            float x = (width - w) / 2f;
            float y = (height - h) / 2f - 8f;
            float inputX = x + 14f;
            float inputY = y + h - 38f;
            float inputW = w - 162f;
            if (Gfx.hit(mx, my, Math.round(inputX), Math.round(inputY), Math.round(inputW), 26)) {
                accountInputFocused = true;
                return true;
            }
            float addX = x + w - 138f;
            if (Gfx.hit(mx, my, Math.round(addX), Math.round(inputY), 60, 26)) {
                if (PvpAccountManager.addOffline(accountInput)) {
                    accountCarouselIndex = PvpAccountManager.profiles().size() - 1;
                }
                accountInput = "";
                accountInputFocused = true;
                return true;
            }
            float randomX = x + w - 72f;
            if (Gfx.hit(mx, my, Math.round(randomX), Math.round(inputY), 26, 26)) {
                String[] parts = {"Void","Nova","Echo","Frost","Ember","Lunar","Cyber","Neon","Shadow","Prism","Riot","Drift","Pulse","Zen","Flux"};
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                accountInput = parts[random.nextInt(parts.length)] + parts[random.nextInt(parts.length)].toLowerCase(java.util.Locale.ROOT)
                        + random.nextInt(10, 100);
                accountInputFocused = true;
                return true;
            }
            float clearX = x + w - 40f;
            if (Gfx.hit(mx, my, Math.round(clearX), Math.round(inputY), 26, 26)) {
                accountInput = "";
                accountInputFocused = true;
                return true;
            }

            java.util.List<PvpAccountManager.Profile> profiles = PvpAccountManager.profiles();
            if (!profiles.isEmpty()) {
                float centerX = x + w / 2f;
                float centerY = y + 113f;
                for (int i = 0; i < profiles.size(); i++) {
                    float distance = i - accountCarouselIndex;
                    float abs = Math.abs(distance);
                    if (abs > 2f) continue;
                    float scale = Math.max(0.68f, 1f - 0.16f * abs);
                    float cardW = 94f * scale;
                    float cardH = 124f * scale;
                    float spacing = abs <= 1f ? abs * 78f : 78f + (abs - 1f) * 52f;
                    float cardX = centerX + Math.signum(distance) * spacing - cardW / 2f;
                    float cardY = centerY - cardH / 2f + abs * 4f;
                    if (Gfx.hit(mx, my, Math.round(cardX), Math.round(cardY),
                            Math.round(cardW), Math.round(cardH))) {
                        accountCarouselIndex = i;
                        accountInputFocused = false;
                        return true;
                    }
                }
                float actionY = y + 193f;
                float selectX = centerX - 80f;
                float deleteX = centerX + 4f;
                if (Gfx.hit(mx, my, Math.round(selectX), Math.round(actionY), 76, 22)) {
                    PvpAccountManager.select(accountCarouselIndex);
                    accountInputFocused = false;
                    return true;
                }
                if (Gfx.hit(mx, my, Math.round(deleteX), Math.round(actionY), 76, 22)) {
                    PvpAccountManager.remove(accountCarouselIndex);
                    accountCarouselIndex = Math.max(0, PvpAccountManager.profiles().size() - 1 == -1 ? 0 : Math.min(PvpAccountManager.profiles().size() - 1, accountCarouselIndex));
                    accountInputFocused = false;
                    return true;
                }
                if (Gfx.hit(mx, my, Math.round(centerX - 82f), Math.round(y + 88f), 24, 32)) {
                    accountCarouselIndex = Math.max(0, accountCarouselIndex - 1);
                    return true;
                }
                if (Gfx.hit(mx, my, Math.round(centerX + 58f), Math.round(y + 88f), 24, 32)) {
                    accountCarouselIndex = Math.min(profiles.size() - 1, accountCarouselIndex + 1);
                    return true;
                }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (accountsPage) {
            int count = PvpAccountManager.profiles().size();
            if (count > 0 && Gfx.hit(mouseX, mouseY,
                    Math.round((width - Math.min(402f, width - 36f)) / 2f),
                    Math.round((height - Math.min(270f, height - 24f)) / 2f - 8f),
                    Math.round(Math.min(402f, width - 36f)), Math.round(Math.min(270f, height - 24f)))) {
                accountCarouselIndex = Math.max(0, Math.min(count - 1,
                        accountCarouselIndex + (scrollY < 0 ? 1 : -1)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
