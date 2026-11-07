package com.pvpvisuals.hud.inventory;

/** Как показывать прочность: полоска, число или оба. */
public enum DurabilityDisplayMode {
	BAR("Bar"),
	NUMBER("Number"),
	BOTH("Both");

	public final String title;

	DurabilityDisplayMode(String title) {
		this.title = title;
	}

	public static DurabilityDisplayMode byName(String n) {
		for (DurabilityDisplayMode m : values()) {
			if (m.name().equalsIgnoreCase(n)) {
				return m;
			}
		}
		return BAR;
	}
}
