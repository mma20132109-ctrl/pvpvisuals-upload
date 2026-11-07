package com.pvpvisuals.gui.echo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ConfigDialog;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleEntry;
import com.pvpvisuals.gui.rival.ModuleRegistry;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.gui.rival.NotificationToast;
import com.pvpvisuals.keybind.KeybindManager;
import com.pvpvisuals.render.LogoRenderer;
import com.pvpvisuals.render.LogoSizeMode;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Главный экран (Right Shift) в новом стиле: окно по центру (~75% × 80%) на размытом фоне, шапка с логотипом и поиском,
 * сайдбар категорий, сетка карточек модулей и подменю настроек модуля. Вся отрисовка и ввод — вручную, без виджетов.
 */
public class EchoScreen extends Screen {
	private static final int R = 14;
	private static final int MARGIN = 14;
	private static final int HEADER_Y = 12;
	private static final int LOGO = 26;
	private static final int TABS_Y = 46;
	private static final int TABS_H = 22;
	private static final int BODY_Y = 78;
	private static final int FOOTER_H = 26;
	private static final int SEARCH_H = 20;
	private static final int DOT_GAP = 13;
	private static final int CFG_H = 14;
	private static final String[] CFG_LABELS = {"Import", "Export", "Reset"};
	private static final ConfigDialog.Mode[] CFG_MODES = {
			ConfigDialog.Mode.IMPORT, ConfigDialog.Mode.EXPORT, ConfigDialog.Mode.RESET};

	private final Map<Category, List<ModuleCard>> byCat = new EnumMap<>(Category.class);
	private final List<ModuleCard> all = new ArrayList<>();
	private final List<ModuleCard> visible = new ArrayList<>();
	private final EchoSearchBar search = new EchoSearchBar();
	private final ScrollableGrid grid = new ScrollableGrid();
	private final ConfigDialog dialog = new ConfigDialog();
	private SettingsView settings;

	private Category selected = Category.VISUAL;
	private String lastQuery;
	private Category lastCat;

	// геометрия окна
	private int wx;
	private int wy;
	private int ww;
	private int wh;
	private int cx0;
	private int cy0;
	private int cw0;
	private int ch0;
	private final int[] cfgW = new int[3];

	// анимации
	private final long openNs = System.nanoTime();
	private long closingNs;
	private boolean closing;
	private long lastNs;
	private long tabNs = System.nanoTime();
	private long bindRefreshNs;
	private float indicator = -1f;
	private final float[] tabHover = new float[Category.values().length];
	private final float[] tabActive = new float[Category.values().length];
	private final float[] dotHover = new float[EchoPalette.values().length];
	private final float[] dotActive = new float[EchoPalette.values().length];
	private boolean interactive;

	// перетаскивание окна за шапку/футер: смещение от центра запоминается между открытиями меню
	private static int dragOffX;
	private static int dragOffY;
	private boolean draggingWindow;
	private boolean glassSettingsOpen;
	private int glassSliderDragging = -1;

	public EchoScreen() {
		super(Component.literal("Echo Visual"));
		for (Category c : Category.values()) {
			byCat.put(c, new ArrayList<>());
		}
		for (ModuleDef def : ModuleRegistry.build()) {
			ModuleCard card = new ModuleCard(def);
			byCat.get(def.category).add(card);
			all.add(card);
		}
		for (ModuleCard c : all) {
			restoreExpanded(c.root, c.def.category.name() + "/");
		}
	}

	/** Открывает меню сразу на настройках модуля с таким названием (из HUD-редактора). */
	public EchoScreen(String focusModule) {
		this();
		for (ModuleCard c : all) {
			if (c.def.title().equalsIgnoreCase(focusModule)) {
				selected = c.def.category;
				settings = new SettingsView(c);
				break;
			}
		}
	}

	// ---------- сохранение раскрытых строк ----------

	private static void restoreExpanded(ModuleEntry e, String prefix) {
		String key = prefix + e.def.name;
		e.expanded = Boolean.TRUE.equals(PvpConfig.get().menuExpanded.get(key));
		for (ModuleEntry k : e.kids) {
			restoreExpanded(k, key + "/");
		}
	}

	private static void captureExpanded(ModuleEntry e, String prefix) {
		String key = prefix + e.def.name;
		if (e.expanded) {
			PvpConfig.get().menuExpanded.put(key, Boolean.TRUE);
		}
		for (ModuleEntry k : e.kids) {
			captureExpanded(k, key + "/");
		}
	}

	private void captureExpanded() {
		PvpConfig.get().menuExpanded.clear();
		for (ModuleCard c : all) {
			captureExpanded(c.root, c.def.category.name() + "/");
		}
	}

	// ---------- геометрия ----------

	@Override
	protected void init() {
		layout();
	}

	private void layout() {
		ww = Math.min(this.width - 12, 440);
		wh = Math.min(this.height - 12, 296);
		wx = Mth.clamp((this.width - ww) / 2 + dragOffX, 4, Math.max(4, this.width - ww - 4));
		wy = Mth.clamp((this.height - wh) / 2 + dragOffY, 4, Math.max(4, this.height - 30));
		dragOffX = wx - (this.width - ww) / 2;
		dragOffY = wy - (this.height - wh) / 2;

		// поиск — справа в шапке
		int sw = Math.min(124, Math.max(90, ww - MARGIN * 2));
		search.w = sw;
		search.h = SEARCH_H;
		search.x = wx + ww - MARGIN - sw;
		search.y = wy + HEADER_Y + (LOGO - SEARCH_H) / 2;

		cx0 = wx + MARGIN;
		cy0 = wy + BODY_Y;
		cw0 = ww - 2 * MARGIN;
		ch0 = wh - BODY_Y - FOOTER_H - 4;
	}

	private int tabSlot() {
		return (cw0 - 4) / Category.values().length;
	}

	private int footerY() {
		return wy + wh - FOOTER_H;
	}

	private int dotX(int i) {
		return wx + ww - MARGIN - 5 - (EchoPalette.values().length - 1 - i) * DOT_GAP;
	}

	private int dotY() {
		return footerY() + FOOTER_H / 2;
	}

	/** Левый край кнопки Import/Export/Reset с номером i: блок стоит левее точек тем (заодно считает ширины). */
	private int cfgX(Font font, int i) {
		int x = dotX(0) - 12;
		for (int k = CFG_LABELS.length - 1; k >= i; k--) {
			cfgW[k] = Gfx.widthAtSize(font, CFG_LABELS[k], 5.8f) + 14;
			x -= cfgW[k] + (k == CFG_LABELS.length - 1 ? 0 : 4);
		}
		return x;
	}

	private int cfgY() {
		return footerY() + (FOOTER_H - CFG_H) / 2;
	}

	// ---------- выбор вкладки / настройки ----------

	private void select(Category c) {
		if (c != selected || settings != null || !search.isEmpty()) {
			selected = c;
			settings = null;
			search.clear();
			tabNs = System.nanoTime();
		}
	}

	private void openSettings(ModuleCard card) {
		settings = new SettingsView(card);
		settings.layout(cx0, cy0, cw0, ch0);
		tabNs = System.nanoTime();
	}

	private void rebuildVisible(String q) {
		visible.clear();
		if (q.isEmpty()) {
			visible.addAll(byCat.get(selected));
		} else {
			for (Category c : Category.values()) {
				for (ModuleCard card : byCat.get(c)) {
					if (card.matches(q)) {
						visible.add(card);
					}
				}
			}
		}
	}

	private void refreshBinds(long now, boolean force) {
		if (!force && now - bindRefreshNs < 250_000_000L) {
			return;
		}
		bindRefreshNs = now;
		for (ModuleCard c : all) {
			if (c.bindable) {
				String k = KeybindManager.boundText(c.def.name);
				c.bindText = k == null ? null : k.toUpperCase(Locale.ROOT);
			}
		}
	}

	// ---------- отрисовка ----------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		EchoTheme.sync();
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 16f : Math.min(50f, (now - lastNs) / 1_000_000f);
		lastNs = now;

		// Снимок сцены нужен стеклянным панелям для локального размытия.
		LiquidGlassRenderer.captureBackground();

		float open = closing ? 1f - Gfx.ease((now - closingNs) / 180_000_000f)
				: Gfx.ease((now - openNs) / 220_000_000f);
		if (closing && open <= 0.02f) {
			super.onClose();
			return;
		}
		interactive = !closing && open >= 0.999f && !dialog.active();
		layout();
		int overlayA = Math.round(Mth.clamp(PvpConfig.get().gui.overlayAlpha, 0f, 1f) * 255f * open) & 0xFF;
		g.fill(0, 0, this.width, this.height, (overlayA << 24) | 0x0A0418);

		boolean listening = KeybindManager.isListening();
		refreshBinds(now, listening);

		String q = search.query();
		if (!q.equals(lastQuery) || selected != lastCat) {
			if (lastQuery != null && !q.equals(lastQuery)) {
				grid.scroll().reset();
			}
			lastQuery = q;
			lastCat = selected;
			rebuildVisible(q);
		}

		Gfx.alpha = open;
		float sc = 0.94f + 0.06f * open;
		float cx = wx + ww / 2f;
		float cy = wy + wh / 2f;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(sc, sc);
		g.pose().translate(-cx, -cy);
		drawWindow(g, mouseX, mouseY, dt, now);
		g.pose().popMatrix();
		Gfx.alpha = 1f;
		if (glassSettingsOpen) {
			drawGlassSettings(g, mouseX, mouseY);
		}

		dialog.render(g, this.font, mouseX, mouseY, this.width, this.height);
		NotificationToast.render(g, this.font, this.width, this.height);
	}

	private void drawWindow(GuiGraphics g, int mx, int my, float dt, long now) {
		boolean hoverOk = interactive;

		// окно: акцентное свечение, градиентная заливка, лёгкая акцентная вуаль, блик сверху и тонкая рамка
		Gfx.glow(g, wx, wy, ww, wh, R, RoundedRender.scaleAlpha(EchoTheme.NEON, 0.20f), 6);
		LiquidGlassRenderer.window(g, wx, wy, ww, wh, R);
		Gfx.rrect(g, wx + 1, wy + 1, ww - 2, 60, R - 1, 0x0AFFFFFF, 0x00FFFFFF, 0, 0f);

		drawHeader(g, dt);
		drawTabs(g, mx, my, dt, hoverOk);

		// содержимое с плавным появлением при смене вкладки
		float tf = Gfx.ease((now - tabNs) / 180_000_000f);
		float prev = Gfx.alpha;
		Gfx.alpha = prev * tf;
		g.pose().pushMatrix();
		g.pose().translate(0f, (1f - tf) * 8f);
		if (settings != null) {
			settings.layout(cx0, cy0, cw0, ch0);
			String bt = settings.card().bindText;
			settings.render(g, this.font, mx, my, dt, hoverOk, bt);
		} else {
			grid.layout(cx0, cy0, cw0, ch0, visible, this.font);
			if (grid.isEmpty()) {
				drawEmpty(g);
			} else {
				grid.render(g, this.font, mx, my, dt, hoverOk);
			}
		}
		g.pose().popMatrix();
		Gfx.alpha = prev;

		drawFooter(g, mx, my, dt, hoverOk);
	}

	private void drawHeader(GuiGraphics g, float dt) {
		PvpConfig cfg = PvpConfig.get();
		int lx = wx + MARGIN;
		int ly = wy + HEADER_Y;

		// плитка с логотипом (LogoRenderer)
		int logoH = LOGO - 10;
		int logoW = cfg.logo.showInGui ? LogoRenderer.widthFor(LogoSizeMode.byName(cfg.logo.sizeGui), logoH, 1f) : 0;
		int tileW = Math.max(LOGO, logoW + 12);
		Gfx.glow(g, lx, ly, tileW, LOGO, 8, RoundedRender.scaleAlpha(EchoTheme.NEON, 0.30f), 4);
		Gfx.rrect(g, lx, ly, tileW, LOGO, 8, RoundedRender.scaleAlpha(EchoTheme.NEON, 0.32f),
				RoundedRender.scaleAlpha(EchoTheme.NEON, 0.14f), RoundedRender.scaleAlpha(EchoTheme.NEON, 0.55f), 1f);
		if (logoW > 0) {
			LogoRenderer.renderLogoAuto(g, LogoSizeMode.byName(cfg.logo.sizeGui), lx + (tileW - logoW) / 2, ly + 4, logoH,
					1f, Gfx.alpha);
		}

		int tx = lx + tileW + 9;
		float ty = ly + 3;
		Gfx.textAtSize(g, this.font, "Echo ", tx, ly + 5, 9f, EchoTheme.TEXT, false);
		Gfx.textAtSize(g, this.font, "Visual", tx + Gfx.widthAtSize(this.font, "Echo ", 9f), ly + 5, 9f, EchoTheme.ACCENT_TEXT, false);
		Gfx.textAtSize(g, this.font, "PvP visuals client", tx, ly + 19, 5.8f, EchoTheme.DIM, false);

		search.update(dt);
		search.render(g, this.font);
	}

	private void drawTabs(GuiGraphics g, int mx, int my, float dt, boolean hoverOk) {
		Category[] cats = Category.values();
		int x = cx0;
		int y = wy + TABS_Y;
		int slot = tabSlot();
		boolean searching = !search.isEmpty();

		Gfx.rrect(g, x, y, cw0, TABS_H, TABS_H / 2f, 0x0AFFFFFF, 0x0AFFFFFF, 0x0CFFFFFF, 0.8f);

		float target = x + 2 + selected.ordinal() * slot;
		if (indicator < 0f) {
			indicator = target;
		}
		indicator += (target - indicator) * Math.min(1f, dt / 70f);
		float dim = searching ? 0.35f : 1f;
		int ih = TABS_H - 4;
		Gfx.glow(g, indicator, y + 2, slot, ih, ih / 2f, RoundedRender.scaleAlpha(EchoTheme.NEON, 0.30f * dim), 3f);
		Gfx.rrect(g, indicator, y + 2, slot, ih, ih / 2f, RoundedRender.scaleAlpha(EchoTheme.TAB_TOP, 0.75f * dim),
				RoundedRender.scaleAlpha(EchoTheme.TAB_BOTTOM, 0.55f * dim),
				RoundedRender.scaleAlpha(EchoTheme.NEON, 0.55f * dim), 0.8f);

		float k = Math.min(1f, dt / 90f);
		for (int i = 0; i < cats.length; i++) {
			Category c = cats[i];
			int sx = x + 2 + i * slot;
			boolean over = hoverOk && Gfx.hit(mx, my, sx, y, slot, TABS_H);
			tabHover[i] += ((over ? 1f : 0f) - tabHover[i]) * k;
			tabActive[i] += ((c == selected ? 1f : 0f) - tabActive[i]) * k;
			float a = tabActive[i] * dim;
			int base = RoundedRender.lerp(EchoTheme.DIM, EchoTheme.MUTED, tabHover[i]);
			int textC = RoundedRender.lerp(base, EchoTheme.TEXT, a);
			int iconC = RoundedRender.lerp(base, EchoTheme.ACCENT_TEXT, a);
			int textW = Gfx.widthAtSize(this.font, c.title, 6.2f);
			int contentW = 9 + 4 + textW;
			int cx = sx + (slot - contentW) / 2;
			g.pose().pushMatrix();
			g.pose().translate(cx + 6f, y + TABS_H / 2f);
			g.pose().scale(0.75f, 0.75f);
			g.pose().translate(-cx - 6f, -(y + TABS_H / 2f));
			EchoIcons.category(g, c, cx, y + 5, iconC);
			g.pose().popMatrix();
			Gfx.textAtSize(g, this.font, c.title, cx + 13, y + (TABS_H - 7) / 2, 6.2f, textC, false);
		}
	}

	private void drawEmpty(GuiGraphics g) {
		String title = search.isEmpty() ? "Здесь пока пусто" : "Ничего не найдено";
		String sub = search.isEmpty() ? "Модули этой категории появятся здесь" : "Попробуй другой запрос";
		int cx = cx0 + cw0 / 2;
		int cy = cy0 + ch0 / 2 - 10;
		Gfx.circle(g, (float) cx, (float) cy, 18f, 0x0AFFFFFF);
		Gfx.ringCircle(g, (float) cx, (float) cy, 18f, 0.8f, 0x14FFFFFF);
		Gfx.textAtSize(g, this.font, title, cx - Gfx.widthAtSize(this.font, title, 7.4f) / 2, cy + 24, 7.4f, EchoTheme.MUTED, false);
		Gfx.textAtSize(g, this.font, sub, cx - Gfx.widthAtSize(this.font, sub, 6f) / 2, cy + 35, 6f, EchoTheme.DIM, false);
	}

	private void drawFooter(GuiGraphics g, int mx, int my, float dt, boolean hoverOk) {
		int fy = footerY();
		Gfx.rect(g, cx0, fy, cx0 + cw0, fy + 1, 0x14FFFFFF);
		int cy = fy + FOOTER_H / 2;

		// подсказки по управлению
		if (ww >= 440) {
			int hx = hint(g, cx0, cy, "LMB", "toggle");
			hint(g, hx + 8, cy, "RMB", "settings");
		}

		// Настройки прозрачности и затемнения Liquid Glass
		int gx = glassButtonX();
		boolean glassHover = hoverOk && Gfx.hit(mx, my, gx, cfgY(), 36, CFG_H);
		Gfx.rrect(g, gx, cfgY(), 36, CFG_H, 6f, glassSettingsOpen || glassHover ? EchoTheme.CARD_HOVER : EchoTheme.PILL,
				glassSettingsOpen || glassHover ? EchoTheme.CARD_HOVER : EchoTheme.PILL,
				glassHover ? EchoTheme.CARD_BORDER_HOT : EchoTheme.PILL_BORDER, 1f);
		Gfx.textAtSize(g, this.font, "Glass", gx + 6, cfgY() + 3, 5.8f, glassSettingsOpen || glassHover ? EchoTheme.TEXT : EchoTheme.MUTED, false);

		// Import / Export / Reset
		for (int i = 0; i < CFG_LABELS.length; i++) {
			int bx = cfgX(this.font, i);
			int by = cfgY();
			boolean hv = hoverOk && Gfx.hit(mx, my, bx, by, cfgW[i], CFG_H);
			int fill = hv ? EchoTheme.CARD_HOVER : EchoTheme.PILL;
			Gfx.rrect(g, bx, by, cfgW[i], CFG_H, 6f, fill, fill,
					hv ? EchoTheme.CARD_BORDER_HOT : EchoTheme.PILL_BORDER, 1f);
			Gfx.textAtSize(g, this.font, CFG_LABELS[i], bx + (cfgW[i] - Gfx.widthAtSize(this.font, CFG_LABELS[i], 5.8f)) / 2f, by + 3, 5.8f,
					hv ? EchoTheme.TEXT : EchoTheme.MUTED, false);
		}

		// точки выбора темы
		EchoPalette[] ps = EchoPalette.values();
		EchoPalette cur = EchoTheme.current();
		float k = Math.min(1f, dt / 90f);
		for (int i = 0; i < ps.length; i++) {
			int dx = dotX(i);
			int dy = dotY();
			boolean over = hoverOk && Gfx.hit(mx, my, dx - 6, dy - 6, 12, 12);
			dotHover[i] += ((over ? 1f : 0f) - dotHover[i]) * k;
			dotActive[i] += ((ps[i] == cur ? 1f : 0f) - dotActive[i]) * k;
			int c = EchoTheme.preview(ps[i]);
			float a = dotActive[i];
			if (a > 0.02f) {
				Gfx.glow(g, dx - 4f, dy - 4f, 8f, 8f, 4f, RoundedRender.scaleAlpha(c, 0.5f * a), 3f);
				Gfx.ringCircle(g, dx + 0.5f, dy + 0.5f, 5.5f, 1f, RoundedRender.scaleAlpha(c, 0.85f * a));
			}
			float rad = 3.4f + dotHover[i] * 0.6f - a * 0.3f;
			int fill = RoundedRender.lerp(RoundedRender.lerp(c, 0xFF000000, 0.2f), c, Math.max(a, dotHover[i]));
			Gfx.circle(g, dx + 0.5f, dy + 0.5f, rad, fill);
		}
	}

	private int glassButtonX() {
		return cfgX(this.font, 0) - 40;
	}

	private void drawGlassSettings(GuiGraphics g, int mx, int my) {
		int px = wx + ww - 210;
		int py = wy + wh - 112;
		Gfx.fill(g, px, py, 196, 98, 10, 0xF0181B24);
		Gfx.ring(g, px, py, 196, 98, 10, 1f, EchoTheme.CARD_BORDER_HOT);
		Gfx.text(g, this.font, "LIQUID GLASS", px + 12, py + 10, EchoTheme.TEXT);
		PvpConfig cfg = PvpConfig.get();
		drawGlassSlider(g, px + 12, py + 34, 172, 0, "Transparency", Math.round((1f - Mth.clamp(cfg.gui.panelOpacity, 0f, 1f)) * 100f));
		drawGlassSlider(g, px + 12, py + 68, 172, 1, "Background dim", Math.round(Mth.clamp(cfg.gui.overlayAlpha, 0f, 0.8f) * 100f));
	}

	private void drawGlassSlider(GuiGraphics g, int x, int y, int width, int id, String label, int value) {
		Gfx.textAtSize(g, this.font, label, x, y, 5.8f, EchoTheme.MUTED, false);
		String shown = value + "%";
		Gfx.textAtSize(g, this.font, shown, x + width - Gfx.widthAtSize(this.font, shown, 5.8f), y, 5.8f, EchoTheme.ACCENT_TEXT, false);
		int barY = y + 13;
		Gfx.fill(g, x, barY, width, 4, 2, EchoTheme.CARD_BORDER);
		float ratio = id == 0 ? value / 100f : value / 80f;
		int fill = Math.round(width * Mth.clamp(ratio, 0f, 1f));
		if (fill > 0) Gfx.fill(g, x, barY, fill, 4, 2, EchoTheme.ACCENT_A, EchoTheme.ACCENT_B);
		Gfx.circle(g, x + Math.round(width * Mth.clamp(ratio, 0f, 1f)), barY + 2, 4, EchoTheme.KNOB);
	}

	private void updateGlassSlider(double mouseX) {
		int px = wx + ww - 210;
		int x = px + 12;
		int width = 172;
		float ratio = Mth.clamp((float) (mouseX - x) / width, 0f, 1f);
		if (glassSliderDragging == 0) {
			PvpConfig.get().gui.panelOpacity = 1f - ratio;
		} else if (glassSliderDragging == 1) {
			PvpConfig.get().gui.overlayAlpha = ratio * 0.8f;
		}
	}

	private int hint(GuiGraphics g, int x, int cy, String key, String text) {
		int kw = Gfx.widthAtSize(this.font, key, 5.2f) + 8;
		Gfx.rrect(g, x, cy - 6, kw, 12, 4f, 0x10FFFFFF, 0x10FFFFFF, 0, 0f);
		Gfx.textAtSize(g, this.font, key, x + 4, cy - 3, 5.2f, EchoTheme.MUTED, false);
		Gfx.textAtSize(g, this.font, text, x + kw + 4, cy - 3, 5.6f, EchoTheme.DIM, false);
		return x + kw + 4 + Gfx.widthAtSize(this.font, text, 5.6f);
	}

	// ---------- ввод ----------

	private void save() {
		PvpConfig.get().save();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int btn = event.button();

		if (KeybindManager.isListening()) {
			if (btn >= 2) {
				KeybindManager.handleMouse(btn);
			} else {
				KeybindManager.cancelListening();
			}
			return true;
		}
		if (dialog.active()) {
			if (btn == 0 && dialog.click(mx, my, this.width, this.height) == ConfigDialog.Result.CONFIG_CHANGED) {
				rebuild();
			}
			return true;
		}
		if (!interactive) {
			return true;
		}
		if (btn != 0 && btn != 1) {
			return false;
		}

		if (search.contains(mx, my)) {
			search.setFocused(true);
			return true;
		}
		search.setFocused(false);

		if (btn == 0) {
			// вкладки категорий
			Category[] cats = Category.values();
			for (int i = 0; i < cats.length; i++) {
				if (Gfx.hit(mx, my, cx0 + 2 + i * tabSlot(), wy + TABS_Y, tabSlot(), TABS_H)) {
					select(cats[i]);
					return true;
				}
			}
			// Панель настроек Liquid Glass
			if (Gfx.hit(mx, my, glassButtonX(), cfgY(), 36, CFG_H)) {
				glassSettingsOpen = !glassSettingsOpen;
				glassSliderDragging = -1;
				return true;
			}
			if (glassSettingsOpen) {
				int px = wx + ww - 210;
				int py = wy + wh - 112;
				if (Gfx.hit(mx, my, px + 12, py + 34, 172, 20)) {
					glassSliderDragging = 0;
					updateGlassSlider(mx);
					return true;
				}
				if (Gfx.hit(mx, my, px + 12, py + 68, 172, 20)) {
					glassSliderDragging = 1;
					updateGlassSlider(mx);
					return true;
				}
			}
			// точки тем
			EchoPalette[] ps = EchoPalette.values();
			for (int i = 0; i < ps.length; i++) {
				if (Gfx.hit(mx, my, dotX(i) - 6, dotY() - 6, 12, 12)) {
					PvpConfig.get().gui.palette = ps[i].name();
					EchoTheme.sync();
					save();
					return true;
				}
			}
			// Import / Export / Reset
			for (int i = 0; i < CFG_LABELS.length; i++) {
				if (Gfx.hit(mx, my, cfgX(this.font, i), cfgY(), cfgW[i], CFG_H)) {
					captureExpanded();
					dialog.open(CFG_MODES[i]);
					return true;
				}
			}
			// шапка и футер — «ручка» для перетаскивания окна
			if (Gfx.hit(mx, my, wx, wy, ww, TABS_Y - 2) || Gfx.hit(mx, my, wx, footerY(), ww, FOOTER_H)) {
				draggingWindow = true;
				return true;
			}
		}

		if (settings != null) {
			SettingsView.Result r = settings.click(mx, my, btn);
			switch (r) {
				case BACK -> {
					settings = null;
					tabNs = System.nanoTime();
				}
				case CHANGED -> save();
				default -> { }
			}
			return r != SettingsView.Result.NONE;
		}

		ScrollableGrid.Hit hit = grid.hit(mx, my);
		if (hit == null) {
			return false;
		}
		ModuleCard card = hit.card();
		ModuleDef def = card.def;
		switch (hit.zone()) {
			case BIND -> {
				if (btn == 1) {
					KeybindManager.clear(def.name);
				} else {
					KeybindManager.startListening(def.name);
				}
			}
			case MORE -> openSettings(card);
			case TOGGLE, BODY -> {
				if (btn == 1) { // ПКМ — настройки
					if (card.hasSettings() || def.isGroup()) {
						openSettings(card);
					}
				} else if (def.isGroup()) {
					openSettings(card);
				} else if (def.activate()) { // ЛКМ — вкл/выкл (или запуск)
					save();
				}
			}
			default -> { }
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (glassSliderDragging >= 0) {
			updateGlassSlider(event.x());
			return true;
		}
		if (draggingWindow) {
			dragOffX += Math.round(dragX);
			dragOffY += Math.round(dragY);
			layout();
			return true;
		}
		if (settings != null && settings.isDragging()) {
			settings.dragTo(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (glassSliderDragging >= 0) {
			glassSliderDragging = -1;
			save();
			return true;
		}
		if (draggingWindow) {
			draggingWindow = false;
			return true;
		}
		if (settings != null && settings.isDragging()) {
			settings.endDrag();
			save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (dialog.active()) {
			return dialog.scroll(scrollY);
		}
		float px = (float) (-scrollY * 28);
		if (settings != null) {
			if (settings.contains(mouseX, mouseY)) {
				settings.scrollBy(px);
				return true;
			}
			return false;
		}
		if (grid.scroll().contains(mouseX, mouseY)) {
			grid.scroll().scrollBy(px);
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (KeybindManager.isListening()) {
			KeybindManager.handleKey(key);
			return true;
		}
		boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

		if (dialog.active()) {
			return dialog.key(key);
		}
		if (settings != null && settings.isEditing()) {
			return settings.editKey(key);
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (search.focused()) {
				search.setFocused(false);
			} else if (settings != null) {
				settings = null;
				tabNs = System.nanoTime();
			} else {
				this.onClose();
			}
			return true;
		}
		if (ctrl && key == GLFW.GLFW_KEY_F) {
			search.setFocused(true);
			return true;
		}
		if (search.focused()) {
			if (key == GLFW.GLFW_KEY_BACKSPACE) {
				search.backspace();
				if (search.isEmpty()) {
					tabNs = System.nanoTime();
				}
			}
			return true; // пока строка поиска активна, остальные клавиши не обрабатываем
		}
		if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (dialog.active()) {
			return dialog.charTyped(event.codepoint());
		}
		if (settings != null && settings.isEditing()) {
			return settings.editChar(event.codepoint());
		}
		if (KeybindManager.isListening()) {
			return false;
		}
		int cp = event.codepoint();
		if (cp >= 32 && cp != 127) {
			search.setFocused(true);
			search.type(cp);
			settings = null; // поиск работает по сетке
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		if (closing) {
			return;
		}
		closing = true;
		closingNs = System.nanoTime();
		interactive = false;
		KeybindManager.cancelListening();
		captureExpanded();
		save();
	}

	/** После импорта/сброса значения изменились целиком: пересоздаём меню с новыми лямбдами. */
	private void rebuild() {
		this.minecraft.setScreen(new EchoScreen());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
