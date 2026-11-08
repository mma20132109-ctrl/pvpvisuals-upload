package com.pvpvisuals.config;

import java.util.HashMap;

import net.minecraft.util.Mth;

/** Миграция формата конфига и приведение значений к допустимым. */
public final class ConfigMigrator {
	/** Текущая версия формата. Увеличивайте при изменении структуры и добавляйте шаг в {@link #step}. */
	public static final int CURRENT = 9;

	private ConfigMigrator() { }

	/**
	 * Доводит конфиг до текущей версии и нормализует значения.
	 * @return true, если версия формата была повышена (файл стоит пересохранить)
	 */
	public static boolean migrate(PvpConfig c) {
		boolean upgraded = false;
		if (c.configVersion > CURRENT) {
			System.err.println("[pvpvisuals] Конфиг создан более новой версией мода (формат " + c.configVersion
					+ "), неизвестные поля будут проигнорированы");
		}
		while (c.configVersion < CURRENT) {
			step(c, c.configVersion);
			c.configVersion++;
			upgraded = true;
		}
		normalize(c);
		return upgraded;
	}

	/** Шаг from -> from+1. */
	private static void step(PvpConfig c, int from) {
		switch (from) {
			case 8 -> {
				// v8 -> v9: появился crosshair (значения по умолчанию из инициализаторов).
			}
			case 7 -> {
				// v7 -> v8: появился hudBinds (значения по умолчанию из инициализаторов).
			}
			case 6 -> {
				// v6 -> v7: появился binds (значения по умолчанию из инициализаторов).
			}
			case 5 -> {
				// v5 -> v6: появился fullbright (значения по умолчанию из инициализаторов).
			}
			case 4 -> {
				// v4 -> v5: появился killEffect (значения по умолчанию из инициализаторов).
			}
			case 3 -> {
				// v3 -> v4: появился cape (значения по умолчанию из инициализаторов).
			}
			case 2 -> {
				// v2 -> v3: появились watermark и logo (значения по умолчанию из инициализаторов).
			}
			case 0, 1 -> {
				// v0/v1 -> v2: появились метаданные, gui, keys, menuExpanded.
				// Все они уже имеют значения по умолчанию из инициализаторов полей; позиции HUD из
				// hudPositions переносятся лениво в AbstractHudModule.settings().
			}
			default -> { }
		}
	}

	/** Заменяет null и недопустимые значения (в т.ч. из ручной правки файла) значениями по умолчанию. */
	public static void normalize(PvpConfig c) {
		PvpConfig d = new PvpConfig();
		if (c.invHud == null) c.invHud = d.invHud;
		if (c.traj == null) c.traj = d.traj;
		if (c.hit == null) c.hit = d.hit;
		if (c.block == null) c.block = d.block;
		if (c.nowPlaying == null) c.nowPlaying = new NowPlayingSettings();
		c.nowPlaying.maxTrackWidth = Mth.clamp(c.nowPlaying.maxTrackWidth, 40, 200);
		if (c.shaderSky == null) c.shaderSky = new ShaderSkySettings();
		c.shaderSky.shaderType = com.pvpvisuals.render.sky.ShaderType.byName(c.shaderSky.shaderType).name();
		c.shaderSky.brightness = Mth.clamp(c.shaderSky.brightness, 0.5f, 2.0f);
		c.shaderSky.speed = Mth.clamp(c.shaderSky.speed, 0.0f, 2.0f);
		c.shaderSky.colorHue = Mth.clamp(c.shaderSky.colorHue, 0, 360);
		c.shaderSky.colorSaturation = Mth.clamp(c.shaderSky.colorSaturation, 0.0f, 2.0f);
		if (c.gui != null) c.gui.palette = com.pvpvisuals.gui.echo.EchoPalette.byName(c.gui.palette).name();
		if (c.handShader == null) c.handShader = new HandShaderSettings();
		c.handShader.shaderType = com.pvpvisuals.render.handshader.HandShaderStyle.byName(c.handShader.shaderType).name();
		c.handShader.colorPrimary &= 0xFFFFFF;
		c.handShader.colorSecondary &= 0xFFFFFF;
		c.handShader.alphaPrimary = Mth.clamp(c.handShader.alphaPrimary, 0.05f, 1.0f);
		c.handShader.alphaSecondary = Mth.clamp(c.handShader.alphaSecondary, 0.05f, 1.0f);
		c.handShader.animationSpeed = Mth.clamp(c.handShader.animationSpeed, 0.1f, 4.0f);
		if (c.viewModel == null) c.viewModel = new ViewModelSettings();
		ViewModelSettings vm = c.viewModel;
		vm.posX = Mth.clamp(vm.posX, -2f, 2f);
		vm.posY = Mth.clamp(vm.posY, -2f, 2f);
		vm.posZ = Mth.clamp(vm.posZ, -2f, 2f);
		vm.scaleX = Mth.clamp(vm.scaleX, 0.1f, 2f);
		vm.scaleY = Mth.clamp(vm.scaleY, 0.1f, 2f);
		vm.scaleZ = Mth.clamp(vm.scaleZ, 0.1f, 2f);
		vm.rotX = Mth.clamp(vm.rotX, -180f, 180f);
		vm.rotY = Mth.clamp(vm.rotY, -180f, 180f);
		vm.rotZ = Mth.clamp(vm.rotZ, -180f, 180f);
		vm.swingSpeed = Mth.clamp(vm.swingSpeed, 0.5f, 2f);
		vm.swingAmplitude = Mth.clamp(vm.swingAmplitude, 0f, 2f);
		vm.customSwing = com.pvpvisuals.render.viewmodel.ViewModelModule.SwingStyle.byName(vm.customSwing).name();
		if (c.radial == null) c.radial = new RadialMenuSettings();
		String[] ph = new String[RadialMenuSettings.SECTORS];
		String[] defaults = new RadialMenuSettings().phrases;
		for (int i = 0; i < ph.length; i++) {
			String v = c.radial.phrases != null && i < c.radial.phrases.length ? c.radial.phrases[i] : null;
			ph[i] = v == null ? defaults[i] : (v.length() > 64 ? v.substring(0, 64) : v);
		}
		c.radial.phrases = ph;
		c.radial.menuRadius = Mth.clamp(c.radial.menuRadius, 80, 200);
		c.radial.deadZoneRadius = Mth.clamp(c.radial.deadZoneRadius, 10, 60);
		c.radial.backgroundColor &= 0xFFFFFF;
		c.radial.highlightColor &= 0xFFFFFF;
		c.radial.textColor &= 0xFFFFFF;
		c.radial.backgroundAlpha = Mth.clamp(c.radial.backgroundAlpha, 0.1f, 1.0f);
		c.radial.highlightAlpha = Mth.clamp(c.radial.highlightAlpha, 0.1f, 1.0f);
		if (c.hitBox == null) c.hitBox = new HitBoxSettings();
		c.hitBox.baseColor &= 0xFFFFFF;
		c.hitBox.targetColor &= 0xFFFFFF;
		c.hitBox.baseAlpha = Mth.clamp(c.hitBox.baseAlpha, 0.05f, 1.0f);
		c.hitBox.targetAlpha = Mth.clamp(c.hitBox.targetAlpha, 0.05f, 1.0f);
		c.hitBox.lineThickness = Mth.clamp(c.hitBox.lineThickness, 0.5f, 4.0f);
		c.hitBox.transitionSpeed = Mth.clamp(c.hitBox.transitionSpeed, 0.02f, 0.5f);
		c.hitBox.maxDistance = Mth.clamp(c.hitBox.maxDistance, 8f, 128f);
		if (c.blockOverlay == null) c.blockOverlay = new BlockOverlaySettings();
		c.blockOverlay.shaderType = com.pvpvisuals.render.blockoverlay.BlockOverlayStyle.byName(c.blockOverlay.shaderType).name();
		c.blockOverlay.color &= 0xFFFFFF;
		c.blockOverlay.alpha = Mth.clamp(c.blockOverlay.alpha, 0.05f, 1.0f);
		c.blockOverlay.pulseSpeed = Mth.clamp(c.blockOverlay.pulseSpeed, 0.1f, 4.0f);
		c.blockOverlay.lineThickness = Mth.clamp(c.blockOverlay.lineThickness, 0.5f, 8.0f);
		c.blockOverlay.colorSecondary &= 0xFFFFFF;
		c.blockOverlay.starDensity = Mth.clamp(c.blockOverlay.starDensity, 0.2f, 3.0f);
		String rq = c.blockOverlay.renderQuality == null ? "" : c.blockOverlay.renderQuality.trim().toUpperCase(java.util.Locale.ROOT);
		c.blockOverlay.renderQuality = rq.equals("LOW") || rq.equals("HIGH") ? rq : "MEDIUM";
		if (c.freelook == null) c.freelook = new FreelookSettings();
		c.freelook.activationMode = "TOGGLE".equalsIgnoreCase(c.freelook.activationMode) ? "TOGGLE" : "HOLD";
		c.freelook.sensitivity = Mth.clamp(c.freelook.sensitivity, 0.2f, 3.0f);
		if (c.binds == null) c.binds = new BindSettings();
		if (c.binds.position == null) c.binds.position = "TOP_RIGHT";
		c.binds.durationMs = Mth.clamp(c.binds.durationMs, 300, 10000);
		if (c.crosshair == null) c.crosshair = new CrosshairSettings();
		CrosshairSettings ch = c.crosshair;
		if (ch.style == null) ch.style = "CROSS";
		if (ch.colorMode == null) ch.colorMode = "STATIC";
		if (ch.animation == null) ch.animation = "NONE";
		ch.colorPrimary &= 0xFFFFFF;
		ch.colorSecondary &= 0xFFFFFF;
		ch.outlineColor &= 0xFFFFFF;
		ch.flashColor &= 0xFFFFFF;
		ch.alpha = Mth.clamp(ch.alpha, 0.05f, 1f);
		ch.thickness = Mth.clamp(ch.thickness, 1, 5);
		ch.length = Mth.clamp(ch.length, 2, 15);
		ch.gap = Mth.clamp(ch.gap, 0, 10);
		ch.dotSize = Mth.clamp(ch.dotSize, 0, 5);
		ch.circleRadius = Mth.clamp(ch.circleRadius, 3, 15);
		ch.outlineThickness = Mth.clamp(ch.outlineThickness, 0, 2);
		ch.animationDuration = Mth.clamp(ch.animationDuration, 100, 500);
		ch.spreadMultiplier = Mth.clamp(ch.spreadMultiplier, 0.5f, 3f);
		if (c.hudBinds == null) c.hudBinds = new HudBindsSettings();
		HudBindsSettings hb = c.hudBinds;
		if (hb.sortMode == null) hb.sortMode = "WIDTH";
		hb.pageSize = Mth.clamp(hb.pageSize, 1, 8);
		hb.autoFlipSeconds = Mth.clamp(hb.autoFlipSeconds, 0, 60);
		hb.animationSpeed = Mth.clamp(hb.animationSpeed, 0, 2000);
		if (c.fullbright == null) c.fullbright = new FullbrightSettings();
		if (c.fullbright.mode == null) c.fullbright.mode = "GAMMA";
		c.fullbright.brightness = Mth.clamp(c.fullbright.brightness, 1.0f, 15.0f);
		c.fullbright.transitionSpeed = Mth.clamp(c.fullbright.transitionSpeed, 1, 200);
		if (c.killEffect == null) c.killEffect = new KillEffectSettings();
		if (c.killEffect.style == null) c.killEffect.style = "EXPLOSION";
		if (c.killEffect.target == null) c.killEffect.target = "PLAYERS_ONLY";
		c.killEffect.duration = Mth.clamp(c.killEffect.duration, 5, 200);
		c.killEffect.size = Mth.clamp(c.killEffect.size, 0.2f, 4.0f);
		if (c.cape == null) c.cape = new CapeSettings();
		if (c.cape.texture == null || c.cape.texture.isBlank()) c.cape.texture = new CapeSettings().texture;
		c.cape.brightness = Mth.clamp(c.cape.brightness, 0.0f, 1.0f);
		c.cape.alpha = Mth.clamp(c.cape.alpha, 0.0f, 1.0f);
		if (c.logo == null) c.logo = new LogoSettings();
		if (c.logo.sizeWatermark == null) c.logo.sizeWatermark = "AUTO";
		if (c.logo.sizeGui == null) c.logo.sizeGui = "AUTO";
		if (c.gui == null) c.gui = new GuiSettings();
		if (c.hud == null) c.hud = new HashMap<>();
		if (c.hudPositions == null) c.hudPositions = new HashMap<>();
		if (c.menuExpanded == null) c.menuExpanded = new HashMap<>();
		if (c.keys == null) c.keys = new HashMap<>();
		if (c.theme == null) c.theme = "GOLD";
		if (c.profile == null || c.profile.isBlank()) c.profile = "default";
		if (c.modVersion == null) c.modVersion = "";
		if (c.savedAt == null) c.savedAt = "";
		c.hud.values().removeIf(java.util.Objects::isNull);
		for (HudSettings s : c.hud.values()) {
			if (s.anchor == null) s.anchor = "TOP_LEFT";
			s.scale = Mth.clamp(s.scale, 0.5f, 3.0f);
			s.alpha = Mth.clamp(s.alpha, 0.05f, 1.0f);
		}
		c.gui.overlayAlpha = Mth.clamp(c.gui.overlayAlpha, 0.0f, 0.8f);
		c.gui.panelOpacity = Mth.clamp(c.gui.panelOpacity, 0.3f, 1.0f);
	}
}
