package com.pvpvisuals.gui.rival;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Всплывающие уведомления в углу экрана (позиция — Client → Bind Toasts). Стопкой, с fade-in/out за 200 мс.
 * show() можно вызывать из любого потока.
 */
public final class NotificationToast {
	public static final long DEFAULT_MS = 3500;
	private static final long FADE_MS = 200;
	private static final int MAX = 5;

	private record Toast(String text, boolean ok, long created, long life) { }

	private static final List<Toast> TOASTS = new CopyOnWriteArrayList<>();

	private NotificationToast() { }

	public static void show(String text, boolean ok) {
		show(text, ok, DEFAULT_MS);
	}

	public static void show(String text, boolean ok, long durationMs) {
		TOASTS.add(new Toast(text, ok, System.currentTimeMillis(), Math.max(2 * FADE_MS, durationMs)));
		while (TOASTS.size() > MAX) {
			TOASTS.remove(0);
		}
	}

	public static void render(GuiGraphics g, Font font, int screenW, int screenH) {
		if (TOASTS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		TOASTS.removeIf(t -> now - t.created >= t.life);
		NotificationPosition pos = NotificationPosition.byName(PvpConfig.get().binds.position);
		int step = 24;
		int y = pos.bottom() ? screenH - 34 - 20 : 10;
		for (Toast t : TOASTS) {
			long age = now - t.created;
			float a = 1f;
			if (age < FADE_MS) {
				a = age / (float) FADE_MS;
			} else if (age > t.life - FADE_MS) {
				a = (t.life - age) / (float) FADE_MS;
			}
			int w = font.width(t.text) + 22;
			int x = pos.right() ? screenW - w - 10 : 10;
			com.pvpvisuals.hud.HudStyle.panel(g, x, y, w, 20, 8, a);
			RoundedRender.fill(g, x + 4, y + 4, 3, 12, 1,
					RoundedRender.scaleAlpha(t.ok ? 0xFF4FD96A : 0xFFE0432B, a));
			g.drawString(font, t.text, x + 12, y + 6, RoundedRender.scaleAlpha(0xFFFFFFFF, a), false);
			y += pos.bottom() ? -step : step;
		}
	}
}
