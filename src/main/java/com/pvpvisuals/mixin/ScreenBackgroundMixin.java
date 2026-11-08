package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Фон стилизованных экранов (выбор/создание мира, загрузка, «Saving level…»). Наш фон рисуется в самом начале
 * кадра экрана, а все ванильные способы нарисовать фон (панорама, размытие мира, затемнение, текстура меню)
 * для этих экранов отключаются. Так работает и для экранов, которые переопределяют renderBackground и не вызывают
 * super. Все инъекции require = 0.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"), require = 0)
	private void pvpvisuals$echoBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			EchoWorldStyle.background(graphics);
		}
		EchoWorldStyle.renderChrome(graphics, this);
	}

	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noBlur(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noPanorama(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void pvpvisuals$noMenuBackground(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void pvpvisuals$noMenuBackgroundRect(GuiGraphics graphics, int x, int y, int w, int h, CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noDarken(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.wantsBackground(this)) {
			ci.cancel();
		}
	}
}
