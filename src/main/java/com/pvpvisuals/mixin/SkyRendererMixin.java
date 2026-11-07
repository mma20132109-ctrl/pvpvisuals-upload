package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pvpvisuals.render.sky.ShaderSkyModule;
import com.pvpvisuals.render.sky.SkyRenderer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;

/**
 * Подмена неба для Shader Sky. Все инъекции с require = 0: если сигнатура в другой версии Minecraft не совпадёт,
 * миксин молча не применится (небо останется ванильным), а не уронит игру при запуске.
 */
@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public abstract class SkyRendererMixin {
	/** Забираем из состояния кадра угол солнца и измерение. */
	@Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
	private void pvpvisuals$capture(ClientLevel level, float partialTick, Camera camera, SkyRenderState state,
			CallbackInfo ci) {
		SkyRenderer.capture(level, state.sunAngle);
	}

	/** Обычный мир (и Незер, если ваниль вызывает этот метод): вместо небесного диска рисуем шейдер. */
	@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$skyDisc(int skyColor, CallbackInfo ci) {
		if (ShaderSkyModule.active()) {
			ci.cancel();
			SkyRenderer.draw();
		}
	}

	/** Энд: ваниль рисует куб с текстурой — заменяем его. */
	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$endSky(CallbackInfo ci) {
		if (ShaderSkyModule.active()) {
			ci.cancel();
			SkyRenderer.draw();
		}
	}

	/** Ванильные кубические звёзды не нужны: звёзды рисует шейдер. */
	@Inject(method = "renderStars", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$stars(float brightness, PoseStack poseStack, CallbackInfo ci) {
		if (ShaderSkyModule.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$sun(float brightness, PoseStack poseStack, CallbackInfo ci) {
		if (ShaderSkyModule.hideSunMoon()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$moon(MoonPhase phase, float brightness, PoseStack poseStack, CallbackInfo ci) {
		if (ShaderSkyModule.hideSunMoon()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$sunrise(PoseStack poseStack, float sunAngle, int color, CallbackInfo ci) {
		if (ShaderSkyModule.active()) {
			ci.cancel();
		}
	}
}
