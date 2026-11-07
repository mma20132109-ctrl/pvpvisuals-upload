package com.pvpvisuals.mixin;

import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pvpvisuals.config.ViewModelSettings;
import com.pvpvisuals.render.viewmodel.ViewModelModule;
import com.pvpvisuals.render.viewmodel.ViewModelModule.SwingStyle;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * ViewModel. Цель: ItemInHandRenderer (Mojang names, 1.21.11), приватный renderArmWithItem(player, tickProgress,
 * pitch, hand, swingProgress, stack, equipProgress, poseStack, collector, light): им рисуется каждая рука от первого
 * лица. На HEAD, когда модуль включён, метод перехватывается: на PoseStack кладётся наш поворот/сдвиг/масштаб,
 * ванильный метод вызывается повторно с изменёнными swingProgress/equipProgress, после чего PoseStack
 * возвращается назад. Если модуль выключен, миксин ничего не делает. Все инжекторы require = 0.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	// Приблизительный центр предмета в видовом пространстве (ITEM_POS_X/Y/Z ванили): вокруг него вращаем и масштабируем.
	private static final float pvpvisuals$PX = 0.56f;
	private static final float pvpvisuals$PY = -0.52f;
	private static final float pvpvisuals$PZ = -0.72f;

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
	private void pvpvisuals$handPassStart(float tickProgress, PoseStack poseStack, SubmitNodeCollector collector,
			net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
		try {
			if (com.pvpvisuals.render.handshader.HandShaderModule.shouldDraw()) {
				// маска Hand Shader берётся из глубины: гарантируем, что до рук она очищена (ванильный проход тоже очищает)
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
						.clearDepthTexture(mc.getMainRenderTarget().getDepthTexture(), 1.0);
				com.pvpvisuals.render.handshader.HandShaderModule.markHandPass();
			}
		} catch (Throwable ignored) {
			// эффект просто не покажется
		}
	}

	private boolean pvpvisuals$inCall;
	private Matrix4f pvpvisuals$attackBefore;

	@Shadow
	protected abstract void renderArmWithItem(AbstractClientPlayer player, float tickProgress, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack,
			SubmitNodeCollector collector, int light);

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$viewModel(AbstractClientPlayer player, float tickProgress, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack poseStack,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (pvpvisuals$inCall || !ViewModelModule.active()) {
			return;
		}
		ViewModelSettings s = ViewModelModule.settings();
		float side = 1f;
		try {
			HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
			side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		} catch (Throwable ignored) {
			// без зеркалирования
		}

		float swing = ViewModelModule.remapSwing(swingProgress, s.swingSpeed);
		SwingStyle style = SwingStyle.byName(s.customSwing);
		float equip = s.disableEquipAnimation ? 0f : equipProgress;

		poseStack.pushPose();
		try {
			poseStack.translate(s.posX * side, s.posY, s.posZ);
			poseStack.translate(pvpvisuals$PX * side, pvpvisuals$PY, pvpvisuals$PZ);
			if (s.rotX != 0f) {
				poseStack.mulPose(Axis.XP.rotationDegrees(s.rotX));
			}
			if (s.rotY != 0f) {
				poseStack.mulPose(Axis.YP.rotationDegrees(s.rotY * side));
			}
			if (s.rotZ != 0f) {
				poseStack.mulPose(Axis.ZP.rotationDegrees(s.rotZ * side));
			}
			poseStack.scale(s.scaleX, s.scaleY, s.scaleZ);

			// пресеты анимации удара
			float k = Mth.sin(swing * (float) Math.PI) * s.swingAmplitude; // 0 -> 1 -> 0 за удар
			switch (style) {
				case SMOOTH -> swing = ViewModelModule.easeOutCubic(swing); // ванильный взмах, но с easing
				case SLIDE -> {
					// не замахивается, а выдвигается вперёд (смещение по Z вместо поворота)
					poseStack.translate(0f, -0.03f * k, -0.55f * k);
					swing = 0f;
				}
				case SCALE_PUNCH -> {
					// 1.0 -> 1.2 -> 1.0
					float sc = 1f + 0.2f * k;
					poseStack.scale(sc, sc, sc);
					swing = 0f;
				}
				case ROTATE_SPIN -> {
					// проворот вокруг вертикальной оси предмета на 360 градусов за удар
					poseStack.mulPose(Axis.YP.rotationDegrees(360f * s.swingAmplitude * swing * side));
					swing = 0f;
				}
				case FADE_SWING -> {
					// ПРИБЛИЖЕНИЕ: настоящая прозрачность предмета недоступна без проверенного хука в
					// отрисовку предмета, поэтому предмет «тает»: уменьшается и отъезжает назад и возвращается
					float sc = Math.max(0.1f, 1f - 0.5f * k);
					poseStack.translate(0f, 0f, 0.08f * k);
					poseStack.scale(sc, sc, sc);
					swing = 0f;
				}
				case TILT -> {
					// наклон вперёд на 90 градусов вокруг оси X предмета и возврат (отрицательный X: верх от камеры)
					poseStack.mulPose(Axis.XP.rotationDegrees(-90f * k));
					swing = 0f;
				}
				case NONE -> swing = 0f;
				default -> { }
			}
			poseStack.translate(-pvpvisuals$PX * side, -pvpvisuals$PY, -pvpvisuals$PZ);

			pvpvisuals$inCall = true;
			renderArmWithItem(player, tickProgress, pitch, hand, swing, stack, equip, poseStack, collector, light);
		} finally {
			pvpvisuals$inCall = false;
			poseStack.popPose();
		}
		ci.cancel();
	}

	/**
	 * Размах взмаха: ванильный applyItemArmAttackTransform поворачивает руку; его итоговое преобразование
	 * раскладывается на сдвиг и поворот, и они масштабируются на swingAmplitude. Работает только внутри нашего вызова.
	 */
	private static boolean isVanillaLike(SwingStyle st) {
		return st == SwingStyle.VANILLA || st == SwingStyle.SMOOTH;
	}

	@Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), require = 0)
	private void pvpvisuals$attackBefore(PoseStack poseStack, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
		pvpvisuals$attackBefore = null;
		if (!pvpvisuals$inCall || !ViewModelModule.active()) {
			return;
		}
		float a = ViewModelModule.settings().swingAmplitude;
		if (Math.abs(a - 1f) < 1e-3f || !isVanillaLike(SwingStyle.byName(ViewModelModule.settings().customSwing))) {
			return;
		}
		pvpvisuals$attackBefore = new Matrix4f(poseStack.last().pose());
		poseStack.pushPose();
	}

	@Inject(method = "applyItemArmAttackTransform", at = @At("RETURN"), require = 0)
	private void pvpvisuals$attackAfter(PoseStack poseStack, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
		Matrix4f before = pvpvisuals$attackBefore;
		if (before == null) {
			return;
		}
		pvpvisuals$attackBefore = null;
		float a = ViewModelModule.settings().swingAmplitude;
		// T = before^-1 * after: чистое преобразование ванильного взмаха
		Matrix4f t = new Matrix4f(before).invert().mul(poseStack.last().pose());
		poseStack.popPose();
		Vector3f tr = t.getTranslation(new Vector3f());
		Quaternionf q = t.getNormalizedRotation(new Quaternionf());
		AxisAngle4f aa = new AxisAngle4f().set(q);
		poseStack.translate(tr.x * a, tr.y * a, tr.z * a);
		if (Float.isFinite(aa.angle) && Float.isFinite(aa.x) && aa.angle != 0f) {
			poseStack.mulPose(new Quaternionf(new AxisAngle4f(aa.angle * a, aa.x, aa.y, aa.z)));
		}
	}
}
