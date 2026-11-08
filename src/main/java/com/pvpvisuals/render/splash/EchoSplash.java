package com.pvpvisuals.render.splash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.SplashSettings;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;

/**
 * Экран загрузки Echo Visual. Рисуется поверх ванильного LoadingOverlay (миксин в конце render): ваниль продолжает
 * вести состояние загрузки (прогресс, завершение, переход в меню), а все её картинки (красный фон, логотип Mojang,
 * полоса) заменены или скрыты миксином.
 *
 * <p>Время: вход логотипа 800 мс (fade-in + scale-up, ease-out-cubic), затем пульсация 1.0 -> 1.02 -> 1.0; при
 * завершении загрузки элементы гаснут за 500 мс, а фон дальше исчезает вместе с ванильным переходом в главное меню
 * (ваниль держит фон 1 с и растворяет его ещё 1 с — наш фон повторяет эту кривую).
 */
public final class EchoSplash {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/splash");

	private static final float INTRO_MS = 800f;
	private static final float OUT_MS = 500f;

	private static Object lastOverlay;
	private static long startMs;
	private static long lastFrameMs;
	private static float shown;
	private static boolean failed;
	private static boolean logged;

	private EchoSplash() { }

	/** Нужно ли заменять ванильный экран (настройка включена и наш рендер не упал). */
	public static boolean enabled() {
		return !failed && PvpConfig.get().splash.customSplashScreen;
	}

	/** Цвет ванильного фона загрузки: тёмный из нашей палитры вместо красного Mojang (RGB, без альфы). */
	public static int backgroundRgb() {
		return EchoTheme.shift(0xFF07040D) & 0xFFFFFF;
	}

	private static float easeOutCubic(float t) {
		float p = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - p * p * p;
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	/** Статус по прогрессу. Ваниль не даёт названий стадий, поэтому подпись оценивается по доле загрузки. */
	private static String status(float p, boolean done) {
		if (done) {
			return "Ready";
		}
		if (p < 0.20f) {
			return "Loading resources...";
		}
		if (p < 0.45f) {
			return "Loading textures...";
		}
		if (p < 0.70f) {
			return "Loading models and sounds...";
		}
		if (p < 0.90f) {
			return "Initializing mods...";
		}
		return "Almost ready...";
	}

	/** Вызывается миксином в конце LoadingOverlay#render. Любая ошибка отключает наш экран, игра не падает. */
	public static void render(Object overlay, GuiGraphics g, float progress, long fadeOutStart) {
		if (!enabled()) {
			return;
		}
		float prevAlpha = Gfx.alpha;
		try {
			draw(overlay, g, progress, fadeOutStart);
		} catch (Throwable e) {
			failed = true;
			LOG.error("Echo splash failed and was disabled, vanilla loading screen is used instead", e);
		} finally {
			Gfx.alpha = prevAlpha;
		}
	}

	private static void draw(Object overlay, GuiGraphics g, float progress, long fadeOutStart) {
		long now = Util.getMillis();
		if (overlay != lastOverlay) { // новый оверлей (например, перезагрузка ресурсов по F3+T): анимация с начала
			lastOverlay = overlay;
			startMs = now;
			lastFrameMs = now;
			shown = 0f;
		}
		float dt = Math.min(0.1f, (now - lastFrameMs) / 1000f);
		lastFrameMs = now;
		if (!logged) {
			logged = true;
			LOG.info("Echo splash is drawing");
		}
		EchoTheme.sync();

		boolean done = fadeOutStart > 0;
		float out = done ? (now - fadeOutStart) : -1f;
		float bgAlpha = out < 1000f ? 1f : clamp01(1f - (out - 1000f) / 1000f);
		if (bgAlpha <= 0.003f) {
			return;
		}
		float intro = easeOutCubic((now - startMs) / INTRO_MS);
		float elemAlpha = intro * (done ? clamp01(1f - out / OUT_MS) : 1f);

		// плавный прогресс: догоняем реальный (он у ванили уже сглажен), по завершении доводим до 100%
		float target = done ? 1f : clamp01(progress);
		shown += (target - shown) * (1f - (float) Math.exp(-dt * 7.0));
		if (done && shown > 0.995f) {
			shown = 1f;
		}

		int w = g.guiWidth();
		int h = g.guiHeight();
		float t = (now - startMs) / 1000f;
		SplashSettings s = PvpConfig.get().splash;

		float cx = w / 2f;
		int logoGui = Math.max(36, Math.min(110, Math.round(h * 0.20f)));
		float cy = h * 0.44f;

		Gfx.alpha = bgAlpha;
		drawBackground(g, w, h, t, s.splashBackground);

		Gfx.alpha = elemAlpha;
		if (s.showSplashParticles) {
			drawParticles(g, cx, cy, logoGui, t);
		}
		drawLogo(g, cx, cy, logoGui, t, intro);
		drawBar(g, w, cy + logoGui / 2f + 20f, t, done);
		drawStatus(g, cx, cy + logoGui / 2f + 20f + 14f, status(shown, done));
	}

	// ---------- фон ----------

	public static void drawBackground(GuiGraphics g, int w, int h, float t, String mode) {
		boolean dark = "DARK".equalsIgnoreCase(mode);
		boolean animated = "SHADER".equalsIgnoreCase(mode);
		int top = dark ? 0xFF07090F : 0xFF080A14;
		int bottom = dark ? 0xFF090B12 : 0xFF100B20;
		Gfx.rrect(g, 0, 0, w, h, 0, top, bottom, 0, 0f);
		if (dark) {
			return;
		}

		// Layered, slow-moving color fields create depth without invoking Minecraft's global blur pass.
		float breathe = 0.78f + 0.22f * (float) Math.sin(t * 0.34f);
		int primary = EchoTheme.accent();
		int secondary = EchoTheme.accent2();
		int violet = 0xFF6541C8;
		Gfx.glow(g, -w * 0.14f, h * 0.12f, w * 0.62f, h * 0.34f, h * 0.12f,
				withAlpha(primary, 0.16f * breathe), 28f);
		Gfx.glow(g, w * 0.57f, h * 0.18f, w * 0.62f, h * 0.38f, h * 0.15f,
				withAlpha(secondary, 0.13f), 30f);
		Gfx.glow(g, w * 0.18f, h * 0.76f, w * 0.66f, h * 0.18f, h * 0.08f,
				withAlpha(violet, 0.18f * breathe), 24f);

		if (!animated) {
			return;
		}

		// Four independent nebula lights drift on long, non-repeating-looking paths.
		for (int i = 0; i < 4; i++) {
			float phase = i * 1.71f;
			float px = w * (0.5f + 0.39f * (float) Math.sin(t * (0.075f + i * 0.013f) + phase));
			float py = h * (0.48f + 0.34f * (float) Math.cos(t * (0.061f + i * 0.009f) + phase * 1.23f));
			float radius = h * (0.075f + 0.018f * (i % 3));
			int col = (i % 2 == 0) ? primary : secondary;
			Gfx.glow(g, px - radius, py - radius, radius * 2f, radius * 2f, radius,
					withAlpha(col, 0.12f + 0.035f * (i % 2)), 22f);
		}

		// Deterministic fine stars with subtle independent twinkle; no random allocation per frame.
		for (int i = 0; i < 34; i++) {
			float sx = ((i * 0.6180339f) % 1f) * w;
			float sy = ((i * 0.7548776f + 0.13f) % 1f) * h;
			float twinkle = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (0.7f + (i % 5) * 0.19f) + i * 2.3f));
			float size = (i % 9 == 0) ? 1.35f : 0.65f + (i % 3) * 0.18f;
			int star = withAlpha(0xFFE4E9FF, twinkle * (i % 9 == 0 ? 0.72f : 0.36f));
			Gfx.circle(g, sx, sy, size, star);
			if (i % 9 == 0) {
				Gfx.line(g, sx - 3f, sy, sx + 3f, sy, 0.7f, withAlpha(secondary, twinkle * 0.35f));
				Gfx.line(g, sx, sy - 3f, sx, sy + 3f, 0.7f, withAlpha(secondary, twinkle * 0.35f));
			}
		}

		// Soft diagonal light streaks are deliberately faint, so the center stays readable.
		for (int i = 0; i < 3; i++) {
			float drift = (float) Math.sin(t * 0.11f + i * 1.9f) * 22f;
			float x = w * (0.18f + i * 0.30f) + drift;
			Gfx.line(g, x, h * 0.04f, x + w * 0.16f, h * 0.96f, 0.8f,
					withAlpha(i == 1 ? secondary : primary, 0.055f));
		}
	}

	// ---------- логотип ----------

	/** Дуга-«C» (открыта вправо) толстой линией со скруглёнными концами. */
	private static void arc(GuiGraphics g, float cx, float cy, float r, float thick, float gapDeg, int color) {
		double a0 = Math.toRadians(gapDeg);
		double a1 = Math.toRadians(360.0 - gapDeg);
		int seg = Math.max(24, (int) (r * 1.2f));
		float px = 0, py = 0;
		for (int i = 0; i <= seg; i++) {
			double a = a0 + (a1 - a0) * i / seg;
			float x = cx + (float) Math.cos(a) * r;
			float y = cy + (float) Math.sin(a) * r;
			if (i > 0) {
				Gfx.line(g, px, py, x, y, thick, color);
			}
			px = x;
			py = y;
		}
		Gfx.circle(g, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, thick / 2f, color);
		Gfx.circle(g, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, thick / 2f, color);
	}

	/**
	 * Логотип Echo Visual, нарисованный кодом (без картинок): две дуги «C», внутри диск с вырезом-«ртом»
	 * и буквой E. Единица u = радиус внешней дуги / 190 (пропорции с референса 512x512).
	 */
	public static void drawLogo(GuiGraphics g, float cx, float cy, int logoGui, float t, float intro) {
		float scale = 0.8f + 0.2f * intro; // scale-up на входе
		float pulse = 1f + 0.01f * (1f - (float) Math.cos(t * Math.PI * 2.0 / 2.4)); // 1.00 .. 1.02
		float u = logoGui * 0.5f / 190f * scale * pulse;
		float ox = cx + 35f * u; // центр дуг; визуальный центр логотипа смещён влево
		float oy = cy;

		int outer = EchoTheme.shift(0xFF6D3FD0);
		int middle = EchoTheme.shift(0xFF9B5CFF);
		int disc = EchoTheme.shift(0xFFB57BFF);
		int glowCol = EchoTheme.shift(0xFF8B3DFF);

		// мягкое свечение за логотипом
		float gr = 175f * u;
		Gfx.glow(g, ox - gr, oy - gr, gr * 2f, gr * 2f, gr, glowCol, 60f * u);

		arc(g, ox, oy, 190f * u, 26f * u, 47f, withAlpha(outer, 0.55f));
		arc(g, ox, oy, 140f * u, 30f * u, 47f, withAlpha(middle, 0.85f));

		// диск с вырезом справа (построчно, без наложений): строка dy идёт от -r до +r
		float r = 82f * u;
		float dcx = ox + 5f * u;
		double tan = Math.tan(Math.toRadians(38.0));
		float step = 0.5f;
		for (float dy = -r; dy < r; dy += step) {
			float mid = dy + step / 2f;
			float hw = (float) Math.sqrt(Math.max(0f, r * r - mid * mid));
			float right = Math.min(hw, (float) (Math.abs(mid) / tan));
			float left = -hw;
			if (right > left) {
				Gfx.rrect(g, dcx + left, oy + dy, right - left, step + 0.05f, 0f, disc, disc, 0, 0f);
			}
		}

		// буква E (светлая, прямыми блоками)
		float ex = dcx - 40f * u;
		float ey = oy - 34f * u;
		float ew = 52f * u;
		float eh = 68f * u;
		float th = 13f * u;
		int white = 0xFFFFFFFF;
		Gfx.rrect(g, ex, ey, th, eh, 0f, white, white, 0, 0f);
		Gfx.rrect(g, ex, ey, ew, th, 0f, white, white, 0, 0f);
		Gfx.rrect(g, ex, ey + (eh - th) / 2f, ew * 0.9f, th, 0f, white, white, 0, 0f);
		Gfx.rrect(g, ex, ey + eh - th, ew, th, 0f, white, white, 0, 0f);
	}

	private static int withAlpha(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * a);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	// ---------- частицы ----------

	public static void drawParticles(GuiGraphics g, float cx, float cy, int logoGui, float t) {
		int n = 36;
		for (int i = 0; i < n; i++) {
			float seed = i * 0.6180339f;
			float f1 = seed - (float) Math.floor(seed);
			float f2 = (i * 0.7548776f) % 1f;
			float ang = f1 * 6.2831855f + t * (0.12f + 0.18f * f2) * (i % 2 == 0 ? 1f : -1f);
			float rad = logoGui * (0.62f + 0.95f * f2) + 5f * (float) Math.sin(t * 0.8f + i);
			float x = cx + (float) Math.cos(ang) * rad * 1.25f;
			float y = cy + (float) Math.sin(ang) * rad * 0.78f - ((t * (4f + 6f * f1) + i * 7f) % 14f);
			float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (1.2f + 2f * f2) + i * 1.7f));
			float size = 0.7f + 1.1f * f1;
			int col = EchoTheme.shift(i % 3 == 0 ? 0xFFFFFFFF : 0xFFA855F7);
			float prev = Gfx.alpha;
			Gfx.alpha = prev * tw * 0.9f;
			Gfx.circle(g, x, y, size, col);
			if (i % 6 == 0) { // несколько искр покрупнее — с лучами
				float r = size * 3f;
				Gfx.line(g, x - r, y, x + r, y, 0.7f, col);
				Gfx.line(g, x, y - r, x, y + r, 0.7f, col);
			}
			Gfx.alpha = prev;
		}
	}

	// ---------- полоса загрузки ----------

	private static void drawBar(GuiGraphics g, int w, float y, float t, boolean done) {
		float bw = Math.max(120f, Math.min(300f, w * 0.38f));
		float bh = 5f;
		float x = (w - bw) / 2f;
		// дорожка
		Gfx.rrect(g, x, y, bw, bh, bh / 2f, EchoTheme.shift(0xFF1A1030), EchoTheme.shift(0xFF120A24), EchoTheme.FIELD_BORDER, 1f);
		float fw = Math.max(bh, bw * shown);
		if (shown > 0.002f) {
			// свечение вокруг заполненной части и сама заливка
			Gfx.glow(g, x, y, fw, bh, bh / 2f, EchoTheme.NEON, 5f);
			Gfx.rrect(g, x, y, fw, bh, bh / 2f, EchoTheme.ACCENT_B, EchoTheme.ACCENT_A, 0, 0f);
			// блик, бегущий по заполненной части
			if (!done && fw > 14f) {
				float pos = (t * 0.55f) % 1f;
				float sx = x + (fw - 12f) * pos;
				Gfx.rrect(g, sx, y, 12f, bh, bh / 2f, 0x90FFFFFF, 0x20FFFFFF, 0, 0f);
			}
		}
	}

	// ---------- текст ----------

	private static void drawStatus(GuiGraphics g, float cx, float y, String text) {
		int scalePx = (int) Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
		SplashAssets.Tex tex = SplashAssets.text(text, 9 * scalePx);
		if (tex == null) {
			return;
		}
		// ширину округляем до кратной масштабу, чтобы текстура рисовалась ровно 1:1
		int wGui = Math.max(1, tex.wPx() / scalePx);
		int hGui = Math.max(1, tex.hPx() / scalePx);
		int a = Math.max(0, Math.min(255, Math.round(255f * Gfx.alpha * 0.8f)));
		if (a > 0) {
			int col = (a << 24) | (EchoTheme.MUTED & 0xFFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, tex.id(), Math.round(cx - wGui / 2f), Math.round(y), 0f, 0f, wGui, hGui,
					tex.wPx(), tex.hPx(), tex.wPx(), tex.hPx(), col);
		}
	}
}
