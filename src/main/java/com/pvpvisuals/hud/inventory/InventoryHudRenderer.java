package com.pvpvisuals.hud.inventory;

import com.pvpvisuals.gui.echo.EchoScreen;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.hud.HudAlpha;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.hud.HudStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Раскладка и отрисовка содержимого Inventory HUD. Панель в стиле Target HUD (скруглённая, цвета темы),
 * внутри — слоты. Положение задаёт {@link InventoryHudComponent} (перетаскивание в чате).
 */
public final class InventoryHudRenderer {
	private static final int GAP = 2;
	public static final int PAD = 7;
	public static final int HEADER = 16;

	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	// результат layout() (без аллокаций)
	private static int slot;
	private static int gridW;
	private static int gridH;
	private static int gridX;
	private static int gridY;
	private static int armorX;
	private static int armorY;
	private static boolean armorVertical;
	private static int contentW;
	private static int contentH;

	private InventoryHudRenderer() { }

	private static void layout(InventoryHudConfig c) {
		int s = Math.max(16, Math.min(24, c.slotSize));
		slot = s;
		boolean main = c.showMain;
		boolean hot = c.showHotbar;
		int rows = (main ? 3 : 0) + (hot ? 1 : 0);
		gridW = rows > 0 ? 9 * s + 8 * GAP : 0;
		gridH = rows > 0 ? rows * s + (rows - 1) * GAP + (main && hot ? 3 : 0) : 0;

		int armorCount = (c.showArmor ? 4 : 0) + (c.showOffhand ? 1 : 0);
		int armorLen = armorCount > 0 ? armorCount * s + (armorCount - 1) * GAP : 0;

		gridX = 0;
		gridY = 0;
		armorX = 0;
		armorY = 0;
		ArmorLayout layout = ArmorLayout.byName(c.armorLayout);
		armorVertical = layout == ArmorLayout.LEFT;
		switch (layout) {
			case LEFT -> {
				gridX = armorCount > 0 ? s + GAP * 2 : 0;
				contentW = gridX + gridW;
				contentH = Math.max(gridH, armorLen);
			}
			case TOP -> {
				gridY = armorCount > 0 ? s + GAP * 2 : 0;
				contentW = Math.max(gridW, armorLen);
				contentH = gridY + gridH;
			}
			default -> {
				armorY = gridH + (rows > 0 ? GAP * 2 : 0);
				contentW = Math.max(gridW, armorLen);
				contentH = armorY + (armorCount > 0 ? s : 0);
			}
		}
	}

	/** Размер панели (без масштаба): ширина. */
	public static int panelWidth(InventoryHudConfig c) {
		layout(c);
		return Math.max(contentW, 54) + PAD * 2;
	}

	/** Размер панели (без масштаба): высота. */
	public static int panelHeight(InventoryHudConfig c) {
		layout(c);
		return HEADER + contentH + PAD;
	}

	/** Скрыт ли HUD по настройкам (для обычного режима, не для превью в чате). */
	public static boolean hidden(Minecraft mc, InventoryHudConfig c) {
		if (c.hideInF1 && mc.options.hideGui) {
			return true;
		}
		var screen = mc.screen;
		return (c.hideInChat && screen instanceof ChatScreen)
				|| (c.hideInInventory && screen instanceof InventoryScreen)
				|| (c.hideInMenu && screen instanceof EchoScreen);
	}

	/** Рисует панель от (0, 0): сдвиг, масштаб и прозрачность задаёт AbstractHudModule. */
	public static void draw(GuiGraphics g, Minecraft mc, InventoryHudConfig c) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		layout(c);
		int s = slot;
		float op = HudAlpha.current;
		Font font = mc.font;
		Inventory inv = player.getInventory();

		int pw = Math.max(contentW, 54) + PAD * 2;
		int ph = HEADER + contentH + PAD;

		// Общая стеклянная поверхность и типографика всех HUD-виджетов.
		HudStyle.panel(g, 0, 0, pw, ph, HudStyle.RADIUS, op);
		HudStyle.accentWash(g, 0, 0, 58, HEADER, HudStyle.RADIUS, op);
		Gfx.textAtSize(g, font, "Inventory", PAD, 5.1f, 6.4f,
				scaleAlpha(HudStyle.TEXT, op), false);

		int ox = PAD;
		int oy = HEADER;

		// ПРОВЕРИТЬ: Inventory#getSelectedSlot() (в 1.21.5+ поле selected стало приватным).
		int selected = inv.getSelectedSlot();

		int row = 0;
		if (c.showMain) {
			for (int r = 0; r < 3; r++, row++) {
				for (int col = 0; col < 9; col++) {
					drawSlot(g, font, c, inv.getItem(9 + r * 9 + col), ox + gridX + col * (s + GAP), oy + gridY + row * (s + GAP), s, false);
				}
			}
		}
		if (c.showHotbar) {
			int hy = oy + gridY + row * (s + GAP) + (c.showMain ? 3 : 0);
			for (int col = 0; col < 9; col++) {
				drawSlot(g, font, c, inv.getItem(col), ox + gridX + col * (s + GAP), hy, s, col == selected);
			}
		}

		int i = 0;
		if (c.showArmor) {
			for (EquipmentSlot es : ARMOR) {
				drawSlot(g, font, c, player.getItemBySlot(es),
						ox + armorX + (armorVertical ? 0 : i * (s + GAP)), oy + armorY + (armorVertical ? i * (s + GAP) : 0), s, false);
				i++;
			}
		}
		if (c.showOffhand) {
			drawSlot(g, font, c, player.getOffhandItem(),
					ox + armorX + (armorVertical ? 0 : i * (s + GAP)), oy + armorY + (armorVertical ? i * (s + GAP) : 0), s, false);
		}
	}

	private static int scaleAlpha(int argb, float op) {
		int a = (int) (((argb >>> 24) & 0xFF) * op);
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
	}

	private static void drawSlot(GuiGraphics g, Font font, InventoryHudConfig c, ItemStack stack, int x, int y, int s, boolean active) {
		SlotRenderer.drawBackground(g, c, x, y, s, active);
		SlotRenderer.drawStack(g, font, c, stack, x, y, s);
	}
}
