package com.pvpvisuals.render.blockoverlay;

/** Стили Block Overlay. {@code shader} — имя фрагментного шейдера в assets/pvpvisuals/shaders/core/ (без расширения). */
public enum BlockOverlayStyle {
	PULSE("Pulse", "block_overlay_pulse", false, false),
	GRID("Grid", "block_overlay_grid", false, false),
	SCANLINE("Scanline", "block_overlay_scanline", false, false),
	GLOW("Glow", "block_overlay_glow", false, false),
	SOLID("Solid", "block_overlay_solid", false, false),
	VOID("Void", "block_overlay_void", true, false),
	NEBULA("Nebula", "block_overlay_nebula", true, false),
	STARFIELD("Starfield", "block_overlay_starfield", true, true),
	GALAXY("Galaxy", "block_overlay_galaxy", true, true);

	public final String title;
	public final String shader;
	/** Космический стиль: показывает второй цвет и качество. */
	public final boolean cosmic;
	/** Рисует звёзды: показывает плотность звёзд. */
	public final boolean stars;

	BlockOverlayStyle(String title, String shader, boolean cosmic, boolean stars) {
		this.title = title;
		this.shader = shader;
		this.cosmic = cosmic;
		this.stars = stars;
	}

	public static BlockOverlayStyle byName(String name) {
		if (name != null) {
			for (BlockOverlayStyle t : values()) {
				if (t.name().equalsIgnoreCase(name.trim())) {
					return t;
				}
			}
		}
		return PULSE;
	}
}
