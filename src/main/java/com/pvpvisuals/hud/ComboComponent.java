package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.state.CombatState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Счётчик ударов подряд. */
public final class ComboComponent extends AbstractHudModule {
	@Override public String id() { return "combo"; }
	@Override public String label() { return "Combo Counter"; }
	@Override public int defaultX() { return 10; }
	@Override public int defaultY() { return 60; }
	@Override protected int contentWidth() { return 74; }
	@Override protected int contentHeight() { return 18; }
	@Override public boolean enabled() { return PvpConfig.get().comboCounter; }

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		int combo = preview ? 5 : CombatState.combo;
		if (combo <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		HudStyle.panel(g, 0, 0, contentWidth(), contentHeight());
		g.drawString(mc.font, "Combo: ", 7, 5, HudAlpha.mul(HudStyle.TEXT), false);
		g.drawString(mc.font, String.valueOf(combo), 7 + mc.font.width("Combo: "), 5, HudAlpha.mul(HudStyle.ACCENT), false);
	}
}
