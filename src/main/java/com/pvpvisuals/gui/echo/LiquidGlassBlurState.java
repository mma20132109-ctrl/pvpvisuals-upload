package com.pvpvisuals.gui.echo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import org.joml.Matrix3x2f;

/** Rounded glass surface that samples the captured scene texture. */
public record LiquidGlassBlurState(
        Matrix3x2f pose,
        TextureSetup textureSetup,
        float x, float y, float width, float height,
        float radius,
        int topLeft, int topRight, int bottomRight, int bottomLeft,
        ScreenRectangle bounds
) implements SimpleGuiElementRenderState {

    @Override
    public RenderPipeline pipeline() {
        return LiquidGlassPipelines.BLUR_RECT;
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public void setupVertices(VertexConsumer vertices) {
        int sw = pack(width);
        int sh = pack(height);
        int r = pack(radius);

        vertices.addVertexWith2DPose(pose, x, y).setColor(topLeft).setUv(0f, 0f).setUv1(sw, sh).setUv2(r, 0);
        vertices.addVertexWith2DPose(pose, x, y + height).setColor(bottomLeft).setUv(0f, height).setUv1(sw, sh).setUv2(r, 0);
        vertices.addVertexWith2DPose(pose, x + width, y + height).setColor(bottomRight).setUv(width, height).setUv1(sw, sh).setUv2(r, 0);
        vertices.addVertexWith2DPose(pose, x + width, y).setColor(topRight).setUv(width, 0f).setUv1(sw, sh).setUv2(r, 0);
    }

    private static int pack(float value) {
        return Math.max(0, Math.min(Short.MAX_VALUE, Math.round(value * 8f)));
    }
}
