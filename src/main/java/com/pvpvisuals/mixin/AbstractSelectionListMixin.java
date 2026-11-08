package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;

/** Cards all rows in world/server/resource lists, while preserving the vanilla list's real entry widgets. */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin<E extends AbstractSelectionList.Entry<E>> {
	@Shadow
	public abstract E getSelected();

	@Inject(method = "renderItem", at = @At("HEAD"), require = 0)
	private void pvpvisuals$entryCard(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
			E entry, CallbackInfo ci) {
		if (!EchoWorldStyle.active()) return;
		try {
			EchoWorldStyle.listEntry(graphics, entry, mouseX, mouseY, entry == getSelected());
		} catch (Throwable t) {
			EchoWorldStyle.fail("list-entry", t);
		}
	}

	@Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noListBackground(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.active()) ci.cancel();
	}

	@Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
	private void pvpvisuals$noSeparators(GuiGraphics graphics, CallbackInfo ci) {
		if (EchoWorldStyle.active()) ci.cancel();
	}

	/** renderSelection(GuiGraphics, E, int color): the only int argument is the highlight color. */
	@ModifyVariable(method = "renderSelection", at = @At("HEAD"), argsOnly = true, require = 0)
	private int pvpvisuals$selectionColor(int color) {
		return EchoWorldStyle.active() ? EchoWorldStyle.selectionColor() : color;
	}
}
