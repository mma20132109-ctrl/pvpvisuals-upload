package com.pvpvisuals.render.targetesp;

/** Единственный актуальный стиль Target ESP. */
public enum TargetEspMode {
    AURA("Aura");

    public final String title;

    TargetEspMode(String title) {
        this.title = title;
    }

    /** Старые сохранённые значения (Ghosts/Circle/Square/3D Box) переходят на Aura. */
    public static TargetEspMode byName(String value) {
        return AURA;
    }
}
