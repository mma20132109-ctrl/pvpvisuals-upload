package com.pvpvisuals.hud.binds;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import com.pvpvisuals.config.HudBindsSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.hud.AbstractHudModule;
import com.pvpvisuals.hud.HudManager;
import com.pvpvisuals.hud.HudModuleUi;

import net.minecraft.client.gui.GuiGraphics;

/** HUD Binds: список включённых модулей с назначенными клавишами в стиле ArrayList. */
public final class HudBindsModule extends AbstractHudModule {
	private static final Category CAT = Category.UTILS;

	private final HudBindsRenderer renderer = new HudBindsRenderer();

	@Override public String id() { return "hud_binds"; }
	@Override public String label() { return "HUD Binds"; }
	@Override public int defaultX() { return 6; }
	@Override public int defaultY() { return 36; }
	@Override public boolean enabled() { return PvpConfig.get().hudBinds.enabled; }

	@Override protected int contentWidth() { return renderer.width(); }
	@Override protected int contentHeight() { return renderer.height(); }

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		renderer.draw(g, preview);
	}

	public void flip(int dir) {
		renderer.flip(dir);
	}

	/** Обновить список при ближайшем кадре (бинд или настройка изменились). */
	public void invalidate() {
		renderer.invalidate();
	}

	// ---------- GUI ----------

	private static HudBindsSettings hb() {
		return PvpConfig.get().hudBinds;
	}

	private static ModuleDef tgl(String name, BooleanSupplier get, Consumer<Boolean> set) {
		return ModuleDef.toggle(name, CAT, get, v -> {
			set.accept(v);
			HudManager.HUD_BINDS.invalidate();
		});
	}

	private static ModuleDef sld(String name, double min, double max, int dec, DoubleSupplier get, DoubleConsumer set) {
		return ModuleDef.slider(name, CAT, min, max, dec, get, v -> {
			set.accept(v);
			HudManager.HUD_BINDS.invalidate();
		});
	}

	public static ModuleDef def(PvpConfig c) {
		ModuleDef root = ModuleDef.toggle("HUD Binds", CAT, () -> hb().enabled, v -> {
			hb().enabled = v;
			HudManager.HUD_BINDS.invalidate();
		});
		root.child(tgl("Bind First", () -> hb().showBindFirst, v -> hb().showBindFirst = v));
		root.child(ModuleDef.cycle(CAT, () -> "Sort: " + HudBindsSortMode.byName(hb().sortMode).title, () -> {
			hb().sortMode = HudBindsSortMode.byName(hb().sortMode).next().name();
			HudManager.HUD_BINDS.invalidate();
		}));
		root.child(sld("Rows Per Page", 1, 8, 0, () -> hb().pageSize, v -> hb().pageSize = (int) v));
		root.child(sld("Auto Flip (s, 0 = off)", 0, 30, 0, () -> hb().autoFlipSeconds, v -> hb().autoFlipSeconds = (int) v));
		root.child(sld("Animation (ms)", 0, 1000, 0, () -> hb().animationSpeed, v -> hb().animationSpeed = (int) v));
		root.child(ModuleDef.action("Next Page", CAT, () -> HudManager.HUD_BINDS.flip(1)));
		root.child(ModuleDef.action("Previous Page", CAT, () -> HudManager.HUD_BINDS.flip(-1)));
		root.child(HudModuleUi.layout(HudManager.HUD_BINDS, CAT));
		return root;
	}
}
