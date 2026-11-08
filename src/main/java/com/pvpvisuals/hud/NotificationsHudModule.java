package com.pvpvisuals.hud;

import com.pvpvisuals.config.HudSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoScreen;
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

    /**
     * Переносит старую настройку Top/Bottom Left/Right в новый общий якорь HUD
     * только для профилей, где ещё нет сохранённой позиции виджета.
     */
    private void migrateLegacyPosition() {
        PvpConfig cfg = PvpConfig.get();
        if (cfg.hud.containsKey(id())) {
            return;
        }
        HudSettings settings = new HudSettings();
        String old = cfg.binds.position == null ? "TOP_RIGHT" : cfg.binds.position.toUpperCase(java.util.Locale.ROOT);
        settings.anchor = old.contains("BOTTOM")
                ? (old.contains("RIGHT") ? HudAnchor.BOTTOM_RIGHT.name() : HudAnchor.BOTTOM_LEFT.name())
                : (old.contains("RIGHT") ? HudAnchor.TOP_RIGHT.name() : HudAnchor.TOP_LEFT.name());
        settings.x = 10;
        settings.y = 10;
        cfg.hud.put(id(), settings);
        cfg.markDirtyQuiet();
    }

    @Override
    protected int contentWidth() {
        migrateLegacyPosition();
        return NotificationToast.CARD_W;
    }

    @Override
    protected int contentHeight() {
        return NotificationToast.contentHeight();
    }

    @Override
    protected void drawContent(GuiGraphics g, boolean preview) {
        if (!preview && Minecraft.getInstance().screen instanceof EchoScreen) {
            return;
        }
        HudAnchor anchor = anchor();
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
        float anchorY = bottom ? contentHeight() : 0f;
        NotificationToast.renderAt(g, Minecraft.getInstance().font,
                0f, anchorY, right, bottom, preview);
    }
}
