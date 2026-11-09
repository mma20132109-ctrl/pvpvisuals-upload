package com.pvpvisuals.gui.echo;

import com.pvpvisuals.config.PvpConfig;

/**
 * Neutral Liquid Glass surfaces with a selectable Svintus-style accent.
 * All colors are ARGB and fields remain mutable for compatibility with existing GUI/HUD renderers.
 */
public final class EchoTheme {
	private EchoTheme() { }

	private static EchoPalette applied;
	private static final int SURFACE_TOP = 0xC32A4858;
	private static final int SURFACE_BOTTOM = 0xC10D202C;
	private static final int CARD_BASE = 0x78304F60;
	private static final int CARD_HOVER_BASE = 0xA63D7188;
	private static final int BORDER_BASE = 0x70D7F4FF;
	private static final int TEXT_BASE = 0xFFF0FBFF;
	private static final int MUTED_BASE = 0xFFAAC6D2;
	private static final int DIM_BASE = 0xFF7896A4;

	public static int WIN_TOP = SURFACE_TOP;
	public static int WIN_BOTTOM = SURFACE_BOTTOM;
	public static int HUD_TOP = 0xB82C5364;
	public static int HUD_BOTTOM = 0xA8112633;
	public static int NEON = 0xFF58D8F3;
	public static int NEON_LINE = 0xFFD5F7FF;
	public static int ACCENT_A = 0xFF58D8F3;
	public static int ACCENT_B = 0xFF719DFF;
	public static int ACCENT_TEXT = 0xFFD1F5FF;
	public static int TAB_TOP = 0xCC58D8F3;
	public static int TAB_BOTTOM = 0xAA719DFF;
	public static int CARD = CARD_BASE;
	public static int CARD_HOVER = CARD_HOVER_BASE;
	public static int CARD_BORDER = BORDER_BASE;
	public static int CARD_BORDER_ON = 0xB8A5EEFF;
	public static int CARD_BORDER_HOT = 0xFF58D8F3;
	public static int TILE = 0x333F9BAF;
	public static int TILE_ICON = 0xFFD1F5FF;
	public static final int TEXT = TEXT_BASE;
	public static int MUTED = MUTED_BASE;
	public static int DIM = DIM_BASE;
	public static int FIELD = 0xAA222630;
	public static int FIELD_FOCUS = 0xCC2A303B;
	public static int FIELD_BORDER = 0x665E6574;
	public static int PILL = 0x77272C36;
	public static int PILL_BORDER = 0x665E6574;
	public static int TOGGLE_OFF = 0xFF414653;
	public static int TOGGLE_OFF_BOTTOM = 0xFF303541;
	public static final int KNOB = 0xFFFFFFFF;
	public static int DIVIDER = 0x338C9EFF;
	public static int HOVER_FILL = 0x228C9EFF;

	public static int shift(int argb) {
		return argb;
	}

	public static int preview(EchoPalette palette) {
		return palette.primary;
	}

	public static void apply(EchoPalette palette) {
		applied = palette;
		int primary = palette.primary;
		int secondary = palette.secondary;
		WIN_TOP = SURFACE_TOP;
		WIN_BOTTOM = SURFACE_BOTTOM;
		HUD_TOP = 0xB82C5364;
		HUD_BOTTOM = 0xA8112633;
		NEON = primary;
		NEON_LINE = withAlpha(primary, 0.92f);
		ACCENT_A = primary;
		ACCENT_B = secondary;
		ACCENT_TEXT = mix(primary, 0xFFFFFFFF, 0.30f);
		TAB_TOP = withAlpha(primary, 0.78f);
		TAB_BOTTOM = withAlpha(secondary, 0.58f);
		CARD = CARD_BASE;
		CARD_HOVER = CARD_HOVER_BASE;
		CARD_BORDER = BORDER_BASE;
		CARD_BORDER_ON = withAlpha(primary, 0.76f);
		CARD_BORDER_HOT = primary;
		TILE = 0x332F3542;
		TILE_ICON = mix(primary, 0xFFFFFFFF, 0.22f);
		MUTED = MUTED_BASE;
		DIM = DIM_BASE;
		FIELD = 0x8E29495A;
		FIELD_FOCUS = 0xB5356678;
		FIELD_BORDER = 0x7899DCEB;
		PILL = 0x79294759;
		PILL_BORDER = 0x668AC9DD;
		TOGGLE_OFF = 0xFF414653;
		TOGGLE_OFF_BOTTOM = 0xFF303541;
		DIVIDER = withAlpha(primary, 0.34f);
		HOVER_FILL = withAlpha(primary, 0.20f);
	}

	public static void sync() {
		EchoPalette wanted = EchoPalette.byName(PvpConfig.get().gui.palette);
		if (wanted != applied) {
			apply(wanted);
		}
	}

	public static EchoPalette current() {
		return applied == null ? EchoPalette.PERIWINKLE : applied;
	}

	private static int withAlpha(int color, float alpha) {
		return (Math.max(0, Math.min(255, Math.round(alpha * 255f))) << 24) | (color & 0x00FFFFFF);
	}

	private static int mix(int a, int b, float amount) {
		float t = Math.max(0f, Math.min(1f, amount));
		int aa = (a >>> 24) + Math.round(((b >>> 24) - (a >>> 24)) * t);
		int r = ((a >> 16) & 255) + Math.round((((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = ((a >> 8) & 255) + Math.round((((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (a & 255) + Math.round(((b & 255) - (a & 255)) * t);
		return (aa << 24) | (r << 16) | (g << 8) | bl;
	}
}
