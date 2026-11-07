package com.pvpvisuals.gui.echo;

import com.pvpvisuals.gui.smooth.SmoothRenderer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Liquid Glass primitives for Echo. The screen background is blurred by Minecraft's GUI background pass;
 * these methods add translucent material, a soft accent edge and a restrained top reflection.
 */
public final class LiquidGlassRenderer {
	private LiquidGlassRenderer() { }

	public static void window(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothRoundedRect(graphics, x, y, width, height, radius,
					EchoTheme.WIN_TOP, EchoTheme.WIN_BOTTOM, 0x44FFFFFF, 1.0f);
		} else {
			Gfx.rrect(graphics, x, y, width, height, radius, EchoTheme.WIN_TOP, EchoTheme.WIN_BOTTOM,
					0x44FFFFFF, 1.0f);
		}
		Gfx.rrect(graphics, x + 1f, y + 1f, width - 2f, Math.min(54f, height * 0.22f), Math.max(1f, radius - 1f),
				0x18FFFFFF, 0x00FFFFFF, 0, 0f);
		Gfx.ring(graphics, x, y, width, height, radius, 0.8f, 0x35FFFFFF);
	}

	public static void surface(GuiGraphics graphics, float x, float y, float width, float height, float radius,
			int tint, int border) {
		Gfx.rrect(graphics, x, y, width, height, radius, tint, withAlpha(tint, 0.72f), border, 0.8f);
	}

	private static int withAlpha(int color, float factor) {
		int alpha = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, factor)));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
