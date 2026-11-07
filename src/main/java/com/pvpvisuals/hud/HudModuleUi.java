package com.pvpvisuals.hud;

import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.client.Minecraft;

/** Общее подменю «Layout» для любого HUD-модуля: якорь, X/Y, масштаб, прозрачность, редактор и сброс. */
public final class HudModuleUi {
	private HudModuleUi() { }

	public static ModuleDef layout(AbstractHudModule m, Category cat) {
		ModuleDef anchor = ModuleDef.group(cat, () -> "Anchor: " + m.anchor().title);
		for (HudAnchor a : HudAnchor.values()) {
			anchor.child(ModuleDef.toggle(a.title, cat, () -> m.anchor() == a, v -> m.changeAnchor(a)));
		}
		return ModuleDef.group(cat, () -> "Layout")
				.child(anchor)
				.child(ModuleDef.slider("X", cat, -300, 960, 0, () -> m.settings().x, v -> m.settings().x = (int) v))
				.child(ModuleDef.slider("Y", cat, -300, 960, 0, () -> m.settings().y, v -> m.settings().y = (int) v))
				.child(ModuleDef.slider("Scale", cat, AbstractHudModule.MIN_SCALE, AbstractHudModule.MAX_SCALE, 2,
						() -> m.settings().scale, v -> m.settings().scale = (float) v))
				.child(ModuleDef.slider("Alpha", cat, AbstractHudModule.MIN_ALPHA, 1.0, 2,
						() -> m.settings().alpha, v -> m.settings().alpha = (float) v))
				.child(ModuleDef.action("Edit HUD", cat, () -> Minecraft.getInstance().setScreen(new HudEditorScreen())))
				.child(ModuleDef.action("Reset", cat, m::reset));
	}
}
