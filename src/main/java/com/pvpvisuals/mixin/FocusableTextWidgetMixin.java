package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.FocusableTextWidget;

/**
 * Чёрная плашка с белой рамкой на экранах «Saving level…» и подобных — это текстовый виджет с фоном. На
 * стилизованных служебных экранах рисуем вместо неё панель Echo Visual. require = 0: если класса или метода
 * в этой версии нет, хук не применяется.
 */
@Mixin(FocusableTextWidget.class)
public abstract class FocusableTextWidgetMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$panel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!EchoWorldStyle.wantsBackground(Minecraft.getInstance().screen)
				|| !EchoWorldStyle.isTransient(Minecraft.getInstance().screen)) {
			return;
		}
		try {
			AbstractWidget w = (AbstractWidget) (Object) this;
			EchoWorldStyle.messagePanelAt(graphics, w.getMessage().getString(), w.getX() + w.getWidth() / 2f,
					w.getY() + w.getHeight() / 2f);
			ci.cancel();
		} catch (Throwable ignored) {
			// остаётся ванильная плашка
		}
	}
}
