package com.pvpvisuals.gui.echo;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Two-column module grid with independent, strictly clipped scrolling. */
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
		scroll.setBounds(x, y, Math.max(0, w), Math.max(0, h));
		cards = list;
		int cols = w >= 250 ? 2 : 1;
		int cw = Math.max(1, (w - GAP * (cols - 1)) / cols);
		int rowTop = y;
		for (int start = 0; start < cards.size(); start += cols) {
			int rowBottom = rowTop + ModuleCard.H;
			for (int col = 0; col < cols && start + col < cards.size(); col++) {
				rowBottom = Math.max(rowBottom, rowTop + cards.get(start + col).totalHeight());
			}
			for (int col = 0; col < cols && start + col < cards.size(); col++) {
				ModuleCard card = cards.get(start + col);
				card.x = x + col * (cw + GAP);
				card.y = rowTop;
				card.w = cw;
				card.refreshText(font);
			}
			rowTop = rowBottom + ROW_GAP;
		}
		int contentBottom = cards.isEmpty() ? y : rowTop - ROW_GAP;
		scroll.setContentHeight(Math.max(0, contentBottom - y));
	}

	public boolean isEmpty() {
		return cards.isEmpty();
	}

	public void render(GuiGraphics g, Font font, int mx, int my, float dtMs, boolean allowHover) {
		scroll.update(dtMs);
		int off = scroll.offset();
		scroll.begin(g);
		boolean inside = allowHover && scroll.contains(mx, my);
		for (ModuleCard card : cards) {
			int sy = card.y - off;
			if (sy + card.totalHeight() <= scroll.y || sy >= scroll.y + scroll.h) continue;

			card.setHover(inside && Gfx.hit(mx, my, card.x, sy, card.w, ModuleCard.H));
			card.update(dtMs);
			card.render(g, font, sy);
			if (card.expanded) {
				int panelY = sy + ModuleCard.H + 4;
				card.inlineSettings.layoutInline(card.x, panelY, card.w, ModuleCard.INLINE_H);
				int clipTop = Math.max(scroll.y, panelY);
				int clipBottom = Math.min(scroll.y + scroll.h, panelY + ModuleCard.INLINE_H);
				if (clipBottom > clipTop) {
					// Clip each expanded panel independently as well as by the grid viewport.
					g.enableScissor(card.x, clipTop, card.x + card.w, clipBottom);
					card.inlineSettings.render(g, font, mx, my, dtMs, inside, card.bindText);
					g.disableScissor();
				}
			}
		}
		scroll.end(g);
		scroll.renderBar(g);
	}

	public Hit hit(double mx, double my) {
		if (!scroll.contains(mx, my)) return null;
		int off = scroll.offset();
		for (ModuleCard card : cards) {
			int sy = card.y - off;
			if (card.expanded) {
				card.inlineSettings.layoutInline(card.x, sy + ModuleCard.H + 4, card.w, ModuleCard.INLINE_H);
				if (card.inlineSettings.contains(mx, my)) return new Hit(card, ModuleCard.Zone.INLINE_SETTINGS, sy);
			}
			ModuleCard.Zone zone = card.zoneAt(mx, my, sy);
			if (zone != ModuleCard.Zone.NONE) return new Hit(card, zone, sy);
		}
		return null;
	}

	public boolean scrollInlineAt(double mx, double my, float amount) {
		if (!scroll.contains(mx, my)) return false;
		int off = scroll.offset();
		for (ModuleCard card : cards) if (card.expanded) {
			int sy = card.y - off;
			card.inlineSettings.layoutInline(card.x, sy + ModuleCard.H + 4, card.w, ModuleCard.INLINE_H);
			if (card.inlineSettings.contains(mx, my)) {
				card.inlineSettings.scrollBy(amount);
				return true;
			}
		}
		return false;
	}

	public boolean dragInline(double mx) {
		for (ModuleCard card : cards) if (card.expanded && card.inlineSettings.isDragging()) {
			card.inlineSettings.dragTo(mx);
			return true;
		}
		return false;
	}

	public boolean releaseInline() {
		for (ModuleCard card : cards) if (card.expanded && card.inlineSettings.isDragging()) {
			card.inlineSettings.endDrag();
			return true;
		}
		return false;
	}
}
