package com.pvpvisuals.render.radial;

/**
 * Один сектор радиального меню. Углы в радианах, отсчёт по часовой стрелке от направления «вверх»;
 * сектор 0 с центром строго сверху и занимает [-36°, +36°), сектор 1 — [36°, 108°) и т.д. (для 5 секторов по 72°).
 */
public record RadialSector(int index, float startRad, float endRad, String text) {
	public float midRad() {
		return (startRad + endRad) * 0.5f;
	}

	public boolean hasText() {
		return text != null && !text.isBlank();
	}

	/**
	 * Номер сектора под курсором или -1 (мёртвая зона). dx, dy — смещение курсора от центра в пикселях (y вниз).
	 * Угол: atan2(dx, -dy) = угол по часовой от «вверх»; сдвиг на пол-сектора делает сектор 0 центрированным вверху.
	 */
	public static int pick(double dx, double dy, double deadZone, int count) {
		if (dx * dx + dy * dy < deadZone * deadZone) {
			return -1;
		}
		double step = Math.PI * 2.0 / count;
		double ang = Math.atan2(dx, -dy);
		ang = (ang + step / 2.0) % (Math.PI * 2.0);
		if (ang < 0) {
			ang += Math.PI * 2.0;
		}
		return Math.min(count - 1, (int) (ang / step));
	}
}
