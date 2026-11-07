package com.pvpvisuals.config;

/** Настройки внешнего вида меню. */
public class GuiSettings {
	/** Непрозрачность тёмного оверлея за меню (0..0.8). */
	public float overlayAlpha = 0.33f;
	/** Множитель непрозрачности панелей (0.3..1). */
	public float panelOpacity = 1.0f;
	/** Плавный рендер GUI (скругления, обводки, свечение с мягкими краями). false — старый построчный рендер. */
	public boolean smoothGui = true;
	/** Шрифт Inter (assets/pvpvisuals/font/inter.*) в меню. false — ванильный шрифт. */
	public boolean smoothFont = true;
	/** Цветовая тема GUI и HUD: PURPLE / BLUE / CYAN / GREEN / RED / ORANGE / PINK / GRAPHITE. */
	public String palette = "GOLD";
}
