package com.pvpvisuals.render.kill;

/** Стиль эффекта убийства. */
public enum KillEffectStyle {
	LIGHTNING("Lightning"),
	EXPLOSION("Explosion"),
	SKULL("Skull"),
	STAR_BURST("Star Burst"),
	SHOCKWAVE("Shockwave");

	public final String title;

	KillEffectStyle(String title) {
		this.title = title;
	}

	/** Имя из конфига -> стиль; неизвестное значение -> EXPLOSION. */
	public static KillEffectStyle byName(String n) {
		for (KillEffectStyle s : values()) {
			if (s.name().equalsIgnoreCase(n)) {
				return s;
			}
		}
		return EXPLOSION;
	}
}
