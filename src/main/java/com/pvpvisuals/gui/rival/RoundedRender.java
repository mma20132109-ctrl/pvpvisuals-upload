package com.pvpvisuals.gui.rival;

import com.pvpvisuals.gui.smooth.SmoothRenderer;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Рисование со скруглёнными углами и мелкие иконки без текстур.
 * В 1.21.11 готового API rounded rect у GuiGraphics нет, поэтому углы рисуются построчно
 * (без сглаживания краёв). Используются только fill/fillGradient.
 */
public final class RoundedRender {
	private RoundedRender() { }

	public static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int out = 0;
		for (int shift = 24; shift >= 0; shift -= 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= ((int) (ca + (cb - ca) * t) & 0xFF) << shift;
		}
		return out;
	}

	/** Умножает альфа-канал на k (0..1); ненулевую альфу не опускает ниже 4 (шрифт считает alpha<=3 непрозрачным). */
	public static int scaleAlpha(int argb, float k) {
		int a = argb >>> 24;
		if (a == 0) {
			return argb;
		}
		int na = Math.max(4, Math.min(255, Math.round(a * k)));
		return (na << 24) | (argb & 0xFFFFFF);
	}

	public static int withAlpha(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	/** Скруглённый прямоугольник с вертикальным градиентом top -> bottom (ARGB). Плавный рендер, если включён. */
	public static void fill(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothRoundedRect(g, x, y, w, h, r, top, bottom);
		} else {
			legacyFill(g, x, y, w, h, r, top, bottom);
		}
	}

	/** Старый построчный рендер углов (gui.smoothGui = false). */
	public static void legacyFill(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));

		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
			int j = h - 1 - i;
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, lerp(top, bottom, (i + 0.5f) / h));
			g.fill(x + inset, y + j, x + w - inset, y + j + 1, lerp(top, bottom, (j + 0.5f) / h));
		}
		if (h - 2 * r > 0) {
			g.fillGradient(x, y + r, x + w, y + h - r,
					lerp(top, bottom, (float) r / h), lerp(top, bottom, (float) (h - r) / h));
		}
	}

	public static void fill(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		fill(g, x, y, w, h, r, color, color);
	}

	public static void circle(GuiGraphics g, int cx, int cy, int rad, int color) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothCircle(g, cx + 0.5f, cy + 0.5f, rad + 0.5f, color);
		} else {
			legacyCircle(g, cx, cy, rad, color);
		}
	}

	public static void legacyCircle(GuiGraphics g, int cx, int cy, int rad, int color) {
		for (int dy = -rad; dy <= rad; dy++) {
			int half = (int) Math.round(Math.sqrt(Math.max(0, rad * rad - dy * dy)));
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	private static final int[][] CHECK = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {7, 0}};

	/** Галочка 8x6 (верхний левый угол x,y). */
	public static void check(GuiGraphics g, int x, int y, int color) {
		for (int[] p : CHECK) {
			g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, color);
		}
	}

	/** Треугольная стрелка шириной 7 (вниз или вверх). */
	public static void arrow(GuiGraphics g, int x, int y, int color, boolean down) {
		for (int i = 0; i < 4; i++) {
			int row = down ? i : 3 - i;
			g.fill(x + i, y + row, x + 7 - i, y + row + 1, color);
		}
	}

	/** Иконка категории 9x9 в точке x,y. */
	public static void icon(GuiGraphics g, Category cat, int x, int y, int color) {
		switch (cat) {
			case VISUAL -> {
				for (int i = 0; i < 8; i++) {
					g.fill(x + i, y + i, x + i + 2, y + i + 1, color);
					g.fill(x + 7 - i, y + i, x + 9 - i, y + i + 1, color);
				}
			}
			case WORLD -> circle(g, x + 4, y + 4, 4, color);
			case PARTICLES -> {
				g.fill(x + 4, y, x + 5, y + 9, color);
				g.fill(x, y + 4, x + 9, y + 5, color);
				g.fill(x + 3, y + 3, x + 6, y + 6, color);
			}
			case UTILS -> fill(g, x, y, 9, 9, 2, color);
			case CLIENT -> {
				for (int i = 0; i < 3; i++) {
					g.fill(x + i * 4, y + 4, x + i * 4 + 2, y + 6, color);
				}
			}
		}
	}
}
