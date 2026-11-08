package com.pvpvisuals.gui.mainmenu;

import com.pvpvisuals.PvpVisualsClient;
import com.mojang.authlib.GameProfile;
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
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

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
            "Мой фон 1", "Мой фон 2", "Мой фон 3", "Мой фон 4"
    };
    private static final String[] MAIN_LABELS = {"Одиночная", "Сетевая игра", "Аккаунты", "Моды"};
    private static final String SOURCE_ASSET_BASE =
            "https://raw.githubusercontent.com/mma20132109-ctrl/pvpvisuals-upload/main/fon/";
    private static final String[] IMAGE_BACKGROUNDS = {"fon1.png", "fon2.png", "fon3.png", "fon4.png"};
    private static final String[] IMAGE_BACKGROUND_URLS = {
            "%D1%84%D0%BE%D0%BD1.png", "%D1%84%D0%BE%D0%BD2.png",
            "%D1%84%D0%BE%D0%BD3.png", "%D1%84%D0%BE%D0%BD4.png"
    };
    private static final Identifier[] LOADED_BACKGROUNDS = new Identifier[IMAGE_BACKGROUNDS.length];
    private static final int[] BACKGROUND_WIDTHS = new int[IMAGE_BACKGROUNDS.length];
    private static final int[] BACKGROUND_HEIGHTS = new int[IMAGE_BACKGROUNDS.length];
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
    private final Map<String, PlayerSkinWidget> skinWidgets = new HashMap<>();
    private final Map<String, Supplier<PlayerSkin>> skinSuppliers = new HashMap<>();
    private final java.util.Set<String> skinLookupsPending = new java.util.HashSet<>();
    private float sidebarHover;
    private float parallaxX;
    private float parallaxY;
    private boolean accountsPage;
    private boolean worldSelectPage;
    private boolean accountInputFocused = true;
    private String accountInput = "";
    private int accountCarouselIndex;
    private float accountCarouselPosition;
    private long lastFrameNs;
    private float accountsTransition;
    private float menuEntrance;

    // Keep the custom Inter typeface, but enlarge it for clearer reading.
    private static final float MENU_TEXT_BOOST = 1.25f;

    private static int menuWidth(net.minecraft.client.gui.Font font, String value, float size) {
        return Math.round(Gfx.widthAtSize(font, value, size) * MENU_TEXT_BOOST);
    }

    private static String menuTrim(net.minecraft.client.gui.Font font, String value, int maxWidth, float size) {
        if (menuWidth(font, value, size) <= maxWidth) return value;
        String trimmed = value;
        while (trimmed.length() > 1 && menuWidth(font, trimmed + "…", size) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "…";
    }

    private static void menuText(GuiGraphics graphics, net.minecraft.client.gui.Font font, String value,
                                 float x, float y, float size, int color, boolean shadow) {
        Gfx.textAtSize(graphics, font, value, x, y, size * MENU_TEXT_BOOST, color, shadow);
    }

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
        // iOS-inspired spring easing: quick response, gentle settle, no abrupt page swap.
        float transitionTarget = accountsPage ? 1f : 0f;
        accountsTransition = spring(accountsTransition, transitionTarget, Math.min(1f, dt / 16f) * 0.22f);
        menuEntrance = Math.min(1f, menuEntrance + dt / 520f);
        drawBackdrop(graphics, now);
        LiquidGlassRenderer.scheduleBackgroundCapture();
        graphics.blurBeforeThisStratum();

        Gfx.alpha = 1f;
        if (accountsPage || accountsTransition > 0.005f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, (1f - accountsTransition) * 9f);
            Gfx.alpha = accountsTransition;
            drawAccountsPage(graphics, mouseX, mouseY, dt);
            graphics.pose().popMatrix();
        }
        if (!accountsPage || accountsTransition < 0.995f) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(0f, accountsTransition * -5f);
            Gfx.alpha = 1f - accountsTransition;
            drawBrand(graphics);
            graphics.pose().popMatrix();
        }
        Gfx.alpha = 1f;
        drawUserBadge(graphics);
        drawSidebar(graphics, mouseX, mouseY, dt, now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, (1f - easeOutBack(menuEntrance)) * 18f);
        Gfx.alpha = easeOutCubic(menuEntrance);
        drawDock(graphics, mouseX, mouseY, dt);
        graphics.pose().popMatrix();
        Gfx.alpha = 1f;
    }

    private static float spring(float current, float target, float response) {
        float delta = target - current;
        float next = current + delta * Math.max(0.01f, Math.min(0.45f, response));
        return Math.abs(target - next) < 0.001f ? target : next;
    }

    private static float easeOutCubic(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private static float easeOutBack(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        float c1 = 1.70158f;
        return 1f + (c1 + 1f) * (float) Math.pow(t - 1f, 3)
                + c1 * (float) Math.pow(t - 1f, 2);
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
                HttpRequest request = HttpRequest.newBuilder(URI.create(SOURCE_ASSET_BASE + IMAGE_BACKGROUND_URLS[index]))
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
                    BACKGROUND_WIDTHS[index] = image.getWidth();
                    BACKGROUND_HEIGHTS[index] = image.getHeight();
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
            float imageAspect = (float) BACKGROUND_WIDTHS[backdropIndex]
                    / Math.max(1, BACKGROUND_HEIGHTS[backdropIndex]);
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
                    x, y, 0f, 0f, w, h, BACKGROUND_WIDTHS[backdropIndex],
                    BACKGROUND_HEIGHTS[backdropIndex], BACKGROUND_WIDTHS[backdropIndex],
                    BACKGROUND_HEIGHTS[backdropIndex], 0xFFFFFFFF);
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
        float titleWidth = menuWidth(font, title, titleSize);
        float subtitleWidth = menuWidth(font, subtitle, subtitleSize);
        float titleY = height * 0.35f;
        menuText(g, font, title, (width - titleWidth) / 2f, titleY,
                titleSize, 0xFFF3F5FF, false);
        menuText(g, font, subtitle, (width - subtitleWidth) / 2f,
                titleY + 39f, subtitleSize, 0xBFC1C9DC, false);
    }

    private void drawUserBadge(GuiGraphics g) {
        String username = Minecraft.getInstance().getUser().getName();
        if (username == null || username.isBlank()) username = "Zenith User";
        int textWidth = menuWidth(font, username, 7.2f);
        float w = Math.max(72f, textWidth + 34f);
        float x = width - w - 16f;
        float y = 16f;
        Gfx.glass(g, x, y, w, 28f, 10f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);
        Gfx.circle(g, x + 15f, y + 10f, 3f, 0xFFE1E7F7);
        Gfx.rrect(g, x + 9.5f, y + 14f, 11f, 7f, 4f, 0xFFE1E7F7, 0xFFB8C4E2, 0, 0f);
        menuText(g, font, username, x + 26f, y + 9f, 7.2f, 0xFFE6E9F5, false);
    }

    private void drawSidebar(GuiGraphics g, int mouseX, int mouseY, float dt, long now) {
        float x = 16f;
        float y = (height - 32f) / 2f;
        boolean over = Gfx.hit(mouseX, mouseY, 16, Math.round(y + 3), 26, 26);
        sidebarHover += ((over ? 1f : 0f) - sidebarHover) * Math.min(1f, dt / 90f);
        Gfx.glass(g, x, y, 26f, 32f, 11f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);
        int iconColor = lerp(0xFFC1C6D8, EchoTheme.ACCENT_TEXT, sidebarHover);
        float sparkleSize = 5.5f + sidebarHover;
        float sx = x + 13f;
        float sy = y + 16f;
        Gfx.line(g, sx, sy - sparkleSize, sx, sy + sparkleSize, 1.15f, iconColor);
        Gfx.line(g, sx - sparkleSize, sy, sx + sparkleSize, sy, 1.15f, iconColor);
        Gfx.line(g, sx - sparkleSize * 0.68f, sy - sparkleSize * 0.68f,
                sx + sparkleSize * 0.68f, sy + sparkleSize * 0.68f, 0.8f, iconColor);
        Gfx.line(g, sx - sparkleSize * 0.68f, sy + sparkleSize * 0.68f,
                sx + sparkleSize * 0.68f, sy - sparkleSize * 0.68f, 0.8f, iconColor);

        long elapsed = now / 1_000_000L - backdropToastAt;
        if (backdropToastAt != 0L && elapsed >= 0L && elapsed < 2400L) {
            float alpha = elapsed > 1800L ? 1f - (elapsed - 1800L) / 600f : 1f;
            String label = "Фон: " + BACKDROP_NAMES[backdropIndex];
            float w = menuWidth(font, label, 7f) + 20f;
            float tx = x + 36f;
            float ty = y + 5f;
            Gfx.alpha = alpha;
            Gfx.glass(g, tx, ty, w, 22f, 6f, GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER);
            menuText(g, font, label, tx + 9f, ty + 7f, 7f, 0xFFF0F2FA, false);
            Gfx.alpha = 1f;
        }
    }

    private void drawDockIcon(GuiGraphics g, int index, float cx, float cy, float scale, int color) {
        float s = 1.0f * scale;
        switch (index) {
            case 0 -> { // Singleplayer: small world/orb
                Gfx.ringCircle(g, cx, cy, 5.2f * s, 1.05f * s, color);
                Gfx.line(g, cx - 4.2f * s, cy, cx + 4.2f * s, cy, 0.85f * s, color);
                Gfx.line(g, cx, cy - 4.2f * s, cx, cy + 4.2f * s, 0.85f * s, color);
                Gfx.polyline(g, 0.8f * s, color,
                        cx - 2.4f * s, cy - 4.4f * s, cx - 1.1f * s, cy - 2.0f * s,
                        cx - 1.1f * s, cy + 2.0f * s, cx - 2.4f * s, cy + 4.4f * s);
                Gfx.polyline(g, 0.8f * s, color,
                        cx + 2.4f * s, cy - 4.4f * s, cx + 1.1f * s, cy - 2.0f * s,
                        cx + 1.1f * s, cy + 2.0f * s, cx + 2.4f * s, cy + 4.4f * s);
            }
            case 1 -> { // Multiplayer: two heads and shoulders
                Gfx.circle(g, cx - 2.7f * s, cy - 2.1f * s, 1.8f * s, color);
                Gfx.circle(g, cx + 3.0f * s, cy - 1.2f * s, 1.55f * s, color);
                Gfx.polyline(g, 1.25f * s, color,
                        cx - 7f * s, cy + 5f * s, cx - 6f * s, cy + 1.5f * s,
                        cx - 2.7f * s, cy + 0.4f * s, cx + 1.0f * s, cy + 2.0f * s,
                        cx + 2.0f * s, cy + 5f * s);
                Gfx.polyline(g, 1.1f * s, color,
                        cx + 1.0f * s, cy + 2.3f * s, cx + 4.6f * s, cy + 1.0f * s,
                        cx + 7.0f * s, cy + 4.7f * s);
            }
            case 2 -> { // Accounts: user bust
                Gfx.circle(g, cx, cy - 2.8f * s, 2.5f * s, color);
                Gfx.polyline(g, 1.35f * s, color,
                        cx - 6.2f * s, cy + 5.6f * s, cx - 5.0f * s, cy + 1.8f * s,
                        cx, cy + 0.3f * s, cx + 5.0f * s, cy + 1.8f * s,
                        cx + 6.2f * s, cy + 5.6f * s);
            }
            case 3 -> { // Mods: four compact tiles
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        Gfx.rrect(g, cx - 5.8f * s + col * 6.2f * s,
                                cy - 5.8f * s + row * 6.2f * s, 4.7f * s, 4.7f * s,
                                1.0f * s, color, color, 0, 0f);
                    }
                }
            }
            case 4 -> { // Settings: eight-tooth gear
                Gfx.ringCircle(g, cx, cy, 4.4f * s, 1.25f * s, color);
                Gfx.ringCircle(g, cx, cy, 1.35f * s, 1.05f * s, color);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * i / 4.0;
                    float ix = (float) Math.cos(angle);
                    float iy = (float) Math.sin(angle);
                    Gfx.line(g, cx + ix * 4.8f * s, cy + iy * 4.8f * s,
                            cx + ix * 6.2f * s, cy + iy * 6.2f * s, 1.35f * s, color);
                }
            }
            case 5 -> { // Exit: door and outward arrow
                Gfx.line(g, cx - 4.7f * s, cy - 6f * s, cx - 4.7f * s, cy + 6f * s, 1.1f * s, color);
                Gfx.line(g, cx - 4.7f * s, cy - 6f * s, cx + 1.5f * s, cy - 6f * s, 1.1f * s, color);
                Gfx.line(g, cx + 1.5f * s, cy - 6f * s, cx + 1.5f * s, cy + 6f * s, 1.1f * s, color);
                Gfx.line(g, cx + 1.5f * s, cy + 6f * s, cx - 4.7f * s, cy + 6f * s, 1.1f * s, color);
                Gfx.line(g, cx - 1.3f * s, cy, cx + 6.5f * s, cy, 1.2f * s, color);
                Gfx.line(g, cx + 3.4f * s, cy - 3.2f * s, cx + 6.5f * s, cy, 1.2f * s, color);
                Gfx.line(g, cx + 3.4f * s, cy + 3.2f * s, cx + 6.5f * s, cy, 1.2f * s, color);
            }
            default -> { }
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
            boolean active = (i == 0 && worldSelectPage) || (i == 2 && accountsPage);
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
            int iconColor = active ? EchoTheme.ACCENT_TEXT : lerp(0xC3BFC5D8, 0xFFFFFFFF, h);
            drawDockIcon(g, i, x + btnW / 2f, y + 10f - h, 1f + h * 0.12f, iconColor);
            float lw = menuWidth(font, MAIN_LABELS[i], 6.5f);
            menuText(g, font, MAIN_LABELS[i], x + (btnW - lw) / 2f, y + 18f,
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
            int base = i == 5 ? 0xFFC3BFCB : 0xFFC1C6D8;
            int hover = i == 5 ? 0xFFFF858B : 0xFFFFFFFF;
            drawDockIcon(g, i, x + actionW / 2f, y + 14f - h, 1f + h * 0.12f, lerp(base, hover, h));
            x += actionW + gap;
        }
    }

    boolean handleDockClick(double mx, double my) {
        float dockW = 271f;
        float dockX = (width - dockW) / 2f;
        float dockY = height - 58f;
        float dockItemX = dockX + 7f;
        for (int i = 0; i < 4; i++) {
            if (Gfx.hit(mx, my, Math.round(dockItemX), Math.round(dockY + 4f), 46, 28)) {
                if (i != 0 || !worldSelectPage) activateDock(i);
                return true;
            }
            dockItemX += 48f;
        }
        dockItemX += 11f;
        if (Gfx.hit(mx, my, Math.round(dockItemX), Math.round(dockY + 4f), 26, 28)) {
            activateDock(4);
            return true;
        }
        dockItemX += 28f;
        if (Gfx.hit(mx, my, Math.round(dockItemX), Math.round(dockY + 4f), 26, 28)) {
            activateDock(5);
            return true;
        }
        return false;
    }

    void renderWorldSelectBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        updateParallax(mouseX, mouseY, 16f);
        drawBackdrop(graphics, System.nanoTime());
        LiquidGlassRenderer.captureBackground();
        Gfx.glass(graphics, width * 0.13f, 18f, width * 0.74f,
                Math.max(120f, height - 92f), 14f,
                0x401C202B, 0x3010141D, 0x20FFFFFF);
    }

    void renderWorldSelectDock(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        worldSelectPage = true;
        Gfx.alpha = 1f;
        drawUserBadge(graphics);
        drawSidebar(graphics, mouseX, mouseY, 16f, System.nanoTime());
        drawDock(graphics, mouseX, mouseY, 16f);
        Gfx.alpha = 1f;
    }

    private PlayerSkinWidget skinWidget(PvpAccountManager.Profile profile) {
        return skinWidgets.computeIfAbsent(profile.uuid, key -> {
            Minecraft minecraft = Minecraft.getInstance();
            GameProfile gameProfile = new GameProfile(UUID.fromString(profile.uuid), profile.name);
            Supplier<PlayerSkin> initialSkin = minecraft.getSkinManager().createLookup(gameProfile, false);
            skinSuppliers.put(key, initialSkin);

            // Offline UUIDs are client-local, so resolve the name to its Mojang profile first,
            // then ask Minecraft's own skin manager for the official player skin.
            if ("OFFLINE".equals(profile.type) && profile.name.matches("[A-Za-z0-9_]{1,16}")
                    && skinLookupsPending.add(key)) {
                ASSET_EXECUTOR.execute(() -> resolveOfflineSkin(key, profile.name));
            }
            Supplier<PlayerSkin> dynamicSkin = () ->
                    skinSuppliers.getOrDefault(key, initialSkin).get();
            return new PlayerSkinWidget(58, 70, minecraft.getEntityModels(), dynamicSkin);
        });
    }

    private void resolveOfflineSkin(String key, String playerName) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
                    .timeout(Duration.ofSeconds(10)).header("User-Agent", "PvPVisuals/1.0").GET().build();
            HttpResponse<String> response = ASSET_HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().isBlank()) return;
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("id")) return;
            String rawId = json.get("id").getAsString();
            if (rawId.length() == 32) {
                rawId = rawId.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5");
            }
            UUID resolvedId = UUID.fromString(rawId);
            Minecraft.getInstance().execute(() -> {
                GameProfile resolvedProfile = new GameProfile(resolvedId, playerName);
                skinSuppliers.put(key, Minecraft.getInstance().getSkinManager().createLookup(resolvedProfile, false));
            });
        } catch (Exception ignored) {
            // Keep the built-in default skin if Mojang profile lookup is unavailable.
        } finally {
            skinLookupsPending.remove(key);
        }
    }

    private void drawAccountsPage(GuiGraphics g, int mouseX, int mouseY, float dt) {
        float w = Math.min(402f, width - 36f);
        float h = Math.min(270f, height - 24f);
        float x = (width - w) / 2f;
        float y = (height - h) / 2f - 8f;
        Gfx.glass(g, x, y, w, h, 12f, 0x85202634, 0x7610141D, 0x37FFFFFF);
        menuText(g, font, "Аккаунты", x + 18f, y + 13f, 15f, 0xFFF0F2FA, false);
        menuText(g, font, "Управление игровыми профилями", x + 18f, y + 36f,
                7.2f, 0xFFAEB5C8, false);

        java.util.List<PvpAccountManager.Profile> profiles = PvpAccountManager.profiles();
        if (profiles.isEmpty()) {
            accountCarouselIndex = 0;
            accountCarouselPosition = 0f;
        } else {
            accountCarouselIndex = Math.max(0, Math.min(profiles.size() - 1, accountCarouselIndex));
            float centerX = x + w / 2f;
            float centerY = y + 113f;
            float targetPosition = accountCarouselIndex;
            float slideSpeed = 1f - (float) Math.exp(-Math.max(0f, dt) / 105f);
            accountCarouselPosition += (targetPosition - accountCarouselPosition) * slideSpeed;
            if (Math.abs(targetPosition - accountCarouselPosition) < 0.001f) accountCarouselPosition = targetPosition;
            java.util.List<Integer> order = new java.util.ArrayList<>();
            for (int i = 0; i < profiles.size(); i++) {
                if (Math.abs(i - accountCarouselPosition) <= 2.25f) order.add(i);
            }
            order.sort((left, right) -> Float.compare(
                    Math.abs(right - accountCarouselPosition), Math.abs(left - accountCarouselPosition)));
            for (int index : order) {
                PvpAccountManager.Profile profile = profiles.get(index);
                float distance = index - accountCarouselPosition;
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
                Gfx.circle(g, cardX + cardW / 2f, cardY + 38f, 25f,
                        active ? 0x4C8C9EFF : 0x403F526E);
                PlayerSkinWidget skin = skinWidget(profile);
                skin.setPosition(Math.round(cardX + (cardW - 58f) / 2f), Math.round(cardY + 3f));
                skin.setAlpha(fade);
                skin.render(g, mouseX, mouseY, Math.min(1f, dt / 50f));
                String name = menuTrim(font, profile.name, Math.round(cardW - 12f), 6.5f);
                float nameW = menuWidth(font, name, 6.5f);
                menuText(g, font, name, cardX + (cardW - nameW) / 2f,
                        cardY + 76f, 6.5f, 0xFFF0F2FA, false);
                String type = active ? "ВЫБРАН" : ("MICROSOFT".equals(profile.type) ? "MICROSOFT" : "OFFLINE");
                float typeW = menuWidth(font, type, 4.8f);
                menuText(g, font, type, cardX + (cardW - typeW) / 2f,
                        cardY + 88f, 4.8f, active ? EchoTheme.ACCENT_TEXT : 0xFF9CA5B8, false);
                if (index == accountCarouselIndex) {
                    Gfx.glass(g, cardX + 9f, cardY + cardH - 24f, cardW - 18f, 17f, 5f,
                            0x74475270, 0x50202A3A, 0x408C9EFF);
                    String select = active ? "Текущий" : "Выбрать";
                    float sw = menuWidth(font, select, 5.5f);
                    menuText(g, font, select,
                            cardX + (cardW - sw) / 2f, cardY + cardH - 18f,
                            5.5f, 0xFFE8EEFF, false);
                }
                Gfx.alpha = 1f;
            }
            if (profiles.size() > 1) {
                String counter = (accountCarouselIndex + 1) + " / " + profiles.size();
                float cw = menuWidth(font, counter, 5.4f);
                menuText(g, font, counter, centerX - cw / 2f, y + 183f,
                        5.4f, 0xFF8D97AC, false);
                menuText(g, font, "‹", centerX - 72f, y + 100f, 15f, 0xFFBFC8DB, false);
                menuText(g, font, "›", centerX + 64f, y + 100f, 15f, 0xFFBFC8DB, false);
            }
            boolean active = PvpAccountManager.isActive(profiles.get(accountCarouselIndex));
            float actionY = y + 193f;
            float actionW = 76f;
            float selectX = x + w / 2f - actionW - 4f;
            float deleteX = x + w / 2f + 4f;
            Gfx.glass(g, selectX, actionY, actionW, 22f, 6f,
                    active ? 0x66343C50 : 0x8C344A6D, 0x53151B27, 0x558C9EFF);
            menuText(g, font, active ? "Выбран" : "Выбрать",
                    selectX + (active ? 22f : 21f), actionY + 7f, 5.8f,
                    active ? EchoTheme.ACCENT_TEXT : 0xFFE8EAF5, false);
            Gfx.glass(g, deleteX, actionY, actionW, 22f, 6f, 0x633B2730, 0x40151B27, 0x42D86C78);
            menuText(g, font, "Удалить", deleteX + 21f, actionY + 7f,
                    5.8f, 0xFFFFB6BA, false);
        }

        float inputY = y + h - 38f;
        float inputX = x + 14f;
        float inputW = w - 162f;
        Gfx.glass(g, inputX, inputY, inputW, 26f, 7f,
                accountInputFocused ? 0xAA303B51 : 0x63242B39, 0x53151B27,
                accountInputFocused ? 0x668C9EFF : 0x32FFFFFF);
        String shownInput = accountInput.isEmpty() ? "Введите ник (3–16 символов)" : accountInput;
        menuText(g, font, shownInput, inputX + 8f, inputY + 8f, 6.1f,
                accountInput.isEmpty() ? 0xFF858DA1 : 0xFFF0F2FA, false);
        if (accountInputFocused && System.currentTimeMillis() % 1000L < 500L) {
            float caretX = inputX + 8f + menuWidth(font, accountInput, 6.1f) + 1f;
            g.fill(Math.round(caretX), Math.round(inputY + 7f), Math.round(caretX + 1f),
                    Math.round(inputY + 19f), 0xFFB8C4FF);
        }
        float addX = x + w - 138f;
        boolean addHover = Gfx.hit(mouseX, mouseY, Math.round(addX), Math.round(inputY), 60, 26);
        Gfx.glass(g, addX, inputY, 60f, 26f, 7f,
                addHover ? 0xAA39445A : 0x63242B39, 0x53151B27, 0x32FFFFFF);
        menuText(g, font, "Добавить", addX + 11f, inputY + 8f, 5.8f, 0xFFE8EAF5, false);
        float randomX = x + w - 72f;
        Gfx.glass(g, randomX, inputY, 26f, 26f, 7f, 0x63242B39, 0x53151B27, 0x32FFFFFF);
        menuText(g, font, "↻", randomX + 8f, inputY + 7f, 10f, 0xFFE8EAF5, false);
        float clearX = x + w - 40f;
        Gfx.glass(g, clearX, inputY, 26f, 26f, 7f, 0x533B2730, 0x40151B27, 0x28FFFFFF);
        menuText(g, font, "×", clearX + 8f, inputY + 6f, 11f, 0xFFFFB6BA, false);
    }

    void setWorldSelectPage(boolean active) { worldSelectPage = active; }

    void activateDock(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (index != 0) worldSelectPage = false;
        switch (index) {
            case 0 -> {
                if (!worldSelectPage) {
                    worldSelectPage = true;
                    minecraft.setScreen(new PvpWorldSelectScreen(this));
                }
            }
            case 1 -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case 2 -> { accountsPage = !accountsPage; accountInputFocused = accountsPage; accountInput = ""; accountCarouselIndex = PvpAccountManager.activeIndex(); accountCarouselPosition = accountCarouselIndex; }
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

        if (handleDockClick(mx, my)) return true;

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
                    float distance = i - accountCarouselPosition;
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
