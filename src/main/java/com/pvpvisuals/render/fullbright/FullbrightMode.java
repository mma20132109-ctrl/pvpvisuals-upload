package com.pvpvisuals.render.fullbright;

/**
 * Режим Fullbright. LIGHTMAP (правка lightmap через mixin) не реализован: точка впрыска в
 * LightTexture.update для 1.21.11 не проверена, поэтому поддерживается только GAMMA.
 */
public enum FullbrightMode {
	GAMMA;

	public static FullbrightMode byName(String n) {
		return GAMMA;
	}
}
