package com.pvpvisuals.hud;

import java.util.ArrayList;
import java.util.List;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleRegistry;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.rival.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Общая логика редактора HUD: рисует превью элементов с рамками и обрабатывает мышь. Используется и отдельным
 * экраном {@link HudEditorScreen}, и режимом редактирования в чате ({@link HudChatEditor}).
 * ЛКМ — переместить, угловой маркер — масштаб, ПКМ — настройки, Shift+колесо — прозрачность.
 */
public final class HudEditorOverlay {
	private static final int HANDLE = 8;
	private static final int TITLE_H = 11;
	private static final int BTN_H = 14;
	private static final String[] BUTTONS = {"Reset All", "Reset Selected", "Grid", "Snap", "Guides"};
	private static final int[] BTN_X = new int[BUTTONS.length];
	private static final int[] BTN_W = new int[BUTTONS.length];

	private static AbstractHudModule dragging;
	private static AbstractHudModule resizing;
	private static AbstractHudModule selected;
	private static HudContextMenu contextMenu;
	private static int grabX;
	private static int grabY;
	private static int resizeX;
	private static int resizeY;
	private static int guideX = -1;
	private static int guideY = -1;

	private HudEditorOverlay() { }

	public static void reset() {
		dragging = null;
		resizing = null;
		contextMenu = null;
		guideX = -1;
		guideY = -1;
	}

	private static AbstractHudModule top(double mx, double my) {
		for (int i = HudManager.COMPONENTS.size() - 1; i >= 0; i--) {
			AbstractHudModule m = HudManager.COMPONENTS.get(i);
			if (m.enabled() && m.bounds.contains(mx, my, HANDLE / 2)) {
				return m;
			}
		}
		return null;
	}

	private static boolean onHandle(AbstractHudModule m, double mx, double my) {
		return Math.abs(mx - (m.bounds.x + m.bounds.width)) <= HANDLE && Math.abs(my - (m.bounds.y + m.bounds.height)) <= HANDLE;
	}

	// ---------- рисование ----------

	public static void render(GuiGraphics g, Font font, int mx, int my, int sw, int sh, boolean dim, int hintY) {
		EchoTheme.sync();
		PvpConfig cfg = PvpConfig.get();
		Theme t = Theme.current();

		if (dim) {
			g.fill(0, 0, sw, sh, 0x66000000);
		}
		if (cfg.hudGrid) {
			for (int x = 0; x < sw; x += 20) {
				g.fill(x, 0, x + 1, sh, 0x18FFFFFF);
			}
			for (int y = 0; y < sh; y += 20) {
				g.fill(0, y, sw, y + 1, 0x18FFFFFF);
			}
		}

		for (AbstractHudModule m : HudManager.COMPONENTS) {
			if (m.enabled()) {
				m.resolve(sw, sh);
				m.render(g, m.bounds.x, m.bounds.y, true);
			}
		}

		AbstractHudModule hover = dragging == null && resizing == null ? top(mx, my) : null;
		for (AbstractHudModule m : HudManager.COMPONENTS) {
			if (m.enabled()) {
				frame(g, font, t, m, m == hover, m == dragging || m == resizing, m == selected);
			}
		}

		if (cfg.hudGuides && dragging != null) {
			if (guideX >= 0) {
				g.fill(guideX, 0, guideX + 1, sh, 0xAAE8C87A);
			}
			if (guideY >= 0) {
				g.fill(0, guideY, sw, guideY + 1, 0xAAE8C87A);
			}
		}

		toolbar(g, font, t, cfg, mx, my, sw);

		String hint = "ЛКМ — переместить  •  ПКМ — настройки  •  Колесо — масштаб  •  Угол — размер  •  Shift+колесо — альфа  •  Esc — сохранить";
		int hintW = font.width(hint);
		int hintX = (sw - hintW) / 2 - 6;
		HudStyle.panel(g, hintX, hintY - 4, hintW + 12, 16, 5, 0.92f);
		Gfx.textAtSize(g, font, hint, (sw - hintW) / 2f, hintY - 0.1f, 5.8f,
				0xFFFFFFFF, false);

		if (resizing != null) {
			String s = String.format(java.util.Locale.ROOT, "%.2f×", resizing.scale());
			g.drawString(font, s, mx + 12, my + 8, EchoTheme.ACCENT_TEXT, true);
		}
		if (contextMenu != null) {
			contextMenu.render(g, font, mx, my);
		}
	}

	private static void frame(GuiGraphics g, Font font, Theme t, AbstractHudModule m, boolean hover, boolean active, boolean selected) {
		int x = m.bounds.x;
		int y = m.bounds.y;
		int w = m.bounds.width;
		int h = m.bounds.height;

		int base = EchoTheme.FIELD_BORDER;
		int col = active ? EchoTheme.shift(0xFFE0C8FF) : hover ? EchoTheme.ACCENT_TEXT : selected ? EchoTheme.NEON : base;

		if (active) {
			g.renderOutline(x - 3, y - 3, w + 6, h + 6, (EchoTheme.ACCENT_TEXT & 0xFFFFFF) | 0x66000000);
			g.renderOutline(x - 2, y - 2, w + 4, h + 4, (EchoTheme.ACCENT_TEXT & 0xFFFFFF) | 0x99000000);
		}
		g.renderOutline(x - 1, y - 1, w + 2, h + 2, col);

		// угловые маркеры
		g.fill(x - 2, y - 2, x + 2, y + 2, col);
		g.fill(x + w - 2, y - 2, x + w + 2, y + 2, col);
		g.fill(x - 2, y + h - 2, x + 2, y + h + 2, col);
		// маркер ресайза 8x8 в правом нижнем углу
		g.fill(x + w - HANDLE / 2, y + h - HANDLE / 2, x + w + HANDLE / 2, y + h + HANDLE / 2, EchoTheme.ACCENT_B);
		g.renderOutline(x + w - HANDLE / 2, y + h - HANDLE / 2, HANDLE, HANDLE, 0xFFFFFFFF);

		// полоска заголовка
		String label = m.label();
		int tw = font.width(label) + 8;
		int ty = y - TITLE_H - 2 >= 0 ? y - TITLE_H - 2 : y + h + 3;
		HudStyle.panel(g, x, ty, tw, TITLE_H, 4, 0.96f);
		HudStyle.accentWash(g, x, ty, tw, TITLE_H, 4, 0.9f);
		Gfx.textAtSize(g, font, label, x + 4f, ty + 2.1f, 6.2f, EchoTheme.TEXT, false);
	}

	private static void toolbar(GuiGraphics g, Font font, Theme t, PvpConfig cfg, int mx, int my, int sw) {
		int x = sw - 6;
		for (int i = BUTTONS.length - 1; i >= 0; i--) {
			int w = font.width(BUTTONS[i]) + 10;
			x -= w;
			BTN_X[i] = x;
			BTN_W[i] = w;
			boolean on = (i == 2 && cfg.hudGrid) || (i == 3 && cfg.hudSnap) || (i == 4 && cfg.hudGuides);
			boolean hov = mx >= x && mx < x + w && my >= 6 && my < 6 + BTN_H;
			int top = on ? EchoTheme.TAB_TOP : EchoTheme.shift(0xC01B1030);
			int bottom = on ? EchoTheme.TAB_BOTTOM : EchoTheme.shift(0xC0120A22);
			HudStyle.panel(g, x, 6, w, BTN_H, 5, on ? 1f : 0.82f);
			Gfx.rrect(g, x + 1, 7, Math.max(0, w - 2), BTN_H - 2, 4,
					HudStyle.opacity(top, on ? 0.55f : 0.16f),
					HudStyle.opacity(bottom, on ? 0.45f : 0.10f), 0, 0f);
			if (hov) {
				g.renderOutline(x, 6, w, BTN_H, EchoTheme.ACCENT_TEXT);
			}
			Gfx.textAtSize(g, font, BUTTONS[i], x + 5f, 9.0f, 5.8f, 0xFFFFFFFF, false);
			x -= 4;
		}
	}

	// ---------- мышь ----------

	/** @return true, если клик обработан редактором. */
	public static boolean onClick(double mx, double my, int button, int sw, int sh) {
		PvpConfig cfg = PvpConfig.get();

		if (contextMenu != null) {
			HudContextMenu menu = contextMenu;
			menu.click(mx, my, button);
			if (menu.closed) {
				contextMenu = null;
			}
			return true;
		}

		if (button == 0 && my >= 6 && my < 6 + BTN_H) {
			for (int i = 0; i < BUTTONS.length; i++) {
				if (mx >= BTN_X[i] && mx < BTN_X[i] + BTN_W[i]) {
					switch (i) {
						case 0 -> {
							for (AbstractHudModule m : HudManager.COMPONENTS) {
								m.reset();
							}
							cfg.hudPositions.clear();
						}
						case 1 -> {
							if (selected != null) {
								selected.reset();
							}
						}
						case 2 -> cfg.hudGrid = !cfg.hudGrid;
						case 3 -> cfg.hudSnap = !cfg.hudSnap;
						default -> cfg.hudGuides = !cfg.hudGuides;
					}
					cfg.save();
					return true;
				}
			}
		}

		AbstractHudModule m = top(mx, my);
		if (m == null) {
			return false;
		}
		selected = m;
		if (button == 1) {
			contextMenu = new HudContextMenu(m, mx, my, sw, sh);
			return true;
		}
		if (button != 0) {
			return true;
		}
		if (onHandle(m, mx, my)) {
			resizing = m;
			resizeX = m.bounds.x;
			resizeY = m.bounds.y;
		} else {
			dragging = m;
			grabX = (int) mx - m.bounds.x;
			grabY = (int) my - m.bounds.y;
		}
		return true;
	}

	public static boolean onDrag(double mx, double my, int sw, int sh) {
		PvpConfig cfg = PvpConfig.get();
		if (resizing != null) {
			AbstractHudModule m = resizing;
			float sc = (float) (mx - resizeX) / m.baseWidth();
			sc = Mth.clamp(Math.round(sc * 100f) / 100f, AbstractHudModule.MIN_SCALE, AbstractHudModule.MAX_SCALE);
			m.settings().scale = sc;
			m.setScreenPos(resizeX, resizeY, sw, sh); // левый верхний угол остаётся на месте
			return true;
		}
		if (dragging != null) {
			AbstractHudModule m = dragging;
			int w = m.bounds.width;
			int h = m.bounds.height;
			int nx = Mth.clamp((int) mx - grabX, 0, Math.max(0, sw - w));
			int ny = Mth.clamp((int) my - grabY, 0, Math.max(0, sh - h));

			if (cfg.hudSnap) {
				nx = Math.round(nx / 5f) * 5;
				ny = Math.round(ny / 5f) * 5;
			}
			guideX = -1;
			guideY = -1;
			if (cfg.hudGuides) {
				int[] snapped = alignToOthers(m, nx, ny, w, h, sw, sh);
				nx = snapped[0];
				ny = snapped[1];
			}
			m.setScreenPos(nx, ny, sw, sh);
			return true;
		}
		return false;
	}

	/** Привязка краёв и центра к краям других элементов и к центру экрана (порог 3 px). */
	private static int[] alignToOthers(AbstractHudModule m, int nx, int ny, int w, int h, int sw, int sh) {
		final int th = 3;
		int[] xs = {nx, nx + w / 2, nx + w};
		int[] ys = {ny, ny + h / 2, ny + h};
		int bestDx = Integer.MAX_VALUE;
		int bestDy = Integer.MAX_VALUE;
		int gx = -1;
		int gy = -1;

		for (int pass = -1; pass < HudManager.COMPONENTS.size(); pass++) {
			int[] tx;
			int[] ty;
			if (pass < 0) {
				tx = new int[] {sw / 2};
				ty = new int[] {sh / 2};
			} else {
				AbstractHudModule o = HudManager.COMPONENTS.get(pass);
				if (o == m || !o.enabled()) {
					continue;
				}
				HudElementBounds b = o.bounds;
				tx = new int[] {b.x, b.x + b.width / 2, b.x + b.width};
				ty = new int[] {b.y, b.y + b.height / 2, b.y + b.height};
			}
			for (int a : xs) {
				for (int b : tx) {
					int d = b - a;
					if (Math.abs(d) <= th && Math.abs(d) < Math.abs(bestDx == Integer.MAX_VALUE ? 99 : bestDx)) {
						bestDx = d;
						gx = b;
					}
				}
			}
			for (int a : ys) {
				for (int b : ty) {
					int d = b - a;
					if (Math.abs(d) <= th && Math.abs(d) < Math.abs(bestDy == Integer.MAX_VALUE ? 99 : bestDy)) {
						bestDy = d;
						gy = b;
					}
				}
			}
		}
		if (bestDx != Integer.MAX_VALUE) {
			nx += bestDx;
			guideX = gx;
		}
		if (bestDy != Integer.MAX_VALUE) {
			ny += bestDy;
			guideY = gy;
		}
		return new int[] {nx, ny};
	}

	/** @return true, если отпускание обработано (конфиг сохраняется). */
	public static boolean onRelease() {
		boolean was = dragging != null || resizing != null;
		dragging = null;
		resizing = null;
		guideX = -1;
		guideY = -1;
		// Не закрываем contextMenu: правый клик открывает его по нажатию,
		// а само меню должно оставаться после отпускания кнопки мыши.
		if (was) {
			PvpConfig.get().save();
		}
		return was || contextMenu != null;
	}

	/** Колесо над элементом меняет масштаб; Shift + колесо — прозрачность. */
	public static boolean onScroll(double mx, double my, double scrollY, boolean shift) {
		if (contextMenu != null || scrollY == 0.0) {
			return false;
		}
		AbstractHudModule m = top(mx, my);
		if (m == null) {
			return false;
		}

		if (shift) {
			float a = Mth.clamp(m.alpha() + (scrollY > 0 ? 0.05f : -0.05f),
					AbstractHudModule.MIN_ALPHA, 1.0f);
			m.settings().alpha = Math.round(a * 100f) / 100f;
		} else {
			var window = Minecraft.getInstance().getWindow();
			int sw = window.getGuiScaledWidth();
			int sh = window.getGuiScaledHeight();
			float centerX = m.bounds.x + m.bounds.width / 2f;
			float centerY = m.bounds.y + m.bounds.height / 2f;

			float scale = Mth.clamp(m.scale() + (scrollY > 0 ? 0.05f : -0.05f),
					AbstractHudModule.MIN_SCALE, AbstractHudModule.MAX_SCALE);
			m.settings().scale = Math.round(scale * 100f) / 100f;
			int newX = Math.round(centerX - m.width() / 2f);
			int newY = Math.round(centerY - m.height() / 2f);
			m.setScreenPos(newX, newY, sw, sh);
		}
		PvpConfig.get().save();
		return true;
	}

	/** Контекстное меню настроек выбранного HUD-элемента, как в Svintus. */
	private static final class HudContextMenu {
		private static final float TITLE_H = 17f;
		private static final float ITEM_H = 15f;
		private static final float SWITCH_W = 20f;
		private static final float SWITCH_H = 11f;

		private final AbstractHudModule target;
		private final ModuleDef parent;
		private final List<ModuleDef> options = new ArrayList<>();
		private final float x;
		private final float y;
		private final float w;
		private final float h;
		private boolean closed;

		HudContextMenu(AbstractHudModule target, double mx, double my, int sw, int sh) {
			this.target = target;
			String moduleName = target == HudManager.INVENTORY ? "Inventory HUD" : target.label();
			ModuleDef found = null;
			for (ModuleDef def : ModuleRegistry.build()) {
				if (def.isToggle() && (def.name.equals(moduleName) || def.title().equals(target.label()))) {
					found = def;
					break;
				}
			}
			parent = found;
			if (parent != null) {
				for (ModuleDef child : parent.children) {
					if (child.isToggle() && child.isVisible()) {
						options.add(child);
					}
				}
			}

			Font font = Minecraft.getInstance().font;
			float width = Math.max(112f, font.width(target.label()) + 20f);
			for (ModuleDef option : options) {
				width = Math.max(width, font.width(option.title()) + SWITCH_W + 29f);
			}
			w = Math.min(Math.max(80f, sw - 8f), width);
			h = TITLE_H + (options.size() + 1) * ITEM_H + 6f;
			x = Mth.clamp((float) mx, 4f, Math.max(4f, sw - w - 4f));
			y = Mth.clamp((float) my, 4f, Math.max(4f, sh - h - 4f));
		}

		void render(GuiGraphics g, Font font, int mx, int my) {
			HudStyle.panel(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), 7, 0.98f);
			HudStyle.accentWash(g, x, y, w, TITLE_H, 7f, 0.92f);
			Gfx.textAtSize(g, font, target.label(), x + 8f, y + 5.2f, 6.4f,
					HudStyle.ACCENT, false);
			Gfx.rrect(g, x + 7f, y + TITLE_H, w - 14f, 0.8f, 0.4f,
					HudStyle.opacity(0x35FFFFFF, 0.8f), 0x00000000, 0, 0f);

			for (int i = 0; i < options.size(); i++) {
				float rowY = y + TITLE_H + 2f + i * ITEM_H;
				ModuleDef option = options.get(i);
				boolean hover = mx >= x + 3f && mx <= x + w - 3f
						&& my >= rowY && my <= rowY + ITEM_H - 1f;
				if (hover) {
					Gfx.rrect(g, x + 3f, rowY, w - 6f, ITEM_H - 1f, 5f,
							HudStyle.opacity(0x20FFFFFF, 1f), HudStyle.opacity(0x20FFFFFF, 1f), 0, 0f);
				}
				Gfx.textAtSize(g, font, option.title(), x + 9f, rowY + 4.4f, 6.2f,
						option.enabled() ? HudStyle.TEXT : HudStyle.MUTED, false);
				drawSwitch(g, x + w - 9f - SWITCH_W, rowY + (ITEM_H - SWITCH_H) / 2f - 1f,
						option.enabled());
			}

			float hideY = y + TITLE_H + 2f + options.size() * ITEM_H;
			boolean hideHover = mx >= x + 3f && mx <= x + w - 3f
					&& my >= hideY && my <= hideY + ITEM_H - 1f;
			if (hideHover) {
				Gfx.rrect(g, x + 3f, hideY, w - 6f, ITEM_H - 1f, 5f,
						HudStyle.opacity(0x24E0707A, 1f), HudStyle.opacity(0x24E0707A, 1f), 0, 0f);
			}
			Gfx.textAtSize(g, font, "Hide element", x + 9f, hideY + 4.4f, 6.2f,
					hideHover ? 0xFFFF9AA2 : 0xFFE0707A, false);
		}

		void click(double mx, double my, int button) {
			if (mx < x || mx > x + w || my < y || my > y + h) {
				closed = true;
				return;
			}
			if (button != 0 || my < y + TITLE_H) {
				return;
			}
			int index = (int) ((my - y - TITLE_H - 2f) / ITEM_H);
			if (index >= 0 && index < options.size()) {
				options.get(index).activate();
				PvpConfig.get().save();
			} else if (index == options.size()) {
				if (parent != null) {
					parent.setEnabled(false);
					PvpConfig.get().save();
				}
				closed = true;
			}
		}

		private static void drawSwitch(GuiGraphics g, float x, float y, boolean enabled) {
			float knob = SWITCH_H - 4f;
			int track = enabled ? HudStyle.ACCENT : 0xFF30313B;
			Gfx.rrect(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2f,
					HudStyle.opacity(track, 0.88f), HudStyle.opacity(track, 0.88f), 0, 0f);
			float knobX = x + 2f + (enabled ? SWITCH_W - 4f - knob : 0f);
			Gfx.circle(g, knobX + knob / 2f, y + SWITCH_H / 2f, knob / 2f, 0xFFFFFFFF);
		}
	}
}
