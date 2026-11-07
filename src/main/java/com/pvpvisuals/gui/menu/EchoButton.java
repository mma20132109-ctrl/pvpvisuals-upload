package com.pvpvisuals.gui.menu;

import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Кнопка главного меню Echo Visual. Намеренно НЕ наследует ванильный AbstractWidget/Button: так не нужны
 * сигнатуры onClick/updateWidgetNarration, которые меняются между версиями. Отрисовка идёт через Gfx
 * (SmoothRenderer), ввод принимает экран меню.
 *
 * Анимации: наведение — плавная заливка и масштаб до 1.02 (ease-out), нажатие — «проседание» до 0.95.
 */
public final class EchoButton {
	public enum Icon { NONE, EXIT, GLOBE }

	public int x, y, w, h;
	public final String label;
	public final Icon icon;
	public final Runnable action;

	private float hover; // 0..1
	private float press; // 0..1
	public boolean down;

	public EchoButton(String label, Icon icon, Runnable action) {
		this.label = label;
		this.icon = icon;
		this.action = action;
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Сглаживание hover/press: экспоненциальное приближение к цели, не зависит от FPS. */
	public void update(float dt, boolean hovered) {
		float k = 1f - (float) Math.exp(-dt * 14f);
		hover += ((hovered ? 1f : 0f) - hover) * k;
		press += ((down && hovered ? 1f : 0f) - press) * (1f - (float) Math.exp(-dt * 30f));
	}

	static int lerp(int a, int b, float t) {
		int r = 0;
		for (int s = 24; s >= 0; s -= 8) {
			int ca = (a >>> s) & 0xFF;
			int cb = (b >>> s) & 0xFF;
			r |= Math.round(ca + (cb - ca) * t) << s;
		}
		return r;
	}

	/** alpha и offY задаёт экран (появление кнопок с задержкой: fade-in + сдвиг снизу). */
	public void render(GuiGraphics g, Font font, float alpha, float offY) {
		if (alpha <= 0.003f) {
			return;
		}
		float prev = Gfx.alpha;
		Gfx.alpha = prev * alpha;
		float e = Gfx.ease(hover);
		float sc = (1f + 0.02f * e) * (1f - 0.05f * press);
		float cw = w * sc;
		float ch = h * sc;
		float cx = x + w / 2f;
		float cy = y + h / 2f + offY;
		float bx = cx - cw / 2f;
		float by = cy - ch / 2f;
		float r = Math.min(ch / 2f, icon == Icon.NONE ? 8f : ch / 2f);

		int fillTop = lerp(EchoTheme.shift(0xFF150C28), EchoTheme.shift(0xFF9B5CFF), e);
		int fillBot = lerp(EchoTheme.shift(0xFF0B0616), EchoTheme.shift(0xFF7A35F0), e);
		float dark = 0.22f * press; // при нажатии лёгкое затемнение
		fillTop = lerp(fillTop, 0xFF000000, dark);
		fillBot = lerp(fillBot, 0xFF000000, dark);
		int border = lerp(EchoTheme.shift(0xFF6D3FD0), EchoTheme.shift(0xFFC9A6FF), e);
		if (e > 0.02f) {
			Gfx.glow(g, bx, by, cw, ch, r, EchoTheme.shift(0xFF8B3DFF), 4f * e);
		}
		Gfx.rrect(g, bx, by, cw, ch, r, fillTop, fillBot, border, 1f);

		int textCol = lerp(0xFFC9B8EE, 0xFFFFFFFF, e);
		if (icon == Icon.NONE) {
			float ts = sc;
			float tw = Gfx.width(font, label) * ts;
			Gfx.textScaled(g, font, label, cx - tw / 2f, cy - 4f * ts, ts, textCol, false);
		} else {
			drawIcon(g, cx, cy, 6f * sc, textCol);
		}
		Gfx.alpha = prev;
	}

	private void drawIcon(GuiGraphics g, float cx, float cy, float r, int col) {
		if (icon == Icon.EXIT) {
			float d = r * 0.62f;
			Gfx.line(g, cx - d, cy - d, cx + d, cy + d, 1.6f, col);
			Gfx.line(g, cx + d, cy - d, cx - d, cy + d, 1.6f, col);
		} else if (icon == Icon.GLOBE) {
			Gfx.ringCircle(g, cx, cy, r, 1.2f, col);
			Gfx.line(g, cx - r, cy, cx + r, cy, 1.1f, col);
			float px = cx, py = cy - r;
			for (int i = 1; i <= 16; i++) { // меридиан-эллипс
				double a = -Math.PI / 2 + Math.PI * 2 * i / 16;
				float x2 = cx + (float) Math.cos(a) * r * 0.45f;
				float y2 = cy + (float) Math.sin(a) * r;
				Gfx.line(g, px, py, x2, y2, 1.0f, col);
				px = x2;
				py = y2;
			}
		}
	}
}
