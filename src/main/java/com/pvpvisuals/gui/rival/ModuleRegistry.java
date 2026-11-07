package com.pvpvisuals.gui.rival;

import java.util.ArrayList;
import java.util.List;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.cape.CapeModule;
import com.pvpvisuals.hud.HudManager;
import com.pvpvisuals.hud.HudModuleUi;
import com.pvpvisuals.hud.inventory.InventoryHudModule;
import com.pvpvisuals.keybind.KeybindManager;
import com.pvpvisuals.render.LogoSizeMode;
import com.pvpvisuals.render.fullbright.FullbrightModule;
import com.pvpvisuals.render.kill.KillEffectModule;
import com.pvpvisuals.render.hat.ChinaHatModule;
import com.pvpvisuals.render.particle.BlockHitParticleModule;
import com.pvpvisuals.render.particle.HitParticleModule;
import com.pvpvisuals.render.trajectory.TrajectoryModule;
import com.pvpvisuals.render.world.WorldParticlesModule;

/**
 * Список модулей для GUI. Состояние каждого модуля лежит в PvpConfig (JSON).
 * Чтобы добавить модуль: поле в PvpConfig + одна строка здесь.
 */
public final class ModuleRegistry {
	private ModuleRegistry() { }

	private static List<ModuleDef> modules() {
		PvpConfig c = PvpConfig.get();
		return List.of(
				ChinaHatModule.def(c),
				com.pvpvisuals.render.blockoverlay.BlockOverlayModule.def(c),
				com.pvpvisuals.render.hitbox.HitBoxModule.def(c),
				com.pvpvisuals.render.viewmodel.ViewModelModule.def(c),
				com.pvpvisuals.render.handshader.HandShaderModule.def(c),
				com.pvpvisuals.render.radial.RadialMenuModule.def(c),
				ModuleDef.toggle("Crit Effect", Category.VISUAL, () -> c.critEffect, v -> c.critEffect = v),
				FullbrightModule.def(c),
				com.pvpvisuals.crosshair.CrosshairModule.def(c),
				TrajectoryModule.def(c),
				ModuleDef.toggle("Damage Numbers", Category.VISUAL, () -> c.damageNumbers, v -> c.damageNumbers = v),

				BlockHitParticleModule.INSTANCE.def(),

				HitParticleModule.INSTANCE.def(),
				WorldParticlesModule.def(c),
				com.pvpvisuals.render.sky.ShaderSkyModule.def(c),
				com.pvpvisuals.render.freelook.FreelookModule.def(c),
				KillEffectModule.def(c),

				ModuleDef.toggle("Target HUD", Category.UTILS, () -> c.targetHud, v -> c.targetHud = v)
						.child(HudModuleUi.layout(HudManager.TARGET, Category.UTILS))
						.child(ModuleDef.toggle("Items and Armor", Category.UTILS,
								() -> c.targetHudItems, v -> c.targetHudItems = v))
						.child(ModuleDef.toggle("Distance", Category.UTILS,
								() -> c.targetHudDistance, v -> c.targetHudDistance = v)),
				ModuleDef.toggle("Armor HUD", Category.UTILS, () -> c.armorHud, v -> c.armorHud = v)
						.child(HudModuleUi.layout(HudManager.ARMOR, Category.UTILS))
						.child(ModuleDef.toggle("Hand Items", Category.UTILS,
								() -> c.armorHudHands, v -> c.armorHudHands = v))
						.child(ModuleDef.toggle("Durability in %", Category.UTILS,
								() -> c.armorHudPercent, v -> c.armorHudPercent = v)),
				ModuleDef.toggle("Combo Counter", Category.UTILS, () -> c.comboCounter, v -> c.comboCounter = v)
						.child(HudModuleUi.layout(HudManager.COMBO, Category.UTILS)),
				ModuleDef.toggle("Watermark", Category.UTILS, () -> c.watermark, v -> c.watermark = v)
						.child(HudModuleUi.layout(HudManager.WATERMARK, Category.UTILS))
						.child(ModuleDef.toggle("Nickname", Category.UTILS,
								() -> c.watermarkShowNick, v -> c.watermarkShowNick = v))
						.child(ModuleDef.toggle("FPS", Category.UTILS,
								() -> c.watermarkShowFps, v -> c.watermarkShowFps = v))
						.child(ModuleDef.toggle("Ping", Category.UTILS,
								() -> c.watermarkShowPing, v -> c.watermarkShowPing = v))
						.child(ModuleDef.toggle("Time", Category.UTILS,
								() -> c.watermarkShowTime, v -> c.watermarkShowTime = v)),
				ModuleDef.toggle("Coordinates", Category.UTILS, () -> c.coordinatesHud, v -> c.coordinatesHud = v)
						.child(HudModuleUi.layout(HudManager.COORDINATES, Category.UTILS))
						.child(ModuleDef.toggle("Direction", Category.UTILS, () -> c.coordinatesDirection, v -> c.coordinatesDirection = v))
						.child(ModuleDef.toggle("Nether / Overworld", Category.UTILS, () -> c.coordinatesNether, v -> c.coordinatesNether = v)),
				ModuleDef.toggle("Module List", Category.UTILS, () -> c.moduleListHud, v -> c.moduleListHud = v)
						.child(HudModuleUi.layout(HudManager.MODULE_LIST, Category.UTILS))
						.child(ModuleDef.toggle("Glow", Category.UTILS, () -> c.moduleListGlow, v -> c.moduleListGlow = v)),
				InventoryHudModule.def(c),
				com.pvpvisuals.hud.binds.HudBindsModule.def(c),
				ModuleDef.toggle("Notifications", Category.UTILS,
						() -> c.binds.showNotifications, v -> c.binds.showNotifications = v)
						.child(HudModuleUi.layout(HudManager.NOTIFICATIONS, Category.UTILS))
						.child(ModuleDef.slider("Duration (ms)", Category.UTILS, 500, 5000, 0,
								() -> c.binds.durationMs, v -> c.binds.durationMs = (int) v)),

				themeGroup(c),
				ModuleDef.group(Category.CLIENT, () -> "Main Menu")
						.child(ModuleDef.toggle("Custom Menu", Category.CLIENT,
								() -> c.menu.customMainMenu, v -> c.menu.customMainMenu = v))
						.child(ModuleDef.toggle("Menu Particles", Category.CLIENT,
								() -> c.menu.showMenuParticles, v -> c.menu.showMenuParticles = v))
						.child(ModuleDef.toggle("Version Text", Category.CLIENT,
								() -> c.menu.showVersionText, v -> c.menu.showVersionText = v))
						.child(ModuleDef.toggle("World Screens Style", Category.CLIENT,
								() -> c.menu.styleWorldMenus, v -> c.menu.styleWorldMenus = v)),
				ModuleDef.group(Category.CLIENT, () -> "Loading Screen")
						.child(ModuleDef.toggle("Custom Splash", Category.CLIENT,
								() -> c.splash.customSplashScreen, v -> c.splash.customSplashScreen = v))
						.child(ModuleDef.toggle("Splash Particles", Category.CLIENT,
								() -> c.splash.showSplashParticles, v -> c.splash.showSplashParticles = v))
						.child(ModuleDef.choice("Background", Category.CLIENT,
								() -> splashBackgroundTitle(c.splash.splashBackground),
								List.of("Gradient", "Animated", "Dark"),
								v -> c.splash.splashBackground = "Gradient".equals(v) ? "GRADIENT" : "Animated".equals(v) ? "SHADER" : "DARK")),
				CapeModule.def(c),
				ModuleDef.group(Category.CLIENT, () -> "Logo")
						.child(ModuleDef.toggle("Watermark Logo", Category.CLIENT,
								() -> c.logo.showInWatermark, v -> c.logo.showInWatermark = v))
						.child(ModuleDef.choice("Watermark Size", Category.CLIENT,
								() -> LogoSizeMode.byName(c.logo.sizeWatermark).title,
								List.of("Auto", "128", "512", "2048"),
								v -> c.logo.sizeWatermark = v.equals("Auto") ? "AUTO" : "S" + v))
						.child(ModuleDef.toggle("Menu Logo", Category.CLIENT,
								() -> c.logo.showInGui, v -> c.logo.showInGui = v))
						.child(ModuleDef.choice("Menu Size", Category.CLIENT,
								() -> LogoSizeMode.byName(c.logo.sizeGui).title,
								List.of("Auto", "128", "512", "2048"),
								v -> c.logo.sizeGui = v.equals("Auto") ? "AUTO" : "S" + v)),
				ModuleDef.group(Category.CLIENT, () -> "Menu Style")
						.child(ModuleDef.slider("Overlay", Category.CLIENT, 0.0, 0.8, 2,
								() -> c.gui.overlayAlpha, v -> c.gui.overlayAlpha = (float) v))
						.child(ModuleDef.slider("Panel Opacity", Category.CLIENT, 0.3, 1.0, 2,
								() -> c.gui.panelOpacity, v -> c.gui.panelOpacity = (float) v)),
				ModuleDef.toggle("Island", Category.CLIENT, () -> c.showIsland, v -> c.showIsland = v)
						.child(ModuleDef.toggle("Now Playing", Category.CLIENT,
								() -> c.nowPlaying.showNowPlaying, v -> c.nowPlaying.showNowPlaying = v))
						.child(ModuleDef.toggle("Track Time", Category.CLIENT,
								() -> c.nowPlaying.showTrackTime, v -> c.nowPlaying.showTrackTime = v))
						.child(ModuleDef.toggle("Track Artist", Category.CLIENT,
								() -> c.nowPlaying.showTrackArtist, v -> c.nowPlaying.showTrackArtist = v))
						.child(ModuleDef.toggle("Cover Art", Category.CLIENT,
								() -> c.nowPlaying.showCoverArt, v -> c.nowPlaying.showCoverArt = v))
						.child(ModuleDef.toggle("Hide When Paused", Category.CLIENT,
								() -> c.nowPlaying.hideWhenPaused, v -> c.nowPlaying.hideWhenPaused = v))
						.child(ModuleDef.slider("Max Track Width", Category.CLIENT, 40, 200, 0,
								() -> c.nowPlaying.maxTrackWidth, v -> c.nowPlaying.maxTrackWidth = (int) v)),
				ModuleDef.toggle("Chat HUD Edit", Category.CLIENT, () -> c.chatHudEdit, v -> c.chatHudEdit = v),
				ModuleDef.action("Reset HUD Positions", Category.CLIENT, () -> {
					c.hudPositions.clear();
					c.hud.clear();
					c.save();
				}));
	}

	private static String splashBackgroundTitle(String v) {
		return "DARK".equalsIgnoreCase(v) ? "Dark" : "SHADER".equalsIgnoreCase(v) ? "Animated" : "Gradient";
	}

	private static String nextSplashBackground(String v) {
		return "GRADIENT".equalsIgnoreCase(v) ? "SHADER" : "SHADER".equalsIgnoreCase(v) ? "DARK" : "GRADIENT";
	}

	/** Выбор цветовой темы GUI и HUD: раскрывающийся список, клик по теме применяет её сразу. */
	private static ModuleDef themeGroup(PvpConfig c) {
		ModuleDef g = ModuleDef.group(Category.CLIENT,
				() -> "Theme: " + com.pvpvisuals.gui.echo.EchoPalette.byName(c.gui.palette).title);
		for (com.pvpvisuals.gui.echo.EchoPalette p : com.pvpvisuals.gui.echo.EchoPalette.values()) {
			g.child(ModuleDef.toggle(p.title, Category.CLIENT,
					() -> com.pvpvisuals.gui.echo.EchoPalette.byName(c.gui.palette) == p,
					v -> {
						if (v) {
							c.gui.palette = p.name();
							com.pvpvisuals.gui.echo.EchoTheme.apply(p);
						}
					}));
		}
		return g;
	}

	/** Переключаемые модули верхнего уровня, для которых создаётся бинд. */
	public static List<ModuleDef> toggleModules() {
		List<ModuleDef> out = new ArrayList<>();
		for (ModuleDef d : modules()) {
			if (d.isToggle() && KeybindManager.isBindable(d.name)) {
				out.add(d);
			}
		}
		return out;
	}

	/** Полный список модулей GUI; бинд назначается плашкой на карточке модуля. */
	public static List<ModuleDef> build() {
		return new ArrayList<>(modules());
	}
}
