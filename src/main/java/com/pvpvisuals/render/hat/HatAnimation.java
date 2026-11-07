package com.pvpvisuals.render.hat;

/** Анимация China Hat: вращение, покачивание, оба сразу или выкл. */
public enum HatAnimation {
	OFF("Off", false, false),
	SPIN("Spin", true, false),
	BOB("Bob", false, true),
	BOTH("Spin + Bob", true, true);

	public final String title;
	public final boolean spin;
	public final boolean bob;

	HatAnimation(String title, boolean spin, boolean bob) {
		this.title = title;
		this.spin = spin;
		this.bob = bob;
	}

	public static HatAnimation byName(String n) {
		for (HatAnimation a : values()) {
			if (a.name().equalsIgnoreCase(n)) {
				return a;
			}
		}
		return SPIN;
	}
}
