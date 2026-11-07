package com.pvpvisuals.hud;

import org.lwjgl.glfw.GLFW;

/** Опрос клавиш напрямую через GLFW (без API Minecraft, которые меняются между версиями). */
public final class HudKeys {
	private HudKeys() { }

	public static boolean shiftDown() {
		long win = GLFW.glfwGetCurrentContext();
		return win != 0L
				&& (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
	}
}
