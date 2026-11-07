package com.pvpvisuals.config;

/** Настройки Shader Sky. Все поля с дефолтами: старый конфиг читается без ошибок. */
public class ShaderSkySettings {
	public boolean enabled = false;
	/** VOID / GRADIENT / STARS / SYNTHWAVE (строка, чтобы неизвестное значение не ломало чтение конфига). */
	public String shaderType = "VOID";
	/** 0.5 .. 2.0 */
	public float brightness = 1.0f;
	/** 0.0 .. 2.0 */
	public float speed = 1.0f;
	/** true — небо не зависит от времени суток (всегда «ночной» вид). */
	public boolean timeOfDayLock = true;
	/** Прятать ванильные облака, пока включён Shader Sky. */
	public boolean hideClouds = false;
	/** Свой цвет неба: оттенок 0..360 градусов (280 — родной фиолетовый) и насыщенность 0..2 (1 — как в шейдере). */
	public boolean customColor = false;
	public int colorHue = 280;
	public float colorSaturation = 1.0f;
}
