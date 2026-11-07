package com.pvpvisuals.render.hitbox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Прогресс перехода цвета для каждой сущности: 0.0 — основной цвет, 1.0 — акцентный (цвет цели).
 * Запись живёт, пока сущность попадает в кадр: остальные удаляются в {@link #endFrame()}, поэтому карта не растёт.
 */
public final class HitBoxState {
	private static final class Entry {
		float progress;
		int stamp;
	}

	private static final Map<UUID, Entry> STATES = new HashMap<>();
	private static int frame;

	private HitBoxState() { }

	public static void beginFrame() {
		frame++;
	}

	/**
	 * Сдвигает прогресс сущности к цели (target: 1 — под прицелом, 0 — нет) и возвращает новое значение.
	 * perTick — доля пути за тик (0.1 = 10% за тик); dtTicks — прошедшее время в тиках, так что скорость
	 * не зависит от FPS: 1 - (1 - perTick)^dtTicks.
	 */
	public static float update(UUID id, boolean target, float perTick, float dtTicks) {
		Entry e = STATES.get(id);
		if (e == null) {
			e = new Entry();
			STATES.put(id, e);
		}
		e.stamp = frame;
		float k = 1f - (float) Math.pow(1.0 - Math.max(0.001f, Math.min(1f, perTick)), dtTicks);
		e.progress += ((target ? 1f : 0f) - e.progress) * k;
		return e.progress;
	}

	/** Удаляет сущности, которых не было в этом кадре (вышли из зоны, умерли, выгружены). */
	public static void endFrame() {
		Iterator<Entry> it = STATES.values().iterator();
		while (it.hasNext()) {
			if (it.next().stamp != frame) {
				it.remove();
			}
		}
	}

	public static void clear() {
		STATES.clear();
	}
}
