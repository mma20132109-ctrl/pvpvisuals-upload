package com.pvpvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.TargetEspSettings;
import com.pvpvisuals.render.targetesp.TargetEspMode;
import com.pvpvisuals.render.targetesp.TargetEspModule;
import com.pvpvisuals.render.targetesp.TargetScanRenderState;
import com.pvpvisuals.render.targetesp.TargetScanRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class TargetScanLivingEntityRendererMixin<
        T extends LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> {

    @Shadow
    public abstract Identifier getTextureLocation(S state);

    @Shadow
    protected M model;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void pvpvisuals$extractTargetScan(T entity, S state, float partialTick, CallbackInfo ci) {
        PvpConfig config = PvpConfig.get();
        boolean scanTarget = config.targetEsp
                && TargetEspMode.byName(config.targetEspSettings.mode) == TargetEspMode.SCAN
                && TargetEspModule.isScanTarget(entity);
        ((TargetScanRenderState) state).pvpvisuals$setTargetScanTarget(scanTarget);
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    shift = At.Shift.BEFORE))
    private void pvpvisuals$submitTargetScan(
            S state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            CallbackInfo ci) {
        if (!((TargetScanRenderState) state).pvpvisuals$isTargetScanTarget()) {
            return;
        }

        PvpConfig config = PvpConfig.get();
        if (!config.targetEsp || TargetEspMode.byName(config.targetEspSettings.mode) != TargetEspMode.SCAN) {
            return;
        }

        TargetEspSettings settings = config.targetEspSettings;
        TargetScanRenderer.submit(
                this.model,
                state,
                poseStack,
                collector,
                this.getTextureLocation(state),
                settings.scanColor,
                settings.scanColorFlow ? settings.scanSecondColor : settings.scanColor,
                settings.scanSaturation,
                TargetEspModule.scanAnimation(),
                settings.scanSpeed,
                settings.scanGlow);
    }
}
