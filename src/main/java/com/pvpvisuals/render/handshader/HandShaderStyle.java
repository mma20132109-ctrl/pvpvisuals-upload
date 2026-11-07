package com.pvpvisuals.render.handshader;

/** Стили Hand Shader: заголовок для GUI и имя фрагментного шейдера (core/<shader>.fsh). */
public enum HandShaderStyle {
	PULSE("Pulse", "hand_pulse"),
	VOID("Void", "hand_void"),
	NEBULA("Nebula", "hand_nebula"),
	STARFIELD("Starfield", "hand_starfield");

	public final String title;
	public final String shader;

	HandShaderStyle(String title, String shader) {
		this.title = title;
		this.shader = shader;
	}

	public static HandShaderStyle byName(String name) {
		for (HandShaderStyle s : values()) {
			if (s.name().equalsIgnoreCase(name)) {
				return s;
			}
		}
		return PULSE;
	}
}
