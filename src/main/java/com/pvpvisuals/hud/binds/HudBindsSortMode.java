package com.pvpvisuals.hud.binds;

public enum HudBindsSortMode {
	WIDTH("Width"),
	ALPHABETICAL("Alphabetical"),
	CUSTOM("Custom");

	public final String title;

	HudBindsSortMode(String title) {
		this.title = title;
	}

	public HudBindsSortMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static HudBindsSortMode byName(String n) {
		for (HudBindsSortMode m : values()) {
			if (m.name().equalsIgnoreCase(n)) {
				return m;
			}
		}
		return WIDTH;
	}
}
