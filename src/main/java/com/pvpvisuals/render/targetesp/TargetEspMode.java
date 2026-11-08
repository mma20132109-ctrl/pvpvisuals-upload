package com.pvpvisuals.render.targetesp;

/** Visual modes ported from TargetESP 1.0.0 (CC0-1.0). */
public enum TargetEspMode {
    GHOSTS("Ghosts"), KVADRAT("Kvadrat"), KRUG("Krug");
    public final String title;
    TargetEspMode(String title) { this.title = title; }
    public static TargetEspMode byName(String value) {
        if (value == null) return GHOSTS;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "KVADRAT", "SQUARE", "3D BOX" -> KVADRAT;
            case "KRUG", "CIRCLE", "RING" -> KRUG;
            case "GHOSTS", "GHOST" -> GHOSTS;
            default -> GHOSTS;
        };
    }
}
