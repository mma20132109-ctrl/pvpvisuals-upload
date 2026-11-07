package com.pvpvisuals.gui.echo;

/**
 * Темы GUI и HUD. Базовая палитра фиолетовая (оттенок около 266 градусов); hueDelta — на сколько градусов
 * повернуть оттенок всех цветов, saturation — множитель насыщенности (Graphite почти без цвета).
 */
public enum EchoPalette {
	/** Фирменная: фиолетовые поверхности (#120A1F) и золотисто-оранжевые акценты (#FFA500). */
	GOLD("Echo Gold", 0, 1.0f, true),
	PURPLE("Purple", 0, 1.0f),
	BLUE("Blue", -50, 1.0f),
	CYAN("Cyan", -88, 1.0f),
	GREEN("Green", -126, 1.0f),
	RED("Red", 90, 1.05f),
	ORANGE("Orange", 120, 1.0f),
	PINK("Pink", 54, 1.0f),
	GRAPHITE("Graphite", 0, 0.10f);

	public final String title;
	public final int hueDelta;
	public final float saturation;
	/** true: поверхности остаются фиолетовыми, а акцентные цвета (тумблеры, обводки, вкладки) заменяются на золото. */
	public final boolean goldAccent;

	EchoPalette(String title, int hueDelta, float saturation) {
		this(title, hueDelta, saturation, false);
	}

	EchoPalette(String title, int hueDelta, float saturation, boolean goldAccent) {
		this.goldAccent = goldAccent;
		this.title = title;
		this.hueDelta = hueDelta;
		this.saturation = saturation;
	}

	public static EchoPalette byName(String name) {
		if (name != null) {
			for (EchoPalette p : values()) {
				if (p.name().equalsIgnoreCase(name.trim())) {
					return p;
				}
			}
		}
		return GOLD;
	}
}
