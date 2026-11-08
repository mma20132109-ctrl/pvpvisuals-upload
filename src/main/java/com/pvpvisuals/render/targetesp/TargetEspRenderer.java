package com.pvpvisuals.render.targetesp;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Entry point for the new Target ESP Aura renderer. */
public final class TargetEspRenderer {
    private TargetEspRenderer() { }

    public static void reset() {
        AuraEspRenderer.reset();
    }

    public static void render(WorldRenderContext context) {
        AuraEspRenderer.render(context);
    }
}
