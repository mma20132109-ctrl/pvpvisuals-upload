package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.TopBar;
import com.pvpvisuals.render.LogoRenderer;
import com.pvpvisuals.render.LogoSizeMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Watermark: логотип (или текстовая плашка, если логотип выключен). Позиция, scale, alpha и anchor —
 * из {@link AbstractHudModule}. Версия текстуры выбирается по эффективной высоте (высота * scale).
 */
public final class WatermarkModule extends AbstractHudModule {
	private static final int LOGO_H = 24;

	@Override public String id() { return "watermark"; }
	@Override public String label() { return "Watermark"; }
	@Override public int defaultX() { return 6; }
	@Override public int defaultY() { return 6; }
	@Override public boolean enabled() { return PvpConfig.get().watermark; }

	private static boolean logo() {
		return PvpConfig.get().logo.showInWatermark && com.pvpvisuals.render.LogoRenderer.hasLogo();
	}

	private LogoSizeMode mode() {
		return LogoSizeMode.byName(PvpConfig.get().logo.sizeWatermark);
	}

	@Override
	protected int contentWidth() {
		if (logo()) {
			return LogoRenderer.widthFor(mode(), LOGO_H, scale());
		}
		return Minecraft.getInstance().font.width(TopBar.NAME) + 16;
	}

	@Override
	protected int contentHeight() {
		return logo() ? LOGO_H : 18;
	}

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		if (logo()) {
			// Keep the mark crisp while placing it on the same translucent glass tile as the text watermark.
			HudStyle.panel(g, 0, 0, contentWidth(), contentHeight(), HudStyle.RADIUS, HudAlpha.current);
			LogoRenderer.renderLogoAuto(g, mode(), 0, 0, LOGO_H, scale(), HudAlpha.current);
			return;
		}
		HudStyle.panel(g, 0, 0, contentWidth(), 18, 9, HudAlpha.current);
		g.drawString(Minecraft.getInstance().font, TopBar.NAME, 8, 5, HudAlpha.mul(HudStyle.TEXT), false);
	}
}
