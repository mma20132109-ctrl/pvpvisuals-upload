package com.pvpvisuals.keybind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.pvpvisuals.PvpVisualsClient;
import com.pvpvisuals.config.BindSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleRegistry;
import com.pvpvisuals.gui.rival.NotificationToast;
import com.pvpvisuals.gui.echo.EchoScreen;
import com.pvpvisuals.hud.HudEditorScreen;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Единая система биндов. Каждому переключаемому модулю при старте регистрируется KeyMapping «Toggle &lt;модуль&gt;»
 * без клавиши (регистрировать новые KeyMapping после старта нельзя — Fabric читает их один раз при загрузке options.txt).
 * Они видны в стандартных настройках управления; назначать клавишу можно и там, и кнопкой Bind в меню мода.
 * Сохранение клавиш ведёт сам Minecraft (options.txt); копия лежит в конфиге мода (поле keys) для экспорта/импорта.
 */
public final class KeybindManager {
	/** Переключатели, для которых бинд не создаётся (это настройки интерфейса, а не модули). */
	private static final Set<String> EXCLUDED = Set.of("Island", "Chat HUD Edit");
	/** Клавиши по умолчанию для модулей (G для Fullbright — как было раньше). */
	private static final Map<String, Integer> DEFAULTS = Map.of("Fullbright", GLFW.GLFW_KEY_G, "Freelook", GLFW.GLFW_KEY_LEFT_ALT,
			"Radial Menu", GLFW.GLFW_KEY_R);

	private static final List<String> NAMES = new ArrayList<>();
	private static final List<KeyMapping> KEYS = new ArrayList<>();
	private static KeyMapping openMenu;
	private static KeyMapping openHud;
	private static KeyMapping toggleAll;
	private static KeyMapping panic;
	private static KeyMapping hudNext;
	private static KeyMapping hudPrev;

	/** Модуль, для которого сейчас ждём нажатие клавиши (или null). */
	private static String listening;
	/** Модули, включённые до Toggle All (чтобы вернуть их повторным нажатием). */
	private static Set<String> snapshot;

	private KeybindManager() { }

	public static boolean isBindable(String moduleName) {
		return !EXCLUDED.contains(moduleName);
	}

	private static String slug(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	/** id клавиши модуля. Freelook — не «Toggle», а клавиша действия (зажать/переключить), но тоже назначается с карточки. */
	private static String keyId(String name) {
		if (name.equals("Radial Menu")) {
			return "radial_menu";
		}
		return name.equals("Freelook") ? "freelook" : "toggle_" + slug(name);
	}

	private static KeyMapping reg(String id, int key, KeyMapping.Category category) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.pvpvisuals." + id,
				InputConstants.Type.KEYSYM, key, category));
	}

	public static void register() {
		KeyMapping.Category client = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "client"));
		KeyMapping.Category modules = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "modules"));

		openMenu = reg("open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT, client);
		openHud = reg("open_hud_editor", GLFW.GLFW_KEY_RIGHT_CONTROL, client);
		toggleAll = reg("toggle_all", GLFW.GLFW_KEY_UNKNOWN, client);
		panic = reg("panic", GLFW.GLFW_KEY_UNKNOWN, client);
		hudNext = reg("hud_binds_next", GLFW.GLFW_KEY_UNKNOWN, client);
		hudPrev = reg("hud_binds_prev", GLFW.GLFW_KEY_UNKNOWN, client);

		for (ModuleDef d : ModuleRegistry.toggleModules()) {
			NAMES.add(d.name);
			KEYS.add(reg(keyId(d.name), DEFAULTS.getOrDefault(d.name, GLFW.GLFW_KEY_UNKNOWN), modules));
		}
		ClientTickEvents.END_CLIENT_TICK.register(KeybindManager::tick);
	}

	// ---------- нажатия ----------

	private static void tick(Minecraft mc) {
		com.pvpvisuals.render.freelook.FreelookModule.keyTick(mc, find("Freelook"));
		com.pvpvisuals.render.radial.RadialMenuModule.keyTick(mc, find("Radial Menu"));
		while (openMenu.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new EchoScreen());
			}
		}
		while (openHud.consumeClick()) {
			if (mc.screen == null && mc.level != null) {
				mc.setScreen(new HudEditorScreen());
			}
		}
		while (toggleAll.consumeClick()) {
			toggleAll();
		}
		while (panic.consumeClick()) {
			panic();
		}
		while (hudNext.consumeClick()) {
			com.pvpvisuals.hud.HudManager.HUD_BINDS.flip(1);
		}
		while (hudPrev.consumeClick()) {
			com.pvpvisuals.hud.HudManager.HUD_BINDS.flip(-1);
		}
		for (int i = 0; i < KEYS.size(); i++) {
			KeyMapping k = KEYS.get(i);
			if (NAMES.get(i).equals("Freelook") || NAMES.get(i).equals("Radial Menu")) {
				continue; // их клавиши разбирают FreelookModule и RadialMenuModule
			}
			while (k.consumeClick()) {
				toggle(NAMES.get(i));
			}
		}
	}

	private static void notify(String text, boolean ok) {
		BindSettings b = PvpConfig.get().binds;
		if (b.showNotifications) {
			NotificationToast.show(text, ok, b.durationMs);
		}
	}

	private static void toggle(String name) {
		// Определения пересобираются заново: лямбды старых могли держать устаревшие вложенные конфиги после импорта.
		for (ModuleDef d : ModuleRegistry.toggleModules()) {
			if (d.name.equals(name)) {
				d.activate();
				PvpConfig.get().save();
				notify(name + ": " + (d.enabled() ? "ON" : "OFF"), d.enabled());
				return;
			}
		}
	}

	private static void toggleAll() {
		List<ModuleDef> all = ModuleRegistry.toggleModules();
		if (snapshot != null) {
			for (ModuleDef d : all) {
				if (snapshot.contains(d.name)) {
					d.setEnabled(true);
				}
			}
			snapshot = null;
			notify("Modules restored", true);
		} else {
			Set<String> on = new HashSet<>();
			for (ModuleDef d : all) {
				if (d.enabled()) {
					on.add(d.name);
					d.setEnabled(false);
				}
			}
			if (on.isEmpty()) {
				for (ModuleDef d : all) {
					d.setEnabled(true);
				}
				notify("All modules: ON", true);
			} else {
				snapshot = on;
				notify("All modules: OFF", false);
			}
		}
		PvpConfig.get().save();
	}

	/** Экстренно выключает все модули. */
	private static void panic() {
		for (ModuleDef d : ModuleRegistry.toggleModules()) {
			d.setEnabled(false);
		}
		snapshot = null;
		PvpConfig.get().save();
		notify("PANIC: all modules OFF", false);
	}

	// ---------- назначение клавиш из GUI ----------

	/** Модуль, для которого сейчас ждём клавишу (или null). */
	public static String listeningModule() {
		return listening;
	}

	public static boolean isListening() {
		return listening != null;
	}

	public static void startListening(String module) {
		listening = module.equals(listening) ? null : module;
	}

	public static void cancelListening() {
		listening = null;
	}

	private static KeyMapping find(String module) {
		int i = NAMES.indexOf(module);
		return i < 0 ? null : KEYS.get(i);
	}

	/** Название клавиши модуля для HUD ("G", "Mouse 4"...) или null, если бинда нет. Строку лучше кэшировать. */
	public static String boundText(String module) {
		KeyMapping k = find(module);
		return k == null || k.isUnbound() ? null : k.getTranslatedKeyMessage().getString();
	}

	/** Подпись кнопки Bind в меню. */
	public static String label(String module) {
		if (module.equals(listening)) {
			return "Bind: Press a key...";
		}
		KeyMapping k = find(module);
		if (k == null) {
			return "Bind: -";
		}
		return "Bind: " + (k.isUnbound() ? "None" : k.getTranslatedKeyMessage().getString());
	}

	/** Клавиша нажата в режиме ожидания. @return true, если нажатие поглощено */
	public static boolean handleKey(int keyCode) {
		if (listening == null) {
			return false;
		}
		String m = listening;
		listening = null;
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			return true; // отмена
		}
		if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			assign(m, InputConstants.UNKNOWN);
		} else {
			// ПРОВЕРИТЬ: InputConstants.Type#getOrCreate(int) в 1.21.11.
			assign(m, InputConstants.Type.KEYSYM.getOrCreate(keyCode));
		}
		return true;
	}

	/** Кнопка мыши в режиме ожидания: 2+ (средняя, Mouse4, Mouse5...) назначаются, левая/правая отменяют. */
	public static boolean handleMouse(int button) {
		if (listening == null) {
			return false;
		}
		String m = listening;
		listening = null;
		if (button >= 2) {
			assign(m, InputConstants.Type.MOUSE.getOrCreate(button));
		}
		return true;
	}

	public static void clear(String module) {
		assign(module, InputConstants.UNKNOWN);
	}

	private static void assign(String module, InputConstants.Key key) {
		KeyMapping k = find(module);
		if (k == null) {
			return;
		}
		// ПРОВЕРИТЬ: KeyMapping#setKey, KeyMapping.resetMapping(), Options#save().
		k.setKey(key);
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
		PvpConfig.get().save();
		com.pvpvisuals.hud.HudManager.HUD_BINDS.invalidate();
	}

	// ---------- синхронизация с конфигом ----------

	/** Кладёт клавиши в map (id -> имя клавиши); модули без клавиши в map не попадают. */
	public static void capture(Map<String, String> out) {
		if (openMenu == null) {
			return;
		}
		// ПРОВЕРИТЬ: KeyMapping#saveString() в 1.21.11.
		out.put("open_menu", openMenu.saveString());
		out.put("open_hud_editor", openHud.saveString());
		out.put("toggle_all", toggleAll.saveString());
		out.put("panic", panic.saveString());
		out.put("hud_binds_next", hudNext.saveString());
		out.put("hud_binds_prev", hudPrev.saveString());
		out.remove("toggle_fullbright");
		for (int i = 0; i < KEYS.size(); i++) {
			String id = keyId(NAMES.get(i));
			if (KEYS.get(i).isUnbound()) {
				out.remove(id);
			} else {
				out.put(id, KEYS.get(i).saveString());
			}
		}
	}

	private static void set(KeyMapping k, Map<String, String> in, String id, String fallback) {
		String v = in.get(id);
		k.setKey(InputConstants.getKey(v != null ? v : fallback)); // ПРОВЕРИТЬ: InputConstants.getKey(String)
	}

	/** Применяет клавиши из map: действия — значение или по умолчанию, модули — только если они есть в map. */
	public static void apply(Map<String, String> in) {
		if (openMenu == null) {
			return;
		}
		set(openMenu, in, "open_menu", "key.keyboard.right.shift");
		set(openHud, in, "open_hud_editor", "key.keyboard.right.control");
		set(toggleAll, in, "toggle_all", "key.keyboard.unknown");
		set(panic, in, "panic", "key.keyboard.unknown");
		set(hudNext, in, "hud_binds_next", "key.keyboard.unknown");
		set(hudPrev, in, "hud_binds_prev", "key.keyboard.unknown");
		for (int i = 0; i < KEYS.size(); i++) {
			String id = keyId(NAMES.get(i));
			if (in.containsKey(id)) {
				set(KEYS.get(i), in, id, "key.keyboard.unknown");
			}
		}
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
	}

	/** Сброс к умолчанию: все бинды модулей сняты, Open GUI / HUD Editor — стандартные. */
	public static void resetAll() {
		if (openMenu == null) {
			return;
		}
		for (int i = 0; i < KEYS.size(); i++) {
			// Freelook — клавиша действия: после сброса возвращается Left Alt, остальные модули остаются без клавиши
			String nm = NAMES.get(i);
			KEYS.get(i).setKey("Freelook".equals(nm) ? InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT)
					: "Radial Menu".equals(nm) ? InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R)
					: InputConstants.UNKNOWN);
		}
		apply(Map.of());
	}
}
