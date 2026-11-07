package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.render.freelook.FreelookCameraHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

/**
 * Freelook: пока камера свободна, движение мыши уходит в углы камеры, а не в поворот игрока.
 * Накопленные за кадр смещения забираем и обнуляем, оригинальный turnPlayer отменяем.
 */
@Mixin(MouseHandler.class)
public abstract class FreelookMouseMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$freelook(CallbackInfo ci) {
		if (!FreelookCameraHandler.capturesMouse() || this.minecraft.player == null) {
			return;
		}
		// та же чувствительность, что и в ваниле: (s * 0.6 + 0.2)^3 * 8
		double base = this.minecraft.options.sensitivity().get() * 0.6 + 0.2;
		double scale = base * base * base * 8.0;
		FreelookCameraHandler.applyMouse(this.accumulatedDX, this.accumulatedDY, scale);
		this.accumulatedDX = 0.0;
		this.accumulatedDY = 0.0;
		ci.cancel();
	}
}
