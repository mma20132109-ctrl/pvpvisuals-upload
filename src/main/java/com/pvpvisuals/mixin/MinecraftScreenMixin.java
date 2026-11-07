package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.pvpvisuals.gui.menu.EchoMainMenuScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Подмена главного меню: когда игра открывает ванильный TitleScreen (запуск, «Save and Quit», выход с сервера),
 * вместо него открывается EchoMainMenuScreen. Подклассы TitleScreen других модов не трогаем (проверка по точному
 * классу). require = 0: если setScreen в игре выглядит иначе, подмена не применится и останется ванильное меню.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
	private Screen pvpvisuals$echoMainMenu(Screen screen) {
		if (screen != null && screen.getClass() == TitleScreen.class && EchoMainMenuScreen.enabled()) {
			try {
				return new EchoMainMenuScreen();
			} catch (Throwable ignored) {
				return screen;
			}
		}
		return screen;
	}
}
