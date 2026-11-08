package com.pvpvisuals.render.targetesp;

/** Target ESP styles matching the three effects shown in the original TargetESP mod. */
public enum TargetEspMode {
    GHOSTS("Ghosts"),
    CIRCLE("Circle"),
    SQUARE("Square");

    public final String title;

    TargetEspMode(String title) {
        this.title = title;
    }

    /** Old Aura/3D Box values fall back to Ghosts without breaking saved profiles. */
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
