package com.pvpvisuals.gui.rival;

public enum Category {
	VISUAL("Visual", 0xFFE0402A),
	WORLD("World", 0xFFE8841F),
	PARTICLES("Particles", 0xFFEF6A1A),
	UTILS("Utils", 0xFF3FA545),
	CLIENT("Client", 0xFFE0402A);

	public final String title;
	public final int iconColor;

	Category(String title, int iconColor) {
		this.title = title;
		this.iconColor = iconColor;
	}
}
