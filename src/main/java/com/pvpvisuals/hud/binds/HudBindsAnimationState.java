package com.pvpvisuals.hud.binds;

/** Прогресс плавного появления строки: 0..1. */
public final class HudBindsAnimationState {
	public float progress;

	public void advance(float dtMs, float durationMs) {
		progress = durationMs <= 1f ? 1f : Math.min(1f, progress + dtMs / durationMs);
	}

	/** Сглаженное значение (ease-out) для альфы. */
	public float eased() {
		float p = 1f - progress;
		return 1f - p * p;
	}
}
