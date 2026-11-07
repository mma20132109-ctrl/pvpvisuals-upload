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
    private static final float SWITCH_W = 20f;
    private static final float SWITCH_H = 11f;

    private static final class Toast {
        final String text;
        final boolean ok;
        final boolean toggle;
        final long created;
        final long life;
        volatile long leaveAt;

        Toast(String text, boolean ok, boolean toggle, long created, long life) {
            this.text = text;
            this.ok = ok;
            this.toggle = toggle;
            this.created = created;
            this.life = life;
        }

        void leave(long now) {
            if (leaveAt == 0L) {
                leaveAt = now;
            }
        }
    }

    private static final List<Toast> TOASTS = new CopyOnWriteArrayList<>();

    private NotificationToast() { }

    /** Обычное системное уведомление (например, Panic или Toggle All). */
    public static void show(String text, boolean ok) {
        show(text, ok, PvpConfig.get().binds.durationMs);
    }

    public static void show(String text, boolean ok, long durationMs) {
        add(new Toast(text, ok, false, System.currentTimeMillis(),
                Math.max(2 * FADE_MS, durationMs)));
    }

    /** Уведомление о переключении модуля в стиле Svintus. */
    public static void showToggle(String module, boolean enabled, long durationMs) {
        long now = System.currentTimeMillis();
        for (Toast toast : TOASTS) {
            if (toast.toggle && toast.text.equals(module)) {
                toast.leave(now);
            }
        }
        add(new Toast(module, enabled, true, now, Math.max(2 * FADE_MS, durationMs)));
    }

    private static void add(Toast toast) {
        TOASTS.add(toast);
        while (TOASTS.size() > MAX) {
            TOASTS.remove(0);
        }
    }

    private static void expire(long now) {
        for (Toast toast : TOASTS) {
            if (now - toast.created >= toast.life) {
                toast.leave(toast.created + toast.life);
            }
        }
        TOASTS.removeIf(t -> t.leaveAt != 0L && now - t.leaveAt >= FADE_MS);
    }

    public static boolean isEmpty() {
        expire(System.currentTimeMillis());
        return TOASTS.isEmpty();
    }

    public static int contentHeight() {
        expire(System.currentTimeMillis());
        return TOASTS.isEmpty() ? CARD_H : TOASTS.size() * (CARD_H + GAP) - GAP;
    }

    private static float progress(Toast toast, long now) {
        long age = Math.max(0L, now - toast.created);
        float appear = Gfx.ease(age / (float) FADE_MS);
        float leave = toast.leaveAt == 0L ? 1f
                : 1f - Gfx.ease((now - toast.leaveAt) / (float) FADE_MS);
        return Math.max(0f, Math.min(1f, Math.min(appear, leave)));
    }

    /**
     * Рисует стопку от локальной точки (anchorX, anchorY).
     * Для bottom=true anchorY задаёт нижний край стопки.
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
            long age = Math.max(0L, now - toast.created);
            float alpha = progress(toast, now);
            if (alpha <= 0.001f) {
                continue;
            }

            float x = anchorX + (right ? 1f : -1f) * (1f - alpha) * 28f;
            float y = bottom ? anchorY - CARD_H - stack : anchorY + stack;
            int accent = toast.ok ? HudStyle.ACCENT : 0xFFE66B75;

            HudStyle.panel(g, Math.round(x), Math.round(y), CARD_W, CARD_H, 7, alpha);
            if (toast.ok) {
                HudStyle.accentWash(g, x, y, 70f, CARD_H, 7f, alpha);
            }

            float textX;
            if (toast.toggle) {
                drawToggle(g, x + 8f, y + (CARD_H - SWITCH_H) / 2f - 1f,
                        toast.ok, alpha);
                textX = x + 8f + SWITCH_W + 7f;
            } else {
                Gfx.rrect(g, x + 8f, y + 6f, 14f, 14f, 4f,
                        HudStyle.opacity(accent, alpha * 0.20f),
                        HudStyle.opacity(accent, alpha * 0.10f), 0, 0f);
                Gfx.circle(g, x + 15f, y + 13f, 2.3f,
                        HudStyle.opacity(accent, alpha));
                textX = x + 29f;
            }

            int available = Math.max(20, Math.round(CARD_W - (textX - x) - 8f));
            String title = Gfx.trimAtSize(font, toast.text, available, 6.8f);
            Gfx.textAtSize(g, font, title, textX, y + 4.5f, 6.8f,
                    HudStyle.opacity(HudStyle.TEXT, alpha), false);

            String subtitle = toast.toggle
                    ? (toast.ok ? "Модуль включён" : "Модуль выключен")
                    : (toast.ok ? "Действие выполнено" : "Не удалось выполнить");
            int subtitleColor = toast.toggle
                    ? (toast.ok ? HudStyle.ACCENT : HudStyle.MUTED)
                    : (toast.ok ? HudStyle.ACCENT : 0xFFE69A9E);
            Gfx.textAtSize(g, font, subtitle, textX, y + 14f, 5.6f,
                    HudStyle.opacity(subtitleColor, alpha), false);

            float remaining = toast.leaveAt != 0L ? 0f
                    : Math.max(0f, 1f - age / (float) toast.life);
            float barW = CARD_W - 16f;
            Gfx.rrect(g, x + 8f, y + CARD_H - 3.5f, barW, 1.2f, 0.6f,
                    HudStyle.opacity(0x24FFFFFF, alpha),
                    HudStyle.opacity(0x24FFFFFF, alpha), 0, 0f);
            Gfx.rrect(g, x + 8f, y + CARD_H - 3.5f,
                    Math.max(1f, barW * remaining), 1.2f, 0.6f,
                    HudStyle.opacity(accent, alpha), HudStyle.opacity(accent, alpha), 0, 0f);

            stack += (CARD_H + GAP) * alpha;
        }
    }

    private static void drawToggle(GuiGraphics g, float x, float y, boolean enabled, float alpha) {
        float radius = SWITCH_H / 2f;
        int track = enabled ? HudStyle.opacity(HudStyle.ACCENT, 0.85f * alpha)
                : HudStyle.opacity(0xFF30313B, alpha);
        Gfx.rrect(g, x, y, SWITCH_W, SWITCH_H, radius, track, track, 0, 0f);

        float knob = SWITCH_H - 4f;
        float knobX = x + 2f + (enabled ? SWITCH_W - 4f - knob : 0f);
        Gfx.circle(g, knobX + knob / 2f, y + SWITCH_H / 2f, knob / 2f,
                HudStyle.opacity(0xFFFFFFFF, alpha));
    }
}
