package com.pvpvisuals.render;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.state.CombatState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Фиолетовая вспышка по краям экрана и стилизованная надпись CRIT при критическом ударе (стиль Echo Visual). */
public final class CritEffect {
	public static final int FLASH_TICKS = 8;

	private CritEffect() { }

	public static void render(GuiGraphics g, Minecraft mc, float partialTick) {
		if (!PvpConfig.get().critEffect || CombatState.critFlash <= 0) {
			return;
		}
		float left = Math.max(0f, CombatState.critFlash - partialTick) / FLASH_TICKS; // 1 -> 0
		if (left < 0.03f) {
			return;
		}
		float prev = Gfx.alpha;
		try {
			EchoTheme.sync();
			int w = g.guiWidth();
			int h = g.guiHeight();
			float fade = left * left * (3f - 2f * left); // smoothstep: мягкое затухание

			// виньетка: ступенчатое затухание от краёв к центру (без наложения, цвет в тему)
			int steps = 14;
			int stepPx = 3;
			int rgb = EchoTheme.shift(0xFF9B5CFF) & 0xFFFFFF;
			for (int i = 0; i < steps; i++) {
				float k = 1f - i / (float) steps;
				int a = Math.round(120f * fade * k * k);
				if (a < 2) {
					continue;
				}
				int c = (a << 24) | rgb;
				int o = i * stepPx;
				g.fill(o, o, w - o, o + stepPx, c); // верх
				g.fill(o, h - o - stepPx, w - o, h - o, c); // низ
				g.fill(o, o + stepPx, o + stepPx, h - o - stepPx, c); // лево
				g.fill(w - o - stepPx, o + stepPx, w - o, h - o - stepPx, c); // право
			}

			// надпись: «выпрыгивает» (scale 1.6 -> 1.0) и всплывает вверх, затухая
			float pop = 1f - left; // 0 -> 1 за время вспышки
			float appear = Gfx.ease(pop * 3.5f); // быстрое появление
			float scale = 1.0f + 0.6f * (1f - appear);
			float cx = w / 2f;
			float cy = h / 2f - 34f - 10f * pop;
			Gfx.alpha = prev * Math.min(1f, left * 2.2f);

			String text = "CRIT";
			float tw = Gfx.width(mc.font, text) * scale * 1.6f;
			float th = 9f * scale * 1.6f;
			float tx = cx - tw / 2f;
			float ty = cy - th / 2f;

			Gfx.glow(g, tx - 6f, ty - 3f, tw + 12f, th + 6f, 6f, EchoTheme.shift(0xFF8B3DFF), 6f);
			Gfx.textScaled(g, mc.font, text, tx, ty, scale * 1.6f, 0xFFFFFFFF, false);
			// подчёркивание-акцент
			float lw = tw * 0.7f;
			Gfx.rrect(g, cx - lw / 2f, ty + th + 3f, lw, 2f, 1f, EchoTheme.shift(0xFFC9A6FF), EchoTheme.shift(0xFF8B3DFF),
					0, 0f);
		} finally {
			Gfx.alpha = prev;
		}
	}
}
