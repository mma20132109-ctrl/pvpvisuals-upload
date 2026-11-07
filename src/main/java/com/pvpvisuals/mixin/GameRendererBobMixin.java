package com.pvpvisuals.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pvpvisuals.render.viewmodel.ViewModelModule;

import net.minecraft.client.renderer.GameRenderer;

/**
 * ViewModel / Disable Sway. GameRenderer.bobView(PoseStack, float) (private, Mojang names 1.21.11) качает картинку при
 * ходьбе. Отменяем его только пока идёт проход отрисовки рук (renderItemInHand), чтобы не трогать камеру мира.
 * Если в этой версии bobView для рук вызывается не отсюда, настройка просто ничего не изменит. require = 0.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {
	private boolean pvpvisuals$inHandPass;

	@Inject(method = "renderItemInHand", at = @At("HEAD"), require = 0)
	private void pvpvisuals$handStart(float partialTick, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
		pvpvisuals$inHandPass = true;
	}

	@Inject(method = "renderItemInHand", at = @At("RETURN"), require = 0)
	private void pvpvisuals$handEnd(float partialTick, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
		pvpvisuals$inHandPass = false;
		try {
			com.pvpvisuals.render.handshader.HandShaderRenderer.render();
		} catch (Throwable ignored) {
			// не ломаем кадр
		}
	}

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noSway(PoseStack poseStack, float partialTick, CallbackInfo ci) {
		if (pvpvisuals$inHandPass && ViewModelModule.active() && ViewModelModule.settings().disableSway) {
			ci.cancel();
		}
	}
}
