package com.pvpvisuals.hud;

/** Привязка HUD-элемента: от какого угла (или центра) экрана считается смещение. */
public enum HudAnchor {
	TOP_LEFT("Top Left"),
	TOP_RIGHT("Top Right"),
	BOTTOM_LEFT("Bottom Left"),
	BOTTOM_RIGHT("Bottom Right"),
	CENTER("Center");

	public final String title;

	HudAnchor(String title) {
		this.title = title;
	}

	public static HudAnchor byName(String n) {
		for (HudAnchor a : values()) {
			if (a.name().equalsIgnoreCase(n)) {
				return a;
			}
		}
		return TOP_LEFT;
	}
}
