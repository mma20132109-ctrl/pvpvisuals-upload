package com.pvpvisuals.gui.echo;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.resources.Identifier;

/**
 * Dedicated GUI pipelines ported from the Svintus render architecture.
 * All rounded GUI geometry is submitted as render state and shaded by GLSL.
 */
public final class LiquidGlassPipelines {
    public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    public static final RenderPipeline ROUND_RECT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/liquid_glass_round_rect"))
            .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/shape"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/round_rect"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline BLUR_RECT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/liquid_glass_blur_rect"))
            .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/shape"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/blur_rect"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(SHAPE_FORMAT, VertexFormat.Mode.QUADS)
            .build();

    private LiquidGlassPipelines() {
    }
}
