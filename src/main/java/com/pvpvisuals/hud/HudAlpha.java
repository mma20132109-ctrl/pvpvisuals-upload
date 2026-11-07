package com.pvpvisuals.hud;

/**
 * Общая прозрачность HUD-элемента на время его отрисовки. AbstractHudModule выставляет значение, а элементы
 * прогоняют свои ARGB-цвета через {@link #mul(int)}. Иконки предметов (ваниль) прозрачность не поддерживают.
 */
public final class HudAlpha {
	public static float current = 1.0f;

	private HudAlpha() { }

	public static int mul(int argb) {
		int src = (argb >>> 24) & 0xFF;
		int a = (int) (src * current);
		if (src > 0 && a < 4) {
			a = 4; // у шрифта альфа <= 3 трактуется как непрозрачный цвет
		}
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
