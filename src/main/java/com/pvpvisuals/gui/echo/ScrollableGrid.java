package com.pvpvisuals.gui.echo;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Сетка карточек модулей (2 колонки) с плавной прокруткой. Хит-тест учитывает смещение прокрутки. */
public final class ScrollableGrid {
	public static final int GAP = 8;
	public static final int ROW_GAP = 6;

	public record Hit(ModuleCard card, ModuleCard.Zone zone, int screenY) { }

	private final ScrollArea scroll = new ScrollArea();
	private List<ModuleCard> cards = List.of();

	public ScrollArea scroll() {
		return scroll;
	}

	public void layout(int x, int y, int w, int h, List<ModuleCard> list, Font font) {
		scroll.setBounds(x, y, w, h);
		cards = list;
		int cols = w >= 250 ? 2 : 1;
		int cw = (w - GAP * (cols - 1)) / cols;
		for (int i = 0; i < cards.size(); i++) {
			ModuleCard c = cards.get(i);
			c.x = x + (i % cols) * (cw + GAP);
			c.y = y + (i / cols) * (ModuleCard.H + ROW_GAP);
			c.w = cw;
			c.refreshText(font);
		}
		int rows = (cards.size() + cols - 1) / cols;
		scroll.setContentHeight(rows == 0 ? 0 : rows * (ModuleCard.H + ROW_GAP) - ROW_GAP);
	}

	public boolean isEmpty() {
		return cards.isEmpty();
	}

	public void render(GuiGraphics g, Font font, int mx, int my, float dtMs, boolean allowHover) {
		scroll.update(dtMs);
		int off = scroll.offset();
		scroll.begin(g);
		boolean inside = allowHover && scroll.contains(mx, my);
		for (ModuleCard c : cards) {
			int sy = c.y - off;
			if (sy + ModuleCard.H < scroll.y - 4 || sy > scroll.y + scroll.h + 4) {
				continue;
			}
			c.setHover(inside && Gfx.hit(mx, my, c.x, sy, c.w, ModuleCard.H));
			c.update(dtMs);
			c.render(g, font, sy);
		}
		scroll.end(g);
		scroll.renderBar(g);
	}

	/** @return карточка и зона под курсором или null. */
	public Hit hit(double mx, double my) {
		if (!scroll.contains(mx, my)) {
			return null;
		}
		int off = scroll.offset();
		for (ModuleCard c : cards) {
			int sy = c.y - off;
			ModuleCard.Zone z = c.zoneAt(mx, my, sy);
			if (z != ModuleCard.Zone.NONE) {
				return new Hit(c, z, sy);
			}
		}
		return null;
	}
}
