package com.pvpvisuals.gui.echo;

import com.pvpvisuals.gui.rival.RoundedRender;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Тумблер на SmoothRenderer: дорожка — скруглённый прямоугольник с градиентом и тонкой обводкой, кружок — гладкая
 * окружность с мягкой тенью. Положение кружка — дробное (без округления до пикселя), поэтому анимация идёт плавно.
 */
public final class ToggleSwitch {
	public static final int W = 24;
	public static final int H = 12;

	private float pos;
	private boolean init;

	/** Двигает кружок к состоянию on (dtMs — время кадра в мс). */
	public void update(boolean on, float dtMs) {
		float target = on ? 1f : 0f;
		if (!init) {
			pos = target;
			init = true;
		}
		pos += (target - pos) * Math.min(1f, dtMs / 70f);
		if (Math.abs(target - pos) < 0.005f) {
			pos = target;
		}
	}

	public void render(GuiGraphics g, int x, int y, int w, int h) {
		float r = h / 2f;
		if (pos > 0.02f) {
			Gfx.glow(g, x, y, w, h, r, RoundedRender.scaleAlpha(EchoTheme.NEON, pos), 3f);
		}
		int top = RoundedRender.lerp(EchoTheme.TOGGLE_OFF, EchoTheme.ACCENT_A, pos);
		int bottom = RoundedRender.lerp(EchoTheme.TOGGLE_OFF_BOTTOM, EchoTheme.ACCENT_B, pos);
		int border = RoundedRender.lerp(0x40FFFFFF, 0x60FFFFFF, pos);
		Gfx.rrect(g, x, y, w, h, r, top, bottom, border, 1f);

		float kr = r - 2f;
		float kx = x + r + pos * (w - h);
		float ky = y + r;
		Gfx.circle(g, kx, ky + 0.6f, kr + 0.8f, 0x50000000);
		Gfx.circle(g, kx, ky, kr, EchoTheme.KNOB);
	}

	/** Область клика чуть больше самого тумблера. */
	public static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return Gfx.hit(mx, my, x - 3, y - 3, w + 6, h + 6);
	}
}
