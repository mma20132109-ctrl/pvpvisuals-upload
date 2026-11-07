package com.pvpvisuals.hud;

import com.pvpvisuals.config.HudSettings;
import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * База для всех HUD-элементов: позиция (с якорем), масштаб, прозрачность, рамка для редактора.
 * Наследник описывает только содержимое: размер в «родных» пикселях и {@link #drawContent}, рисующий от (0, 0).
 * Матрицу (translate + scale) и альфу накладывает {@link #render}.
 */
public abstract class AbstractHudModule {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;
	public static final float MIN_ALPHA = 0.05f;

	/** Рамка на экране (обновляется в {@link #resolve}); с учётом scale. */
	public final HudElementBounds bounds = new HudElementBounds();

	public abstract String id();

	/** Название (совпадает со строкой в меню). */
	public abstract String label();

	public abstract int defaultX();

	public abstract int defaultY();

	public abstract boolean enabled();

	/** Ширина содержимого без масштаба. */
	protected abstract int contentWidth();

	/** Высота содержимого без масштаба. */
	protected abstract int contentHeight();

	/** Рисует содержимое от (0, 0); цвета — через HudAlpha.mul. preview = true в редакторе. */
	protected abstract void drawContent(GuiGraphics g, boolean preview);

	// ---------- настройки ----------

	public final HudSettings settings() {
		PvpConfig cfg = PvpConfig.get();
		HudSettings s = cfg.hud.get(id());
		if (s == null) {
			s = new HudSettings();
			int[] legacy = cfg.hudPositions.get(id()); // позиции из старых версий мода
			s.x = legacy != null && legacy.length == 2 ? legacy[0] : defaultX();
			s.y = legacy != null && legacy.length == 2 ? legacy[1] : defaultY();
			cfg.hud.put(id(), s);
		}
		return s;
	}

	public final float scale() {
		return Mth.clamp(settings().scale, MIN_SCALE, MAX_SCALE);
	}

	public final float alpha() {
		return Mth.clamp(settings().alpha, MIN_ALPHA, 1.0f);
	}

	public final HudAnchor anchor() {
		return HudAnchor.byName(settings().anchor);
	}

	public final int baseWidth() {
		return Math.max(1, contentWidth());
	}

	public final int width() {
		return Math.max(1, Math.round(contentWidth() * scale()));
	}

	public final int height() {
		return Math.max(1, Math.round(contentHeight() * scale()));
	}

	public final void reset() {
		PvpConfig cfg = PvpConfig.get();
		cfg.hud.remove(id());
		cfg.hudPositions.remove(id());
	}

	// ---------- позиция ----------

	/** Считает положение на экране (левый верхний угол) по якорю и смещению, прижимает к границам. */
	public final void resolve(int gw, int gh) {
		int w = width();
		int h = height();
		HudSettings s = settings();
		int sx;
		int sy;
		switch (anchor()) {
			case TOP_RIGHT -> {
				sx = gw - w - s.x;
				sy = s.y;
			}
			case BOTTOM_LEFT -> {
				sx = s.x;
				sy = gh - h - s.y;
			}
			case BOTTOM_RIGHT -> {
				sx = gw - w - s.x;
				sy = gh - h - s.y;
			}
			case CENTER -> {
				sx = (gw - w) / 2 + s.x;
				sy = (gh - h) / 2 + s.y;
			}
			default -> {
				sx = s.x;
				sy = s.y;
			}
		}
		bounds.set(Mth.clamp(sx, 0, Math.max(0, gw - w)), Mth.clamp(sy, 0, Math.max(0, gh - h)), w, h);
	}

	/** Задаёт положение по экранным координатам левого верхнего угла (обратная операция к resolve). */
	public final void setScreenPos(int sx, int sy, int gw, int gh) {
		int w = width();
		int h = height();
		sx = Mth.clamp(sx, 0, Math.max(0, gw - w));
		sy = Mth.clamp(sy, 0, Math.max(0, gh - h));
		HudSettings s = settings();
		switch (anchor()) {
			case TOP_RIGHT -> {
				s.x = gw - w - sx;
				s.y = sy;
			}
			case BOTTOM_LEFT -> {
				s.x = sx;
				s.y = gh - h - sy;
			}
			case BOTTOM_RIGHT -> {
				s.x = gw - w - sx;
				s.y = gh - h - sy;
			}
			case CENTER -> {
				s.x = sx - (gw - w) / 2;
				s.y = sy - (gh - h) / 2;
			}
			default -> {
				s.x = sx;
				s.y = sy;
			}
		}
	}

	/** Меняет якорь, не двигая элемент на экране. */
	public final void changeAnchor(HudAnchor a) {
		// ПРОВЕРИТЬ: Window#getGuiScaledWidth() / getGuiScaledHeight() в 1.21.11.
		var window = Minecraft.getInstance().getWindow();
		int gw = window.getGuiScaledWidth();
		int gh = window.getGuiScaledHeight();
		resolve(gw, gh);
		int sx = bounds.x;
		int sy = bounds.y;
		settings().anchor = a.name();
		setScreenPos(sx, sy, gw, gh);
	}

	/** Рисует элемент с левым верхним углом в (x, y) экрана, с масштабом и прозрачностью из настроек. */
	public final void render(GuiGraphics g, int x, int y, boolean preview) {
		float sc = scale();
		float previousAlpha = HudAlpha.current;
		HudAlpha.current = alpha();
		g.pose().pushMatrix();
		try {
			g.pose().translate(x, y);
			g.pose().scale(sc, sc);
			drawContent(g, preview);
		} finally {
			g.pose().popMatrix();
			HudAlpha.current = previousAlpha;
		}
	}
}
