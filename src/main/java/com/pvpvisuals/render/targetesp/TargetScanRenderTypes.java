package com.pvpvisuals.render.targetesp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/** Minecraft 1.21.11 render type for the original TargetESP scan-line effect. */
public final class TargetScanRenderTypes {
    private static final RenderPipeline TARGET_ESP_SCAN_PIPELINE =
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/target_esp_scan_submit"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_scan"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_scan"))
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();

    private static final Function<Identifier, RenderType> TARGET_ESP_SCAN = Util.memoize(texture ->
            RenderType.create(
                    "pvpvisuals_target_esp_scan",
                    RenderSetup.builder(TARGET_ESP_SCAN_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .sortOnUpload()
                            .createRenderSetup()));

    public static RenderType targetEspScan(Identifier texture) {
        return TARGET_ESP_SCAN.apply(texture);
    }

    public static void initialize() {
        // Forces the pipeline's static initialization during client setup.
    }

    private TargetScanRenderTypes() {
    }
}
