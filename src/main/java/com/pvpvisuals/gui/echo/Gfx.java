package com.pvpvisuals.gui.echo;

import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.gui.smooth.SmoothRenderer;
import com.pvpvisuals.gui.smooth.Typo;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Рисование нового GUI: все цвета умножаются на общую альфу {@link #alpha} (для fade-in окна и вкладок).
 * Формы строит {@link SmoothRenderer} (геометрия с мягкими краями), текст — шрифтом Inter через {@link Typo}.
 * Если в конфиге {@code gui.smoothGui = false}, работает старый построчный рендер (legacy*).
 */
public final class Gfx {
	/** Общий множитель прозрачности (выставляется экраном на время отрисовки слоя). */
	public static float alpha = 1f;

	private Gfx() { }

	public static int a(int argb) {
		return alpha >= 0.999f ? argb : RoundedRender.scaleAlpha(argb, alpha);
	}

	/** Цвет с общей альфой, но без нижнего порога 4 (для градиентов/свечения, которые уходят в ноль). */
	private static int fade(int argb) {
		return alpha >= 0.999f ? argb : SmoothRenderer.mulAlpha(argb, alpha);
	}

	/** Обычный пиксельный прямоугольник: ровные края не требуют сглаживания. */
	public static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
		if (x1 > x0 && y1 > y0) {
			g.fill(x0, y0, x1, y1, a(color));
		}
	}

	// ---------- формы ----------

	public static void fill(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
		fill(g, x, y, w, h, r, color, color);
	}

	public static void fill(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
		rrect(g, x, y, w, h, r, top, bottom, 0, 0f);
	}

	/** Скруглённый прямоугольник (float-координаты), градиент top -> bottom, обводка внутрь толщиной border. */
	public static void rrect(GuiGraphics g, float x, float y, float w, float h, float r, int top, int bottom,
			int borderColor, float border) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothRoundedRect(g, x, y, w, h, r, fade(top), fade(bottom), fade(borderColor), border);
		} else {
			legacyFill(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Math.round(r), a(top), a(bottom));
			if (border > 0f) {
				legacyRing(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Math.round(r),
						Math.max(1, Math.round(border)), a(borderColor));
			}
		}
	}

	public static void circle(GuiGraphics g, int cx, int cy, int rad, int color) {
		circle(g, cx + 0.5f, cy + 0.5f, rad + 0.5f, color);
	}

	/** Круг с центром в (cx, cy) и радиусом rad (float). */
	public static void circle(GuiGraphics g, float cx, float cy, float rad, int color) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothCircle(g, cx, cy, rad, fade(color));
		} else {
			RoundedRender.legacyCircle(g, Math.round(cx - 0.5f), Math.round(cy - 0.5f), Math.round(rad - 0.5f), a(color));
		}
	}

	/** Скруглённая рамка толщиной t внутрь от границы. */
	public static void ring(GuiGraphics g, int x, int y, int w, int h, int r, int t, int color) {
		ring(g, (float) x, y, w, h, r, t, color);
	}

	public static void ring(GuiGraphics g, float x, float y, float w, float h, float r, float t, int color) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothBorder(g, x, y, w, h, r, fade(color), t);
		} else {
			legacyRing(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), Math.round(r),
					Math.max(1, Math.round(t)), a(color));
		}
	}

	/** Остров с уступом (см. SmoothRenderer.drawSmoothIsland); без сглаживания — обычный прямоугольник по ширине верха. */
	public static void island(GuiGraphics g, float cx, float y, float topW, float topH, float botW, float botH,
			float r, float rc, float rf, int top, int bottom, int border, float bw, int glow, float glowSpread) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothIsland(g, cx, y, topW, topH, botW, botH, r, rc, rf, fade(top), fade(bottom),
					fade(border), bw, fade(SmoothRenderer.mulAlpha(glow, 0.6f)), glowSpread);
		} else {
			legacyFill(g, Math.round(cx - topW / 2f), Math.round(y), Math.round(topW), Math.round(topH), Math.round(r),
					a(top), a(top));
			legacyFill(g, Math.round(cx - botW / 2f), Math.round(y + topH), Math.round(botW), Math.round(botH),
					Math.round(r), a(bottom), a(bottom));
		}
	}

	/** Неоновое свечение вокруг скруглённого прямоугольника: spread = layers пикселей GUI, альфа падает как (1-k)^2. */
	public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int color, int layers) {
		glow(g, (float) x, y, w, h, r, color, layers);
	}

	public static void glow(GuiGraphics g, float x, float y, float w, float h, float r, int color, float layers) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothGlow(g, x, y, w, h, r, fade(SmoothRenderer.mulAlpha(color, 0.6f)), layers + 1f);
		} else {
			for (int i = 1; i <= layers; i++) {
				float f = 1f - (i - 1f) / layers;
				legacyRing(g, Math.round(x) - i, Math.round(y) - i, Math.round(w) + 2 * i, Math.round(h) + 2 * i,
						Math.round(r) + i, 1, a(RoundedRender.scaleAlpha(color, f * f * 0.55f)));
			}
		}
	}

	/** Кольцо-окружность: центр — пиксель (cx, cy), радиус r по средней линии, толщина t. */
	public static void ringCircle(GuiGraphics g, int cx, int cy, int r, int t, int color) {
		ringCircle(g, cx + 0.5f, cy + 0.5f, r + 0.5f, t, color);
	}

	public static void ringCircle(GuiGraphics g, float cx, float cy, float r, float t, int color) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothRing(g, cx, cy, r, t, fade(color));
		} else {
			legacyRingCircle(g, Math.round(cx - 0.5f), Math.round(cy - 0.5f), Math.round(r - 0.5f), Math.round(t), a(color));
		}
	}

	/** Линия толщиной t (плоские концы). */
	public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float t, int color) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothLine(g, x0, y0, x1, y1, t, fade(color));
		} else {
			// legacy: пиксельная «лесенка» по Брезенхему упрощённо
			int steps = (int) Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
			for (int i = 0; i <= steps; i++) {
				float k = i / (float) steps;
				int px = Math.round(x0 + (x1 - x0) * k);
				int py = Math.round(y0 + (y1 - y0) * k);
				g.fill(px, py, px + Math.max(1, Math.round(t)), py + Math.max(1, Math.round(t)), a(color));
			}
		}
	}

	/** Ломаная с круглыми стыками: pts = x0,y0,x1,y1,... */
	public static void polyline(GuiGraphics g, float t, int color, float... pts) {
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothPolyline(g, pts, t, fade(color));
		} else {
			for (int i = 0; i + 3 < pts.length; i += 2) {
				line(g, pts[i], pts[i + 1], pts[i + 2], pts[i + 3], t, color);
			}
		}
	}

	// ---------- legacy (gui.smoothGui = false) ----------

	private static void legacyFill(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
		RoundedRender.legacyFill(g, x, y, w, h, r, top, bottom);
	}

	private static int inset(int r, int row, int h) {
		int i = Math.min(row, h - 1 - row);
		if (r <= 0 || i >= r) {
			return 0;
		}
		double dy = r - i - 0.5;
		return (int) Math.round(r - Math.sqrt(r * (double) r - dy * dy));
	}

	private static void legacyRing(GuiGraphics g, int x, int y, int w, int h, int r, int t, int color) {
		if (w <= 0 || h <= 0 || t <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		t = Math.min(t, Math.min(w, h) / 2);
		int ri = Math.max(0, r - t);
		int rows = Math.min(Math.max(r, t), h / 2);
		for (int i = 0; i < rows; i++) {
			int oi = inset(r, i, h);
			int top = y + i;
			int bot = y + h - 1 - i;
			if (i < t) {
				g.fill(x + oi, top, x + w - oi, top + 1, color);
				g.fill(x + oi, bot, x + w - oi, bot + 1, color);
			} else {
				int ii = Math.max(oi, t + inset(ri, i - t, h - 2 * t));
				g.fill(x + oi, top, x + ii, top + 1, color);
				g.fill(x + w - ii, top, x + w - oi, top + 1, color);
				g.fill(x + oi, bot, x + ii, bot + 1, color);
				g.fill(x + w - ii, bot, x + w - oi, bot + 1, color);
			}
		}
		if (h - rows > rows) {
			g.fill(x, y + rows, x + t, y + h - rows, color);
			g.fill(x + w - t, y + rows, x + w, y + h - rows, color);
		}
	}

	private static void legacyRingCircle(GuiGraphics g, int cx, int cy, int r, int t, int color) {
		double ri = Math.max(0.5, r - t / 2.0);
		double ro = ri + t;
		int rows = (int) Math.ceil(ro);
		for (int dy = -rows; dy <= rows; dy++) {
			double oy = (double) dy * dy;
			if (oy > ro * ro) {
				continue;
			}
			int ax = (int) Math.floor(Math.sqrt(ro * ro - oy));
			if (oy < ri * ri) {
				int b = (int) Math.floor(Math.sqrt(ri * ri - oy));
				if (b + 1 <= ax) {
					g.fill(cx - ax, cy + dy, cx - b, cy + dy + 1, color);
					g.fill(cx + b + 1, cy + dy, cx + ax + 1, cy + dy + 1, color);
				}
			} else {
				g.fill(cx - ax, cy + dy, cx + ax + 1, cy + dy + 1, color);
			}
		}
	}

	// ---------- текст (Inter, если включён) ----------

	public static int width(Font font, String s) {
		return Typo.width(font, s);
	}

	public static int width(Font font, Component s) {
		return Typo.width(font, s);
	}

	public static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
		Typo.check(font);
		g.drawString(font, Typo.of(s), x, y, a(color), false);
	}

	public static void textShadow(GuiGraphics g, Font font, String s, int x, int y, int color) {
		Typo.check(font);
		g.drawString(font, Typo.of(s), x, y, a(color), true);
	}

	public static void text(GuiGraphics g, Font font, Component s, int x, int y, int color, boolean shadow) {
		Typo.check(font);
		g.drawString(font, Typo.of(s), x, y, a(color), shadow);
	}

	public static void textScaled(GuiGraphics g, Font font, String s, float x, float y, float scale, int color,
			boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		Typo.check(font);
		g.drawString(font, Typo.of(s), 0, 0, a(color), shadow);
		g.pose().popMatrix();
	}

	public static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	public static float ease(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float p = 1f - t;
		return 1f - p * p * p;
	}
}
