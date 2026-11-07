package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;

/**
 * Экран генерации/загрузки мира в стиле Echo Visual: свой фон (у LevelLoadingScreen собственный renderBackground,
 * поэтому общий миксин на Screen его не затрагивает) и полоса загрузки. Текст и карта чанков остаются ванильными.
 * Все инъекции require = 0: если сигнатуры в игре другие, соответствующая часть просто не применится.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
	@Shadow
	private float smoothedProgress;

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$background(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		// сам фон рисует ScreenBackgroundMixin в начале кадра экрана, здесь только отключаем ванильный
		if (EchoWorldStyle.isLoadingScreen()) {
			ci.cancel();
		}
	}

	@Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$bar(GuiGraphics graphics, int a, int b, int c, int d, float delta, CallbackInfo ci) {
		if (EchoWorldStyle.isLoadingScreen()) {
			try {
				EchoWorldStyle.loadingBar(graphics, a, b, c, d, this.smoothedProgress);
				ci.cancel();
			} catch (Throwable ignored) {
				// ванильная полоса
			}
		}
	}
}
