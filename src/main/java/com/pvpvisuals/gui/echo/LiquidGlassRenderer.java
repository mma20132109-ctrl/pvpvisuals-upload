package com.pvpvisuals.gui.echo;

import com.pvpvisuals.mixin.GuiGraphicsAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;

/**
 * Echo's custom GUI material renderer.
 *
 * Rounded shapes are submitted to Minecraft's 1.21.11 GUI render-state collector and
 * rendered by dedicated GLSL pipelines. This replaces the old SmoothRenderer-only path.
 */
public final class LiquidGlassRenderer {
    private LiquidGlassRenderer() { }

    public static void window(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        roundRect(graphics, x, y, width, height, radius,
                EchoTheme.WIN_TOP, EchoTheme.WIN_BOTTOM, 0x55FFFFFF, 1.0f);
        roundRect(graphics, x + 1f, y + 1f, width - 2f, Math.min(54f, height * 0.22f),
                Math.max(1f, radius - 1f), 0x18FFFFFF, 0x00FFFFFF, 0, 0f);
        roundRect(graphics, x + 0.6f, y + 0.6f, width - 1.2f, height - 1.2f,
                Math.max(1f, radius - 0.6f), 0, 0, 0x35FFFFFF, 0.8f);
    }

    public static void surface(GuiGraphics graphics, float x, float y, float width, float height, float radius,
            int tint, int border) {
        roundRect(graphics, x, y, width, height, radius, tint, withAlpha(tint, 0.72f), border, 0.8f);
    }

    public static void roundRect(GuiGraphics graphics, float x, float y, float width, float height, float radius,
            int top, int bottom, int borderColor, float border) {
        if (width <= 0f || height <= 0f) return;

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x), (int) Math.floor(y),
                Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)))
                .transformAxisAligned(pose);

        var state = ((GuiGraphicsAccessor) graphics).pvpvisuals$getGuiRenderState();
        state.addSimpleElement(new LiquidGlassShapeState(pose, x, y, width, height,
                radius, 0f, top, top, bottom, bottom, bounds));

        if (border > 0f && (borderColor >>> 24) != 0) {
            state.addSimpleElement(new LiquidGlassShapeState(pose, x, y, width, height,
                    radius, border, borderColor, borderColor, borderColor, borderColor, bounds));
        }
    }

    private static int withAlpha(int color, float factor) {
        int alpha = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, factor)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
