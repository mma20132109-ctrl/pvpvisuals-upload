package com.pvpvisuals.hud;

import com.pvpvisuals.gui.echo.Gfx;

import net.minecraft.client.gui.GuiGraphics;

/** Мелкие линейные иконки для HUD (часы, FPS, сигнал, сердце): рисуются линиями Gfx, без текстур. Центр — (cx, cy). */
public final class HudIcons {
	private HudIcons() { }

	public static void clock(GuiGraphics g, float cx, float cy, float r, int color) {
		Gfx.ringCircle(g, cx, cy, r, 1f, color);
		Gfx.line(g, cx, cy, cx, cy - r * 0.6f, 1f, color);
		Gfx.line(g, cx, cy, cx + r * 0.45f, cy + r * 0.2f, 1f, color);
	}

	/** «Пульс» — иконка FPS. */
	public static void activity(GuiGraphics g, float cx, float cy, float r, int color) {
		Gfx.polyline(g, 1.1f, color, cx - r, cy, cx - r * 0.5f, cy, cx - r * 0.2f, cy - r * 0.9f,
				cx + r * 0.2f, cy + r * 0.9f, cx + r * 0.5f, cy, cx + r, cy);
	}

	/** Три столбика — иконка пинга. */
	public static void signal(GuiGraphics g, float cx, float cy, float r, int color) {
		float w = r * 0.55f;
		for (int i = 0; i < 3; i++) {
			float h = r * (0.7f + i * 0.65f);
			Gfx.fill(g, Math.round(cx - r + i * (w + 1f)), Math.round(cy + r * 0.9f - h), Math.round(w), Math.round(h), 0, color);
		}
	}

	/** Минималистичная пиктограмма игрока для брендовой части Watermark. */
	public static void player(GuiGraphics g, float cx, float cy, float r, int color) {
		Gfx.ringCircle(g, cx, cy - r * 0.38f, r * 0.27f, 0.9f, color);
		Gfx.polyline(g, 1f, color,
				cx - r * 0.72f, cy + r * 0.72f,
				cx - r * 0.58f, cy + r * 0.22f,
				cx - r * 0.20f, cy + r * 0.02f,
				cx + r * 0.20f, cy + r * 0.02f,
				cx + r * 0.58f, cy + r * 0.22f,
				cx + r * 0.72f, cy + r * 0.72f);
	}

	/** Компасная роза, используемая рядом с направлением координат. */
	public static void compass(GuiGraphics g, float cx, float cy, float r, int color) {
		Gfx.ringCircle(g, cx, cy, r, 0.8f, color);
		Gfx.polyline(g, 1f, color,
				cx, cy - r * 0.72f,
				cx - r * 0.16f, cy + r * 0.18f,
				cx, cy + r * 0.02f,
				cx + r * 0.16f, cy + r * 0.18f,
				cx, cy - r * 0.72f);
	}

	public static void heart(GuiGraphics g, float cx, float cy, float r, int color) {
		Gfx.circle(g, cx - r * 0.5f, cy - r * 0.25f, r * 0.55f, color);
		Gfx.circle(g, cx + r * 0.5f, cy - r * 0.25f, r * 0.55f, color);
		Gfx.line(g, cx - r * 0.95f, cy + r * 0.05f, cx, cy + r * 0.95f, r * 0.8f, color);
		Gfx.line(g, cx + r * 0.95f, cy + r * 0.05f, cx, cy + r * 0.95f, r * 0.8f, color);
	}
}
