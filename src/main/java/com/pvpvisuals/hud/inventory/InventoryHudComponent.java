package com.pvpvisuals.hud.inventory;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.hud.AbstractHudModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Inventory HUD на базе AbstractHudModule: позиция, масштаб и прозрачность — общие настройки Layout. */
public final class InventoryHudComponent extends AbstractHudModule {
	@Override public String id() { return "inventory_hud"; }
	@Override public String label() { return "Inventory HUD"; }
	@Override public int defaultX() { return 10; }
	@Override public int defaultY() { return 90; }
	@Override public boolean enabled() { return PvpConfig.get().invHud.enabled; }

	@Override
	protected int contentWidth() {
		return InventoryHudRenderer.panelWidth(PvpConfig.get().invHud);
	}

	@Override
	protected int contentHeight() {
		return InventoryHudRenderer.panelHeight(PvpConfig.get().invHud);
	}

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		InventoryHudConfig c = PvpConfig.get().invHud;
		Minecraft mc = Minecraft.getInstance();
		if (!preview && InventoryHudRenderer.hidden(mc, c)) {
			return;
		}
		InventoryHudRenderer.draw(g, mc, c);
	}
}
