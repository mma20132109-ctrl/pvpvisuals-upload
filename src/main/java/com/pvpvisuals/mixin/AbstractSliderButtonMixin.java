package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Ползунки (громкость, FOV, чувствительность и т.д.) на стилизованных экранах. Значение берётся через @Shadow
 * (работает и в релизе с обфусцированными именами). require = 0.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	@Shadow
	protected double value;

	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$echoSlider(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			CallbackInfo ci) {
		if (!EchoWorldStyle.active()) {
			return;
		}
		try {
			EchoWorldStyle.slider(graphics, (AbstractWidget) (Object) this, this.value);
			ci.cancel();
		} catch (Throwable t) {
			EchoWorldStyle.fail("slider", t);
		}
	}
}
