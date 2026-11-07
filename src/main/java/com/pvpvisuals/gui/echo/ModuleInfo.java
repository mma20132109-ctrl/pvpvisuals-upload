package com.pvpvisuals.gui.echo;

import java.util.HashMap;
import java.util.Map;

import com.pvpvisuals.gui.echo.EchoIcons.Glyph;
import com.pvpvisuals.gui.rival.Category;

/** Краткие описания и иконки модулей для карточек. Неизвестные модули получают иконку своей категории. */
public final class ModuleInfo {
	private record Info(String desc, Glyph glyph) { }

	private static final Map<String, Info> MAP = new HashMap<>();

	static {
		put("China Hat", "Render a stylish hat above players.", Glyph.TRIANGLE);
		put("Crit Effect", "Flash and particles on critical hits.", Glyph.SPARKLE);
		put("Fullbright", "Maximum brightness in caves and at night.", Glyph.SUN);
		put("Custom Crosshair", "Replace the default crosshair with a custom one.", Glyph.CROSSHAIR);
		put("Trajectory", "Preview the flight path of projectiles.", Glyph.ARC);
		put("Damage Numbers", "Floating numbers showing damage dealt.", Glyph.HASH);
		put("Block Hit Particles", "Particles when you hit a block.", Glyph.CUBE);
		put("Hit Particles", "Particles when you hit an entity.", Glyph.SPARKLE);
		put("World Particles", "Ambient particles floating around you.", Glyph.DOTS);
		put("Kill Effect", "Visual effect when you defeat a target.", Glyph.STAR);
		put("Target HUD", "Health, armor and distance of your target.", Glyph.RECT);
		put("Combo Counter", "Counts your consecutive hits.", Glyph.BOLT);
		put("Watermark", "Echo Visual logo on your screen.", Glyph.EYE);
		put("Inventory HUD", "Hotbar, armor and offhand on screen.", Glyph.GRID);
		put("HUD Binds", "List of your modules and their keys.", Glyph.LIST);
		put("Cape", "Wear a custom cape visible to other users.", Glyph.RECT);
		put("Theme", "Switch the color theme of HUD elements.", Glyph.DOTS);
		put("Logo", "Logo size and visibility settings.", Glyph.EYE);
		put("Menu Style", "Overlay darkness and panel opacity.", Glyph.GRID);
		put("Radial Menu", "Hold a key, point the mouse at a sector and release to send a quick chat phrase.", Glyph.PILL);
		put("Hand Shader", "Shader effect on your hand and held item in first person: pulse, void, nebula or starfield.", Glyph.SPARKLE);
		put("ViewModel", "Move, scale and rotate your hand and held item in first person; disable bobbing and equip animation; tune the swing.", Glyph.CUBE);
		put("HitBox", "Custom 3D hitboxes on players and mobs; the box smoothly changes color when you aim at it.", Glyph.CUBE);
		put("Block Overlay", "Shader highlight of the block you are looking at: pulse, grid, scanlines, glow or space styles (void, nebula, starfield, galaxy).", Glyph.CUBE);
		put("Main Menu", "Custom Echo Visual title screen with animated logo and buttons.", Glyph.PILL);
		put("Loading Screen", "Custom Echo Visual loading screen with animated logo and progress bar.", Glyph.PILL);
		put("Freelook", "Look around freely with the mouse while your character keeps its direction.", Glyph.EYE);
		put("Shader Sky", "Replace the sky with an animated shader: nebula, gradient, stars or synthwave.", Glyph.STAR);
		put("Island", "Status island with time, FPS and the track you are listening to.", Glyph.PILL);
		put("Chat HUD Edit", "Drag HUD elements while chat is open.", Glyph.LIST);
		put("Reset HUD Positions", "Put every HUD element back in place.", Glyph.BOLT);
		put("Bind Toasts", "Notifications when a key toggles a module.", Glyph.SPARKLE);
	}

	private ModuleInfo() { }

	private static void put(String name, String desc, Glyph glyph) {
		MAP.put(name, new Info(desc, glyph));
	}

	private static Info find(String name) {
		Info i = MAP.get(name);
		if (i == null) {
			int colon = name.indexOf(':');
			if (colon > 0) {
				i = MAP.get(name.substring(0, colon)); // «Theme: Gold» -> «Theme»
			}
		}
		return i;
	}

	public static String desc(String name) {
		Info i = find(name);
		return i == null ? "" : i.desc;
	}

	public static Glyph glyph(String name, Category fallback) {
		Info i = find(name);
		return i == null ? EchoIcons.forCategory(fallback) : i.glyph;
	}
}
