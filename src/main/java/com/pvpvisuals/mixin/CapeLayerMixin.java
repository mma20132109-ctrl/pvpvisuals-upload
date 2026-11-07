package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pvpvisuals.cape.CapeModule;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Не даёт ванильному CapeLayer рисовать плащ, когда рисуется плащ мода. */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void pvpvisuals$hideVanillaCape(PoseStack poseStack, SubmitNodeCollector collector, int light,
			AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
		if (CapeModule.replacesVanilla(state)) {
			ci.cancel();
		}
	}
}
