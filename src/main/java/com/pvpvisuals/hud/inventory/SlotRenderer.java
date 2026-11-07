package com.pvpvisuals.hud.inventory;

import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.hud.HudAlpha;
import com.pvpvisuals.hud.HudStyle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Рисует один слот: фон, рамку, предмет, количество и прочность. Без аллокаций в обычном случае. */
public final class SlotRenderer {
	private static final String[] COUNTS = new String[100];
	private static final String[] NUMBERS = new String[2200];

	static {
		for (int i = 0; i < COUNTS.length; i++) {
			COUNTS[i] = Integer.toString(i);
		}
	}

	private SlotRenderer() { }

	private static String count(int n) {
		return n < COUNTS.length ? COUNTS[n] : Integer.toString(n);
	}

	private static String number(int n) {
		if (n < 0 || n >= NUMBERS.length) {
			return Integer.toString(n);
		}
		String s = NUMBERS[n];
		if (s == null) {
			s = Integer.toString(n);
			NUMBERS[n] = s;
		}
		return s;
	}

	static int argb(int rgb, int alpha, float opacity) {
		int a = Math.max(0, Math.min(255, (int) (alpha * opacity)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Зелёный (100%) -> жёлтый (50%) -> красный (0%). */
	public static int durabilityColor(float ratio) {
		float r = ratio > 0.5f ? (1f - ratio) * 2f : 1f;
		float g = ratio > 0.5f ? 1f : ratio * 2f;
		return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8);
	}

	/** Фон, рамка и подсветка слота (до предмета) в цветах темы меню. */
	public static void drawBackground(GuiGraphics g, InventoryHudConfig c, int x, int y, int s, boolean active) {
		float op = HudAlpha.current;
		if (active && c.highlightActive) {
			RoundedRender.fill(g, x - 2, y - 2, s + 4, s + 4, 5, argb(HudStyle.ACCENT, 90, op));
		}
		if (c.showBackground) {
			RoundedRender.fill(g, x, y, s, s, 3, argb(0x000000, c.backgroundAlpha, op));
		}
		if (c.showBorder) {
			int col = argb(HudStyle.MUTED, 130, op);
			for (int i = 0; i < c.borderWidth; i++) {
				g.renderOutline(x + i, y + i, s - 2 * i, s - 2 * i, col);
			}
		}
		if (active && c.highlightActive) {
			int col = argb(HudStyle.ACCENT, 255, op);
			g.renderOutline(x, y, s, s, col);
			g.renderOutline(x + 1, y + 1, s - 2, s - 2, col);
		}
	}

	/** Предмет, количество и прочность. */
	public static void drawStack(GuiGraphics g, Font font, InventoryHudConfig c, ItemStack stack, int x, int y, int s) {
		if (stack.isEmpty()) {
			return;
		}
		float op = HudAlpha.current;
		// ПРОВЕРИТЬ: GuiGraphics#renderItem(ItemStack, int, int) в 1.21.11.
		g.renderItem(stack, x + (s - 16) / 2, y + (s - 16) / 2);

		if (c.showDurability && stack.isDamageableItem()) {
			int max = stack.getMaxDamage();
			int left = max - stack.getDamageValue();
			float ratio = max > 0 ? Math.max(0f, Math.min(1f, left / (float) max)) : 0f;
			DurabilityDisplayMode mode = DurabilityDisplayMode.byName(c.durabilityMode);

			if (mode != DurabilityDisplayMode.NUMBER && stack.getDamageValue() > 0) {
				int bw = s - 4;
				int by = y + s - 4;
				g.fill(x + 2, by, x + 2 + bw, by + 2, argb(0x000000, 200, op));
				int fw = Math.max(1, Math.round(bw * ratio));
				g.fill(x + 2, by, x + 2 + fw, by + 1, durabilityAlpha(ratio, op));
			}
			if (mode != DurabilityDisplayMode.BAR && stack.getDamageValue() > 0) {
				String t = number(left);
				g.pose().pushMatrix();
				g.pose().translate(x + 1, y + 1);
				g.pose().scale(0.6f, 0.6f);
				g.drawString(font, t, 0, 0, durabilityAlpha(ratio, op), true);
				g.pose().popMatrix();
			}
		}

		if (c.showCount && stack.getCount() > 1) {
			String t = count(stack.getCount());
			float k = 0.75f;
			g.pose().pushMatrix();
			g.pose().translate(x + s - 1 - font.width(t) * k, y + s - 1 - 8 * k);
			g.pose().scale(k, k);
			g.drawString(font, t, 0, 0, argb(0xFFFFFF, 255, op), true);
			g.pose().popMatrix();
		}
	}

	private static int durabilityAlpha(float ratio, float op) {
		return (durabilityColor(ratio) & 0xFFFFFF) | (Math.max(0, Math.min(255, (int) (255 * op))) << 24);
	}
}
