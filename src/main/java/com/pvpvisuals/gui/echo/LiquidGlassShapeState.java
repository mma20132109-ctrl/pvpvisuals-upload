package com.pvpvisuals.gui.echo;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;

/** Immutable rounded-rectangle submission for Minecraft's 1.21.11 GUI render-state pipeline. */
public record LiquidGlassShapeState(
        Matrix3x2f pose,
        float x, float y, float width, float height,
        float radius, float border,
        int topLeft, int topRight, int bottomRight, int bottomLeft,
        ScreenRectangle bounds
) implements GuiElementRenderState {

    @Override
    public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
        return LiquidGlassPipelines.ROUND_RECT;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        float x0 = x;
        float y0 = y;
        float x1 = x + width;
        float y1 = y + height;

        int sw = pack(width);
        int sh = pack(height);
        int r = pack(radius);
        int b = pack(border);

        vertices.addVertexWith2DPose(pose, x0, y0).setColor(topLeft).setUv(0f, 0f).setUv1(sw, sh).setUv2(r, b);
        vertices.addVertexWith2DPose(pose, x0, y1).setColor(bottomLeft).setUv(0f, height).setUv1(sw, sh).setUv2(r, b);
        vertices.addVertexWith2DPose(pose, x1, y1).setColor(bottomRight).setUv(width, height).setUv1(sw, sh).setUv2(r, b);
        vertices.addVertexWith2DPose(pose, x1, y0).setColor(topRight).setUv(width, 0f).setUv1(sw, sh).setUv2(r, b);
    }

    private static int pack(float value) {
        return Math.max(0, Math.min(Short.MAX_VALUE, Math.round(value * 8f)));
    }
}
