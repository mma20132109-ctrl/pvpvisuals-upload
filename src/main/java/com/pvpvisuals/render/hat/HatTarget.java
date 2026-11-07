package com.pvpvisuals.render.hat;

/** На ком показывать шляпу. */
public enum HatTarget {
	SELF("Self"),
	OTHERS("Others"),
	ALL("All");

	public final String title;

	HatTarget(String title) {
		this.title = title;
	}

	public static HatTarget byName(String n) {
		for (HatTarget t : values()) {
			if (t.name().equalsIgnoreCase(n)) {
				return t;
			}
		}
		return SELF;
	}
}
