package com.pvpvisuals.media;

import com.pvpvisuals.config.NowPlayingSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.RoundedRender;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Верхняя («музыкальная») строка Island HUD: слева [обложка 16×16] + название (белое, жирное), по центру таймкод
 * (акцентный цвет темы), справа автор (серый, alpha 0.7, scale 0.9). Анимация: высота острова растёт за 350 мс
 * (ease-out-cubic), текст выезжает снизу вверх с лёгким bounce (ease-out-back) и появляется fade-in.
 * Саму плашку и нижнюю строку рисует TopBar, здесь — состояние, размеры и содержимое строки.
 */
public final class NowPlayingWidgetRenderer {
	/** Высота музыкальной строки вместе с отступами. */
	public static final int ROW_H = 22;
	private static final int PAD = 12;
	private static final int COVER = 16;
	private static final float ARTIST_SCALE = 0.9f;
	private static final float ANIM_MS = 350f;
		private float anim;
	private float fade;
	private long lastMs;
	private TrackInfo shown;

	private String cacheTitleSrc = "";
	private String cacheArtistSrc = "";
	private int cacheMaxW = -1;
	private String titleShown = "";
	private String artistShown = "";
	private int titleW;
	private int artistW;
	private int timeW;
	private int cachedSec = -1;
	private int cachedDur = -1;
	private String timeStr = "00:00 / 00:00";

	private static float easeOutCubic(float t) {
		float p = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - p * p * p;
	}

	private static float easeOutBack(float t) {
		t = Math.max(0f, Math.min(1f, t));
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		float p = t - 1f;
		return 1f + c3 * p * p * p + c1 * p * p;
	}

	/** Прогресс раскрытия 0..1 с easing: высота острова = базовая + ROW_H * progress. */
	public float progress() {
		return easeOutCubic(anim);
	}

	public boolean active() {
		return shown != null && anim > 0.001f;
	}

	/** Обновляет анимации; вызывать раз за кадр до остальных методов. */
	public void update(Font font) {
		long now = Util.getMillis();
		float dt = lastMs == 0 ? 16f : Math.min(100f, now - lastMs);
		lastMs = now;
		NowPlayingSettings s = PvpConfig.get().nowPlaying;

		TrackInfo cur = MediaManager.available() ? MediaManager.track() : null;
		boolean want = s.showNowPlaying && cur != null && (MediaManager.playing() || !s.hideWhenPaused);

		if (cur != null) {
			if (shown == null) {
				shown = cur;
				fade = 0f;
			} else if (!shown.key.equals(cur.key)) {
				fade -= dt / 150f; // fade-out старого трека, затем подмена
				if (fade <= 0f) {
					shown = cur;
					fade = 0f;
				}
			} else {
				shown = cur;
				fade = Math.min(1f, fade + dt / 150f);
			}
		}
		anim = Math.max(0f, Math.min(1f, anim + (want ? dt : -dt) / ANIM_MS));
		if (!want && anim <= 0f) {
			shown = null;
		}
		rebuild(font, s);
	}

	private void rebuild(Font font, NowPlayingSettings s) {
		if (shown == null) {
			return;
		}
		int maxW = Math.max(40, Math.min(200, s.maxTrackWidth));
		if (!shown.title.equals(cacheTitleSrc) || maxW != cacheMaxW) {
			cacheTitleSrc = shown.title;
			titleShown = ellipsize(font, shown.title.isEmpty() ? "Unknown" : shown.title, maxW, true);
			titleW = Gfx.width(font, Component.literal(titleShown).withStyle(ChatFormatting.BOLD));
		}
		if (!shown.artist.equals(cacheArtistSrc) || maxW != cacheMaxW) {
			cacheArtistSrc = shown.artist;
			artistShown = ellipsize(font, shown.artist, Math.round(maxW * 0.75f / ARTIST_SCALE), false);
			artistW = Math.round(Gfx.width(font, artistShown) * ARTIST_SCALE);
		}
		cacheMaxW = maxW;
		if (timeW == 0) {
			timeW = Gfx.width(font, "00:00 / 00:00");
		}
	}

	/** Обрезка с «...» по ширине (аналог trimToWidth). */
	private static String ellipsize(Font font, String text, int maxW, boolean bold) {
		java.util.function.ToIntFunction<String> w = str -> bold
				? Gfx.width(font, Component.literal(str).withStyle(ChatFormatting.BOLD))
				: Gfx.width(font, str);
		String t = text;
		if (w.applyAsInt(t) <= maxW) {
			return t;
		}
		while (t.length() > 1 && w.applyAsInt(t + "...") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "...";
	}

	/** Ширина музыкальной строки при полном раскрытии (симметрична относительно центра, таймкод строго по центру). */
	public int rowWidth() {
		if (shown == null) {
			return 0;
		}
		NowPlayingSettings s = PvpConfig.get().nowPlaying;
		int left = (s.showCoverArt ? COVER + 4 : 0) + titleW;
		int right = s.showTrackArtist ? artistW : 0;
		int half = Math.max(left, right);
		int center = s.showTrackTime ? timeW / 2 + 10 : 6;
		return 2 * (half + center) + 2 * PAD;
	}

	private static String fmt(int sec) {
		sec = Math.max(0, sec);
		return String.format("%02d:%02d", sec / 60, sec % 60);
	}

	/**
	 * Рисует содержимое строки в прямоугольнике (x, y, w, h) (clipL..clipR — видимая часть). Текст выезжает снизу (bounce) и проявляется; всё
	 * обрезано по прямоугольнику, поэтому выезд выглядит чисто.
	 */
	public void renderRow(GuiGraphics g, Font font, int x, int y, int w, int h, int clipL, int clipR) {
		if (shown == null || anim <= 0.001f) {
			return;
		}
		NowPlayingSettings s = PvpConfig.get().nowPlaying;
		float p2 = Math.max(0f, Math.min(1f, (anim - 0.25f) / 0.75f));
		float a = Math.max(0f, Math.min(1f, p2 * 1.4f)) * Math.max(0f, Math.min(1f, fade));
		if (a <= 0.01f) {
			return;
		}
		float slide = (1f - easeOutBack(p2)) * 8f;

		int sec = MediaManager.positionSeconds();
		if (sec != cachedSec || shown.durationSeconds != cachedDur) {
			cachedSec = sec;
			cachedDur = shown.durationSeconds;
			timeStr = fmt(sec) + " / " + fmt(shown.durationSeconds);
		}

		float prev = Gfx.alpha;
		Gfx.alpha = prev * a;
		g.enableScissor(clipL, y, clipR, y + h);
		float cy = y + h / 2f + slide;
		int left = x + PAD;

		if (s.showCoverArt) {
			int top = Math.round(cy - COVER / 2f);
			Identifier tex = CoverArtCache.get(shown);
			if (tex != null) {
				int col = (Math.max(1, Math.round(255 * Gfx.alpha)) << 24) | 0xFFFFFF;
				g.blit(RenderPipelines.GUI_TEXTURED, tex, left, top, 0f, 0f, COVER, COVER, 96, 96, 96, 96, col);
			} else {
				placeholder(g, left, top);
			}
			left += COVER + 4;
		}
		Gfx.text(g, font, Component.literal(titleShown).withStyle(ChatFormatting.BOLD), left,
				Math.round(cy - 4), EchoTheme.TEXT, false);

		if (s.showTrackTime) {
			Gfx.text(g, font, timeStr, x + (w - timeW) / 2, Math.round(cy - 4), EchoTheme.ACCENT_TEXT);
		}
		if (s.showTrackArtist && !artistShown.isEmpty()) {
			Gfx.textScaled(g, font, artistShown, x + w - PAD - artistW, cy - 4f * ARTIST_SCALE - 0.5f, ARTIST_SCALE,
					RoundedRender.scaleAlpha(EchoTheme.MUTED, 0.7f), false);
		}
		g.disableScissor();
		Gfx.alpha = prev;
	}

	/** Заглушка 16×16: фиолетовый скруглённый квадрат с нотой. */
	private static void placeholder(GuiGraphics g, int x, int y) {
		Gfx.rrect(g, x, y, COVER, COVER, 4f, EchoTheme.TAB_TOP, EchoTheme.TAB_BOTTOM, 0, 0f);
		Gfx.circle(g, x + 6.3f, y + 11.2f, 1.9f, EchoTheme.TEXT);
		Gfx.line(g, x + 8f, y + 11.2f, x + 8f, y + 4.2f, 1.2f, EchoTheme.TEXT);
		Gfx.line(g, x + 8f, y + 4.2f, x + 11.8f, y + 5.8f, 1.3f, EchoTheme.TEXT);
	}
}
