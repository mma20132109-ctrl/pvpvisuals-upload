package com.pvpvisuals.config;

/** Target ESP options. Defaults mirror TargetESP 1.0.0 where applicable. */
public final class TargetEspSettings {
    public String mode = "GHOSTS";
    public boolean showPlayers = true;
    public boolean showMobs = true;
    public float maxDistance = 64.0f;
    /** Kept for older profiles; depth testing is always enabled. */
    public boolean throughWalls = false;

    /** HEALTH, STATIC, DISTANCE, RAINBOW or PRESET. */
    public String colorMode = "HEALTH";
    public String colorPreset = "CYAN";
    public int color = 0x00FFFF;
    public int friendColor = 0x75B9FF;
    public int invisibleColor = 0xB58AFF;
    public boolean useInvisibleColor = true;

    public float lineThickness = 1.6f;
    /** Legacy fields retained for backward-compatible profile loading. */
    public boolean auraGlow = true;
    public float auraSize = 1.0f;
    public boolean fillBox = true;
    public float fillAlpha = 0.10f;
    @Deprecated public int ghostCount = 60;

    /** Original Ghosts orbit controls. */
    public int ghostLength = 14;
    public int ghostLayers = 3;
    public float ghostFactor = 8.0f;
    public float ghostShaking = 1.8f;
    public float ghostAmplitude = 0.55f;
    public float ghostSize = 0.20f;
    public float ghostRadius = 0.75f;
    /** Original Ghosts orbit radius multiplier. */
    public float ghostOrbitRadius = 1.5f;
    /** Original Ring/Krug radius. */
    public float ringRadius = 0.50f;
    public float ghostSpeed = 1.0f;
    public boolean enableAlphaAnimation = false;
    public int alphaAnimationMs = 350;
    public int maxAlpha = 255;
    public float alphaFactor = 3.0f;
    public float minSizeFactor = 0.30f;

    public float animationSpeed = 1.0f;
    public float fadeOutSeconds = 0.50f;
}
