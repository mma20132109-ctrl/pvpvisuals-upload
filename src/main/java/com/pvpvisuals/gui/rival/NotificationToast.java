package com.pvpvisuals.gui.rival;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.hud.HudStyle;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Очередь уведомлений. Отрисовка выполняется NotificationsHudModule,
 * поэтому карточки используют общие позиции, масштаб, альфу и редактор HUD.
 */
public final class NotificationToast {
    public static final long DEFAULT_MS = 3500;
    private static final long FADE_MS = 220;
    public static final int CARD_W = 150;
    public static final int CARD_H = 27;
    public static final int GAP = 4;
    private static final int MAX = 5;

    private record Toast(String text, boolean ok, long created, long life) { }
    private static final List<Toast> TOASTS = new CopyOnWriteArrayList<>();

    private NotificationToast() { }

    public static void show(String text, boolean ok) {
        show(text, ok, PvpConfig.get().binds.durationMs);
    }

    public static void show(String text, boolean ok, long durationMs) {
        TOASTS.add(new Toast(text, ok, System.currentTimeMillis(),
                Math.max(2 * FADE_MS, durationMs)));
        while (TOASTS.size() > MAX) {
            TOASTS.remove(0);
        }
    }

    private static void expire(long now) {
        TOASTS.removeIf(t -> now - t.created() >= t.life());
    }

    public static boolean isEmpty() {
        expire(System.currentTimeMillis());
        return TOASTS.isEmpty();
    }

    public static int contentHeight() {
        expire(System.currentTimeMillis());
        return TOASTS.isEmpty() ? CARD_H : TOASTS.size() * (CARD_H + GAP) - GAP;
    }

    /**
     * Рисует стопку от локальной точки (anchorX, anchorY).
     * Для bottom=true anchorY задаёт нижний край стопки, как в Svintus.
     */
    public static void renderAt(
            GuiGraphics g,
            Font font,
            float anchorX,
            float anchorY,
            boolean right,
            boolean bottom,
            boolean preview
    ) {
        long now = System.currentTimeMillis();
        expire(now);
        if (TOASTS.isEmpty()) {
            if (preview) {
                HudStyle.panel(g, Math.round(anchorX), Math.round(anchorY - (bottom ? CARD_H : 0)),
                        CARD_W, CARD_H, 7, 0.72f);
                Gfx.textAtSize(g, font, "Здесь появятся уведомления",
                        anchorX + 9f, anchorY + (bottom ? -16f : 10f), 5.8f,
                        HudStyle.opacity(HudStyle.MUTED, 0.95f), false);
            }
            return;
        }

        float stack = 0f;
        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            Toast toast = TOASTS.get(i);
            long age = Math.max(0L, now - toast.created());

            float appear = Gfx.ease(age / (float) FADE_MS);
            float leave = age <= toast.life() - FADE_MS ? 1f
                    : 1f - Gfx.ease((age - (toast.life() - FADE_MS)) / (float) FADE_MS);
            float progress = Math.min(appear, leave);

            float x = anchorX + (right ? 1f : -1f) * (1f - progress) * 28f;
            float y = bottom ? anchorY - CARD_H - stack : anchorY + stack;
            float alpha = Math.max(0f, Math.min(1f, progress));
            int accent = toast.ok() ? 0xFF4FD96A : 0xFFE66B65;

            HudStyle.panel(g, Math.round(x), Math.round(y), CARD_W, CARD_H, 7, alpha);
            if (toast.ok()) {
                HudStyle.accentWash(g, x, y, 70f, CARD_H, 7f, alpha);
            }

            Gfx.rrect(g, x + 8f, y + 6f, 14f, 14f, 4f,
                    HudStyle.opacity(accent, alpha * 0.20f),
                    HudStyle.opacity(accent, alpha * 0.10f), 0, 0f);
            Gfx.circle(g, x + 15f, y + 13f, 2.3f,
                    HudStyle.opacity(accent, alpha));

            String title = Gfx.trimAtSize(font, toast.text(), 118, 6.8f);
            Gfx.textAtSize(g, font, title, x + 29f, y + 4.5f, 6.8f,
                    HudStyle.opacity(HudStyle.TEXT, alpha), false);
            String subtitle = toast.ok() ? "Действие выполнено" : "Не удалось выполнить";
            Gfx.textAtSize(g, font, subtitle, x + 29f, y + 14f, 5.6f,
                    HudStyle.opacity(toast.ok() ? HudStyle.ACCENT : HudStyle.MUTED, alpha), false);

            float remaining = Math.max(0f, 1f - age / (float) toast.life());
            Gfx.rrect(g, x + 8f, y + CARD_H - 3.5f, CARD_W - 16f, 1.2f, 0.6f,
                    HudStyle.opacity(0x24FFFFFF, alpha),
                    HudStyle.opacity(0x24FFFFFF, alpha), 0, 0f);
            Gfx.rrect(g, x + 8f, y + CARD_H - 3.5f,
                    Math.max(1f, (CARD_W - 16f) * remaining), 1.2f, 0.6f,
                    HudStyle.opacity(accent, alpha), HudStyle.opacity(accent, alpha), 0, 0f);

            stack += (CARD_H + GAP) * alpha;
        }
    }
}
