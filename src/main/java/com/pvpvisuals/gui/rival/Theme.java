package com.pvpvisuals.gui.rival;

import com.pvpvisuals.config.PvpConfig;

/** Цветовые темы меню и островка. Все цвета в формате ARGB. */
public enum Theme {
	//            panelTop    panelBottom title       textOn      textOff     accent      icon        barA        barB        chipA       chipB       searchIdle  searchFocus searchHint
	GOLD("Gold",   0xD9EBCB86, 0xD9D9A257, 0xFFFFF4D6, 0xFF2A1A06, 0xFF54401A, 0xFFD9481A, 0xFF5A3A0C, 0xC0371F0C, 0xC0201006, 0xFF8A3414, 0xFFC4411A, 0xC0261C3C, 0xE0301E50, 0xFF9C8FC4),
	PURPLE("Purple", 0xD99B7BE0, 0xD96F4BC4, 0xFFF1E8FF, 0xFF1E0F3F, 0xFF3F2C73, 0xFFFFD15C, 0xFF2A1458, 0xC0221040, 0xC0140828, 0xFF5B34A8, 0xFF7B4FD8, 0xC0261C3C, 0xE0301E50, 0xFFB7A6E8),
	ICE("Ice",     0xD98CC8EB, 0xD95796D9, 0xFFEAF6FF, 0xFF08223F, 0xFF254468, 0xFFFF7A3C, 0xFF0C3A5A, 0xC00C2A40, 0xC0061828, 0xFF1E5E96, 0xFF2F7FC4, 0xC01C2A3C, 0xE0203A50, 0xFF9CB8D8),
	MINT("Mint",   0xD99BE0A8, 0xD94FB86A, 0xFFEFFFF1, 0xFF0B2A12, 0xFF2A5A36, 0xFFE0432B, 0xFF0F4A1E, 0xC00E3016, 0xC0061A0A, 0xFF1F7A38, 0xFF2FA04E, 0xC01C3022, 0xE0204A2C, 0xFF9CD0AA),
	ROSE("Rose",   0xD9EB96B4, 0xD9D95780, 0xFFFFEEF4, 0xFF3F0A1E, 0xFF73274A, 0xFF5C2BE0, 0xFF5A0C2C, 0xC0401022, 0xC0280614, 0xFFA82E5E, 0xFFD84F85, 0xC03C1C2A, 0xE050203A, 0xFFD89CB8),
	DARK("Dark",   0xD93A3A44, 0xD9202028, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF8A8A98, 0xFF4FD96A, 0xFFE6E6F0, 0xC0141418, 0xC00A0A0E, 0xFF34343E, 0xFF4A4A58, 0xC0181820, 0xE0242430, 0xFF8A8A98);

	public final String title;
	public final int panelTop;
	public final int panelBottom;
	public final int titleColor;
	public final int textOn;
	public final int textOff;
	public final int accent;
	public final int icon;
	public final int barTop;
	public final int barBottom;
	public final int chipA;
	public final int chipB;
	public final int searchIdle;
	public final int searchFocus;
	public final int searchHint;
	public final int island;

	Theme(String title, int panelTop, int panelBottom, int titleColor, int textOn, int textOff, int accent, int icon,
			int barTop, int barBottom, int chipA, int chipB, int searchIdle, int searchFocus, int searchHint) {
		this.title = title;
		this.panelTop = panelTop;
		this.panelBottom = panelBottom;
		this.titleColor = titleColor;
		this.textOn = textOn;
		this.textOff = textOff;
		this.accent = accent;
		this.icon = icon;
		this.barTop = barTop;
		this.barBottom = barBottom;
		this.chipA = chipA;
		this.chipB = chipB;
		this.searchIdle = searchIdle;
		this.searchFocus = searchFocus;
		this.searchHint = searchHint;
		this.island = chipB;
	}

	public static Theme current() {
		String n = PvpConfig.get().theme;
		for (Theme t : values()) {
			if (t.name().equalsIgnoreCase(n)) {
				return t;
			}
		}
		return GOLD;
	}

	public Theme next() {
		Theme[] v = values();
		return v[(ordinal() + 1) % v.length];
	}
}
