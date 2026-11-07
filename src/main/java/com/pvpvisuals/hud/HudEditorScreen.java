package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Отдельный экран редактора HUD (правый Ctrl или кнопка «Edit HUD»). Затемняет мир и показывает все включённые
 * элементы с рамками; вся логика мыши — в {@link HudEditorOverlay}. Это не основное меню мода (Right Shift).
 */
public class HudEditorScreen extends Screen {
	public HudEditorScreen() {
		super(Component.literal("HUD Editor"));
	}

	@Override
	protected void init() {
		HudEditorOverlay.reset();
	}

	/** Фон рисуем сами (без блюра ванильного фона). */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		HudEditorOverlay.render(g, this.font, mouseX, mouseY, this.width, this.height, true, this.height - 24);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return HudEditorOverlay.onClick(event.x(), event.y(), event.button(), this.width, this.height)
				|| super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return HudEditorOverlay.onDrag(event.x(), event.y(), this.width, this.height)
				|| super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return HudEditorOverlay.onRelease() || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return HudEditorOverlay.onScroll(mouseX, mouseY, scrollY, HudKeys.shiftDown())
				|| super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		HudEditorOverlay.reset();
		PvpConfig.get().save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
