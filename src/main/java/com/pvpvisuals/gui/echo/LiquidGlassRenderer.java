package com.pvpvisuals.gui.echo;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared Liquid Glass materials for the Click GUI and in-game HUD.
 * Minecraft's screen background pass supplies the blur; these primitives add a cool tinted glass body,
 * a restrained top reflection, a fine inner rim and a cyan edge light without changing GUI interaction.
 */
public final class LiquidGlassRenderer {
	private LiquidGlassRenderer() { }

	public static void window(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
		Gfx.rrect(graphics, x, y, width, height, radius, EchoTheme.WIN_TOP, EchoTheme.WIN_BOTTOM,
				0x88D7F5FF, 1.0f);
		float sheenHeight = Math.min(38f, Math.max(8f, height * 0.18f));
		Gfx.rrect(graphics, x + 1f, y + 1f, width - 2f, sheenHeight, Math.max(1f, radius - 1f),
				0x2AFFFFFF, 0x00FFFFFF, 0, 0f);
		Gfx.line(graphics, x + radius, y + 1f, x + width - radius, y + 1f, 1f, 0x62FFFFFF);
		Gfx.line(graphics, x + radius, y + height - 1f, x + width - radius, y + height - 1f, 1f, 0x2676DFFF);
		Gfx.ring(graphics, x + 1f, y + 1f, width - 2f, height - 2f, Math.max(1f, radius - 1f), 0.7f,
				0x32FFFFFF);
		Gfx.ring(graphics, x, y, width, height, radius, 0.8f, 0x6CDDF7FF);
	}

	/** Glass card/control with a subtle reflection and a crisp inner edge. */
	public static void surface(GuiGraphics graphics, float x, float y, float width, float height, float radius,
			int tint, int border) {
		if (width <= 0f || height <= 0f) {
			return;
		}
		if (border != 0) {
			Gfx.glow(graphics, x, y, width, height, radius, withAlpha(border, 0.16f), 2f);
		}
		Gfx.rrect(graphics, x, y, width, height, radius, tint, withAlpha(tint, 0.82f), border, 0.85f);
		if (height > 5f) {
			float sheenHeight = Math.min(10f, Math.max(3f, height * 0.32f));
			Gfx.rrect(graphics, x + 1f, y + 1f, width - 2f, sheenHeight, Math.max(1f, radius - 1f),
					0x22FFFFFF, 0x00FFFFFF, 0, 0f);
			Gfx.line(graphics, x + radius, y + 1f, x + width - radius, y + 1f, 0.8f, 0x48FFFFFF);
		}
		Gfx.ring(graphics, x + 1f, y + 1f, width - 2f, height - 2f, Math.max(1f, radius - 1f), 0.55f,
				0x24E4FAFF);
	}

	/** Shared panel material used by all movable HUD modules. Gfx.alpha is supplied by HudStyle. */
	public static void hudPanel(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
		Gfx.glow(graphics, x, y, width, height, radius, 0x3858D8F3, 2.5f);
		Gfx.rrect(graphics, x, y, width, height, radius, EchoTheme.HUD_TOP, EchoTheme.HUD_BOTTOM,
				0x72D5F5FF, 0.9f);
		float sheenHeight = Math.min(12f, Math.max(4f, height * 0.24f));
		Gfx.rrect(graphics, x + 1f, y + 1f, width - 2f, sheenHeight, Math.max(1f, radius - 1f),
				0x20FFFFFF, 0x00FFFFFF, 0, 0f);
		Gfx.line(graphics, x + radius, y + 1f, x + width - radius, y + 1f, 0.8f, 0x56FFFFFF);
		Gfx.ring(graphics, x, y, width, height, radius, 0.8f, 0x58DDF5FF);
	}

	private static int withAlpha(int color, float factor) {
		int alpha = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, factor)));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
