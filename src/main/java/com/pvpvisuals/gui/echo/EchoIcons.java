package com.pvpvisuals.gui.echo;

import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.smooth.SmoothRenderer;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Векторные иконки (линии, круги, скруглённые прямоугольники) на {@link SmoothRenderer}: без текстур и без пикселей,
 * поэтому остаются ровными при любом масштабе GUI. Все рассчитаны на квадрат 12×12 с левым верхним углом (x, y).
 */
public final class EchoIcons {
	private EchoIcons() { }

	public enum Glyph {
		EYE, CROSSHAIR, SPARKLE, CUBE, SUN, TRIANGLE, RECT, DOTS, GRID, LIST, BOLT, STAR, HASH, ARC, PILL, GEAR, GLOBE,
		WRENCH
	}

	private static final float T = 1.25f;

	public static Glyph forCategory(Category cat) {
		return switch (cat) {
			case VISUAL -> Glyph.EYE;
			case WORLD -> Glyph.GLOBE;
			case PARTICLES -> Glyph.SPARKLE;
			case UTILS -> Glyph.WRENCH;
			case CLIENT -> Glyph.GEAR;
		};
	}

	public static void category(GuiGraphics g, Category cat, int x, int y, int c) {
		glyph(g, forCategory(cat), x, y, c);
	}

	/** Точки эллипса (замкнутая ломаная, если full) или дуги от a0 до a1 (радианы). */
	private static float[] arc(float cx, float cy, float rx, float ry, double a0, double a1, int steps) {
		float[] p = new float[(steps + 1) * 2];
		for (int i = 0; i <= steps; i++) {
			double a = a0 + (a1 - a0) * i / steps;
			p[2 * i] = cx + rx * (float) Math.cos(a);
			p[2 * i + 1] = cy + ry * (float) Math.sin(a);
		}
		return p;
	}

	private static void spokes(GuiGraphics g, float cx, float cy, float r0, float r1, int count, double phase, float t,
			int c) {
		for (int i = 0; i < count; i++) {
			double a = phase + Math.PI * 2 * i / count;
			float ca = (float) Math.cos(a);
			float sa = (float) Math.sin(a);
			Gfx.line(g, cx + ca * r0, cy + sa * r0, cx + ca * r1, cy + sa * r1, t, c);
		}
	}

	public static void glyph(GuiGraphics g, Glyph glyph, int ix, int iy, int c) {
		float x = ix;
		float y = iy;
		float cx = x + 6f;
		float cy = y + 6f;
		switch (glyph) {
			case EYE -> {
				Gfx.polyline(g, T, c, arc(cx, cy, 5.6f, 3.6f, Math.PI, Math.PI * 2, 10));
				Gfx.polyline(g, T, c, arc(cx, cy, 5.6f, 3.6f, 0, Math.PI, 10));
				Gfx.circle(g, cx, cy, 1.8f, c);
			}
			case CROSSHAIR -> {
				Gfx.ringCircle(g, cx, cy, 3.2f, T, c);
				Gfx.line(g, cx, y + 0.5f, cx, y + 3f, T, c);
				Gfx.line(g, cx, y + 9f, cx, y + 11.5f, T, c);
				Gfx.line(g, x + 0.5f, cy, x + 3f, cy, T, c);
				Gfx.line(g, x + 9f, cy, x + 11.5f, cy, T, c);
				Gfx.circle(g, cx, cy, 0.8f, c);
			}
			case SPARKLE -> {
				SmoothRenderer.drawSmoothPolygon(g, new float[] {cx, y, cx + 1.6f, cy - 1.6f, x + 11.5f, cy,
						cx + 1.6f, cy + 1.6f, cx, y + 12f, cx - 1.6f, cy + 1.6f, x + 0.5f, cy, cx - 1.6f, cy - 1.6f},
						Gfx.a(c));
			}
			case CUBE -> {
				Gfx.ring(g, x + 1f, y + 4f, 7f, 7f, 1.5f, T, c);
				Gfx.ring(g, x + 4f, y + 1f, 7f, 7f, 1.5f, T, c);
			}
			case SUN -> {
				Gfx.circle(g, cx, cy, 2.3f, c);
				spokes(g, cx, cy, 3.9f, 5.6f, 8, 0, T, c);
			}
			case TRIANGLE -> {
				Gfx.polyline(g, T, c, cx, y + 1.2f, x + 11f, y + 10.6f, x + 1f, y + 10.6f, cx, y + 1.2f, x + 11f, y + 10.6f);
			}
			case RECT -> {
				Gfx.ring(g, x + 0.5f, y + 2f, 11f, 8f, 2f, T, c);
				Gfx.line(g, x + 3f, y + 5f, x + 8f, y + 5f, T, c);
				Gfx.line(g, x + 3f, y + 7.2f, x + 6f, y + 7.2f, T, c);
			}
			case DOTS -> {
				Gfx.circle(g, x + 2f, y + 3f, 1.2f, c);
				Gfx.circle(g, x + 8f, y + 2f, 1.2f, c);
				Gfx.circle(g, x + 5f, y + 6f, 1.2f, c);
				Gfx.circle(g, x + 10f, y + 8f, 1.2f, c);
				Gfx.circle(g, x + 3f, y + 10f, 1.2f, c);
			}
			case GRID -> {
				for (int i = 0; i < 2; i++) {
					for (int j = 0; j < 2; j++) {
						Gfx.rrect(g, x + 1f + i * 5.5f, y + 1f + j * 5.5f, 4.5f, 4.5f, 1.2f, c, c, 0, 0f);
					}
				}
			}
			case LIST -> {
				for (int i = 0; i < 3; i++) {
					Gfx.circle(g, x + 2f, y + 2.5f + i * 3.5f, 1f, c);
					Gfx.line(g, x + 5f, y + 2.5f + i * 3.5f, x + 11f, y + 2.5f + i * 3.5f, T, c);
				}
			}
			case BOLT -> {
				Gfx.polyline(g, 1.6f, c, x + 7.5f, y + 0.8f, x + 3.2f, y + 6.6f, x + 8.4f, y + 5.4f, x + 4.4f, y + 11.2f);
			}
			case STAR -> {
				Gfx.line(g, cx, y + 0.5f, cx, y + 11.5f, T, c);
				Gfx.line(g, x + 0.5f, cy, x + 11.5f, cy, T, c);
				Gfx.line(g, x + 2.5f, y + 2.5f, x + 9.5f, y + 9.5f, T, c);
				Gfx.line(g, x + 9.5f, y + 2.5f, x + 2.5f, y + 9.5f, T, c);
			}
			case HASH -> {
				Gfx.line(g, x + 3.8f, y + 1f, x + 3.8f, y + 11f, T, c);
				Gfx.line(g, x + 8.2f, y + 1f, x + 8.2f, y + 11f, T, c);
				Gfx.line(g, x + 1f, y + 4.2f, x + 11f, y + 4.2f, T, c);
				Gfx.line(g, x + 1f, y + 7.8f, x + 11f, y + 7.8f, T, c);
			}
			case ARC -> {
				float[] p = new float[18];
				for (int i = 0; i < 9; i++) {
					p[2 * i] = x + 0.8f + i * 1.3f;
					p[2 * i + 1] = cy - 3f * (float) Math.sin(Math.PI * i / 4.0);
				}
				Gfx.polyline(g, T, c, p);
			}
			case PILL -> {
				Gfx.rrect(g, x, y + 3f, 12f, 6f, 3f, c, c, 0, 0f);
				Gfx.circle(g, x + 4f, y + 6f, 1.3f, EchoTheme.TILE);
			}
			case GEAR -> {
				Gfx.ringCircle(g, cx, cy, 2.8f, T, c);
				spokes(g, cx, cy, 4.2f, 5.6f, 8, 0, 1.8f, c);
			}
			case GLOBE -> {
				Gfx.ringCircle(g, cx, cy, 5.2f, T, c);
				Gfx.polyline(g, 1f, c, arc(cx, cy, 2.4f, 5.2f, 0, Math.PI * 2, 16));
				Gfx.line(g, x + 0.8f, cy, x + 11.2f, cy, 1f, c);
			}
			case WRENCH -> {
				Gfx.line(g, x + 1.5f, y + 10.5f, x + 7f, y + 5f, 2f, c);
				Gfx.ringCircle(g, x + 8.6f, y + 3.4f, 2.1f, T, c);
			}
		}
	}

	// ---------- элементы окна ----------

	public static void magnifier(GuiGraphics g, int ix, int iy, int c) {
		Gfx.ringCircle(g, ix + 4.2f, iy + 4.2f, 3.1f, T, c);
		Gfx.line(g, ix + 6.6f, iy + 6.6f, ix + 9.4f, iy + 9.4f, 1.5f, c);
	}

	public static void close(GuiGraphics g, int ix, int iy, int c) {
		Gfx.line(g, ix + 0.6f, iy + 0.6f, ix + 7.4f, iy + 7.4f, 1.4f, c);
		Gfx.line(g, ix + 7.4f, iy + 0.6f, ix + 0.6f, iy + 7.4f, 1.4f, c);
	}

	public static void minus(GuiGraphics g, int ix, int iy, int c) {
		Gfx.line(g, ix + 0.5f, iy + 4f, ix + 8.5f, iy + 4f, 1.5f, c);
	}

	/** Шеврон: dir 0 — влево, 1 — вправо, 2 — вниз. Размер примерно 5×8. */
	public static void chevron(GuiGraphics g, int ix, int iy, int dir, int c) {
		float x = ix;
		float y = iy;
		switch (dir) {
			case 0 -> Gfx.polyline(g, 1.5f, c, x + 4.2f, y + 0.6f, x + 0.8f, y + 4f, x + 4.2f, y + 7.4f);
			case 1 -> Gfx.polyline(g, 1.5f, c, x + 0.8f, y + 0.6f, x + 4.2f, y + 4f, x + 0.8f, y + 7.4f);
			default -> Gfx.polyline(g, 1.5f, c, x + 0.6f, y + 0.8f, x + 4f, y + 4.2f, x + 7.4f, y + 0.8f);
		}
	}
}
