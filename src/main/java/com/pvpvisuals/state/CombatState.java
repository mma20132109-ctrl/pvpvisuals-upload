package com.pvpvisuals.state;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.entity.LivingEntity;

/** Общее клиентское состояние боя (только главный поток клиента). */
public final class CombatState {
	private CombatState() { }

	/** Счётчик клиентских тиков. */
	public static long now;

	public static LivingEntity lastTarget;
	public static long lastTargetTime;

	public static int combo;
	public static long lastHitTime;

	/** Оставшиеся тики вспышки крита. */
	public static int critFlash;

	/** entityId -> тик, когда мы нанесли крит. Нужно, чтобы покрасить число урона. */
	public static final Map<Integer, Long> critHits = new HashMap<>();

	public static void reset() {
		lastTarget = null;
		combo = 0;
		critFlash = 0;
		critHits.clear();
	}
}
