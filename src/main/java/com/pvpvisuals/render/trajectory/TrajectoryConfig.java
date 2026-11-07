package com.pvpvisuals.render.trajectory;

/** Настройки Trajectory (внутри PvpConfig; недостающие в старом JSON поля берут значения отсюда). */
public class TrajectoryConfig {
	/** HAND — только предсказание для предмета в руке; PLAYERS — плюс снаряды игроков; ALL — любые снаряды. */
	public String source = "HAND";

	public boolean showArrow = true;
	public boolean showTrident = true;
	public boolean showPearl = true;
	public boolean showSnowball = true;
	public boolean showEgg = true;
	public boolean showPotion = true;
	public boolean showExpBottle = true;
	public boolean showFireball = true;
	public boolean showWitherSkull = true;

	public String style = "GRADIENT";
	public float width = 0.03f;
	public int colorStart = 0x55FF55;
	public int colorEnd = 0xFF5555;
	public boolean fade = true;
	public boolean glow = true;
	/** Точек на тик (плавность штрихов и градиента). */
	public int detail = 3;
	public int maxDistance = 64;

	public boolean showStart = true;
	public boolean showLanding = true;
	public float landingSize = 0.18f;
	public boolean dropLine = true;
	public boolean showCollision = true;
	public int collisionColor = 0xFF3030;
	public boolean faceHighlight = true;
	public boolean showProgress = true;
	public int progressInterval = 5;
	public boolean showPeak = true;

	public boolean showDistance = true;
	public boolean showTime = true;
	public boolean showDamage = true;
	/** true — «m», false — «blocks». */
	public boolean unitsMeters = true;
	/** true — секунды, false — тики. */
	public boolean timeSeconds = true;
}
