package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.state.CombatState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Счётчик ударов подряд. */
public final class ComboComponent extends AbstractHudModule {
	@Override public String id() { return "combo"; }
	@Override public String label() { return "Combo Counter"; }
	@Override public int defaultX() { return 10; }
	@Override public int defaultY() { return 60; }
	@Override protected int contentWidth() { return Math.max(58, 14 + Gfx.widthAtSize(Minecraft.getInstance().font, "Combo:", 6.4f) + Gfx.widthAtSize(Minecraft.getInstance().font, "999", 6.4f)); }
	@Override protected int contentHeight() { return 18; }
	@Override public boolean enabled() { return PvpConfig.get().comboCounter; }

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		int combo = preview ? 5 : CombatState.combo;
		if (combo <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		HudStyle.panel(g, 0, 0, contentWidth(), contentHeight(), 7, HudAlpha.current);
		HudStyle.accentWash(g, 0, 0, 28, contentHeight(), 7, HudAlpha.current);
		Gfx.textAtSize(g, mc.font, "Combo:", 7f, 5.1f, 6.4f,
				HudAlpha.mul(HudStyle.TEXT), false);
		float valueX = 7f + Gfx.widthAtSize(mc.font, "Combo: ", 6.4f);
		Gfx.textAtSize(g, mc.font, Integer.toString(combo), valueX, 5.1f, 6.4f,
				HudAlpha.mul(HudStyle.ACCENT), false);
	}
}
