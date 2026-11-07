package com.pvpvisuals.gui.rival;

/** Угол экрана для уведомлений. */
public enum NotificationPosition {
	TOP_LEFT("Top Left"),
	TOP_RIGHT("Top Right"),
	BOTTOM_LEFT("Bottom Left"),
	BOTTOM_RIGHT("Bottom Right");

	public final String title;

	NotificationPosition(String title) {
		this.title = title;
	}

	public NotificationPosition next() {
		NotificationPosition[] v = values();
		return v[(ordinal() + 1) % v.length];
	}

	public boolean right() {
		return this == TOP_RIGHT || this == BOTTOM_RIGHT;
	}

	public boolean bottom() {
		return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
	}

	public static NotificationPosition byName(String n) {
		for (NotificationPosition p : values()) {
			if (p.name().equalsIgnoreCase(n)) {
				return p;
			}
		}
		return TOP_RIGHT;
	}
}
