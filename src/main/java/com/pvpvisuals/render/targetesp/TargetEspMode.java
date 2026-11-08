package com.pvpvisuals.render.targetesp;

/** Visual modes ported from the original TargetESP JAR; legacy PvPVisuals styles stay available. */
public enum TargetEspMode {
    GHOSTS("Ghosts"),
    GHOSTS_2("Ghosts 2"),
    KVADRAT("Kvadrat"),
    KRUG("Krug");

    public final String title;
    TargetEspMode(String title) { this.title = title; }

    public static TargetEspMode byName(String value) {
        if (value == null) return GHOSTS;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "GHOSTS_2", "GHOSTS2", "GHOSTS 2" -> GHOSTS_2;
            case "KVADRAT", "SQUARE", "3D BOX" -> KVADRAT;
            case "KRUG", "CIRCLE", "RING" -> KRUG;
            case "GHOSTS", "GHOST" -> GHOSTS;
            default -> GHOSTS;
        };
    }
}
