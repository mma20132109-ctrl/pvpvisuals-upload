package com.pvpvisuals;


import com.pvpvisuals.config.ConfigManager;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.event.CombatEvents;
import com.pvpvisuals.hud.HudChatEditor;
import com.pvpvisuals.hud.HudManager;
import com.pvpvisuals.render.hat.ChinaHatModule;
import com.pvpvisuals.render.particle.ParticleRenderer;
import com.pvpvisuals.render.trajectory.TrajectoryModule;
import com.pvpvisuals.render.engine.SvintusRenderEngine;
import com.pvpvisuals.render.world.WorldParticlesModule;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class PvpVisualsClient implements ClientModInitializer {
	public static final String MOD_ID = "pvpvisuals";

	@Override
	public void onInitializeClient() {
		PvpConfig.load();
		SvintusRenderEngine.initialize();
		com.pvpvisuals.keybind.KeybindManager.register();

		// Регистрируется до обработчика CLIENT_STOPPING конфига: гамма восстанавливается раньше, чем пишется конфиг.
		com.pvpvisuals.render.fullbright.FullbrightModule.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> ConfigManager.getInstance().tick());

		// Сохранение при выходе из игры (событие Fabric API lifecycle-events-v1).
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.getInstance().shutdown());

		com.pvpvisuals.cape.CapeModule.register();
		com.pvpvisuals.cape.CapeSyncManager.register();
		com.pvpvisuals.render.kill.KillEffectModule.register();
		CombatEvents.register();
		HudManager.register();
		com.pvpvisuals.media.MediaManager.register();
		com.pvpvisuals.render.sky.ShaderSkyModule.register();
		com.pvpvisuals.render.freelook.FreelookModule.register();
		com.pvpvisuals.gui.echo.EchoTheme.sync();
		com.pvpvisuals.crosshair.CrosshairModule.register();
		HudChatEditor.register();
		WorldParticlesModule.register();
		ChinaHatModule.register();
		com.pvpvisuals.render.blockoverlay.BlockOverlayModule.register();
		com.pvpvisuals.render.hitbox.HitBoxModule.register();
		ParticleRenderer.register();
		TrajectoryModule.register();
	}
}
