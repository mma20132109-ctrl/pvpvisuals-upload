package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Редактирование HUD прямо в открытом чате: те же рамки, перемещение, ресайз и ПКМ-настройки, что в
 * {@link HudEditorScreen} (общая логика в {@link HudEditorOverlay}), без затемнения.
 */
public final class HudChatEditor {
	private HudChatEditor() { }

	/** true, если сейчас открыт чат и режим редактирования включён (тогда HudManager не рисует элементы сам). */
	public static boolean active(Minecraft mc) {
		return mc.screen instanceof ChatScreen && PvpConfig.get().chatHudEdit;
	}

	public static void register() {
		// Подписки на события экрана живут только до его пересоздания, поэтому вешаем их в AFTER_INIT.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof ChatScreen) || !PvpConfig.get().chatHudEdit) {
				return;
			}
			HudEditorOverlay.reset();
			ScreenEvents.afterRender(screen).register(HudChatEditor::render);
			ScreenMouseEvents.allowMouseClick(screen).register(HudChatEditor::onClick);
			ScreenMouseEvents.allowMouseDrag(screen).register(HudChatEditor::onDrag);
			ScreenMouseEvents.allowMouseRelease(screen).register(HudChatEditor::onRelease);
			ScreenMouseEvents.allowMouseScroll(screen).register(HudChatEditor::onScroll);
		});
	}

	private static void render(Screen screen, GuiGraphics g, int mouseX, int mouseY, float delta) {
		HudEditorOverlay.render(g, Minecraft.getInstance().font, mouseX, mouseY, screen.width, screen.height, false, 30);
	}

	/** @return false, чтобы чат не обработал клик, если его обработал редактор. */
	private static boolean onClick(Screen screen, MouseButtonEvent event) {
		return !HudEditorOverlay.onClick(event.x(), event.y(), event.button(), screen.width, screen.height);
	}

	private static boolean onDrag(Screen screen, MouseButtonEvent event, double dx, double dy) {
		return !HudEditorOverlay.onDrag(event.x(), event.y(), screen.width, screen.height);
	}

	private static boolean onRelease(Screen screen, MouseButtonEvent event) {
		return !HudEditorOverlay.onRelease();
	}

	private static boolean onScroll(Screen screen, double mx, double my, double sx, double sy) {
		return !HudEditorOverlay.onScroll(mx, my, sy, HudKeys.shiftDown());
	}
}
