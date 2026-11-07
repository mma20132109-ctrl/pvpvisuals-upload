package com.pvpvisuals.gui.echo;

/** Accent presets used by the Echo GUI. Surfaces stay neutral; only interactive accents change. */
public enum EchoPalette {
	PERIWINKLE("Periwinkle", 0xFF8C9EFF, 0xFFB69CFF),
	ROSE("Rose", 0xFFE5737F, 0xFFF29E7C),
	VIOLET("Violet", 0xFF8A72F2, 0xFFCB7CF4),
	MINT("Mint", 0xFF5DD39E, 0xFF5BC0E8),
	SILVER("Silver", 0xFFC2C6D0, 0xFF8D93A3);

	public final String title;
	public final int primary;
	public final int secondary;

	EchoPalette(String title, int primary, int secondary) {
		this.title = title;
		this.primary = primary;
		this.secondary = secondary;
	}

	public static EchoPalette byName(String name) {
		if (name != null) {
			for (EchoPalette palette : values()) {
				if (palette.name().equalsIgnoreCase(name.trim())) {
					return palette;
				}
			}
		}
		return PERIWINKLE;
	}
}
