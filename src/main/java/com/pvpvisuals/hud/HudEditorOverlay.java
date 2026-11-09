package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoScreen;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.echo.LiquidGlassRenderer;
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

		String hint = "ЛКМ — переместить  •  ПКМ — настройки  •  Угол — изменить размер  •  Shift+колесо — прозрачность  •  Esc — сохранить";
		LiquidGlassRenderer.surface(g, (sw - font.width(hint)) / 2f - 6f, hintY - 4f,
				font.width(hint) + 12f, 16f, 5f, 0xB82C5364, 0x70D7F4FF);
		g.drawString(font, hint, (sw - font.width(hint)) / 2, hintY, 0xFFFFFFFF, true);

		if (resizing != null) {
			String s = String.format(java.util.Locale.ROOT, "%.2f×", resizing.scale());
			g.drawString(font, s, mx + 12, my + 8, EchoTheme.ACCENT_TEXT, true);
		}
	}

	private static void frame(GuiGraphics g, Font font, Theme t, AbstractHudModule m, boolean hover, boolean active, boolean selected) {
		int x = m.bounds.x;
		int y = m.bounds.y;
		int w = m.bounds.width;
		int h = m.bounds.height;

		int base = EchoTheme.FIELD_BORDER;
		int col = active ? 0xFFD7F7FF : hover ? EchoTheme.ACCENT_TEXT : selected ? EchoTheme.NEON : base;

		if (active) {
			Gfx.glow(g, x - 2, y - 2, w + 4, h + 4, 7, 0xA858D8F3, 4f);
			Gfx.ring(g, x - 2, y - 2, w + 4, h + 4, 7, 1f, 0xB8D7F7FF);
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
		LiquidGlassRenderer.surface(g, x, ty, tw, TITLE_H, 4, EchoTheme.HUD_TOP, 0x70D7F4FF);
		g.drawString(font, label, x + 4, ty + 2, EchoTheme.TEXT, false);
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
			int top = on ? EchoTheme.TAB_TOP : 0xA62B5263;
			int bottom = on ? EchoTheme.TAB_BOTTOM : 0x92203646;
			LiquidGlassRenderer.surface(g, x, 6, w, BTN_H, 5, on ? top : bottom,
				hov ? EchoTheme.ACCENT_TEXT : 0x668BC7D8);
			if (hov) {
				g.renderOutline(x, 6, w, BTN_H, EchoTheme.ACCENT_TEXT);
			}
			g.drawString(font, BUTTONS[i], x + 5, 6 + 3, 0xFFFFFFFF, false);
			x -= 4;
		}
	}

	// ---------- мышь ----------

	/** @return true, если клик обработан редактором. */
	public static boolean onClick(double mx, double my, int button, int sw, int sh) {
		PvpConfig cfg = PvpConfig.get();

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
			// то же подменю, что в основном GUI
			Minecraft.getInstance().setScreen(new EchoScreen(m.label()));
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
		reset();
		if (was) {
			PvpConfig.get().save();
		}
		return was;
	}

	/** Shift + колесо над элементом меняет его прозрачность. */
	public static boolean onScroll(double mx, double my, double scrollY, boolean shift) {
		if (!shift) {
			return false;
		}
		AbstractHudModule m = top(mx, my);
		if (m == null) {
			return false;
		}
		float a = Mth.clamp(m.alpha() + (scrollY > 0 ? 0.05f : -0.05f), AbstractHudModule.MIN_ALPHA, 1.0f);
		m.settings().alpha = Math.round(a * 100f) / 100f;
		PvpConfig.get().save();
		return true;
	}
}
