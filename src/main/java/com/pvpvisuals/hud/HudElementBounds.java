package com.pvpvisuals.hud;

/** Рамка элемента на экране в GUI-пикселях, с учётом масштаба. Мутабельная, чтобы не создавать объекты каждый кадр. */
public final class HudElementBounds {
	public int x;
	public int y;
	public int width;
	public int height;

	public void set(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public boolean contains(double mx, double my, int margin) {
		return mx >= x - margin && mx <= x + width + margin && my >= y - margin && my <= y + height + margin;
	}
}
