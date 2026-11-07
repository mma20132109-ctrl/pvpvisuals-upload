package com.pvpvisuals.render.kill;

/** Для каких целей проигрывать эффект. */
public enum KillEffectTarget {
	PLAYERS_ONLY("Players"),
	MOBS_ONLY("Mobs"),
	ALL("All");

	public final String title;

	KillEffectTarget(String title) {
		this.title = title;
	}

	public KillEffectTarget next() {
		KillEffectTarget[] v = values();
		return v[(ordinal() + 1) % v.length];
	}

	public static KillEffectTarget byName(String n) {
		for (KillEffectTarget t : values()) {
			if (t.name().equalsIgnoreCase(n)) {
				return t;
			}
		}
		return PLAYERS_ONLY;
	}
}
