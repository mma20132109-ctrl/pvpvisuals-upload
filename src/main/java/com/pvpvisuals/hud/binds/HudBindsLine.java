package com.pvpvisuals.hud.binds;

/** Одна строка списка. Строки и ширины кэшируются и пересоздаются только при изменении бинда или настроек. */
public final class HudBindsLine {
	public final String module;
	public final int order;
	/** Включён ли модуль сейчас (выключенные остаются в списке, но тусклые). */
	public boolean on;
	public final HudBindsAnimationState anim = new HudBindsAnimationState();

	/** Название модуля и часть с клавишей ("[G]" / "G" / ""), в порядке отображения: first, second. */
	public String name = "";
	public String bind = "";
	public boolean bindFirst;
	public int nameW;
	public int bindW;
	/** Ширина текста строки без паддингов (для сортировки и выравнивания). */
	public int width;
	/** Сигнатура для проверки «изменилось ли что-то». */
	public String signature = "";

	public HudBindsLine(String module, int order) {
		this.module = module;
		this.order = order;
	}
}
