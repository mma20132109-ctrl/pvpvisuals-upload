package com.pvpvisuals.render.trajectory;

import net.minecraft.world.entity.EntityType;

/** Поддерживаемые снаряды и их физика (гравитация, сопротивление за тик — значения ванили). */
public enum ProjectileType {
	ARROW("Arrow", 0.05, 0.99),
	TRIDENT("Trident", 0.05, 0.99),
	ENDER_PEARL("Ender Pearl", 0.03, 0.99),
	SNOWBALL("Snowball", 0.03, 0.99),
	EGG("Egg", 0.03, 0.99),
	POTION("Potion", 0.05, 0.99),
	EXP_BOTTLE("Exp Bottle", 0.07, 0.99),
	/** Огненные шары: у них нет гравитации, ускорение не моделируется (прямая по текущей скорости). */
	FIREBALL("Fireball", 0.0, 1.0),
	WITHER_SKULL("Wither Skull", 0.0, 1.0);

	public final String title;
	public final double gravity;
	public final double drag;

	ProjectileType(String title, double gravity, double drag) {
		this.title = title;
		this.gravity = gravity;
		this.drag = drag;
	}

	public boolean enabled(TrajectoryConfig c) {
		return switch (this) {
			case ARROW -> c.showArrow;
			case TRIDENT -> c.showTrident;
			case ENDER_PEARL -> c.showPearl;
			case SNOWBALL -> c.showSnowball;
			case EGG -> c.showEgg;
			case POTION -> c.showPotion;
			case EXP_BOTTLE -> c.showExpBottle;
			case FIREBALL -> c.showFireball;
			case WITHER_SKULL -> c.showWitherSkull;
		};
	}

	public void setEnabled(TrajectoryConfig c, boolean v) {
		switch (this) {
			case ARROW -> c.showArrow = v;
			case TRIDENT -> c.showTrident = v;
			case ENDER_PEARL -> c.showPearl = v;
			case SNOWBALL -> c.showSnowball = v;
			case EGG -> c.showEgg = v;
			case POTION -> c.showPotion = v;
			case EXP_BOTTLE -> c.showExpBottle = v;
			case FIREBALL -> c.showFireball = v;
			case WITHER_SKULL -> c.showWitherSkull = v;
		}
	}

	/** Тип по сущности в мире или null, если сущность не поддерживается. */
	public static ProjectileType of(EntityType<?> t) {
		if (t == EntityType.ARROW || t == EntityType.SPECTRAL_ARROW) {
			return ARROW;
		}
		if (t == EntityType.TRIDENT) {
			return TRIDENT;
		}
		if (t == EntityType.ENDER_PEARL) {
			return ENDER_PEARL;
		}
		if (t == EntityType.SNOWBALL) {
			return SNOWBALL;
		}
		if (t == EntityType.EGG) {
			return EGG;
		}
		if (t == EntityType.SPLASH_POTION || t == EntityType.LINGERING_POTION) {
			return POTION;
		}
		if (t == EntityType.EXPERIENCE_BOTTLE) {
			return EXP_BOTTLE;
		}
		if (t == EntityType.FIREBALL || t == EntityType.SMALL_FIREBALL || t == EntityType.DRAGON_FIREBALL) {
			return FIREBALL;
		}
		if (t == EntityType.WITHER_SKULL) {
			return WITHER_SKULL;
		}
		return null;
	}
}
