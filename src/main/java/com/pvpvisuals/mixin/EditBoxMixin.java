package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pvpvisuals.gui.menu.EchoWorldStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * Поля ввода (название мира, адрес сервера, поиск) в стиле Echo Visual. На время отрисовки рамка ванильного
 * поля отключается, поле сдвигается внутрь нашей плашки (у поля без рамки текст рисуется без отступа), а после
 * отрисовки всё возвращается. Если что-то не читается, остаётся ванильное поле. require = 0.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
	@Shadow
	private boolean bordered;

	private boolean pvpvisuals$patched;
	private int pvpvisuals$x;
	private int pvpvisuals$y;
	private int pvpvisuals$w;

	@Inject(method = "renderWidget", at = @At("HEAD"), require = 0)
	private void pvpvisuals$before(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		pvpvisuals$patched = false;
		if (!EchoWorldStyle.active()) {
			return;
		}
		try {
			EditBox box = (EditBox) (Object) this;
			if (!this.bordered) {
				return;
			}
			EchoWorldStyle.textBox(graphics, (AbstractWidget) (Object) this);
			pvpvisuals$x = box.getX();
			pvpvisuals$y = box.getY();
			pvpvisuals$w = box.getWidth();
			box.setBordered(false);
			box.setX(pvpvisuals$x + 4);
			box.setY(pvpvisuals$y + (box.getHeight() - 8) / 2);
			box.setWidth(pvpvisuals$w - 8);
			pvpvisuals$patched = true;
		} catch (Throwable t) {
			EchoWorldStyle.fail("editbox", t);
			pvpvisuals$patched = false;
			try {
				EditBox box = (EditBox) (Object) this;
				box.setBordered(true);
				box.setX(pvpvisuals$x);
				box.setY(pvpvisuals$y);
				box.setWidth(pvpvisuals$w);
			} catch (Throwable ignored) {
				// ничего больше не сделать
			}
		}
	}

	@Inject(method = "renderWidget", at = @At("RETURN"), require = 0)
	private void pvpvisuals$after(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if (!pvpvisuals$patched) {
			return;
		}
		pvpvisuals$patched = false;
		EditBox box = (EditBox) (Object) this;
		box.setBordered(true);
		box.setX(pvpvisuals$x);
		box.setY(pvpvisuals$y);
		box.setWidth(pvpvisuals$w);
	}
}
