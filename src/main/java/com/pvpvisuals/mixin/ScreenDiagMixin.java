package com.pvpvisuals.mixin;

import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Диагностика: один раз на экран пишет в лог его класс и классы его виджетов (строка "Screen diag"). По ней видно,
 * какой именно класс рисует плашку/фон на экране «Saving level…» и т.п. require = 0, на игру не влияет.
 */
@Mixin(Screen.class)
public abstract class ScreenDiagMixin {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/screens");
	private static final Set<Object> SEEN = java.util.Collections.newSetFromMap(new WeakHashMap<>());

	@Inject(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"), require = 0)
	private void pvpvisuals$diag(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		com.pvpvisuals.gui.echo.Gfx.alpha = 1f; // защита: «залипшая» прозрачность не должна прятать кнопки/плашки
		Screen self = (Screen) (Object) this;
		if (!SEEN.add(self)) {
			return;
		}
		try {
			StringBuilder sb = new StringBuilder();
			for (Object c : self.children()) {
				sb.append(c.getClass().getName()).append("; ");
			}
			LOG.info("Screen diag: {} children=[{}]", self.getClass().getName(), sb);
		} catch (Throwable e) {
			LOG.info("Screen diag: {} (children unavailable: {})", self.getClass().getName(), e.toString());
		}
	}
}
