package com.pvpvisuals.render.freelook;

import com.pvpvisuals.config.FreelookSettings;
import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Состояние Freelook: абсолютные углы камеры (freeYaw/freePitch) отдельно от углов игрока.
 *
 * <p>Пока камера отвязана, мышь меняет только эти два числа (см. FreelookMouseMixin), а игрок не поворачивается,
 * поэтому WASD, коллизия и направление тела остаются прежними. Камера (FreelookCameraMixin) подставляет эти углы в
 * Camera#setup до расчёта позиции, поэтому в третьем лице она облетает игрока по орбите.
 * При отпускании клавиши углы плавно сходятся к углам игрока (экспоненциальное сглаживание), затем режим выключается.
 */
public final class FreelookCameraHandler {
	private static boolean active;
	private static boolean returning;
	private static float freeYaw;
	private static float freePitch;

	private static CameraType savedType;
	private static boolean typeChanged;
	private static long lastNanos;

	private FreelookCameraHandler() { }

	private static FreelookSettings cfg() {
		return PvpConfig.get().freelook;
	}

	/** Камера сейчас отвязана от игрока (включая фазу возврата). */
	public static boolean isActive() {
		return active;
	}

	/** Мышь управляет камерой, а не игроком (в фазе возврата мышь уже снова управляет игроком). */
	public static boolean capturesMouse() {
		return active && !returning;
	}

	public static float yaw() {
		return freeYaw;
	}

	public static float pitch() {
		return freePitch;
	}

	/** Вызывается каждый тик: хочет ли игрок сейчас свободный обзор (клавиша зажата / включён toggle). */
	public static void setWanted(boolean wanted) {
		if (wanted) {
			if (!active) {
				start();
			} else if (returning) {
				returning = false; // клавишу нажали снова во время возврата: продолжаем с текущих углов
			}
		} else if (active && !returning) {
			if (cfg().smoothReturn) {
				returning = true;
			} else {
				finish();
			}
		}
	}

	private static void start() {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null) {
			return;
		}
		active = true;
		returning = false;
		freeYaw = p.getYRot();
		freePitch = p.getXRot();
		lastNanos = 0L;
		if (cfg().forceThirdPerson && mc.options.getCameraType().isFirstPerson()) {
			savedType = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			typeChanged = true;
		}
	}

	/** Полное выключение: возвращает прежний вид камеры, если мы его меняли. */
	public static void finish() {
		if (typeChanged && savedType != null) {
			Minecraft.getInstance().options.setCameraType(savedType);
		}
		typeChanged = false;
		savedType = null;
		active = false;
		returning = false;
		lastNanos = 0L;
	}

	/** Мышь в режиме Freelook. scale — уже посчитанная ванильная чувствительность (d*d*d*8). */
	public static void applyMouse(double dx, double dy, double scale) {
		if (!capturesMouse()) {
			return;
		}
		FreelookSettings s = cfg();
		double k = scale * 0.15 * s.sensitivity;
		freeYaw += (float) (dx * k);
		freePitch += (float) (dy * k * (s.invertY ? -1.0 : 1.0));
		freePitch = Mth.clamp(freePitch, -90.0f, 90.0f); // чтобы камера не переворачивалась
	}

	/** Раз за кадр из Camera#setup: плавное возвращение к углам игрока. */
	public static void frame() {
		if (!active) {
			return;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (!returning) {
			return;
		}
		Player p = Minecraft.getInstance().player;
		if (p == null) {
			finish();
			return;
		}
		float k = 1f - (float) Math.exp(-dt * 10.0); // ~0.25 с на основную часть пути
		float dy = Mth.wrapDegrees(p.getYRot() - freeYaw);
		float dp = p.getXRot() - freePitch;
		freeYaw += dy * k;
		freePitch += dp * k;
		if (Math.abs(dy) < 0.4f && Math.abs(dp) < 0.4f) {
			finish();
		}
	}
}
