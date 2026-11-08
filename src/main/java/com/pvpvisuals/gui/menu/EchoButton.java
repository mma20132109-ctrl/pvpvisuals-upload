package com.pvpvisuals.gui.menu;

import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Animated liquid-glass navigation button used by the Echo title menu.
 * Wide buttons carry an icon and label; compact utility buttons reveal a label on hover.
 */
public final class EchoButton {
    public enum Icon { NONE, SINGLEPLAYER, MULTIPLAYER, ACCOUNTS, MODS, SETTINGS, EXIT, GLOBE, VISUALS }

    public int x, y, w, h;
    public final String label;
    public final Icon icon;
    public final Runnable action;

    private float hover;
    private float press;
    public boolean down;

    public EchoButton(String label, Icon icon, Runnable action) {
        this.label = label;
        this.icon = icon;
        this.action = action;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Frame-rate independent hover and press transitions. */
    public void update(float dt, boolean hovered) {
        float hoverK = 1f - (float) Math.exp(-dt * 13f);
        hover += ((hovered ? 1f : 0f) - hover) * hoverK;
        press += ((down && hovered ? 1f : 0f) - press) * (1f - (float) Math.exp(-dt * 28f));
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int result = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            result |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return result;
    }

    private static int alpha(int color, float factor) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * factor)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Screen controls the intro fade and small upward entrance offset. */
    public void render(GuiGraphics g, Font font, float appear, float offY) {
        if (appear <= 0.003f) return;

        float previousAlpha = Gfx.alpha;
        Gfx.alpha = previousAlpha * appear;
        try {
            float h = Gfx.ease(hover);
            float pressAmount = press;
            float scale = (1f + 0.012f * h) * (1f - 0.045f * pressAmount);
            float drawW = w * scale;
            float drawH = this.h * scale;
            float cx = x + w / 2f;
            float cy = y + this.h / 2f + offY;
            float bx = cx - drawW / 2f;
            float by = cy - drawH / 2f;
            float radius = Math.min(drawH / 2f, 11f);

            int accent = EchoTheme.accent();
            int secondary = EchoTheme.accent2();
            int top = lerp(0xD51B202B, alpha(accent, 0.58f), h);
            int bottom = lerp(0xD00E121C, alpha(secondary, 0.72f), h);
            int border = lerp(0x46FFFFFF, alpha(accent, 0.88f), h);
            int text = lerp(0xFFD5D9E4, 0xFFFFFFFF, h);

            if (h > 0.02f) {
                Gfx.glow(g, bx, by, drawW, drawH, radius, alpha(accent, 0.8f), 3.5f * h);
            }
            Gfx.rrect(g, bx, by, drawW, drawH, radius,
                    lerp(top, 0xFF090B12, pressAmount * 0.24f),
                    lerp(bottom, 0xFF070910, pressAmount * 0.24f),
                    border, 0.9f);

            // A restrained glass reflection across the top edge.
            Gfx.rrect(g, bx + 2f, by + 1.5f, Math.max(0f, drawW - 4f), Math.min(8f, drawH * 0.30f),
                    Math.max(1f, radius - 2f), alpha(0xFFFFFFFF, 0.075f + h * 0.06f),
                    alpha(0xFFFFFFFF, 0f), 0, 0f);

            boolean compact = w < 112 || drawW <= drawH + 8f;
            if (compact) {
                drawIcon(g, cx, cy, Math.min(7.4f, drawH * 0.27f), text);
                if (h > 0.10f && label != null && !label.isBlank()) {
                    drawTooltip(g, font, cx, by - 7f, h, text, accent);
                }
            } else {
                float iconSize = 12f;
                float labelWidth = label == null || label.isBlank() ? 0f : Gfx.width(font, label);
                float gap = labelWidth > 0f ? 8f : 0f;
                float contentWidth = (icon == Icon.NONE ? 0f : iconSize) + gap + labelWidth;
                float contentX = cx - contentWidth / 2f;
                if (icon != Icon.NONE) {
                    drawIcon(g, contentX + iconSize / 2f, cy, 6.2f, text);
                    contentX += iconSize + gap;
                }
                if (labelWidth > 0f) {
                    Gfx.text(g, font, label, Math.round(contentX), Math.round(cy - 4f), text);
                }
                // Small accent dash appears under the label on hover.
                if (h > 0.02f) {
                    float dashW = Math.min(22f, Math.max(8f, labelWidth * 0.22f)) * h;
                    Gfx.rrect(g, cx - dashW / 2f, by + drawH - 3f, dashW, 1.4f, 1f,
                            alpha(accent, 0.9f * h), alpha(secondary, 0.9f * h), 0, 0f);
                }
            }
        } finally {
            Gfx.alpha = previousAlpha;
        }
    }

    private void drawTooltip(GuiGraphics g, Font font, float cx, float anchorY, float amount, int textColor, int accent) {
        float width = Gfx.width(font, label) + 18f;
        float height = 20f;
        float tx = Math.max(6f, Math.min(g.guiWidth() - width - 6f, cx - width / 2f));
        float ty = Math.max(4f, anchorY - height);
        Gfx.alpha *= amount;
        Gfx.glass(g, tx, ty, width, height, 7f, 0xDE171B26, 0xDA0D111A, alpha(accent, 0.55f));
        Gfx.text(g, font, label, Math.round(tx + (width - Gfx.width(font, label)) / 2f),
                Math.round(ty + 6f), textColor);
    }

    private static void drawArc(GuiGraphics g, float cx, float cy, float radius, float thickness,
            float startDegrees, float endDegrees, int color) {
        int segments = 12;
        float previousX = cx + (float) Math.cos(Math.toRadians(startDegrees)) * radius;
        float previousY = cy + (float) Math.sin(Math.toRadians(startDegrees)) * radius;
        for (int i = 1; i <= segments; i++) {
            float angle = startDegrees + (endDegrees - startDegrees) * i / segments;
            float x = cx + (float) Math.cos(Math.toRadians(angle)) * radius;
            float y = cy + (float) Math.sin(Math.toRadians(angle)) * radius;
            Gfx.line(g, previousX, previousY, x, y, thickness, color);
            previousX = x;
            previousY = y;
        }
    }

    private void drawIcon(GuiGraphics g, float cx, float cy, float r, int color) {
        switch (icon) {
            case SINGLEPLAYER -> {
                Gfx.ring(g, cx - r, cy - r * 0.72f, r * 2f, r * 1.35f, 2f, 1.2f, color);
                Gfx.line(g, cx, cy + r * 0.63f, cx, cy + r * 1.12f, 1.2f, color);
                Gfx.line(g, cx - r * 0.55f, cy + r * 1.16f, cx + r * 0.55f, cy + r * 1.16f, 1.2f, color);
            }
            case MULTIPLAYER -> {
                Gfx.circle(g, cx - r * 0.38f, cy - r * 0.38f, r * 0.34f, color);
                Gfx.circle(g, cx + r * 0.48f, cy - r * 0.20f, r * 0.28f, color);
                drawArc(g, cx - r * 0.35f, cy + r * 0.06f, r * 0.74f, 1.3f, 20f, 160f, color);
                drawArc(g, cx + r * 0.48f, cy + r * 0.10f, r * 0.52f, 1.1f, 20f, 160f, color);
            }
            case ACCOUNTS -> {
                Gfx.ringCircle(g, cx, cy - r * 0.28f, r * 0.34f, 1.2f, color);
                drawArc(g, cx, cy + r * 0.08f, r * 0.72f, 1.4f, 20f, 160f, color);
            }
            case MODS -> {
                float cell = r * 0.42f;
                float gap = r * 0.16f;
                for (int row = -1; row <= 1; row++) {
                    for (int column = -1; column <= 1; column++) {
                        if (row == 0 && column == 0) continue;
                        Gfx.rrect(g, cx + column * (cell + gap) - cell / 2f,
                                cy + row * (cell + gap) - cell / 2f,
                                cell, cell, 1.1f, color, color, 0, 0f);
                    }
                }
            }
            case SETTINGS -> {
                Gfx.ringCircle(g, cx, cy, r * 0.42f, 1.5f, color);
                Gfx.circle(g, cx, cy, r * 0.13f, color);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2.0 * i / 8.0;
                    float x0 = cx + (float) Math.cos(a) * r * 0.62f;
                    float y0 = cy + (float) Math.sin(a) * r * 0.62f;
                    float x1 = cx + (float) Math.cos(a) * r * 0.98f;
                    float y1 = cy + (float) Math.sin(a) * r * 0.98f;
                    Gfx.line(g, x0, y0, x1, y1, 1.5f, color);
                }
            }
            case EXIT -> {
                float d = r * 0.68f;
                Gfx.line(g, cx - d, cy - d, cx + d, cy + d, 1.6f, color);
                Gfx.line(g, cx + d, cy - d, cx - d, cy + d, 1.6f, color);
            }
            case VISUALS -> {
                Gfx.circle(g, cx, cy, r * 0.72f, color);
                Gfx.ringCircle(g, cx, cy, r, 1.1f, color);
                Gfx.line(g, cx - r * 0.7f, cy, cx + r * 0.7f, cy, 1.0f, color);
            }
            case GLOBE -> {
                Gfx.ringCircle(g, cx, cy, r, 1.2f, color);
                Gfx.line(g, cx - r, cy, cx + r, cy, 1.1f, color);
                float px = cx, py = cy - r;
                for (int i = 1; i <= 16; i++) {
                    double a = -Math.PI / 2 + Math.PI * 2 * i / 16;
                    float x2 = cx + (float) Math.cos(a) * r * 0.45f;
                    float y2 = cy + (float) Math.sin(a) * r;
                    Gfx.line(g, px, py, x2, y2, 1.0f, color);
                    px = x2;
                    py = y2;
                }
            }
            default -> { }
        }
    }
}
