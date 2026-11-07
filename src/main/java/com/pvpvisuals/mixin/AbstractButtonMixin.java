package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Кнопки на экранах выбора/создания мира рисуются в стиле Echo Visual. Кнопки, у которых свой renderWidget
 * (чекбоксы, вкладки), не затрагиваются. При любой ошибке рисования остаётся ванильная кнопка.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$echoButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		if (!EchoWorldStyle.active()) {
			return;
		}
		try {
			EchoWorldStyle.button(graphics, (AbstractWidget) (Object) this);
			ci.cancel();
		} catch (Throwable t) {
			EchoWorldStyle.fail("button", t);
		}
	}
}
