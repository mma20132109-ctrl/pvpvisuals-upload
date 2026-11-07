package com.pvpvisuals.config;

/** Настройки Radial Menu (быстрые сообщения). Клавиша вызова хранится в системе биндов (по умолчанию R). */
public class RadialMenuSettings {
	public static final int SECTORS = 5;

	public boolean enabled = false;
	/** Фразы секторов по часовой стрелке, начиная с верхнего. */
	public String[] phrases = {"GG", "WP", "Help", "Push", "Retreat"};
	/** Радиус меню в GUI-пикселях (диаметр ~300 при 150) и мёртвая зона в центре. */
	public int menuRadius = 150;
	public int deadZoneRadius = 30;
	public int backgroundColor = 0x120A1F;
	public float backgroundAlpha = 0.8f;
	public int highlightColor = 0x8A2BE2;
	public float highlightAlpha = 0.9f;
	public int textColor = 0xFFFFFF;
}
