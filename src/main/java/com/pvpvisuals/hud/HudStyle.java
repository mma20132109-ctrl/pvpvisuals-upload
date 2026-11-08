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

	/** Размытая стеклянная панель с акцентной кромкой и альфой HUD-модуля. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int r, float alpha) {
		EchoTheme.sync();
		float a = Math.max(0f, Math.min(1f, alpha));
		com.pvpvisuals.gui.echo.LiquidGlassRenderer.surface(g, x, y, w, h, r,
				withAlpha(EchoTheme.HUD_TOP, a), withAlpha(EchoTheme.NEON_LINE, 0.34f * a));
		// Мягкий верхний блик сохраняет характерный Liquid Glass вид даже на тёмном фоне.
		com.pvpvisuals.gui.echo.LiquidGlassRenderer.roundRect(g, x + 1f, y + 1f,
				Math.max(0f, w - 2f), Math.min(12f, Math.max(0f, h - 2f)), Math.max(1f, r - 1f),
				withAlpha(0x18FFFFFF, a), 0x00000000, 0, 0f);
	}

	/** Умножает исходную ARGB-альфу без изменения RGB. */
	public static int opacity(int color, float factor) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static int withAlpha(int color, float factor) {
		return opacity(color, factor);
	}

	/** Тонкий разделитель между информационными сегментами HUD. */
	public static void separator(GuiGraphics g, float x, float y, float height, float alpha) {
		com.pvpvisuals.gui.echo.LiquidGlassRenderer.roundRect(g, x, y, 0.8f, height, 0.4f,
				withAlpha(0x35FFFFFF, alpha), 0x00000000, 0, 0f);
	}

	/** Полупрозрачный акцентный wash слева от стеклянной панели. */
	public static void accentWash(GuiGraphics g, float x, float y, float w, float h, float radius, float alpha) {
		com.pvpvisuals.gui.echo.LiquidGlassRenderer.roundRect(g, x, y, w, h, radius,
				withAlpha(EchoTheme.ACCENT_A, 0.15f * alpha),
				withAlpha(EchoTheme.ACCENT_B, 0.025f * alpha), 0, 0f);
	}

	/** Панель с текущей альфой HUD-модуля (из AbstractHudModule). */
	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h, RADIUS, HudAlpha.current);
	}
}
