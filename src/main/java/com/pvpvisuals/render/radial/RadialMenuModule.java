package com.pvpvisuals.render.radial;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.RadialMenuSettings;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Radial Menu (быстрые сообщения). Клавиша «Radial Menu» (по умолчанию R) создаётся в {@code KeybindManager};
 * пока она зажата, открыт {@link RadialMenuScreen}, при отпускании фраза выбранного сектора уходит в чат.
 */
public final class RadialMenuModule {
	private RadialMenuModule() { }

	/** Раз за тик из KeybindManager: key — клавиша Radial Menu. */
	public static void keyTick(Minecraft mc, KeyMapping key) {
		if (key == null) {
			return;
		}
		RadialMenuSettings s = PvpConfig.get().radial;
		boolean ready = s.enabled && mc.screen == null && mc.player != null && mc.level != null
				&& mc.getConnection() != null;
		if (ready && key.isDown()) {
			mc.setScreen(new RadialMenuScreen(key));
		}
		while (key.consumeClick()) {
			// очередь кликов не копим
		}
	}

	public static ModuleDef def(PvpConfig pc) {
		RadialMenuSettings s = pc.radial;
		ModuleDef d = ModuleDef.toggle("Radial Menu", Category.UTILS, () -> s.enabled, v -> s.enabled = v);
		for (int i = 0; i < RadialMenuSettings.SECTORS; i++) {
			final int idx = i;
			d.child(ModuleDef.text("Phrase " + (i + 1), Category.UTILS, () -> s.phrases[idx], v -> s.phrases[idx] = v));
		}
		return d
				.child(ModuleDef.slider("Menu Radius", Category.UTILS, 80, 200, 0,
						() -> s.menuRadius, v -> s.menuRadius = (int) v))
				.child(ModuleDef.slider("Dead Zone", Category.UTILS, 10, 60, 0,
						() -> s.deadZoneRadius, v -> s.deadZoneRadius = (int) v))
				.child(ModuleDef.slider("BG R", Category.UTILS, 0, 255, 0, () -> (s.backgroundColor >> 16) & 0xFF,
						v -> s.backgroundColor = (s.backgroundColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("BG G", Category.UTILS, 0, 255, 0, () -> (s.backgroundColor >> 8) & 0xFF,
						v -> s.backgroundColor = (s.backgroundColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("BG B", Category.UTILS, 0, 255, 0, () -> s.backgroundColor & 0xFF,
						v -> s.backgroundColor = (s.backgroundColor & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("BG Alpha", Category.UTILS, 0.1, 1.0, 2,
						() -> s.backgroundAlpha, v -> s.backgroundAlpha = (float) v))
				.child(ModuleDef.slider("Highlight R", Category.UTILS, 0, 255, 0, () -> (s.highlightColor >> 16) & 0xFF,
						v -> s.highlightColor = (s.highlightColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("Highlight G", Category.UTILS, 0, 255, 0, () -> (s.highlightColor >> 8) & 0xFF,
						v -> s.highlightColor = (s.highlightColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("Highlight B", Category.UTILS, 0, 255, 0, () -> s.highlightColor & 0xFF,
						v -> s.highlightColor = (s.highlightColor & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Highlight Alpha", Category.UTILS, 0.1, 1.0, 2,
						() -> s.highlightAlpha, v -> s.highlightAlpha = (float) v))
				.child(ModuleDef.slider("Text R", Category.UTILS, 0, 255, 0, () -> (s.textColor >> 16) & 0xFF,
						v -> s.textColor = (s.textColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("Text G", Category.UTILS, 0, 255, 0, () -> (s.textColor >> 8) & 0xFF,
						v -> s.textColor = (s.textColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("Text B", Category.UTILS, 0, 255, 0, () -> s.textColor & 0xFF,
						v -> s.textColor = (s.textColor & 0xFFFF00) | (int) v));
	}
}
