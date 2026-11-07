package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.render.sky.ShaderSkyModule;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;

/** Отключение ванильных облаков для Shader Sky. require = 0: при несовпадении сигнатуры облака просто не скрываются. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$hideClouds(int color, CloudStatus status, float height, Vec3 cameraPos, long ticks,
			float partialTick, CallbackInfo ci) {
		if (ShaderSkyModule.hideClouds()) {
			ci.cancel();
		}
	}
}
