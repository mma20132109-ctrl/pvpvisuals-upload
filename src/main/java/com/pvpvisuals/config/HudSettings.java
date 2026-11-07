package com.pvpvisuals.config;

/** Универсальные настройки одного HUD-элемента. Отсутствующие в старом JSON поля берут значения отсюда. */
public class HudSettings {
	/** Смещение от якоря в GUI-пикселях. */
	public int x = 10;
	public int y = 10;
	public String anchor = "TOP_LEFT";
	public float scale = 1.0f;
	public float alpha = 1.0f;
}
