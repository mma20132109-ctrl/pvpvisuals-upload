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
	private static final int W = 164;
	private static final float PAD = 6f;
	private static final float COVER = 33f;
	private static final float LYRIC_SIZE = 8.5f;
	private static final float LYRIC_ROW = 11f;

	private record LyricLayout(float size, List<String> lines) {
		static LyricLayout empty() {
			return new LyricLayout(LYRIC_SIZE, List.of());
		}

		float rows() {
			return lines.size();
		}
	}

	private String shownKey = "";
	private String lastLineShown = "";
	private String previousLine = "";
	private float progress;
	private float beat;
	private float trackFade = 1f;
	private float coverFade;
	private float lineFade = 1f;
	private float lyricsRoom;
	private float lyricRows = 1f;
	private float sungWidth;

	@Override public String id() { return "music"; }
	@Override public String label() { return "Music HUD"; }
	@Override public int defaultX() { return 8; }
	@Override public int defaultY() { return 92; }
	@Override public boolean enabled() { return PvpConfig.get().musicHud.enabled; }

	@Override
	protected int contentWidth() {
		return W;
	}

	private MusicHudSettings musicSettings() {
		return PvpConfig.get().musicHud;
	}

	private LyricLayout lyricLayout(TrackInfo track) {
		if (!musicSettings().showLyrics || track == null) {
			return LyricLayout.empty();
		}
		SyncedLyrics.ensure(track.key, track.artist, track.title, track.durationSeconds);
		if (!SyncedLyrics.has(track.key)) {
			return LyricLayout.empty();
		}
		long positionMs = MediaManager.positionMillis();
		SyncedLyrics.Line line = SyncedLyrics.current(track.key, positionMs);
		String raw = line == null ? "♫" : line.text();
		return wrap(raw, W - PAD * 2f);
	}

	private LyricLayout wrap(String raw, float maxWidth) {
		Minecraft mc = Minecraft.getInstance();
		String text = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ').trim();
		if (text.isEmpty() || maxWidth <= 0f) {
			return LyricLayout.empty();
		}

		// First try to keep the lyric on one line. Shrink only slightly before wrapping,
		// so two readable rows are preferred over a tiny single row.
		for (float size = LYRIC_SIZE; size >= LYRIC_SIZE - 0.5f; size -= 0.5f) {
			if (Gfx.widthAtSize(mc.font, text, size) <= maxWidth) {
				return new LyricLayout(size, List.of(text));
			}
		}

		float size = LYRIC_SIZE - 0.5f;
		StringBuilder first = new StringBuilder();
		StringBuilder second = new StringBuilder();
		for (String word : text.split("\\s+")) {
			if (second.isEmpty()) {
				String candidate = first.isEmpty() ? word : first + " " + word;
				if (Gfx.widthAtSize(mc.font, candidate, size) <= maxWidth) {
					first.setLength(0);
					first.append(candidate);
					continue;
				}
			}
			if (!second.isEmpty()) second.append(' ');
			second.append(word);
		}

		if (second.isEmpty()) {
			// A single unusually long word still gets a chance to fit without being cut off.
			while (size > 4f && Gfx.widthAtSize(mc.font, first.toString(), size) > maxWidth) {
				size -= 0.25f;
			}
			return new LyricLayout(size, List.of(first.toString()));
		}
		while (size > 4f && Gfx.widthAtSize(mc.font, second.toString(), size) > maxWidth) {
			size -= 0.25f;
		}
		return new LyricLayout(size, List.of(first.toString(), second.toString()));
	}

	@Override
	protected int contentHeight() {
		float lyricHeight = lyricsRoom > 0.01f
				? (LYRIC_ROW * Math.max(1f, lyricRows) + 3f) * lyricsRoom
				: 0f;
		return Math.round(PAD * 2f + COVER + lyricHeight + 8f);
	}

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		// Keep the card and its text visible while the media bridge is waiting for a track.
		// The previous early return made the card completely blank outside editor preview,
		// even though the title fallback below was intended to display an idle state.
		TrackInfo track = MediaManager.track();

		if (track != null && !track.key.equals(shownKey)) {
			shownKey = track.key;
			progress = 0f;
			trackFade = 0f;
			coverFade = 0f;
			lastLineShown = "";
			previousLine = "";
			lineFade = 1f;
			sungWidth = 0f;
		}
		trackFade += (1f - trackFade) * 0.2f;

		LyricLayout lyrics = lyricLayout(track);
		boolean lyricsKnown = track != null && musicSettings().showLyrics && SyncedLyrics.has(track.key);
		float wantedRows = lyrics.lines().isEmpty() ? (lyricsKnown ? 1f : 0f) : lyrics.rows();
		lyricRows += (wantedRows - lyricRows) * 0.22f;
		lyricsRoom += ((lyricsKnown ? 1f : 0f) - lyricsRoom) * 0.22f;
		float lyricHeight = (LYRIC_ROW * Math.max(1f, lyricRows) + 3f) * lyricsRoom;
		int height = Math.round(PAD * 2f + COVER + lyricHeight + 8f);

		float alpha = HudAlpha.current;
		HudStyle.panel(g, 0, 0, W, height, 8, alpha);
		HudStyle.accentWash(g, 0, 0, Math.min(48, W), height, 8, alpha);

		String title = track == null
				? "Ожидание воспроизведения"
				: track.title.isBlank() ? "Неизвестный трек" : track.title;
		String artist = track == null ? "Запусти музыку в плеере" : track.artist;
		String source = track == null ? "" : displaySource(track.app);
		if (musicSettings().showSource && !source.isEmpty()) {
			artist = artist.isEmpty() ? source : artist + "  ·  " + source;
		}
		float trackAlpha = 0.25f + 0.75f * trackFade;

		float textX = PAD;
		if (musicSettings().showCover) {
			drawCover(g, track, PAD, PAD);
			textX += COVER + 7f;
		}
		float eqRight = W - PAD;
		float textMax = Math.max(12f, eqRight - textX - 18f);
		drawMarquee(g, mc.font, title, textX, PAD + 1.5f, textMax, 9f,
				HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), trackAlpha));
		if (!artist.isBlank()) {
			drawMarquee(g, mc.font, artist, textX, PAD + 14f, textMax, 7f,
					HudStyle.opacity(HudAlpha.mul(HudStyle.MUTED), trackAlpha * 0.72f));
		}
		drawEqualizer(g, eqRight, PAD + 1f);

		float rowY = PAD + COVER;
		if (lyricsRoom > 0.01f) {
			rowY += 2f;
			if (!lyrics.lines().isEmpty() && track != null) {
				drawLyrics(g, mc.font, track, lyrics, PAD, rowY, MediaManager.positionMillis());
			}
			rowY += lyricHeight;
		}
		drawProgress(g, mc.font, track, PAD, rowY + 3f, W - PAD * 2f);
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
		coverFade += ((texture != null ? 1f : 0f) - coverFade) * 0.18f;

		// Keep the animated music-note tile underneath while the artwork fades in.
		float pulse = MediaManager.playing()
				? 1f + 0.08f * (float) Math.sin(System.currentTimeMillis() / 320.0)
				: 1f;
		float wave = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1600.0));
		int first = RoundedRender.lerp(EchoTheme.ACCENT_A, EchoTheme.ACCENT_B, wave);
		int second = RoundedRender.lerp(EchoTheme.ACCENT_B, EchoTheme.ACCENT_A, wave);
		Gfx.rrect(g, x, y, COVER, COVER, 6f,
				HudStyle.opacity(first, alpha(0.98f)),
				HudStyle.opacity(second, alpha(0.92f)), 0x00000000, 0f);
		int ink = HudStyle.opacity(HudStyle.TEXT, alpha(0.92f));
		float cx = x + COVER / 2f;
		float cy = y + COVER / 2f;
		float head = 5.5f * pulse;
		Gfx.circle(g, cx - 5f, cy + 3f, head / 2.7f, ink);
		Gfx.circle(g, cx + 3.2f, cy + 1f, head / 2.7f, ink);
		Gfx.line(g, cx - 3.4f, cy + 3f, cx - 3.4f, cy - 7f, 1.3f, ink);
		Gfx.line(g, cx + 4.8f, cy + 1f, cx + 4.8f, cy - 8f, 1.3f, ink);
		Gfx.line(g, cx - 3.4f, cy - 7f, cx + 4.8f, cy - 8f, 1.3f, ink);

		if (texture != null && coverFade > 0.001f) {
			int tint = HudStyle.opacity(0xFFFFFFFF, alpha(coverFade));
			int px = Math.round(x);
			int py = Math.round(y);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, px, py, 0f, 0f,
					Math.round(COVER), Math.round(COVER), 96, 96, 96, 96, tint);
			Gfx.ring(g, px, py, Math.round(COVER), Math.round(COVER), 5, 1,
					HudAlpha.mul(0x38FFFFFF));
		}
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
		if (layout.lines().isEmpty()) {
			return;
		}
		String key = String.join("\n", layout.lines());
		float totalWidth = totalWidth(font, layout);
		float lyricProgress = Math.max(0f, Math.min(1f, SyncedLyrics.progress(track.key, positionMs)));
		if (!key.equals(lastLineShown)) {
			previousLine = lastLineShown;
			lastLineShown = key;
			lineFade = 0f;
			sungWidth = lyricProgress * totalWidth;
		}
		lineFade += (1f - lineFade) * 0.16f;
		float eased = lineFade * lineFade * (3f - 2f * lineFade);

		if (!previousLine.isEmpty() && eased < 0.999f) {
			String leaving = previousLine.split("\n", 2)[0];
			Gfx.textAtSize(g, font, leaving, x, y - 2f * eased, layout.size(),
					HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), 0.3f * (1f - eased)), false);
		}

		float targetWidth = lyricProgress * totalWidth;
		sungWidth += (targetWidth - sungWidth) * 0.9f;
		float lift = (1f - eased) * 2.5f;
		float textAlpha = eased * lyricsRoom;

		// Prefer real per-word timestamps from LRCLIB Lyricsfile when the entry has them.
		// Ordinary LRC has only line timestamps, so it falls through to the estimated sweep below.
		SyncedLyrics.Line timedLine = SyncedLyrics.current(track.key, positionMs);
		SyncedLyrics.WordTiming wordTiming = SyncedLyrics.wordTiming(track.key, positionMs);
		if (wordTiming != null && timedLine != null
				&& String.join(" ", layout.lines()).equals(timedLine.text().trim())) {
			drawWordTimedLyrics(g, font, layout, wordTiming, x, y, lift, textAlpha);
			return;
		}

		float consumed = 0f;
		for (int row = 0; row < layout.lines().size(); row++) {
			String line = layout.lines().get(row);
			float lineWidth = Gfx.widthAtSize(font, line, layout.size());
			float lit = Math.max(0f, Math.min(lineWidth, sungWidth - consumed));
			int cut = 0;
			while (cut < line.length()
					&& Gfx.widthAtSize(font, line.substring(0, cut + 1), layout.size()) <= lit) {
				cut++;
			}
			String done = line.substring(0, cut);
			String rest = line.substring(cut);
			float doneWidth = Gfx.widthAtSize(font, done, layout.size());
			float rowY = y + row * LYRIC_ROW + lift;
			Gfx.textAtSize(g, font, done, x, rowY, layout.size(),
					HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), textAlpha), false);
			Gfx.textAtSize(g, font, rest, x + doneWidth, rowY, layout.size(),
					HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), textAlpha * 0.35f), false);

			// Fade the character currently being sung instead of snapping at a letter boundary.
			if (cut < line.length() && lit > 0f) {
				String next = String.valueOf(line.charAt(cut));
				float nextWidth = Math.max(0.5f, Gfx.widthAtSize(font, next, layout.size()));
				float within = Math.max(0f, Math.min(1f, (lit - doneWidth) / nextWidth));
				Gfx.textAtSize(g, font, next, x + doneWidth, rowY, layout.size(),
					HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), textAlpha * within), false);
			}
			consumed += lineWidth;
		}
	}

	/** Paints completed, currently spoken, and upcoming words using their own LRCLIB timestamps. */
	private void drawWordTimedLyrics(GuiGraphics g, Font font, LyricLayout layout,
			SyncedLyrics.WordTiming timing, float x, float y, float lift, float alpha) {
		int charOffset = 0;
		int activeColor = RoundedRender.lerp(HudStyle.TEXT, HudStyle.ACCENT, timing.progress());
		for (int row = 0; row < layout.lines().size(); row++) {
			String line = layout.lines().get(row);
			int rowEnd = charOffset + line.length();
			float rowY = y + row * LYRIC_ROW + lift;

			if (timing.startChar() >= rowEnd) {
				// This entire row precedes the word currently being sung.
				Gfx.textAtSize(g, font, line, x, rowY, layout.size(),
						HudStyle.opacity(HudAlpha.mul(HudStyle.ACCENT), alpha), false);
			} else if (timing.endChar() <= charOffset) {
				// This row follows the currently sung word.
				Gfx.textAtSize(g, font, line, x, rowY, layout.size(),
						HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), alpha * 0.35f), false);
			} else {
				int localStart = Math.max(0, Math.min(line.length(), timing.startChar() - charOffset));
				int localEnd = Math.max(localStart, Math.min(line.length(), timing.endChar() - charOffset));
				String done = line.substring(0, localStart);
				String active = line.substring(localStart, localEnd);
				String upcoming = line.substring(localEnd);
				float doneWidth = Gfx.widthAtSize(font, done, layout.size());
				float activeWidth = Gfx.widthAtSize(font, active, layout.size());

				Gfx.textAtSize(g, font, done, x, rowY, layout.size(),
						HudStyle.opacity(HudAlpha.mul(HudStyle.ACCENT), alpha), false);
				Gfx.textAtSize(g, font, active, x + doneWidth, rowY, layout.size(),
						HudStyle.opacity(HudAlpha.mul(activeColor), alpha), false);
				Gfx.textAtSize(g, font, upcoming, x + doneWidth + activeWidth, rowY, layout.size(),
						HudStyle.opacity(HudAlpha.mul(HudStyle.TEXT), alpha * 0.35f), false);
			}

			// Lyric wrapping joins rows with one space; keep offsets aligned with Lyricsfile word text.
			charOffset = rowEnd + (row + 1 < layout.lines().size() ? 1 : 0);
		}
	}

	private static float totalWidth(Font font, LyricLayout layout) {
		float width = 0f;
		for (String line : layout.lines()) {
			width += Gfx.widthAtSize(font, line, layout.size());
		}
		return Math.max(1f, width);
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

		// Keep the progress rail and its thumb on the same horizontal line as both time labels.
		float barY = y + 1.5f;
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
