package com.pvpvisuals.gui.echo;

import com.pvpvisuals.config.PvpConfig;

/**
 * Палитра GUI и HUD. Исходная (базовая) палитра — тёмно-фиолетовая; остальные темы получаются поворотом оттенка
 * и изменением насыщенности всех базовых цветов ({@link EchoPalette}), поэтому меню, HUD и островок меняются разом.
 * Поля не final: {@link #apply} переписывает их, а код везде читает {@code EchoTheme.CARD} и т.п. как раньше.
 * Все цвета ARGB.
 */
public final class EchoTheme {
	private EchoTheme() { }

	private static EchoPalette applied;
	private static int hueDelta;
	private static float satScale = 1f;

	/** Базовая (фиолетовая) палитра. */
	private static final int B_WIN_TOP = 0xF2150C27;
	private static final int B_WIN_BOTTOM = 0xF20F081A;
	private static final int B_HUD_TOP = 0xDC150C27;
	private static final int B_HUD_BOTTOM = 0xDC0F081A;
	private static final int B_NEON = 0xFF8B3DFF;
	private static final int B_NEON_LINE = 0xFF7B3FF2;
	private static final int B_ACCENT_A = 0xFF7C3AED;
	private static final int B_ACCENT_B = 0xFFA855F7;
	private static final int B_ACCENT_TEXT = 0xFFB68CFF;
	private static final int B_TAB_TOP = 0xE05A2BC8;
	private static final int B_TAB_BOTTOM = 0xE0792EE0;
	private static final int B_CARD = 0xD0190F2E;
	private static final int B_CARD_HOVER = 0xE0211440;
	private static final int B_CARD_BORDER = 0xFF2A1B4D;
	private static final int B_CARD_BORDER_ON = 0xFF3F2A78;
	private static final int B_CARD_BORDER_HOT = 0xFF9D6BFF;
	private static final int B_TILE = 0xFF2B1A5E;
	private static final int B_TILE_ICON = 0xFFB07CFF;
	private static final int B_MUTED = 0xFFA99DCB;
	private static final int B_DIM = 0xFF6E6492;
	private static final int B_FIELD = 0xE61A1030;
	private static final int B_FIELD_FOCUS = 0xF0241546;
	private static final int B_FIELD_BORDER = 0xFF3A2A66;
	private static final int B_PILL = 0xFF1B1230;
	private static final int B_PILL_BORDER = 0xFF3A2A66;
	private static final int B_TOGGLE_OFF = 0xFF3A3450;
	private static final int B_TOGGLE_OFF_BOTTOM = 0xFF2E2940;
	private static final int B_DIVIDER = 0x40A06BFF;
	private static final int B_HOVER_FILL = 0x30A06BFF;

	public static int WIN_TOP = B_WIN_TOP;
	public static int WIN_BOTTOM = B_WIN_BOTTOM;
	public static int HUD_TOP = B_HUD_TOP;
	public static int HUD_BOTTOM = B_HUD_BOTTOM;
	public static int NEON = B_NEON;
	public static int NEON_LINE = B_NEON_LINE;
	public static int ACCENT_A = B_ACCENT_A;
	public static int ACCENT_B = B_ACCENT_B;
	public static int ACCENT_TEXT = B_ACCENT_TEXT;
	public static int TAB_TOP = B_TAB_TOP;
	public static int TAB_BOTTOM = B_TAB_BOTTOM;
	public static int CARD = B_CARD;
	public static int CARD_HOVER = B_CARD_HOVER;
	public static int CARD_BORDER = B_CARD_BORDER;
	public static int CARD_BORDER_ON = B_CARD_BORDER_ON;
	public static int CARD_BORDER_HOT = B_CARD_BORDER_HOT;
	public static int TILE = B_TILE;
	public static int TILE_ICON = B_TILE_ICON;
	public static final int TEXT = 0xFFFFFFFF;
	public static int MUTED = B_MUTED;
	public static int DIM = B_DIM;
	public static int FIELD = B_FIELD;
	public static int FIELD_FOCUS = B_FIELD_FOCUS;
	public static int FIELD_BORDER = B_FIELD_BORDER;
	public static int PILL = B_PILL;
	public static int PILL_BORDER = B_PILL_BORDER;
	public static int TOGGLE_OFF = B_TOGGLE_OFF;
	public static int TOGGLE_OFF_BOTTOM = B_TOGGLE_OFF_BOTTOM;
	public static final int KNOB = 0xFFFFFFFF;
	public static int DIVIDER = B_DIVIDER;
	public static int HOVER_FILL = B_HOVER_FILL;

	/** Сдвигает любой «фиолетовый» цвет в текущую палитру (для цветов, зашитых в коде). Альфа сохраняется. */
	public static int shift(int argb) {
		return shift(argb, hueDelta, satScale);
	}

	/** Цвет точки-превью темы в футере меню. */
	public static int preview(EchoPalette p) {
		return p.goldAccent ? 0xFFFFA500 : shift(B_ACCENT_B, p.hueDelta, p.saturation);
	}

	private static int shift(int argb, int hueDelta, float satScale) {
		if (hueDelta == 0 && satScale == 1f) {
			return argb;
		}
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d < 1e-5f) {
			h = 0f;
		} else if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h = (h * 60f + hueDelta) % 360f;
		if (h < 0f) {
			h += 360f;
		}
		float s = max <= 0f ? 0f : d / max;
		s = Math.max(0f, Math.min(1f, s * satScale));
		// HSV -> RGB
		float c = max * s;
		float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
		float m = max - c;
		float rr;
		float gg;
		float bb;
		if (h < 60f) {
			rr = c; gg = x; bb = 0;
		} else if (h < 120f) {
			rr = x; gg = c; bb = 0;
		} else if (h < 180f) {
			rr = 0; gg = c; bb = x;
		} else if (h < 240f) {
			rr = 0; gg = x; bb = c;
		} else if (h < 300f) {
			rr = x; gg = 0; bb = c;
		} else {
			rr = c; gg = 0; bb = x;
		}
		int ri = Math.round((rr + m) * 255f);
		int gi = Math.round((gg + m) * 255f);
		int bi = Math.round((bb + m) * 255f);
		return (argb & 0xFF000000) | (ri << 16) | (gi << 8) | bi;
	}

	/** Применяет палитру: пересчитывает все поля от базовых цветов. */
	public static void apply(EchoPalette p) {
		applied = p;
		hueDelta = p.hueDelta;
		satScale = p.saturation;
		WIN_TOP = shift(B_WIN_TOP);
		WIN_BOTTOM = shift(B_WIN_BOTTOM);
		HUD_TOP = shift(B_HUD_TOP);
		HUD_BOTTOM = shift(B_HUD_BOTTOM);
		NEON = shift(B_NEON);
		NEON_LINE = shift(B_NEON_LINE);
		ACCENT_A = shift(B_ACCENT_A);
		ACCENT_B = shift(B_ACCENT_B);
		ACCENT_TEXT = shift(B_ACCENT_TEXT);
		TAB_TOP = shift(B_TAB_TOP);
		TAB_BOTTOM = shift(B_TAB_BOTTOM);
		CARD = shift(B_CARD);
		CARD_HOVER = shift(B_CARD_HOVER);
		CARD_BORDER = shift(B_CARD_BORDER);
		CARD_BORDER_ON = shift(B_CARD_BORDER_ON);
		CARD_BORDER_HOT = shift(B_CARD_BORDER_HOT);
		TILE = shift(B_TILE);
		TILE_ICON = shift(B_TILE_ICON);
		MUTED = shift(B_MUTED);
		DIM = shift(B_DIM);
		FIELD = shift(B_FIELD);
		FIELD_FOCUS = shift(B_FIELD_FOCUS);
		FIELD_BORDER = shift(B_FIELD_BORDER);
		PILL = shift(B_PILL);
		PILL_BORDER = shift(B_PILL_BORDER);
		TOGGLE_OFF = shift(B_TOGGLE_OFF);
		TOGGLE_OFF_BOTTOM = shift(B_TOGGLE_OFF_BOTTOM);
		DIVIDER = shift(B_DIVIDER);
		HOVER_FILL = shift(B_HOVER_FILL);
		if (p.goldAccent) {
			applyGold();
		}
	}

	/** Echo Gold: фон остаётся тёмно-фиолетовым, все «акцентные» поля переписываются золотисто-оранжевыми. */
	private static void applyGold() {
		WIN_TOP = 0xF2150B26;
		WIN_BOTTOM = 0xF2120A1F;
		NEON = 0xFFFFA500;
		NEON_LINE = 0xFFE8920A;
		ACCENT_A = 0xFFFF8C00;
		ACCENT_B = 0xFFFFC233;
		ACCENT_TEXT = 0xFFFFB347;
		TAB_TOP = 0xE0E07A00;
		TAB_BOTTOM = 0xE0FFA51F;
		CARD_BORDER_ON = 0xFF5B3B22;
		CARD_BORDER_HOT = 0xFFFFB347;
		TILE_ICON = 0xFFFFB347;
		DIVIDER = 0x40FFA500;
		HOVER_FILL = 0x30FFA500;
		// поверхности в духе «стекла»: полупрозрачные белые карточки поверх тёмно-фиолетового окна
		CARD = 0x0DFFFFFF;
		CARD_HOVER = 0x1AFFFFFF;
		CARD_BORDER = 0x16FFFFFF;
		TILE = 0x14FFFFFF;
		PILL = 0x14FFFFFF;
		PILL_BORDER = 0x1CFFFFFF;
		FIELD = 0x10FFFFFF;
		FIELD_FOCUS = 0x1AFFFFFF;
		FIELD_BORDER = 0x18FFFFFF;
		TOGGLE_OFF = 0xFF2A2536;
		TOGGLE_OFF_BOTTOM = 0xFF221E2D;
		MUTED = 0xFFB3AAC8;
		DIM = 0xFF6F6785;
	}

	/** Подхватывает палитру из конфига, если она изменилась (вызывается из рендера меню/HUD; при совпадении почти бесплатно). */
	public static void sync() {
		EchoPalette want = EchoPalette.byName(PvpConfig.get().gui.palette);
		if (want != applied) {
			apply(want);
		}
	}

	public static EchoPalette current() {
		return applied == null ? EchoPalette.PURPLE : applied;
	}
}
