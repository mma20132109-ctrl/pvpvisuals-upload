package com.pvpvisuals.render.targetesp;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/** Render pipeline for the original TargetESP scan-line shader. */
public final class TargetScanRenderTypes {
    private static final ColorTargetState ADDITIVE = new ColorTargetState(
            new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE));
    private static final DepthStencilState VISIBLE_SURFACE =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    private static final RenderPipeline TARGET_ESP_SCAN_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/target_esp_scan_submit"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_scan"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_scan"))
                    .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(ADDITIVE)
                    .withDepthStencilState(VISIBLE_SURFACE)
                    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build());

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
        // Force static pipeline registration during client initialization.
    }

    private TargetScanRenderTypes() {
    }
}
