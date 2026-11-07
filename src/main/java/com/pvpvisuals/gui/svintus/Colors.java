package com.pvpvisuals.gui.svintus;

/** ARGB color helpers used by the licensed Svintus GUI port. */
public final class Colors {
    private Colors() { }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int fade(int color, float factor) {
        int a = Math.round(alpha(color) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
