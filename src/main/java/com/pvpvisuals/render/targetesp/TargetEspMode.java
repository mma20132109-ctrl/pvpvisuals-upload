package com.pvpvisuals.render.targetesp;

/** Визуальные стили Target ESP, доступные в GUI. */
public enum TargetEspMode {
    GHOSTS("Ghosts"),
    CIRCLE("Circle"),
    SQUARE("Square"),
    BOX_3D("3D Box");

    public final String title;

    TargetEspMode(String title) {
        this.title = title;
    }

    public static TargetEspMode byName(String value) {
        if (value != null) {
            for (TargetEspMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value) || mode.title.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
        }
        return GHOSTS;
    }
}
