package com.pvpvisuals.config;

/** Настройки Hand Shader (шейдерный эффект на предмете/руке от первого лица). */
public class HandShaderSettings {
	public boolean enabled = false;
	/** PULSE, VOID, NEBULA, STARFIELD. */
	public String shaderType = "PULSE";
	/** Основной цвет RGB и прозрачность 0.05..1. */
	public int colorPrimary = 0x9B5CFF;
	public float alphaPrimary = 0.55f;
	/** Второй цвет RGB и прозрачность. */
	public int colorSecondary = 0xFF5EB5;
	public float alphaSecondary = 0.55f;
	/** Скорость анимации 0.1..4. */
	public float animationSpeed = 1f;
	/** true: эффект и на пустой руке/карте, false: только когда в руках есть предмет. */
	public boolean affectHand = true;
	/** Эффект только когда в руке оружие (меч, топор, копьё, лук, арбалет, трезубец, булава). */
	public boolean onlyWhenHoldingWeapon = false;
}
