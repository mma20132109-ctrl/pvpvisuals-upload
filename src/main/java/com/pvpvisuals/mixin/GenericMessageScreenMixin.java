package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * «Saving level…», «Preparing…» и другие экраны с одним сообщением: вместо чёрной плашки с белой рамкой рисуем
 * панель Echo Visual с названием экрана и бегущей полосой. require = 0: если у GenericMessageScreen нет своего
 * render (плашку рисует кто-то другой), хук просто не применится.
 */
@Mixin(GenericMessageScreen.class)
public abstract class GenericMessageScreenMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$panel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!EchoWorldStyle.wantsBackground(self)) {
			return;
		}
		try {
			EchoWorldStyle.messagePanel(graphics, self.getTitle().getString(), self.width, self.height);
			ci.cancel();
		} catch (Throwable ignored) {
			// остаётся ванильная плашка
		}
	}
}
