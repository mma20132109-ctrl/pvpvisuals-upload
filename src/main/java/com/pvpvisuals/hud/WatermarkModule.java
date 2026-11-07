package com.pvpvisuals.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.TopBar;
import com.pvpvisuals.render.LogoRenderer;
import com.pvpvisuals.render.LogoSizeMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Сегментированная Watermark-плашка в стиле Svintus:
 * компактный брендовый блок и независимо включаемые ник, FPS, ping и время.
 */
public final class WatermarkModule extends AbstractHudModule {
    private static final float H = 20f;
    private static final float TITLE = 7.4f;
    private static final float VALUE = 6.4f;
    private static final float UNIT = 5.6f;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final int PLAYER = 0;
    private static final int FPS = 1;
    private static final int PING = 2;
    private static final int CLOCK = 3;

    private record Segment(int icon, String value, String unit) { }
    private final List<Segment> segments = new ArrayList<>(4);

    @Override public String id() { return "watermark"; }
    @Override public String label() { return "Watermark"; }
    @Override public int defaultX() { return 6; }
    @Override public int defaultY() { return 6; }
    @Override public boolean enabled() { return PvpConfig.get().watermark; }

    private static boolean logo() {
        return PvpConfig.get().logo.showInWatermark && LogoRenderer.hasLogo();
    }

    private static LogoSizeMode mode() {
        return LogoSizeMode.byName(PvpConfig.get().logo.sizeWatermark);
    }

    private void rebuildSegments() {
        PvpConfig c = PvpConfig.get();
        Minecraft mc = Minecraft.getInstance();
        segments.clear();

        if (c.watermarkShowNick) {
            String nick = mc.player != null
                    ? mc.player.getName().getString()
                    : mc.getUser().getName();
            segments.add(new Segment(PLAYER, nick, ""));
        }
        if (c.watermarkShowFps) {
            segments.add(new Segment(FPS, Integer.toString(mc.getFps()), " fps"));
        }
        if (c.watermarkShowPing) {
            segments.add(new Segment(PING, Integer.toString(latency(mc)), " ms"));
        }
        if (c.watermarkShowTime) {
            segments.add(new Segment(CLOCK, LocalTime.now().format(TIME), ""));
        }
    }

    private static int latency(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) {
            return 0;
        }
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private static int segmentWidth(Font font, Segment segment) {
        return 8 + 7 + 4
                + Gfx.widthAtSize(font, segment.value(), VALUE)
                + Gfx.widthAtSize(font, segment.unit(), UNIT);
    }

    @Override
    protected int contentWidth() {
        rebuildSegments();
        Font font = Minecraft.getInstance().font;
        int width = 6 + 13 + 5 + Gfx.widthAtSize(font, TopBar.NAME, TITLE) + 7;
        for (Segment segment : segments) {
            width += segmentWidth(font, segment);
        }
        return Math.max(42, width);
    }

    @Override
    protected int contentHeight() {
        return Math.round(H);
    }

    @Override
    protected void drawContent(GuiGraphics g, boolean preview) {
        rebuildSegments();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = contentWidth();

        HudStyle.panel(g, 0, 0, width, Math.round(H), 7, HudAlpha.current);
        HudStyle.accentWash(g, 0, 0, Math.min(44, width), H, 7, HudAlpha.current);

        if (logo()) {
            LogoRenderer.renderLogoAuto(g, mode(), 6, 3, 14, 1f, HudAlpha.current);
        } else {
            HudIcons.player(g, 12.5f, 10f, 6f, HudAlpha.mul(HudStyle.ACCENT));
        }

        float x = 24f;
        Gfx.textAtSize(g, font, TopBar.NAME, x, 6.1f, TITLE,
                HudAlpha.mul(EchoTheme.ACCENT_TEXT), false);
        x += Gfx.widthAtSize(font, TopBar.NAME, TITLE) + 3f;

        for (Segment segment : segments) {
            x += 4f;
            HudStyle.separator(g, x, 6f, 8f, HudAlpha.current);
            x += 4.8f;

            float cy = H / 2f;
            int iconColor = HudAlpha.mul(HudStyle.ACCENT);
            switch (segment.icon()) {
                case PLAYER -> HudIcons.player(g, x + 3.5f, cy, 3.6f, iconColor);
                case FPS -> HudIcons.activity(g, x + 3.5f, cy, 3.2f, iconColor);
                case PING -> HudIcons.signal(g, x + 3.5f, cy, 3.2f, iconColor);
                case CLOCK -> HudIcons.clock(g, x + 3.5f, cy, 3.2f, iconColor);
                default -> { }
            }
            x += 7f + 3.5f;

            Gfx.textAtSize(g, font, segment.value(), x, 6.7f, VALUE,
                    HudAlpha.mul(HudStyle.TEXT), false);
            x += Gfx.widthAtSize(font, segment.value(), VALUE);

            if (!segment.unit().isEmpty()) {
                Gfx.textAtSize(g, font, segment.unit(), x, 7.1f, UNIT,
                        HudAlpha.mul(HudStyle.MUTED), false);
                x += Gfx.widthAtSize(font, segment.unit(), UNIT);
            }
        }
    }
}
