package com.pvpvisuals.crosshair;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.pvpvisuals.config.CrosshairSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Custom Crosshair. Ванильный прицел подменяется штатным API Fabric: HudElementRegistry#replaceElement для
 * VanillaHudElements.CROSSHAIR. Если модуль выключен, вызывается исходный элемент — ванильный прицел работает как раньше.
 */
public final class CrosshairModule {
	private static final Category CAT = Category.VISUAL;
	private static final CrosshairRenderer RENDERER = new CrosshairRenderer();

	private CrosshairModule() { }

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, previous -> (graphics, tracker) -> {
			CrosshairSettings s = PvpConfig.get().crosshair;
			if (s.enabled) {
				RENDERER.render(graphics, s);
			} else {
				previous.render(graphics, tracker);
			}
		});
	}

	/** Вызывается при ударе по сущности (из CombatEvents). */
	public static void onHit() {
		if (PvpConfig.get().crosshair.enabled) {
			RENDERER.onHit();
		}
	}

	// ---------- GUI ----------

	private static CrosshairSettings cs() {
		return PvpConfig.get().crosshair;
	}

	private static ModuleDef channel(String name, IntSupplier get, IntConsumer set, int shift) {
		return ModuleDef.slider(name, CAT, 0, 255, 0, () -> (get.getAsInt() >> shift) & 0xFF, v -> {
			int c = get.getAsInt() & ~(0xFF << shift);
			set.accept(c | (((int) v & 0xFF) << shift));
		});
	}

	private static ModuleDef rgb(String title, IntSupplier get, IntConsumer set) {
		return ModuleDef.group(CAT, () -> title)
				.child(channel("Red", get, set, 16))
				.child(channel("Green", get, set, 8))
				.child(channel("Blue", get, set, 0));
	}

	public static ModuleDef def(PvpConfig pc) {
		return ModuleDef.toggle("Custom Crosshair", CAT, () -> cs().enabled, v -> cs().enabled = v)
				.child(ModuleDef.cycle(CAT, () -> "Style: " + CrosshairStyle.byName(cs().style).title,
						() -> cs().style = CrosshairStyle.byName(cs().style).next().name()))
				.child(ModuleDef.cycle(CAT, () -> "Color: " + CrosshairColorMode.byName(cs().colorMode).title,
						() -> cs().colorMode = CrosshairColorMode.byName(cs().colorMode).next().name()))
				.child(rgb("Primary Color", () -> cs().colorPrimary, v -> cs().colorPrimary = v))
				.child(rgb("Secondary Color", () -> cs().colorSecondary, v -> cs().colorSecondary = v))
				.child(ModuleDef.slider("Alpha", CAT, 0.05, 1.0, 2, () -> cs().alpha, v -> cs().alpha = (float) v))
				.child(ModuleDef.slider("Thickness", CAT, 1, 5, 0, () -> cs().thickness, v -> cs().thickness = (int) v))
				.child(ModuleDef.slider("Length", CAT, 2, 15, 0, () -> cs().length, v -> cs().length = (int) v))
				.child(ModuleDef.slider("Gap", CAT, 0, 10, 0, () -> cs().gap, v -> cs().gap = (int) v))
				.child(ModuleDef.slider("Dot Size", CAT, 0, 5, 0, () -> cs().dotSize, v -> cs().dotSize = (int) v))
				.child(ModuleDef.slider("Circle Radius", CAT, 3, 15, 0,
						() -> cs().circleRadius, v -> cs().circleRadius = (int) v))
				.child(ModuleDef.group(CAT, () -> "Outline")
						.child(ModuleDef.toggle("Enabled", CAT, () -> cs().outline, v -> cs().outline = v))
						.child(rgb("Outline Color", () -> cs().outlineColor, v -> cs().outlineColor = v))
						.child(ModuleDef.slider("Outline Thickness", CAT, 0, 2, 0,
								() -> cs().outlineThickness, v -> cs().outlineThickness = (int) v)))
				.child(ModuleDef.group(CAT, () -> "Hit Animation")
						.child(ModuleDef.cycle(CAT, () -> "Animation: " + CrosshairAnimation.byName(cs().animation).title,
								() -> cs().animation = CrosshairAnimation.byName(cs().animation).next().name()))
						.child(ModuleDef.slider("Duration (ms)", CAT, 100, 500, 0,
								() -> cs().animationDuration, v -> cs().animationDuration = (int) v))
						.child(rgb("Flash Color", () -> cs().flashColor, v -> cs().flashColor = v)))
				.child(ModuleDef.group(CAT, () -> "Dynamic Spread")
						.child(ModuleDef.toggle("Enabled", CAT, () -> cs().dynamicSpread, v -> cs().dynamicSpread = v))
						.child(ModuleDef.slider("Multiplier", CAT, 0.5, 3.0, 1,
								() -> cs().spreadMultiplier, v -> cs().spreadMultiplier = (float) v)))
				.child(ModuleDef.toggle("Hide In GUI", CAT, () -> cs().hideInGui, v -> cs().hideInGui = v))
				.child(ModuleDef.toggle("Hide In 3rd Person", CAT,
						() -> cs().hideInThirdPerson, v -> cs().hideInThirdPerson = v));
	}
}
