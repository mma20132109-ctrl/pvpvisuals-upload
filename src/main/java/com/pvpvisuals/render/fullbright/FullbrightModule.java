package com.pvpvisuals.render.fullbright;

import com.pvpvisuals.config.FullbrightSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

/** Fullbright: описание модуля для меню (Visual). Логика — {@link FullbrightController}. */
public final class FullbrightModule {
	private FullbrightModule() { }

	public static void register() {
		FullbrightController.register();
	}

	public static ModuleDef def(PvpConfig pc) {
		FullbrightSettings f = pc.fullbright;
		return ModuleDef.toggle("Fullbright", Category.VISUAL, () -> f.enabled, v -> f.enabled = v)
				.child(ModuleDef.slider("Brightness", Category.VISUAL, 1.0, 15.0, 1,
						() -> f.brightness, v -> f.brightness = (float) v))
				.child(ModuleDef.toggle("Smooth", Category.VISUAL, () -> f.smoothTransition, v -> f.smoothTransition = v))
				.child(ModuleDef.slider("Transition", Category.VISUAL, 1, 60, 0,
						() -> f.transitionSpeed, v -> f.transitionSpeed = (int) v));
	}
}
