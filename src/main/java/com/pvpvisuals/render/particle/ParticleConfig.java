package com.pvpvisuals.render.particle;

/**
 * Общие настройки частиц (сохраняются в GSON-конфиг мода внутри PvpConfig).
 * Недостающие в старом файле поля получают значения по умолчанию из инициализаторов.
 */
public class ParticleConfig {
	public String style = "SPARK";
	public int count = 14;
	public float size = 0.12f;
	/** -1 .. 1: отрицательное — частица уменьшается к концу жизни, положительное — растёт. */
	public float sizeGrow = -0.5f;
	public int life = 18;
	public float spread = 0.6f;
	public float velocity = 0.8f;
	/** -1 .. 1; отрицательная — частицы летят вверх. */
	public float gravity = 0.2f;
	/** Множитель скорости за тик: 1.0 — без трения. */
	public float friction = 0.88f;

	public String colorMode = "STATIC";
	public int color1 = 0xFFFFFF;
	public int color2 = 0xFF5522;
	public int hueMin = 0;
	public int hueMax = 360;
	public int alpha = 220;
}
