package com.pvpvisuals.render.targetesp;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Entry point for the three Target ESP visual styles. */
public final class TargetEspRenderer {
    private TargetEspRenderer() { }

    public static void reset() {
        TargetEspStyleRenderer.reset();
    }

    public static void render(WorldRenderContext context) {
        TargetEspStyleRenderer.render(context);
    }
}
