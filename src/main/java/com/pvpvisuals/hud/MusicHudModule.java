package com.pvpvisuals.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.pvpvisuals.config.MusicHudSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.media.CoverArtCache;
import com.pvpvisuals.media.MediaManager;
import com.pvpvisuals.media.SyncedLyrics;
import com.pvpvisuals.media.TrackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Отдельная карточка Music HUD: обложка, трек, исполнитель, эквалайзер, субтитры и таймлайн. */
public final class MusicHudModule extends AbstractHudModule {
	private static final int W = 178;
	private static final float PAD = 8f;
	private static final float COVER = 34f;
	private static final float LYRIC_SIZE = 7f;
	private static final float LYRIC_ROW = 10f;

	private record LyricLayout(float size, List<String> lines) {
		static LyricLayout empty() {
			return new LyricLayout(LYRIC_SIZE, List.of());
		}
	}

	private String shownKey = "";
	private float progress;
	private float beat;
	private float trackFade = 1f;

	@Override public String id() { return "music"; }
	@Override public String label() { return "Music HUD"; }
	@Override public int defaultX() { return 8; }
	@Override public int defaultY() { return 92; }
	@Override public boolean enabled() { return PvpConfig.get().musicHud.enabled; }

	@Override
	protected int contentWidth() {
		return W;
	}

	private MusicHudSettings settings() {
		return PvpConfig.get().musicHud;
	}

	private LyricLayout lyricLayout(TrackInfo track) {
		if (!settings().showLyrics || track == null) {
			return LyricLayout.empty();
		}
		SyncedLyrics.ensure(track.key, track.artist, track.title);
		if (!SyncedLyrics.has(track.key)) {
			return LyricLayout.empty();
		}
		long positionMs = MediaManager.positionSeconds() * 1000L;
		SyncedLyrics.Line line = SyncedLyrics.current(track.key, positionMs);
		String raw = line == null ? "♫" : line.text();
		return wrap(raw, W - PAD * 2f);
	}

	private LyricLayout wrap(String raw, float maxWidth) {
		Minecraft mc = Minecraft.getInstance();
		String text = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ').trim();
		if (text.isEmpty()) {
			return LyricLayout.empty();
		}
		StringBuilder first = new StringBuilder();
		StringBuilder second = new StringBuilder();
		for (String word : text.split("\\s+")) {
			String candidate = first.isEmpty() ? word : first + " " + word;
			if (second.isEmpty() && Gfx.widthAtSize(mc.font, candidate, LYRIC_SIZE) <= maxWidth) {
				first.setLength(0);
				first.append(candidate);
				continue;
			}
			String next = second.isEmpty() ? word : second + " " + word;
			second.setLength(0);
			second.append(next);
		}
		if (second.isEmpty()) {
			return new LyricLayout(LYRIC_SIZE, List.of(first.toString()));
		}
		String firstLine = first.isEmpty()
				? Gfx.trimAtSize(mc.font, second.toString(), Math.round(maxWidth), LYRIC_SIZE)
				: first.toString();
		String secondLine = first.isEmpty() ? "" :
				Gfx.trimAtSize(mc.font, second.toString(), Math.round(maxWidth), LYRIC_SIZE);
		List<String> rows = secondLine.isEmpty() ? List.of(firstLine) : List.of(firstLine, secondLine);
		return new LyricLayout(LYRIC_SIZE, rows);
	}

	@Override
	protected int contentHeight() {
		TrackInfo track = MediaManager.track();
		int rows = lyricLayout(track).lines().size();
		float y = PAD + COVER;
		if (rows > 0) {
			y += 5f + rows * LYRIC_ROW + 3f;
		}
		return Math.round(y + 13f + PAD);
	}

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		TrackInfo track = MediaManager.available() ? MediaManager.track() : null;
		if (track == null && !preview) {
			return;
		}

		if (track != null && !track.key.equals(shownKey)) {
			shownKey = track.key;
			progress = 0f;
			trackFade = 0f;
		}
		trackFade = Math.min(1f, trackFade + 0.16f);

		LyricLayout lyrics = lyricLayout(track);
		int height = contentHeight();
		float alpha = HudAlpha.current;
		HudStyle.panel(g, 0, 0, W, height, 8, alpha);
		HudStyle.accentWash(g, 0, 0, Math.min(48, W), height, 8, alpha);

		String title = track == null || track.title.isBlank() ? "Ничего не играет" : track.title;
		String artist = track == null ? "" : track.artist;
		String source = track == null ? "" : displaySource(track.app);
		if (settings().showSource && !source.isEmpty()) {
			artist = artist.isEmpty() ? source : artist + "  ·  " + source;
		}

		float textX = PAD;
		if (settings().showCover) {
			drawCover(g, track, PAD, PAD);
			textX += COVER + 7f;
		}
		float eqRight = W - PAD;
		float textMax = Math.max(12f, eqRight - textX - 18f);
		drawMarquee(g, mc.font, title, textX, PAD + 2f, textMax, 8f,
				HudAlpha.mul(HudStyle.TEXT));
		if (!artist.isBlank()) {
			drawMarquee(g, mc.font, artist, textX, PAD + 16f, textMax, 6.2f,
					HudAlpha.mul(HudStyle.MUTED));
		}
		drawEqualizer(g, eqRight, PAD + 1f);

		float rowY = PAD + COVER;
		if (!lyrics.lines().isEmpty() && track != null) {
			rowY += 5f;
			drawLyrics(g, mc.font, track, lyrics, PAD, rowY, MediaManager.positionSeconds() * 1000L);
			rowY += lyrics.lines().size() * LYRIC_ROW + 3f;
		}
		drawProgress(g, mc.font, track, PAD, rowY, W - PAD * 2f);
	}

	private void drawMarquee(GuiGraphics g, Font font, String value, float x, float y, float maxWidth,
			float size, int color) {
		if (value == null || value.isBlank() || maxWidth <= 1f) {
			return;
		}
		float textWidth = Gfx.widthAtSize(font, value, size);
		if (textWidth <= maxWidth) {
			Gfx.textAtSize(g, font, value, x, y, size, color, false);
			return;
		}
		int left = Math.max(0, (int) Math.floor(x));
		int top = Math.max(0, (int) Math.floor(y - 1));
		int right = (int) Math.ceil(x + maxWidth);
		g.enableScissor(left, top, right, top + 12);
		float gap = 20f;
		float offset = (System.currentTimeMillis() / 38f) % (textWidth + gap);
		Gfx.textAtSize(g, font, value, x - offset, y, size, color, false);
		Gfx.textAtSize(g, font, value, x - offset + textWidth + gap, y, size, color, false);
		g.disableScissor();
	}

	private void drawCover(GuiGraphics g, TrackInfo track, float x, float y) {
		Identifier texture = track == null ? null : CoverArtCache.get(track);
		int px = Math.round(x);
		int py = Math.round(y);
		if (texture != null) {
			int tint = HudAlpha.mul(0xFFFFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0f, 0f,
					Math.round(COVER), Math.round(COVER), 96, 96, 96, 96, tint);
			Gfx.ring(g, px, py, Math.round(COVER), Math.round(COVER), 5, 1,
					HudAlpha.mul(0x38FFFFFF));
			return;
		}

		float pulse = MediaManager.playing()
				? 1f + 0.06f * (float) Math.sin(System.currentTimeMillis() / 320.0)
				: 1f;
		Gfx.rrect(g, x, y, COVER, COVER, 6f,
				HudStyle.opacity(EchoTheme.ACCENT_A, alpha(0.95f)),
				HudStyle.opacity(EchoTheme.ACCENT_B, alpha(0.88f)), 0x00000000, 0f);
		int ink = HudStyle.opacity(HudStyle.TEXT, alpha(0.95f));
		float cx = x + COVER / 2f;
		float cy = y + COVER / 2f;
		float head = 5.2f * pulse;
		Gfx.circle(g, cx - 5f, cy + 4f, head / 2.7f, ink);
		Gfx.circle(g, cx + 3.2f, cy + 1.5f, head / 2.7f, ink);
		Gfx.line(g, cx - 3.4f, cy + 4f, cx - 3.4f, cy - 6f, 1.3f, ink);
		Gfx.line(g, cx + 4.8f, cy + 1.5f, cx + 4.8f, cy - 8f, 1.3f, ink);
		Gfx.line(g, cx - 3.4f, cy - 6f, cx + 4.8f, cy - 8f, 1.3f, ink);
	}

	private static float alpha(float factor) {
		return Math.max(0f, Math.min(1f, HudAlpha.current * factor));
	}

	private void drawEqualizer(GuiGraphics g, float right, float top) {
		float barWidth = 2.1f;
		float gap = 1.8f;
		float maxHeight = 9f;
		float bottom = top + maxHeight;
		double seconds = System.currentTimeMillis() / 1000.0;
		float target = MediaManager.playing() ? 1f : 0f;
		beat += (target - beat) * 0.12f;
		for (int i = 0; i < 4; i++) {
			double phase = seconds * (2.6 + i * 0.47) + i * 1.7;
			double slow = Math.sin(seconds * (1.1 + i * 0.19) + i);
			float level = (float) (0.5 + 0.35 * Math.sin(phase) + 0.15 * slow);
			level = Math.max(0.08f, Math.min(1f, level));
			float h = Math.max(2.1f, maxHeight * (0.18f + 0.82f * level * beat));
			float bx = right - barWidth - i * (barWidth + gap);
			int color = RoundedRender.lerp(HudStyle.MUTED, HudStyle.ACCENT, level);
			Gfx.rrect(g, bx, bottom - h, barWidth, h, barWidth / 2f,
					HudStyle.opacity(color, alpha(0.45f + 0.5f * level)),
					HudStyle.opacity(color, alpha(0.45f + 0.5f * level)), 0x00000000, 0f);
		}
	}

	private void drawLyrics(GuiGraphics g, Font font, TrackInfo track, LyricLayout layout,
			float x, float y, long positionMs) {
		float totalWidth = 0f;
		for (String line : layout.lines()) {
			totalWidth += Gfx.widthAtSize(font, line, layout.size());
		}
		float sung = SyncedLyrics.progress(track.key, positionMs) * totalWidth;
		float consumed = 0f;
		for (int row = 0; row < layout.lines().size(); row++) {
			String line = layout.lines().get(row);
			float lineWidth = Gfx.widthAtSize(font, line, layout.size());
			float lit = Math.max(0f, Math.min(lineWidth, sung - consumed));
			int cut = 0;
			while (cut < line.length()
					&& Gfx.widthAtSize(font, line.substring(0, cut + 1), layout.size()) <= lit) {
				cut++;
			}
			String done = line.substring(0, cut);
			String rest = line.substring(cut);
			float rowY = y + row * LYRIC_ROW;
			Gfx.textAtSize(g, font, done, x, rowY, layout.size(), HudAlpha.mul(HudStyle.ACCENT), false);
			float doneWidth = Gfx.widthAtSize(font, done, layout.size());
			Gfx.textAtSize(g, font, rest, x + doneWidth, rowY, layout.size(),
					HudAlpha.mul(HudStyle.MUTED), false);
		}
	}

	private void drawProgress(GuiGraphics g, Font font, TrackInfo track, float x, float y, float width) {
		int position = track == null ? 0 : MediaManager.positionSeconds();
		int duration = track == null ? 0 : Math.max(0, track.durationSeconds);
		float target = duration > 0 ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
		if (Math.abs(progress - target) < 0.0015f) {
			progress = target;
		} else {
			progress += (target - progress) * 0.22f;
		}

		String elapsed = time(position);
		String remaining = duration > 0 ? "-" + time(Math.max(0, duration - position)) : "--:--";
		float timeSize = 5.8f;
		float elapsedWidth = Gfx.widthAtSize(font, elapsed, timeSize);
		float remainingWidth = Gfx.widthAtSize(font, remaining, timeSize);
		float barX = x + elapsedWidth + 7f;
		float barWidth = Math.max(12f, width - elapsedWidth - remainingWidth - 14f);

		Gfx.textAtSize(g, font, elapsed, x, y, timeSize, HudAlpha.mul(HudStyle.MUTED), false);
		Gfx.textAtSize(g, font, remaining, x + width - remainingWidth, y, timeSize,
				HudAlpha.mul(HudStyle.MUTED), false);

		float barY = y + 9f;
		Gfx.rrect(g, barX, barY, barWidth, 3f, 1.5f,
				HudStyle.opacity(HudStyle.MUTED, alpha(0.25f)),
				HudStyle.opacity(HudStyle.MUTED, alpha(0.25f)), 0x00000000, 0f);
		float filled = barWidth * progress;
		if (filled > 0.5f) {
			int start = HudStyle.opacity(EchoTheme.ACCENT_A, alpha(0.95f));
			int end = HudStyle.opacity(EchoTheme.ACCENT_B, alpha(0.95f));
			Gfx.rrect(g, barX, barY, filled, 3f, 1.5f, start, end, 0x00000000, 0f);
			Gfx.circle(g, barX + filled, barY + 1.5f, 2.1f, HudAlpha.mul(0xFFFFFFFF));
		}
	}

	private static String time(int seconds) {
		int value = Math.max(0, seconds);
		return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60);
	}

	private static String displaySource(String app) {
		if (app == null || app.isBlank()) {
			return "";
		}
		String lower = app.toLowerCase(Locale.ROOT);
		if (lower.contains("spotify")) return "Spotify";
		if (lower.contains("soundcloud")) return "SoundCloud";
		if (lower.contains("yandex") || lower.contains("music.yandex")) return "Яндекс Музыка";
		if (lower.contains("vk")) return "VK Музыка";
		if (lower.contains("deezer")) return "Deezer";
		if (lower.contains("tidal")) return "TIDAL";
		if (lower.contains("apple") || lower.contains("itunes")) return "Apple Music";
		if (lower.contains("youtube")) return "YouTube";
		if (lower.contains("chrome")) return "Chrome";
		if (lower.contains("msedge") || lower.contains("edge")) return "Edge";
		if (lower.contains("firefox")) return "Firefox";
		if (lower.contains("opera")) return "Opera";
		if (lower.contains("aimp")) return "AIMP";
		if (lower.contains("foobar")) return "foobar2000";
		if (lower.contains("vlc")) return "VLC";
		int dot = app.indexOf('.');
		String name = dot > 0 ? app.substring(0, dot) : app;
		name = name.replace("exe", "").replace("!", "").trim();
		return name.isEmpty() ? "Музыка"
				: Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}
}
