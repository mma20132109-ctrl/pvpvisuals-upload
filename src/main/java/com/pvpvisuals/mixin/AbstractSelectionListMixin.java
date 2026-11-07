package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;

/** Список миров: убираем «земляной» фон и разделители, рамка выбранного мира становится фиолетовой. */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
	@Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noListBackground(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.active()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noSeparators(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.active()) {
			ci.cancel();
		}
	}

	/** renderSelection(GuiGraphics, E, int color): единственный int-аргумент — цвет рамки. */
	@ModifyVariable(method = "renderSelection", at = @At("HEAD"), argsOnly = true, require = 0)
	private int pvpvisuals$selectionColor(int color) {
		return EchoWorldStyle.active() ? EchoWorldStyle.selectionColor() : color;
	}
}
