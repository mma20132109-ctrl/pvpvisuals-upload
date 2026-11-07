package com.pvpvisuals.render.engine;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

/** Safe, small facade passed to render features. */
public record RenderContext(WorldRenderContext minecraft, RenderQueue queue) {
    public void line(float ax, float ay, float az, float bx, float by, float bz,
                     float width, int argb) {
        queue.line(ax, ay, az, bx, by, bz, width, argb);
    }

    public void box(float minX, float minY, float minZ,
                    float maxX, float maxY, float maxZ, float width, int argb) {
        queue.box(minX, minY, minZ, maxX, maxY, maxZ, width, argb);
    }
}
