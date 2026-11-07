package com.pvpvisuals.config;

/** Настройки Block Overlay (шейдерная подсветка блока под прицелом). Все поля с дефолтами. */
public class BlockOverlaySettings {
	public boolean enabled = false;
	/** PULSE / GRID / SCANLINE / GLOW / SOLID (строка, чтобы неизвестное значение не ломало чтение конфига). */
	public String shaderType = "PULSE";
	/** Основной цвет RGB (без альфы) и прозрачность 0.05 .. 1.0. */
	public int color = 0x9B5CFF;
	public float alpha = 0.4f;
	/** Скорость анимации 0.1 .. 4.0. */
	public float pulseSpeed = 1.0f;
	/** Толщина линий (сетка, бегущие линии, обводка) 0.5 .. 8.0. */
	public float lineThickness = 2.0f;
	/** Видно сквозь стены (без depth-теста). */
	public boolean showThroughWalls = false;
	/** Рисовать только рёбра блока, без заливки граней. */
	public boolean outlineOnly = false;
	/** Космические стили (VOID / NEBULA / STARFIELD / GALAXY): второй цвет градиента (по умолчанию розовый). */
	public int colorSecondary = 0xFF5EB5;
	/** Плотность звёзд 0.2 .. 3.0 (STARFIELD / GALAXY). */
	public float starDensity = 1.0f;
	/** LOW / MEDIUM / HIGH: число октав шума и слоёв звёзд (космические стили). */
	public String renderQuality = "MEDIUM";
}
