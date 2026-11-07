package com.pvpvisuals.render.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame command queue. Coordinates are camera-relative, matching the
 * matrices supplied by Fabric's world-render context in this render phase.
 * Colors are ARGB; line width and box bounds are measured in world units.
 */
public final class RenderQueue {
    sealed interface Command permits Line, Box {}
    record Line(float ax, float ay, float az, float bx, float by, float bz,
                float width, int argb) implements Command {}
    record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
               float width, int argb) implements Command {}

    private final List<Command> commands = new ArrayList<>(256);

    public void line(float ax, float ay, float az, float bx, float by, float bz,
                     float width, int argb) {
        if (width > 0 && Float.isFinite(width)) {
            commands.add(new Line(ax, ay, az, bx, by, bz, width, argb));
        }
    }

    public void box(float minX, float minY, float minZ,
                    float maxX, float maxY, float maxZ, float lineWidth, int argb) {
        if (lineWidth > 0 && Float.isFinite(lineWidth)
                && minX <= maxX && minY <= maxY && minZ <= maxZ) {
            commands.add(new Box(minX, minY, minZ, maxX, maxY, maxZ, lineWidth, argb));
        }
    }

    List<Command> commands() { return commands; }
    void clear() { commands.clear(); }
}
