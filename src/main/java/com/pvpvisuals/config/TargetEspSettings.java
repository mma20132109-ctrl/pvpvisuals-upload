package com.pvpvisuals.config;

/** Настройки Target ESP. Поля имеют безопасные значения для старых конфигов. */
public final class TargetEspSettings {
    /** Reserved for the replacement renderer; old saved style values remain compatible. */
    public String mode = "GHOSTS";

    public boolean showPlayers = true;
    public boolean showMobs = true;
    public float maxDistance = 64.0f;
    /** Deprecated: Target ESP only follows visible targets and never renders through walls. */
    public boolean throughWalls = false;

    /** STATIC, HEALTH, DISTANCE или RAINBOW. */
    public String colorMode = "HEALTH";
    public int color = 0xB56BFF;
    public int friendColor = 0x75B9FF;
    public int invisibleColor = 0xB58AFF;
    public boolean useInvisibleColor = true;

    public float lineThickness = 1.6f;
    /** Shared effect size multiplier retained under its old JSON key for profile compatibility. */
    public boolean auraGlow = true;
    public float auraSize = 1.0f;
    public boolean fillBox = true;
    public float fillAlpha = 0.10f;

    /** Количество одновременно рисуемых текстурных призраков (4..48). */
    public int ghostCount = 12;
    public float animationSpeed = 1.0f;
    public float fadeOutSeconds = 0.30f;
}
