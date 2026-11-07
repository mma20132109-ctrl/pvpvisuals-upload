package com.pvpvisuals.config;

/** Настройки Fullbright. */
public class FullbrightSettings {
	public boolean enabled = false;
	/** Целевая гамма (1.0–15.0). */
	public float brightness = 10.0f;
	public boolean smoothTransition = true;
	/** Длительность перехода в тиках. */
	public int transitionSpeed = 10;
	/** Режим (сейчас поддерживается только GAMMA). */
	public String mode = "GAMMA";
	/** Служебное: исходная гамма, пока Fullbright активен (-1 — не активен). Нужна, чтобы восстановить её после сбоя. */
	public double savedGamma = -1.0;
}
