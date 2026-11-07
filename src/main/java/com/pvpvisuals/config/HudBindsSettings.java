package com.pvpvisuals.config;

/** Настройки HUD Binds. Оформление берётся из темы (как у Target HUD), поэтому цветов здесь нет. */
public class HudBindsSettings {
	public boolean enabled = false;
	/** Клавиша перед названием ("G  Fullbright") вместо "Fullbright [G]". */
	public boolean showBindFirst = false;
	/** Имя HudBindsSortMode. */
	public String sortMode = "WIDTH";
	/** Сколько строк на странице. */
	public int pageSize = 4;
	/** Автолистание страниц, секунды (0 — выключено). */
	public int autoFlipSeconds = 0;
	/** Длительность плавного появления строк и смены страницы, мс. */
	public int animationSpeed = 200;
}
