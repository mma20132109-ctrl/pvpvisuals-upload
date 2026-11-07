package com.pvpvisuals.gui.echo;

import net.minecraft.client.gui.GuiGraphics;

/** Вертикальная прокрутка с плавным доезжанием, обрезкой по области (scissor) и тонким скроллбаром. */
public final class ScrollArea {
	public int x;
	public int y;
	public int w;
	public int h;

	private float target;
	private float shown;
	private int contentH;

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		clamp();
	}

	public void setContentHeight(int contentH) {
		this.contentH = contentH;
		clamp();
	}

	public int max() {
		return Math.max(0, contentH - h);
	}

	private void clamp() {
		target = Math.max(0, Math.min(max(), target));
		shown = Math.max(0, Math.min(max(), shown));
	}

	public void scrollBy(float px) {
		target = Math.max(0, Math.min(max(), target + px));
	}

	public void reset() {
		target = 0;
		shown = 0;
	}

	public void update(float dtMs) {
		shown += (target - shown) * Math.min(1f, dtMs / 60f);
		if (Math.abs(target - shown) < 0.3f) {
			shown = target;
		}
	}

	public int offset() {
		return Math.round(shown);
	}

	public boolean contains(double mx, double my) {
		return Gfx.hit(mx, my, x, y, w, h);
	}

	public void begin(GuiGraphics g) {
		g.enableScissor(x, y, x + w, y + h);
	}

	public void end(GuiGraphics g) {
		g.disableScissor();
	}

	public void renderBar(GuiGraphics g) {
		if (max() <= 0) {
			return;
		}
		int barH = Math.max(14, (int) (h * (h / (float) contentH)));
		int by = y + (int) ((h - barH) * (shown / max()));
		Gfx.fill(g, x + w - 2, by, 2, barH, 1, 0x30FFFFFF); // скроллбар остаётся внутри области окна
	}
}
