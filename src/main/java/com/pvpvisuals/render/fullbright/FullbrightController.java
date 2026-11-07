package com.pvpvisuals.render.fullbright;

import com.pvpvisuals.config.FullbrightSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.mixin.OptionInstanceAccessor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Логика гаммы: сохранение исходного значения, плавный переход каждый тик, восстановление при выключении,
 * при выходе из игры и при следующем запуске после сбоя. Гамма читается/пишется только при изменении.
 */
public final class FullbrightController {
	private static boolean active;
	private static double original;
	private static double current;
	private static double lastApplied = Double.NaN;
	private static boolean warned;

	private FullbrightController() { }

	public static void register() {
		ClientLifecycleEvents.CLIENT_STARTED.register(FullbrightController::onStarted);
		ClientLifecycleEvents.CLIENT_STOPPING.register(FullbrightController::onStopping);
		ClientTickEvents.END_CLIENT_TICK.register(FullbrightController::tick);
	}

	/** Запись гаммы в обход проверки диапазона. */
	private static void apply(Minecraft mc, double value) {
		// ПРОВЕРИТЬ: Options#gamma() -> OptionInstance<Double> в 1.21.11 (поле gamma, аксессор gamma()).
		try {
			((OptionInstanceAccessor) (Object) mc.options.gamma()).pvpvisuals$setValue(value);
		} catch (Throwable t) { // например, mixin не подключён в pvpvisuals.mixins.json
			if (!warned) {
				warned = true;
				System.err.println("[pvpvisuals] Fullbright: mixin-аксессор недоступен, гамма ограничена 1.0: " + t);
			}
			mc.options.gamma().set(Math.min(1.0, value));
		}
		lastApplied = value;
	}

	private static void saveOptions(Minecraft mc) {
		mc.options.save(); // ПРОВЕРИТЬ: Options#save()
	}

	/** Если прошлая сессия оборвалась с активным Fullbright — вернуть исходную гамму. */
	private static void onStarted(Minecraft mc) {
		FullbrightSettings f = PvpConfig.get().fullbright;
		if (f.savedGamma >= 0) {
			apply(mc, f.savedGamma);
			f.savedGamma = -1.0;
			saveOptions(mc);
			PvpConfig.get().save();
		}
	}

	private static void onStopping(Minecraft mc) {
		if (!active) {
			return;
		}
		FullbrightSettings f = PvpConfig.get().fullbright;
		apply(mc, original);
		active = false;
		f.savedGamma = -1.0;
		saveOptions(mc);
	}

	private static void tick(Minecraft mc) {
		if (mc.options == null) {
			return;
		}
		FullbrightSettings f = PvpConfig.get().fullbright;
		boolean want = f.enabled;
		if (!want && !active) {
			return; // модуль выключен — ничего не делаем
		}

		double brightness = Mth.clamp(f.brightness, 1.0f, 15.0f);
		if (want && !active) {
			double g = mc.options.gamma().get();
			// g > 1 — гамму уже кто-то поднял (например, сбой прошлой сессии): берём сохранённое значение
			original = g > 1.0 ? (f.savedGamma >= 0 ? f.savedGamma : 0.5) : g;
			current = g;
			active = true;
			f.savedGamma = original;
			PvpConfig.get().save();
		}

		double target = want ? brightness : original;
		if (f.smoothTransition) {
			double step = Math.abs(brightness - original) / Math.max(1, f.transitionSpeed);
			if (step < 1e-4) {
				current = target;
			} else if (current < target) {
				current = Math.min(target, current + step);
			} else {
				current = Math.max(target, current - step);
			}
		} else {
			current = target;
		}

		if (current != lastApplied) {
			apply(mc, current);
		}

		if (!want && current == original) {
			active = false;
			f.savedGamma = -1.0;
			saveOptions(mc);
			PvpConfig.get().save();
		}
	}
}
