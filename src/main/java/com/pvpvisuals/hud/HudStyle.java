package com.pvpvisuals.hud;

import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.echo.LiquidGlassRenderer;

import net.minecraft.client.gui.GuiGraphics;

/** Shared Liquid Glass material for every movable HUD panel. */
public final class HudStyle {
	public static final int RADIUS = 9;
	public static final int TEXT = EchoTheme.TEXT;
	public static final int MUTED = EchoTheme.MUTED;
	public static final int ACCENT = EchoTheme.ACCENT_TEXT;

	private HudStyle() { }

	public static int opacity(int color, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * alpha)));
		return (color & 0x00FFFFFF) | (a << 24);
	}

	public static void accentWash(GuiGraphics g, int x, int y, int w, int h, int r, float alpha) {
		Gfx.rrect(g, x, y, w, h, r, opacity(EchoTheme.ACCENT_A, alpha * 0.13f),
				opacity(EchoTheme.ACCENT_B, alpha * 0.025f), 0x00000000, 0f);
	}

	public static void separator(GuiGraphics g, float x, float y, float h, float alpha) {
		Gfx.line(g, x, y, x, y + h, 1f, opacity(0x70FFFFFF, alpha));
	}

	/** Applies the current HUD opacity to a translucent glass panel with a cool inner rim and top reflection. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int r, float alpha) {
		EchoTheme.sync();
		float prev = Gfx.alpha;
		Gfx.alpha = alpha;
		LiquidGlassRenderer.hudPanel(g, x, y, w, h, r);
		Gfx.alpha = prev;
	}

	/** Panel with current module opacity. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h, RADIUS, HudAlpha.current);
	}
}
