package com.pvpvisuals.hud;

import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Единый стиль всех HUD-элементов (как у нового GUI): тёмно-фиолетовая скруглённая панель, тонкая неоновая обводка и
 * лёгкое свечение. Текст — {@link EchoTheme#TEXT}, второстепенный — {@link EchoTheme#MUTED}, акцент — {@link EchoTheme#ACCENT_TEXT}.
 */
public final class HudStyle {
	public static final int RADIUS = 8;
	public static final int TEXT = EchoTheme.TEXT;
	public static final int MUTED = EchoTheme.MUTED;
	public static final int ACCENT = EchoTheme.ACCENT_TEXT;

	private HudStyle() { }

	/** Панель с прозрачностью alpha (0..1), умноженной на альфу цветов палитры. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int r, float alpha) {
		EchoTheme.sync();
		float prev = Gfx.alpha;
		Gfx.alpha = alpha;
		Gfx.glow(g, x, y, w, h, r, EchoTheme.NEON, 2);
		Gfx.fill(g, x, y, w, h, r, EchoTheme.HUD_TOP, EchoTheme.HUD_BOTTOM);
		Gfx.ring(g, x, y, w, h, r, 1, EchoTheme.NEON_LINE);
		Gfx.alpha = prev;
	}

	/** Панель с текущей альфой HUD-модуля (из AbstractHudModule). */
	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h, RADIUS, HudAlpha.current);
	}
}
