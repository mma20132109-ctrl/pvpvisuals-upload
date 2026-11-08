package com.pvpvisuals.gui.menu;

import java.util.Map;
import java.util.WeakHashMap;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.render.splash.EchoSplash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.util.Util;

/**
 * Стиль Echo Visual для экранов выбора и создания мира: фон как в главном меню, кнопки в виде тёмных плашек с
 * фиолетовой обводкой (при наведении заливка становится фиолетовой), фиолетовая рамка выбранного мира.
 * Вызывается из миксинов (ScreenBackgroundMixin, AbstractButtonMixin, AbstractSelectionListMixin).
 */
public final class EchoWorldStyle {
	private static final Map<AbstractWidget, float[]> STATE = new WeakHashMap<>(); // {hover, lastMs}

	private EchoWorldStyle() { }

	/**
	 * Остальные ванильные меню. В релизе (Fabric, intermediary) имена классов обфусцированы, поэтому по именам их
	 * не найти: окна подтверждения берём через instanceof, а когда мир не загружен, стилизуем любой экран из
	 * net.minecraft.* (кроме главного меню, оно заменяется нашим).
	 */
	private static boolean byName(Object screen) {
		if (screen == null) {
			return false;
		}
		if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen) {
			return true;
		}
		if (screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
			return false;
		}
		return Minecraft.getInstance().level == null && screen.getClass().getName().startsWith("net.minecraft.");
	}

	/** Стилизовать ли этот экран. Проверка через instanceof (имена классов в релизе обфусцированы). */
	public static boolean isStyled(Object screen) {
		// The account screen must never use Minecraft's global background blur: it can already be active
		// in the title-menu frame and the blur post-chain is limited to one execution per frame.
		if (screen instanceof EchoAccountManagerScreen) {
			return true;
		}
		return PvpConfig.get().menu.styleWorldMenus
				&& (screen instanceof SelectWorldScreen || screen instanceof JoinMultiplayerScreen
						|| screen instanceof CreateWorldScreen
						|| screen instanceof EditWorldScreen || screen instanceof LevelLoadingScreen
						|| screen instanceof GenericMessageScreen || screen instanceof ProgressScreen
						|| screen instanceof LanguageSelectScreen
						// все экраны настроек: видео, звук, управление, клавиши, мышь, чат, доступность, скин, ресурспаки
						|| screen instanceof OptionsScreen || screen instanceof OptionsSubScreen
						|| screen instanceof PackSelectionScreen || byName(screen));
	}

	/** Активен ли стиль для экрана, открытого прямо сейчас (для кнопок и списков, у которых нет ссылки на экран). */
	public static boolean active() {
		return isStyled(Minecraft.getInstance().screen);
	}

	/** Short section name used by the shared Zenith-style chrome on every menu sub-screen. */
	private static String sectionLabel(Object screen) {
		if (screen instanceof EchoAccountManagerScreen) return "ACCOUNTS";
		if (screen instanceof SelectWorldScreen) return "SINGLEPLAYER";
		if (screen instanceof JoinMultiplayerScreen) return "MULTIPLAYER";
		if (screen instanceof CreateWorldScreen) return "CREATE WORLD";
		if (screen instanceof EditWorldScreen) return "EDIT WORLD";
		if (screen instanceof OptionsScreen || screen instanceof OptionsSubScreen) return "SETTINGS";
		if (screen instanceof LanguageSelectScreen) return "LANGUAGE";
		if (screen instanceof PackSelectionScreen) return "RESOURCE PACKS";
		if (screen instanceof GenericMessageScreen || screen instanceof ProgressScreen
				|| screen instanceof LevelLoadingScreen) return "PLEASE WAIT";
		return "MENU";
	}

	/**
	 * Shared top chrome for all routes opened from Echo's main dock. It is deliberately
	 * drawn without a blur pass: sub-screens can be opened in a frame in which the main
	 * menu has already used Minecraft's one-per-frame blur pipeline.
	 */
	public static void renderChrome(GuiGraphics g, Object screen) {
		if (!isStyled(screen)) return;
		float previous = Gfx.alpha;
		try {
			EchoTheme.sync();
			Gfx.alpha = 1f;
			float w = g.guiWidth();
			float leftX = 12f;
			float topY = 10f;
			Gfx.rrect(g, leftX, topY, 116f, 26f, 8f,
					0xC91B202B, 0xB80C111A, 0x55FFFFFF, 0.8f);
			Gfx.circle(g, leftX + 13f, topY + 13f, 3.6f, EchoTheme.accent());
			Gfx.text(g, Minecraft.getInstance().font, "PVP VISUALS",
					Math.round(leftX + 23f), Math.round(topY + 9f), 0xFFE8EAF2);

			String section = sectionLabel(screen);
			float sectionW = Gfx.width(Minecraft.getInstance().font, section) + 22f;
			float sectionX = Math.max(leftX + 124f, w - sectionW - 12f);
			Gfx.rrect(g, sectionX, topY, sectionW, 26f, 8f,
					0xB91A1E29, 0xA90C1019, 0x44FFFFFF, 0.7f);
			Gfx.rrect(g, sectionX + 8f, topY + 8f, 2f, 10f, 1f,
					EchoTheme.accent(), EchoTheme.accent2(), 0, 0f);
			Gfx.text(g, Minecraft.getInstance().font, section,
					Math.round(sectionX + 15f), Math.round(topY + 9f), 0xFFD5D8E3);

			// A faint separator gives every submenu the same visual frame.
			Gfx.rrect(g, 12f, topY + 32f, Math.max(1f, w - 24f), 1f, 0.5f,
					0x28FFFFFF, 0x00FFFFFF, 0, 0f);
		} finally {
			Gfx.alpha = previous;
		}
	}

	public static void background(GuiGraphics g) {
		float prev = Gfx.alpha;
		try {
			EchoTheme.sync();
			PvpConfig cfg = PvpConfig.get();
			float t = Util.getMillis() / 1000f;
			Gfx.alpha = 1f;
			EchoSplash.drawBackground(g, g.guiWidth(), g.guiHeight(), t, cfg.splash.splashBackground);
			if (cfg.menu.showMenuParticles) {
				EchoSplash.drawParticles(g, g.guiWidth() / 2f, g.guiHeight() * 0.4f, Math.round(g.guiHeight() * 0.2f), t);
			}
		} finally {
			Gfx.alpha = prev;
		}
	}

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("pvpvisuals/worldstyle");
	private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();

	/** Пишет в лог ошибку стилизации (один раз на место), чтобы по latest.log было видно, что именно падает. */
	public static void fail(String where, Throwable t) {
		if (REPORTED.add(where)) {
			LOG.error("Echo style failed in " + where + " (vanilla widget is used instead)", t);
		}
	}

	/** Ползунок: тёмная дорожка с фиолетовой заливкой до ручки, светящаяся ручка, подпись по центру. */
	public static void slider(GuiGraphics g, AbstractWidget w, double value) {
		float prev = Gfx.alpha;
		try {
			long now = Util.getMillis();
			float[] st = STATE.computeIfAbsent(w, k -> new float[] {0f, now});
			float dt = Math.min(0.1f, Math.max(0f, (now - st[1]) / 1000f));
			st[1] = now;
			boolean hot = w.active && w.isHoveredOrFocused();
			st[0] += ((hot ? 1f : 0f) - st[0]) * (1f - (float) Math.exp(-dt * 14f));
			float e = Gfx.ease(st[0]);

			Gfx.alpha = prev * w.getAlpha();
			float x = w.getX();
			float y = w.getY();
			float bw = w.getWidth();
			float bh = w.getHeight();
			float r = Math.min(bh / 2f, 6f);
			float v = (float) Math.max(0.0, Math.min(1.0, value));
			float hw = 8f;
			float hx = x + v * (bw - hw);

			int border = EchoButton.lerp(EchoTheme.accent(), EchoTheme.accent2(), e);
			if (e > 0.02f) {
				Gfx.glow(g, x, y, bw, bh, r, EchoTheme.accent(), 3f * e);
			}
			// дорожка
			Gfx.rrect(g, x, y, bw, bh, r, 0xFF111522, 0xFF0A0E17, border, 1f);
			// заливка до центра ручки
			float fw = Math.max(0f, hx + hw / 2f - x - 2f);
			if (fw > 2f) {
				Gfx.rrect(g, x + 1f, y + 1f, fw, bh - 2f, Math.max(1f, r - 1f), EchoTheme.accent(),
						EchoTheme.accent2(), 0x00000000, 0f);
			}
			// ручка
			int hTop = w.active ? EchoButton.lerp(0xFFD9C2FF, 0xFFFFFFFF, e) : 0xFF6F6488;
			int hBot = w.active ? EchoButton.lerp(0xFFA97BFF, 0xFFD9C2FF, e) : 0xFF4A4060;
			Gfx.glow(g, hx, y, hw, bh, Math.min(r, hw / 2f), EchoTheme.shift(0xFF8B3DFF), 2f + 2f * e);
			Gfx.rrect(g, hx, y, hw, bh, Math.min(r, hw / 2f), hTop, hBot, 0xFFFFFFFF, 1f);

			Font font = Minecraft.getInstance().font;
			float tw = Gfx.width(font, w.getMessage());
			float sc = tw > bw - 16f ? (bw - 16f) / tw : 1f;
			Gfx.textScaled(g, font, w.getMessage().getString(), x + (bw - tw * sc) / 2f, y + (bh - 9f * sc) / 2f, sc,
					w.active ? 0xFFFFFFFF : 0xFF6F6488, true);
		} finally {
			Gfx.alpha = prev;
		}
	}

	/** Фон поля ввода: тёмная плашка, фиолетовая рамка, при фокусе яркая рамка и свечение. */
	public static void textBox(GuiGraphics g, AbstractWidget w) {
		float prev = Gfx.alpha;
		try {
			Gfx.alpha = prev * w.getAlpha();
			boolean focus = w.isFocused();
			float x = w.getX();
			float y = w.getY();
			float bw = w.getWidth();
			float bh = w.getHeight();
			float r = Math.min(bh / 2f, 5f);
			if (focus) {
				Gfx.glow(g, x, y, bw, bh, r, EchoTheme.shift(0xFF8B3DFF), 3f);
			}
			int border = focus ? EchoTheme.shift(0xFFC9A6FF) : EchoTheme.shift(0xFF4A2C8F);
			Gfx.rrect(g, x, y, bw, bh, r, 0xFF111520, 0xFF0A0D15, border, 1f);
		} finally {
			Gfx.alpha = prev;
		}
	}

	public static void button(GuiGraphics g, AbstractWidget w) {
		float prev = Gfx.alpha;
		try {
			long now = Util.getMillis();
			float[] st = STATE.computeIfAbsent(w, k -> new float[] {0f, now});
			float dt = Math.min(0.1f, Math.max(0f, (now - st[1]) / 1000f));
			st[1] = now;
			boolean hot = w.active && w.isHoveredOrFocused();
			st[0] += ((hot ? 1f : 0f) - st[0]) * (1f - (float) Math.exp(-dt * 14f));
			float e = Gfx.ease(st[0]);

			Gfx.alpha = prev * w.getAlpha();
			float x = w.getX();
			float y = w.getY();
			float bw = w.getWidth();
			float bh = w.getHeight();
			float r = Math.min(bh / 2f, 6f);

			int top = EchoButton.lerp(EchoTheme.shift(0xFF150C28), EchoTheme.shift(0xFF9B5CFF), e);
			int bot = EchoButton.lerp(EchoTheme.shift(0xFF0B0616), EchoTheme.shift(0xFF7A35F0), e);
			int border = EchoButton.lerp(EchoTheme.shift(0xFF6D3FD0), EchoTheme.shift(0xFFC9A6FF), e);
			int text = EchoButton.lerp(0xFFC9B8EE, 0xFFFFFFFF, e);
			if (!w.active) {
				top = EchoTheme.shift(0xFF0E0918);
				bot = EchoTheme.shift(0xFF0A0612);
				border = EchoTheme.shift(0xFF2C2046);
				text = 0xFF6F6488;
			}
			if (e > 0.02f) {
				Gfx.glow(g, x, y, bw, bh, r, EchoTheme.shift(0xFF8B3DFF), 3f * e);
			}
			Gfx.rrect(g, x, y, bw, bh, r, top, bot, border, 1f);

			Font font = Minecraft.getInstance().font;
			float tw = Gfx.width(font, w.getMessage());
			// длинные подписи (например, «Game Mode: Survival») не выходят за кнопку
			float sc = tw > bw - 8f ? (bw - 8f) / tw : 1f;
			Gfx.textScaled(g, font, w.getMessage().getString(), x + (bw - tw * sc) / 2f, y + (bh - 9f * sc) / 2f, sc,
					text, false);
		} finally {
			Gfx.alpha = prev;
		}
	}

	/**
	 * Служебные экраны «Saving level...», «Preparing...», прогресс и загрузка мира: стилизуем всегда, даже если
	 * клиентский мир ещё не выгружен (при сохранении и выходе из мира mc.level != null).
	 */
	public static boolean isTransient(Object screen) {
		return screen instanceof LevelLoadingScreen || screen instanceof GenericMessageScreen
				|| screen instanceof ProgressScreen;
	}

	/** Нужно ли заменить фон этого экрана: стилизованный экран и (нет мира под ним или это служебный экран). */
	public static boolean wantsBackground(Object screen) {
		// Account manager is also opened from the title screen; skip vanilla renderBlurredBackground
		// unconditionally, including if another screen later opens it while a world is still attached.
		return screen instanceof EchoAccountManagerScreen
				|| isStyled(screen) && (Minecraft.getInstance().level == null || isTransient(screen));
	}

	/** Панель с сообщением по центру («Saving level...» и т.п.): тёмная плашка с фиолетовой рамкой и бегущей полосой. */
	public static void messagePanel(GuiGraphics g, String title, int w, int h) {
		messagePanelAt(g, title, w / 2f, h / 2f - 6f);
	}

	/** То же, но с заданным центром панели (cx, cy). */
	public static void messagePanelAt(GuiGraphics g, String title, float cx, float cy) {
		int w = Math.round(cx * 2f);
		float prev = Gfx.alpha;
		try {
			EchoTheme.sync();
			Font font = Minecraft.getInstance().font;
			float tw = Gfx.width(font, title);
			float pw = Math.max(150f, tw + 44f);
			float ph = 34f;
			float x = cx - pw / 2f;
			float y = cy - ph / 2f;
			Gfx.glow(g, x, y, pw, ph, 10f, EchoTheme.shift(0xFF8B3DFF), 6f);
			Gfx.rrect(g, x, y, pw, ph, 10f, EchoTheme.shift(0xFF150C28), EchoTheme.shift(0xFF0B0616),
					EchoTheme.shift(0xFF6D3FD0), 1f);
			Gfx.text(g, font, title, Math.round(cx - tw / 2f), Math.round(y + (ph - 8f) / 2f), 0xFFFFFFFF);

			// неопределённая полоса: светлый отрезок бегает туда-сюда под панелью
			float bw = Math.min(140f, pw);
			float bh = 4f;
			float bx = cx - bw / 2f;
			float by = y + ph + 12f;
			Gfx.rrect(g, bx, by, bw, bh, bh / 2f, EchoTheme.shift(0xFF1A1030), EchoTheme.shift(0xFF120A24),
					EchoTheme.FIELD_BORDER, 1f);
			float t = (Util.getMillis() % 1400L) / 1400f;
			float k = 0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2.0); // 0..1..0
			float sw = bw * 0.32f;
			float sx = bx + (bw - sw) * k;
			Gfx.glow(g, sx, by, sw, bh, bh / 2f, EchoTheme.NEON, 4f);
			Gfx.rrect(g, sx, by, sw, bh, bh / 2f, EchoTheme.ACCENT_B, EchoTheme.ACCENT_A, 0, 0f);
		} finally {
			Gfx.alpha = prev;
		}
	}

	/** Экран загрузки/генерации мира (ему фон подменяем независимо от того, создан ли уже клиентский мир). */
	public static boolean isLoadingScreen() {
		return PvpConfig.get().menu.styleWorldMenus && Minecraft.getInstance().screen instanceof LevelLoadingScreen;
	}

	/**
	 * Полоса загрузки в стиле Echo Visual вместо ванильной. Аргументы ванильного drawProgressBar заранее неизвестны
	 * (углы x0,y0,x1,y1 или x,y,ширина,высота), поэтому определяем по признаку: у углов a2 > a0 и a3 > a1.
	 */
	public static void loadingBar(GuiGraphics g, int a0, int a1, int a2, int a3, float progress) {
		float x;
		float y;
		float w;
		float h;
		if (a2 > a0 && a3 > a1 && (a3 - a1) < (a2 - a0)) {
			x = a0;
			y = a1;
			w = a2 - a0;
			h = a3 - a1;
		} else {
			x = a0;
			y = a1;
			w = a2;
			h = a3;
		}
		h = Math.max(5f, Math.min(h, 8f));
		float p = Math.max(0f, Math.min(1f, progress));
		float prev = Gfx.alpha;
		try {
			EchoTheme.sync();
			Gfx.rrect(g, x, y, w, h, h / 2f, EchoTheme.shift(0xFF1A1030), EchoTheme.shift(0xFF120A24), EchoTheme.FIELD_BORDER, 1f);
			float fw = Math.max(h, w * p);
			if (p > 0.002f) {
				Gfx.glow(g, x, y, fw, h, h / 2f, EchoTheme.NEON, 5f);
				Gfx.rrect(g, x, y, fw, h, h / 2f, EchoTheme.ACCENT_B, EchoTheme.ACCENT_A, 0, 0f);
			}
		} finally {
			Gfx.alpha = prev;
		}
	}

	/** Цвет рамки выбранного элемента списка: фиолетовый вместо белого/серого. */
	public static int selectionColor() {
		return EchoTheme.accent();
	}
}
