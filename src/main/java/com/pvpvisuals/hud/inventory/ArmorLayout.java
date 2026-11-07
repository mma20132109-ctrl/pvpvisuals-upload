package com.pvpvisuals.hud.inventory;

/** Где рисовать броню и оффхенд относительно основной сетки. */
public enum ArmorLayout {
	LEFT("Left"),
	TOP("Top"),
	ROW("Own row");

	public final String title;

	ArmorLayout(String title) {
		this.title = title;
	}

	public static ArmorLayout byName(String n) {
		for (ArmorLayout a : values()) {
			if (a.name().equalsIgnoreCase(n)) {
				return a;
			}
		}
		return LEFT;
	}
}
