package com.pvpvisuals.render.freelook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pvpvisuals.config.FreelookSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Freelook (Orbit Camera): описание модуля для меню (Visual), обработка клавиши и блокировка действий.
 * Клавиша «Freelook» (по умолчанию Left Alt) создаётся в {@code KeybindManager}, категория «Echo Visual — Modules».
 * Логика камеры — {@link FreelookCameraHandler}, миксины — FreelookCameraMixin и FreelookMouseMixin.
 */
public final class FreelookModule {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/freelook");
	private static boolean toggled;

	private FreelookModule() { }

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(FreelookModule::startTick);
		// Диагностика: реальные методы этой версии игры, чтобы быстро сверить сигнатуры миксинов.
		for (String name : new String[] {"net.minecraft.client.Camera", "net.minecraft.client.MouseHandler"}) {
			try {
				Class<?> c = Class.forName(name);
				for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
					String n = m.getName();
					if (n.equals("setup") || n.equals("setRotation") || n.equals("turnPlayer") || n.equals("update")
							|| n.equals("alignWithEntity")) {
						LOG.info("Freelook: {}#{}{}", c.getSimpleName(), n, java.util.Arrays.toString(m.getParameterTypes()));
					}
				}
			} catch (Throwable e) {
				LOG.warn("Freelook: class {} not found: {}", name, e.toString());
			}
		}
	}

	/** Блокировка атаки и использования, пока камера отвязана (если выключено Allow Actions). */
	private static void startTick(Minecraft mc) {
		FreelookSettings s = PvpConfig.get().freelook;
		if (!s.enabled || s.allowActions || !FreelookCameraHandler.isActive() || mc.player == null) {
			return;
		}
		mc.options.keyAttack.setDown(false);
		mc.options.keyUse.setDown(false);
		while (mc.options.keyAttack.consumeClick()) {
			// сбрасываем накопленные клики
		}
		while (mc.options.keyUse.consumeClick()) {
			// то же для правой кнопки
		}
	}

	/** Раз за тик из KeybindManager: key — клавиша Freelook. */
	public static void keyTick(Minecraft mc, KeyMapping key) {
		if (key == null) {
			return;
		}
		FreelookSettings s = PvpConfig.get().freelook;
		if (!s.enabled || mc.player == null || mc.level == null) {
			toggled = false;
			while (key.consumeClick()) {
				// очередь кликов не копим
			}
			if (FreelookCameraHandler.isActive()) {
				FreelookCameraHandler.finish();
			}
			return;
		}
		boolean wanted;
		if ("TOGGLE".equalsIgnoreCase(s.activationMode)) {
			while (key.consumeClick()) {
				toggled = !toggled;
			}
			wanted = toggled && mc.screen == null;
		} else {
			while (key.consumeClick()) {
				// в режиме HOLD важно только состояние isDown
			}
			toggled = false;
			wanted = key.isDown() && mc.screen == null;
		}
		FreelookCameraHandler.setWanted(wanted);
	}

	public static ModuleDef def(PvpConfig pc) {
		FreelookSettings f = pc.freelook;
		return ModuleDef.toggle("Freelook", Category.VISUAL, () -> f.enabled, v -> f.enabled = v)
				.child(ModuleDef.cycle(Category.VISUAL,
						() -> "Mode: " + ("TOGGLE".equalsIgnoreCase(f.activationMode) ? "Toggle" : "Hold"),
						() -> f.activationMode = "TOGGLE".equalsIgnoreCase(f.activationMode) ? "HOLD" : "TOGGLE"))
				.child(ModuleDef.toggle("Invert Y", Category.VISUAL, () -> f.invertY, v -> f.invertY = v))
				.child(ModuleDef.toggle("Smooth Return", Category.VISUAL, () -> f.smoothReturn, v -> f.smoothReturn = v))
				.child(ModuleDef.slider("Sensitivity", Category.VISUAL, 0.2, 3.0, 2,
						() -> f.sensitivity, v -> f.sensitivity = (float) v))
				.child(ModuleDef.toggle("Force Third Person", Category.VISUAL,
						() -> f.forceThirdPerson, v -> f.forceThirdPerson = v))
				.child(ModuleDef.toggle("Allow Actions", Category.VISUAL, () -> f.allowActions, v -> f.allowActions = v));
	}
}
