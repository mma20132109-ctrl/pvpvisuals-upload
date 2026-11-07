package com.pvpvisuals.gui.echo;

import com.pvpvisuals.gui.rival.RoundedRender;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/** Строка поиска в шапке: скруглённая плашка с лупой. Ввод обрабатывается вручную, фильтр работает на лету. */
public final class EchoSearchBar {
	private static final int MAX_LEN = 32;

	public int x;
	public int y;
	public int w;
	public int h = 18;

	private final StringBuilder text = new StringBuilder();
	private boolean focused;
	private float focusAnim;

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean f) {
		this.focused = f;
	}

	public String query() {
		return text.toString().trim().toLowerCase(java.util.Locale.ROOT);
	}

	public boolean isEmpty() {
		return text.length() == 0;
	}

	public void setQuery(String q) {
		text.setLength(0);
		text.append(q);
	}

	public void clear() {
		text.setLength(0);
	}

	public void type(int codepoint) {
		if (text.length() < MAX_LEN && codepoint >= 32 && codepoint != 127) {
			text.appendCodePoint(codepoint);
		}
	}

	public void backspace() {
		if (text.length() > 0) {
			text.deleteCharAt(text.length() - 1);
		}
	}

	public boolean contains(double mx, double my) {
		return Gfx.hit(mx, my, x, y, w, h);
	}

	public void update(float dtMs) {
		focusAnim += ((focused ? 1f : 0f) - focusAnim) * Math.min(1f, dtMs / 90f);
	}

	public void render(GuiGraphics g, Font font) {
		if (focusAnim > 0.02f) {
			Gfx.glow(g, x, y, w, h, h / 2, RoundedRender.scaleAlpha(EchoTheme.NEON, focusAnim * 0.7f), 2);
		}
		Gfx.fill(g, x, y, w, h, h / 2, RoundedRender.lerp(EchoTheme.FIELD, EchoTheme.FIELD_FOCUS, focusAnim));
		Gfx.ring(g, x, y, w, h, h / 2, 1,
				RoundedRender.lerp(EchoTheme.FIELD_BORDER, EchoTheme.NEON, focusAnim));
		EchoIcons.magnifier(g, x + 8, y + (h - 10) / 2, focused ? EchoTheme.ACCENT_TEXT : EchoTheme.MUTED);

		int tx = x + 24;
		int ty = y + (h - 8) / 2;
		int maxW = w - 24 - 8;
		if (text.length() == 0) {
			Gfx.textAtSize(g, font, "Поиск", tx, ty, 6.2f, EchoTheme.DIM, false);
			if (focused && (Util.getMillis() / 500) % 2 == 0) {
				Gfx.rect(g, tx, ty - 1, tx + 1, ty + 9, EchoTheme.TEXT);
			}
			return;
		}
		String shown = text.toString();
		while (shown.length() > 1 && Gfx.widthAtSize(font, shown, 6.2f) > maxW) {
			shown = shown.substring(1);
		}
		Gfx.textAtSize(g, font, shown, tx, ty, 6.2f, EchoTheme.TEXT, false);
		if (focused && (Util.getMillis() / 500) % 2 == 0) {
			int cx = tx + Gfx.widthAtSize(font, shown, 6.2f) + 1;
			Gfx.rect(g, cx, ty - 1, cx + 1, ty + 9, EchoTheme.TEXT);
		}
	}
}
