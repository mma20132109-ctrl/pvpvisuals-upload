package com.pvpvisuals.render.targetesp;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/**
 * Empty rendering hook while Target ESP visuals are rebuilt from scratch.
 * Target selection and frame extraction remain in TargetEspModule.
 */
public final class TargetEspRenderer {
    private TargetEspRenderer() { }

    public static void reset() {
        // Reserved for the new renderer.
    }

    public static void render(WorldRenderContext context) {
        // Legacy Ghosts, Circle, Square, 3D Box, and Aura visuals were removed.
    }
}
