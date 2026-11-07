package com.pvpvisuals.hud.inventory;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.hud.HudManager;
import com.pvpvisuals.hud.HudModuleUi;

/**
 * Описание модуля Inventory HUD для меню (категория Utils). Сам HUD-элемент — {@link InventoryHudComponent},
 * он зарегистрирован в HudManager.COMPONENTS (редактор HUD и перетаскивание в чате).
 */
public final class InventoryHudModule {
	private InventoryHudModule() { }

	public static ModuleDef def(PvpConfig pc) {
		InventoryHudConfig c = pc.invHud;
		Category cat = Category.UTILS;

		ModuleDef mode = ModuleDef.group(cat, () -> "Mode: " + DurabilityDisplayMode.byName(c.durabilityMode).title);
		for (DurabilityDisplayMode m : DurabilityDisplayMode.values()) {
			mode.child(ModuleDef.toggle(m.title, cat, () -> DurabilityDisplayMode.byName(c.durabilityMode) == m,
					v -> c.durabilityMode = m.name()));
		}
		ModuleDef look = ModuleDef.group(cat, () -> "Look")
				.child(ModuleDef.slider("Slot Size", cat, 16, 24, 0, () -> c.slotSize, v -> c.slotSize = (int) v))
				.child(ModuleDef.toggle("Background", cat, () -> c.showBackground, v -> c.showBackground = v))
				.child(ModuleDef.slider("BG Alpha", cat, 0, 255, 0, () -> c.backgroundAlpha, v -> c.backgroundAlpha = (int) v))
				.child(ModuleDef.toggle("Border", cat, () -> c.showBorder, v -> c.showBorder = v))
				.child(ModuleDef.slider("Border Width", cat, 1, 3, 0, () -> c.borderWidth, v -> c.borderWidth = (int) v))
				.child(ModuleDef.toggle("Count", cat, () -> c.showCount, v -> c.showCount = v))
				.child(ModuleDef.toggle("Durability", cat, () -> c.showDurability, v -> c.showDurability = v))
				.child(mode)
				.child(ModuleDef.toggle("Highlight", cat, () -> c.highlightActive, v -> c.highlightActive = v));

		ModuleDef layout = ModuleDef.group(cat, () -> "Armor: " + ArmorLayout.byName(c.armorLayout).title);
		for (ArmorLayout l : ArmorLayout.values()) {
			layout.child(ModuleDef.toggle(l.title, cat, () -> ArmorLayout.byName(c.armorLayout) == l, v -> c.armorLayout = l.name()));
		}
		ModuleDef show = ModuleDef.group(cat, () -> "Show")
				.child(ModuleDef.toggle("Inventory", cat, () -> c.showMain, v -> c.showMain = v))
				.child(ModuleDef.toggle("Hotbar", cat, () -> c.showHotbar, v -> c.showHotbar = v))
				.child(ModuleDef.toggle("Armor", cat, () -> c.showArmor, v -> c.showArmor = v))
				.child(ModuleDef.toggle("Offhand", cat, () -> c.showOffhand, v -> c.showOffhand = v))
				.child(layout);

		ModuleDef behavior = ModuleDef.group(cat, () -> "Behavior")
				.child(ModuleDef.toggle("Hide in Chat", cat, () -> c.hideInChat, v -> c.hideInChat = v))
				.child(ModuleDef.toggle("Hide in Inv", cat, () -> c.hideInInventory, v -> c.hideInInventory = v))
				.child(ModuleDef.toggle("Hide in Menu", cat, () -> c.hideInMenu, v -> c.hideInMenu = v))
				.child(ModuleDef.toggle("Hide in F1", cat, () -> c.hideInF1, v -> c.hideInF1 = v));

		return ModuleDef.toggle("Inventory HUD", cat, () -> c.enabled, v -> c.enabled = v)
				.child(HudModuleUi.layout(HudManager.INVENTORY, cat)).child(look).child(show).child(behavior);
	}
}
