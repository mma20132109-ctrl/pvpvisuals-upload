package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.pvpvisuals.render.splash.EchoSplash;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;

/**
 * Экран загрузки Echo Visual поверх ванильного LoadingOverlay. Ваниль сама ведёт состояние (прогресс, конец загрузки,
 * затухание и переход в меню), мы лишь меняем картинку:
 * 1) replaceAlpha — фон загрузки становится тёмным цветом нашей темы вместо красного Mojang;
 * 2) blit внутри render — логотип Mojang не рисуется;
 * 3) drawProgressBar — ванильная полоса не рисуется;
 * 4) конец render — рисуем наш экран (фон, частицы, логотип, полоса, статус).
 * Все инъекции с require = 0: если сигнатура в игре другая, соответствующая часть просто не применится, игра не упадёт.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;
	@Shadow
	private long fadeOutStart;

	@Inject(method = "render", at = @At("RETURN"), require = 0)
	private void pvpvisuals$drawSplash(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		EchoSplash.render(this, graphics, this.currentProgress, this.fadeOutStart);
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noVanillaBar(GuiGraphics graphics, int x0, int y0, int x1, int y1, float alpha,
			CallbackInfo ci) {
		if (EchoSplash.enabled()) {
			ci.cancel();
		}
	}

	@Inject(method = "replaceAlpha", at = @At("HEAD"), cancellable = true, require = 0)
	private static void pvpvisuals$background(int color, int alpha, CallbackInfoReturnable<Integer> cir) {
		if (EchoSplash.enabled()) {
			cir.setReturnValue((alpha << 24) | EchoSplash.backgroundRgb());
		}
	}

	/** Логотип Mojang (две половинки) не рисуем; всё остальное, что рисует render через blit, остаётся как есть. */
	@Redirect(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"),
			require = 0)
	private void pvpvisuals$noMojangLogo(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x,
			int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth,
			int textureHeight, int color) {
		if (!EchoSplash.enabled()) {
			graphics.blit(pipeline, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth,
					textureHeight, color);
		}
	}
}
