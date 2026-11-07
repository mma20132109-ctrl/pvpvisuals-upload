package com.pvpvisuals.gui.rival;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Кликабельная строка модуля внутри колонки (и, при раскрытии, строки подмодулей). */
public final class ModuleEntry {
	public static final int BAR_W = 44;

	public final ModuleDef def;
	public final int depth;
	public final List<ModuleEntry> kids = new ArrayList<>();
	public boolean expanded;

	public ModuleEntry(ModuleDef def, int depth) {
		this.def = def;
		this.depth = depth;
		for (ModuleDef child : def.children) {
			kids.add(new ModuleEntry(child, depth + 1));
		}
	}

	public boolean hasKids() {
		return !kids.isEmpty();
	}

	public boolean matches(String q) {
		return q.isEmpty() || def.title().toLowerCase().contains(q);
	}

	public boolean subtreeMatches(String q) {
		if (matches(q)) {
			return true;
		}
		for (ModuleEntry k : kids) {
			if (k.subtreeMatches(q)) {
				return true;
			}
		}
		return false;
	}

	/** Левая граница полосы ползунка для колонки с левым краем x и шириной w. */
	public static int barX(int x, int w) {
		return x + w - 8 - BAR_W;
	}

	public void render(GuiGraphics g, Font font, int x, int y, int w, int rowH, boolean hovered) {
		Theme t = Theme.current();
		if (hovered) {
			RoundedRender.fill(g, x + 3, y, w - 6, rowH - 1, 3, 0x38000000);
		}

		int textX = x + 9 + depth * 8;
		boolean lit = def.enabled() || def.isGroup() || def.isSlider();
		g.drawString(font, def.title(), textX, y + (rowH - 8) / 2, lit ? t.textOn : t.textOff, false);

		if (def.isSlider()) {
			int bx = barX(x, w);
			int by = y + 3;
			int bh = rowH - 7;
			RoundedRender.fill(g, bx, by, BAR_W, bh, 3, 0x55000000);
			int fw = Math.max(5, (int) (BAR_W * def.ratio()));
			RoundedRender.fill(g, bx, by, fw, bh, 3, t.accent);
			String v = def.valueText();
			g.drawString(font, v, bx + (BAR_W - font.width(v)) / 2, by + (bh - 8) / 2 + 1, 0xFFFFFFFF, true);
			return;
		}

		int right = x + w - 8;
		if (hasKids()) {
			RoundedRender.arrow(g, right - 7, y + (rowH - 4) / 2, t.textOn, !expanded);
			right -= 14;
		}
		if (def.enabled()) {
			RoundedRender.check(g, right - 8, y + (rowH - 6) / 2, t.accent);
		}
	}
}
