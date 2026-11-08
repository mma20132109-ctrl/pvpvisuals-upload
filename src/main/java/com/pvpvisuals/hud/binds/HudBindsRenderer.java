package com.pvpvisuals.hud.binds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pvpvisuals.config.HudBindsSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleRegistry;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.hud.HudAlpha;
import com.pvpvisuals.hud.HudStyle;
import com.pvpvisuals.keybind.KeybindManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Список HUD Binds в стиле Target HUD: одна панель с цветами темы, заголовок «Binds» и номер страницы,
 * на странице не больше pageSize строк (по умолчанию 4). В списке все модули с назначенной клавишей;
 * выключенные остаются на месте, но рисуются тусклым цветом textOff и без тени.
 * Состав обновляется раз в 250 мс (или сразу после invalidate()); строки, ширины и сортировка кэшируются.
 * Ширина считается по самой длинной строке среди ВСЕХ страниц, поэтому панель не прыгает при листании.
 */
public final class HudBindsRenderer {
	private static final long REFRESH_NS = 250_000_000L;
	private static final String SELF = "HUD Binds";
	private static final int HEAD_H = 18;
	private static final int ROW_H = 11;
	private static final int FOOT = 3;
	private static final int SIDE = 7;
	private static final int GAP = 12;

	private static final Comparator<HudBindsLine> BY_WIDTH = (a, b) -> {
		int c = Integer.compare(b.width, a.width);
		return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
	};
	private static final Comparator<HudBindsLine> BY_NAME = (a, b) -> a.name.compareToIgnoreCase(b.name);
	private static final Comparator<HudBindsLine> BY_ORDER = (a, b) -> Integer.compare(a.order, b.order);

	private static final String[] SAMPLE_NAMES = {"Fullbright", "Trajectory", "Kill Effect"};
	private static final String[] SAMPLE_BINDS = {"[G]", "[R]", "[H]"};

	private final Map<String, HudBindsLine> map = new HashMap<>();
	private final List<HudBindsLine> lines = new ArrayList<>();
	private final Set<String> active = new HashSet<>();
	private long lastRefresh;
	private long lastFrame;
	private long pageStart;
	private long lastFlip;
	private boolean dirtySort = true;
	private String lastSortMode = "";
	private int page;
	private int pagesShown = -1;
	private int pageShown = -1;
	private String pageText = "";

	public void invalidate() {
		lastRefresh = 0;
	}

	private static HudBindsSettings s() {
		return PvpConfig.get().hudBinds;
	}

	/** Листает страницу: +1 вперёд, -1 назад (по кругу). */
	public void flip(int dir) {
		int pages = pages();
		if (pages > 1) {
			page = Math.floorMod(page + dir, pages);
			pageStart = System.nanoTime();
			lastFlip = pageStart;
		}
	}

	private int pages() {
		int size = Math.max(1, s().pageSize);
		return Math.max(1, (lines.size() + size - 1) / size);
	}

	// ---------- данные ----------

	private void ensureFresh() {
		long now = System.nanoTime();
		if (lastRefresh != 0 && now - lastRefresh < REFRESH_NS) {
			return;
		}
		lastRefresh = now;
		refresh();
	}

	private void refresh() {
		HudBindsSettings s = s();
		Font font = Minecraft.getInstance().font;
		active.clear();
		int order = 0;
		// ModuleRegistry.toggleModules() собирает определения заново, поэтому лямбды не держат устаревший конфиг.
		for (ModuleDef d : ModuleRegistry.toggleModules()) {
			order++;
			if (d.name.equals(SELF)) {
				continue;
			}
			String key = KeybindManager.boundText(d.name);
			if (key == null) {
				continue; // только модули с назначенной клавишей
			}
			active.add(d.name);
			HudBindsLine l = map.get(d.name);
			if (l == null) {
				l = new HudBindsLine(d.name, order);
				map.put(d.name, l);
				lines.add(l);
				dirtySort = true;
			}
			l.on = d.enabled();
			rebuildText(l, key, s, font);
		}
		for (int i = lines.size() - 1; i >= 0; i--) {
			HudBindsLine l = lines.get(i);
			if (!active.contains(l.module)) {
				lines.remove(i);
				map.remove(l.module);
			}
		}
		if (!s.sortMode.equals(lastSortMode)) {
			lastSortMode = s.sortMode;
			dirtySort = true;
		}
		if (dirtySort) {
			dirtySort = false;
			lines.sort(switch (HudBindsSortMode.byName(s.sortMode)) {
				case WIDTH -> BY_WIDTH;
				case ALPHABETICAL -> BY_NAME;
				case CUSTOM -> BY_ORDER;
			});
		}
		page = Math.min(page, pages() - 1);
	}

	private void rebuildText(HudBindsLine l, String key, HudBindsSettings s, Font font) {
		if (l.bindFirst == s.showBindFirst && key.equals(l.signature) && !l.name.isEmpty()) {
			return;
		}
		l.signature = key;
		l.bindFirst = s.showBindFirst;
		l.name = l.module;
		l.bind = s.showBindFirst ? key : "[" + key + "]";
		l.nameW = Gfx.widthAtSize(font, l.name, 6.2f);
		l.bindW = Gfx.widthAtSize(font, l.bind, 5.4f);
		l.width = l.nameW + Gfx.widthAtSize(font, " ", 5.4f) + l.bindW;
		dirtySort = true;
	}

	private float frameDt() {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(50f, (now - lastFrame) / 1_000_000f);
		lastFrame = now;
		return dt;
	}

	// ---------- размеры ----------

	public int width() {
		ensureFresh();
		Font f = Minecraft.getInstance().font;
		int w = Gfx.widthAtSize(f, "Binds", 6.8f) + Gfx.widthAtSize(f, "99/99", 5.4f) + GAP;
		if (lines.isEmpty()) {
			w = Math.max(w, Gfx.widthAtSize(f, "Fullbright", 6.2f)
					+ Gfx.widthAtSize(f, "[G]", 5.4f) + GAP + 2 * SIDE);
		}
		for (int i = 0; i < lines.size(); i++) {
			w = Math.max(w, lines.get(i).width + GAP);
		}
		return Math.max(96, w + 2 * SIDE);
	}

	public int height() {
		ensureFresh();
		int size = Math.max(1, s().pageSize);
		int rows = lines.isEmpty() ? 1 : Math.min(size, lines.size());
		return HEAD_H + rows * ROW_H + FOOT;
	}

	// ---------- рисование ----------

	public void draw(GuiGraphics g, boolean preview) {
		ensureFresh();
		HudBindsSettings s = s();
		float dt = frameDt();
		long now = System.nanoTime();
		if (s.autoFlipSeconds > 0 && pages() > 1 && now - lastFlip > s.autoFlipSeconds * 1_000_000_000L) {
			flip(1);
		}
		if (lines.isEmpty() && !preview) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int w = width();
		int h = height();
		HudStyle.panel(g, 0, 0, w, h);
		HudStyle.accentWash(g, 0, 0, 42, HEAD_H, 8, HudAlpha.current);
		Gfx.textAtSize(g, font, "Binds", SIDE, 5.4f, 6.8f, HudAlpha.mul(HudStyle.TEXT), false);

		int pages = pages();
		if (pages > 1) {
			if (pages != pagesShown || page != pageShown) {
				pagesShown = pages;
				pageShown = page;
				pageText = (page + 1) + "/" + pages;
			}
			int pageW = Gfx.widthAtSize(font, pageText, 5.4f);
			Gfx.textAtSize(g, font, pageText, w - SIDE - pageW, 6.1f, 5.4f,
					HudAlpha.mul(HudStyle.ACCENT), false);
		}

		if (lines.isEmpty()) {
			for (int i = 0; i < 1; i++) {
				drawRow(g, font, w, HEAD_H, SAMPLE_NAMES[i], SAMPLE_BINDS[i], false, true, 1f);
			}
			return;
		}

		float fade = s.animationSpeed <= 0 ? 1f : Math.min(1f, (now - pageStart) / (s.animationSpeed * 1_000_000f));
		int size = Math.max(1, s.pageSize);
		int from = page * size;
		int to = Math.min(lines.size(), from + size);
		for (int i = from; i < to; i++) {
			HudBindsLine l = lines.get(i);
			l.anim.advance(dt, s.animationSpeed);
			float k = l.anim.eased() * fade;
			drawRow(g, font, w, HEAD_H + (i - from) * ROW_H, l.name, l.bind, l.bindFirst, l.on, k);
		}
	}

	private static int col(int argb, float k) {
		return HudAlpha.mul(RoundedRender.scaleAlpha(argb, k));
	}

	private static void drawRow(GuiGraphics g, Font font, int w, int y, String name, String bind,
			boolean bindFirst, boolean on, float k) {
		int nameC = col(on ? HudStyle.TEXT : EchoTheme.DIM, k);
		int bindC = col(on ? HudStyle.ACCENT : EchoTheme.DIM, k);
		int bindW = Gfx.widthAtSize(font, bind, 5.4f);
		if (bindFirst) {
			Gfx.textAtSize(g, font, bind, SIDE, y + 1.8f, 5.4f, bindC, false);
			float nameX = SIDE + bindW + Gfx.widthAtSize(font, " ", 5.4f);
			Gfx.textAtSize(g, font, name, nameX, y + 1.5f, 6.2f, nameC, on);
		} else {
			Gfx.textAtSize(g, font, name, SIDE, y + 1.5f, 6.2f, nameC, on);
			Gfx.textAtSize(g, font, bind, w - SIDE - bindW, y + 1.8f, 5.4f, bindC, false);
		}
	}
}
