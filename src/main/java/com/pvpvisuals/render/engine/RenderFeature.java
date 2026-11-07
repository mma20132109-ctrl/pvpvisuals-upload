package com.pvpvisuals.render.engine;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** A mod-owned feature invoked once per world frame by SvintusRenderEngine. */
@FunctionalInterface
public interface RenderFeature {
    void render(WorldRenderContext minecraftContext, RenderQueue queue);
}
