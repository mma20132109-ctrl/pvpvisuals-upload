package com.pvpvisuals.render.sky;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.ShaderSkySettings;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.loader.api.FabricLoader;

/** Shader Sky: условие включения и описание модуля для меню (World). Рендер — {@link SkyRenderer}. */
public final class ShaderSkyModule {
	private static final boolean SHADER_MOD = FabricLoader.getInstance().isModLoaded("iris")
			|| FabricLoader.getInstance().isModLoaded("optifabric");

	private static boolean loggedBlocked;

	private ShaderSkyModule() { }

	/**
	 * Диагностика при старте: печатает в лог реальные методы SkyRenderer и поля SkyRenderState этой версии игры.
	 * По ним видно, совпадают ли сигнатуры в SkyRendererMixin.
	 */
	public static void register() {
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("pvpvisuals/sky");
		log.info("Shader Sky: module loaded (build 2), enabled={}", PvpConfig.get().shaderSky.enabled);
		for (String name : new String[] {"net.minecraft.client.renderer.SkyRenderer",
				"net.minecraft.client.renderer.state.SkyRenderState"}) {
			try {
				Class<?> c = Class.forName(name);
				for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
					log.info("Shader Sky: {}#{}{}", c.getSimpleName(), m.getName(),
							java.util.Arrays.toString(m.getParameterTypes()));
				}
				for (java.lang.reflect.Field f : c.getDeclaredFields()) {
					log.info("Shader Sky: {}.{} : {}", c.getSimpleName(), f.getName(), f.getType().getSimpleName());
				}
			} catch (Throwable e) {
				log.warn("Shader Sky: class {} not found: {}", name, e.toString());
			}
		}
	}

	/** Нужно ли подменять небо прямо сейчас (модуль включён, нет Iris/OptiFine, рендер не упал). */
	public static boolean active() {
		boolean on = PvpConfig.get().shaderSky.enabled;
		if (on && !loggedBlocked && (SHADER_MOD || SkyRenderer.isFailed())) {
			loggedBlocked = true;
			org.slf4j.LoggerFactory.getLogger("pvpvisuals/sky").warn("Shader Sky is on but blocked: shaderMod={} failed={}", SHADER_MOD, SkyRenderer.isFailed());
		}
		return on && !SHADER_MOD && !SkyRenderer.isFailed();
	}

	/** Прятать ванильные облака (только при включённом Shader Sky). */
	public static boolean hideClouds() {
		return active() && PvpConfig.get().shaderSky.hideClouds;
	}

	/** Прятать ванильные солнце и луну: при блокировке времени суток они бы противоречили небу. */
	public static boolean hideSunMoon() {
		return active() && PvpConfig.get().shaderSky.timeOfDayLock;
	}

	public static ModuleDef def(PvpConfig pc) {
		ShaderSkySettings s = pc.shaderSky;
		ModuleDef dropdown = ModuleDef.group(Category.WORLD, () -> "Shader: " + ShaderType.byName(s.shaderType).title);
		for (ShaderType t : ShaderType.values()) {
			dropdown.child(ModuleDef.toggle(t.title, Category.WORLD,
					() -> ShaderType.byName(s.shaderType) == t,
					v -> {
						if (v) {
							s.shaderType = t.name();
						}
					}));
		}
		return ModuleDef.toggle("Shader Sky", Category.WORLD, () -> s.enabled, v -> {
			s.enabled = v;
			if (v) {
				SkyRenderer.reset();
			}
		})
				.child(dropdown)
				.child(ModuleDef.slider("Brightness", Category.WORLD, 0.5, 2.0, 2,
						() -> s.brightness, v -> s.brightness = (float) v))
				.child(ModuleDef.slider("Speed", Category.WORLD, 0.0, 2.0, 2,
						() -> s.speed, v -> s.speed = (float) v))
				.child(ModuleDef.toggle("Time Of Day Lock", Category.WORLD,
						() -> s.timeOfDayLock, v -> s.timeOfDayLock = v))
				.child(ModuleDef.toggle("Hide Clouds", Category.WORLD,
						() -> s.hideClouds, v -> s.hideClouds = v))
				.child(ModuleDef.toggle("Custom Color", Category.WORLD,
						() -> s.customColor, v -> s.customColor = v))
				.child(ModuleDef.slider("Hue", Category.WORLD, 0, 360, 0,
						() -> s.colorHue, v -> s.colorHue = (int) v))
				.child(ModuleDef.slider("Saturation", Category.WORLD, 0.0, 2.0, 2,
						() -> s.colorSaturation, v -> s.colorSaturation = (float) v));
	}
}
