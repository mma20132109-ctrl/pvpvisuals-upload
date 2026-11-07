package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.NotificationToast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Стопка карточек Notifications: позиция и анимация интегрированы с HUD Editor. */
public final class NotificationsHudModule extends AbstractHudModule {
    @Override public String id() { return "notifications"; }
    @Override public String label() { return "Notifications"; }
    @Override public int defaultX() { return 6; }
    @Override public int defaultY() { return 40; }
    @Override public boolean enabled() { return PvpConfig.get().binds.showNotifications; }

    @Override
    protected int contentWidth() {
        return NotificationToast.CARD_W;
    }

    @Override
    protected int contentHeight() {
        return NotificationToast.contentHeight();
    }

    @Override
    protected void drawContent(GuiGraphics g, boolean preview) {
        HudAnchor anchor = anchor();
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
        float anchorY = bottom ? contentHeight() : 0f;
        NotificationToast.renderAt(g, Minecraft.getInstance().font,
                0f, anchorY, right, bottom, preview);
    }
}
