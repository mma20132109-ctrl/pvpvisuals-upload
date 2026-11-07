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
		int rowY = y;
		for (int start = 0; start < cards.size(); start += cols) {
			int rowH = ModuleCard.H;
			for (int col = 0; col < cols && start + col < cards.size(); col++) rowH = Math.max(rowH, cards.get(start + col).totalHeight());
			for (int col = 0; col < cols && start + col < cards.size(); col++) {
				ModuleCard c = cards.get(start + col);
				c.x = x + col * (cw + GAP); c.y = rowY; c.w = cw; c.refreshText(font);
			}
			rowY += rowH + ROW_GAP;
		}
		scroll.setContentHeight(cards.isEmpty() ? 0 : rowY - y - ROW_GAP);
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
			if (sy + c.totalHeight() < scroll.y - 4 || sy > scroll.y + scroll.h + 4) continue;
			c.setHover(inside && Gfx.hit(mx, my, c.x, sy, c.w, ModuleCard.H));
			c.update(dtMs); c.render(g, font, sy);
			if (c.expanded) {
				c.inlineSettings.layoutInline(c.x, sy + ModuleCard.H + 4, c.w, ModuleCard.INLINE_H);
				c.inlineSettings.render(g, font, mx, my, dtMs, inside, c.bindText);
			}
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
			if (c.expanded) {
				c.inlineSettings.layoutInline(c.x, sy + ModuleCard.H + 4, c.w, ModuleCard.INLINE_H);
				if (c.inlineSettings.contains(mx, my)) return new Hit(c, ModuleCard.Zone.INLINE_SETTINGS, sy);
			}
			ModuleCard.Zone z = c.zoneAt(mx, my, sy);
			if (z != ModuleCard.Zone.NONE) return new Hit(c, z, sy);
		}
		return null;
	}

	public boolean scrollInlineAt(double mx, double my, float amount) {
		int off = scroll.offset();
		for (ModuleCard c : cards) if (c.expanded) {
			int sy = c.y - off;
			c.inlineSettings.layoutInline(c.x, sy + ModuleCard.H + 4, c.w, ModuleCard.INLINE_H);
			if (c.inlineSettings.contains(mx, my)) { c.inlineSettings.scrollBy(amount); return true; }
		}
		return false;
	}

	public boolean dragInline(double mx) {
		for (ModuleCard c : cards) if (c.expanded && c.inlineSettings.isDragging()) { c.inlineSettings.dragTo(mx); return true; }
		return false;
	}

	public boolean releaseInline() {
		for (ModuleCard c : cards) if (c.expanded && c.inlineSettings.isDragging()) { c.inlineSettings.endDrag(); return true; }
		return false;
	}
}
