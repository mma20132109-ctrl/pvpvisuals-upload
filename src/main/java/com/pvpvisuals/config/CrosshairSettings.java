package com.pvpvisuals.config;

/** Настройки Custom Crosshair. Цвета — RGB без альфы (альфа общая). Enum-значения хранятся именами. */
public class CrosshairSettings {
	public boolean enabled = false;
	public String style = "CROSS";
	public String colorMode = "STATIC";
	public int colorPrimary = 0xFFFFFF;
	public int colorSecondary = 0xE8C87A;
	public float alpha = 1.0f;
	public int thickness = 1;
	public int length = 6;
	public int gap = 3;
	public int dotSize = 1;
	public int circleRadius = 6;
	public boolean outline = true;
	public int outlineColor = 0x000000;
	public int outlineThickness = 1;
	public String animation = "NONE";
	public int animationDuration = 200;
	/** Цвет вспышки для анимации FLASH. */
	public int flashColor = 0xFF3B3B;
	public boolean dynamicSpread = false;
	public float spreadMultiplier = 1.0f;
	public boolean hideInGui = true;
	public boolean hideInThirdPerson = true;
}
