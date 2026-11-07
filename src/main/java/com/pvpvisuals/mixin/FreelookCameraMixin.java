package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import com.pvpvisuals.render.freelook.FreelookCameraHandler;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Freelook: подмена углов камеры. Camera#setup считает углы по сущности и вызывает setRotation(yaw, pitch) ДО
 * расчёта позиции (в третьем лице камера отодвигается назад вдоль взгляда), поэтому подмена аргументов setRotation
 * даёт настоящую орбиту вокруг игрока. require = 0: при несовпадении сигнатуры миксин молча не применяется.
 */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {
	/** Угол взгляда сущности в этом кадре: по нему отличаем обычный вид от вида спереди (там yaw + 180). */
	@Unique
	private float pvpvisuals$entityYaw;

	@Inject(method = "setup", at = @At("HEAD"), require = 0)
	private void pvpvisuals$begin(Level level, Entity entity, boolean thirdPerson, boolean inverse, float partialTick,
			CallbackInfo ci) {
		FreelookCameraHandler.frame();
		this.pvpvisuals$entityYaw = entity.getViewYRot(partialTick);
	}

	@ModifyArgs(method = "setup",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), require = 0)
	private void pvpvisuals$rotation(Args args) {
		if (!FreelookCameraHandler.isActive()) {
			return;
		}
		float yaw = args.get(0);
		boolean front = Math.abs(Mth.wrapDegrees(yaw - this.pvpvisuals$entityYaw)) > 90.0f;
		args.set(0, FreelookCameraHandler.yaw() + (front ? 180.0f : 0.0f));
		args.set(1, front ? -FreelookCameraHandler.pitch() : FreelookCameraHandler.pitch());
	}
}
