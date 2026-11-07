package com.pvpvisuals.config;

/** Настройки HitBox (кастомные хитбоксы с подсветкой цели). Все поля с дефолтами. */
public class HitBoxSettings {
	public boolean enabled = false;
	public boolean showPlayers = true;
	public boolean showMobs = true;
	/** Основной цвет RGB и прозрачность 0..1. */
	public int baseColor = 0x8A2BE2;
	public float baseAlpha = 0.5f;
	/** Цвет сущности под прицелом. */
	public int targetColor = 0xFFD700;
	public float targetAlpha = 0.8f;
	/** Толщина линий в пикселях экрана 0.5 .. 4.0. */
	public float lineThickness = 1.5f;
	/** Скорость смены цвета: доля пути к цели за тик (0.02 .. 0.5), не зависит от FPS. */
	public float transitionSpeed = 0.1f;
	public boolean throughWalls = true;
	/** Максимальная дистанция, блоки (8 .. 128). */
	public float maxDistance = 64f;
	/** Рисовать только углы коробки (как в ESP). */
	public boolean cornerOnly = false;
	/** Мягкое свечение вокруг линий. */
	public boolean glow = false;
}
