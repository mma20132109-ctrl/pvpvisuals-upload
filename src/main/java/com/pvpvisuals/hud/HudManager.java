package com.pvpvisuals.hud;

import java.util.List;

import com.pvpvisuals.PvpVisualsClient;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.NotificationToast;
import com.pvpvisuals.gui.echo.EchoScreen;
import com.pvpvisuals.gui.rival.TopBar;
import com.pvpvisuals.hud.inventory.InventoryHudComponent;
import com.pvpvisuals.render.CritEffect;
import com.pvpvisuals.render.DamageNumbers;
import com.pvpvisuals.render.trajectory.TrajectoryRenderer;
import com.pvpvisuals.state.CombatState;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Регистрирует HUD-слой мода и рисует все модули. */
public final class HudManager {
	public static final TargetHudComponent TARGET = new TargetHudComponent();
	public static final ComboComponent COMBO = new ComboComponent();
	public static final InventoryHudComponent INVENTORY = new InventoryHudComponent();
	public static final WatermarkModule WATERMARK = new WatermarkModule();
	public static final CoordinatesHudModule COORDINATES = new CoordinatesHudModule();
	public static final MusicHudModule MUSIC = new MusicHudModule();
	public static final com.pvpvisuals.hud.binds.HudBindsModule HUD_BINDS = new com.pvpvisuals.hud.binds.HudBindsModule();
	public static final List<AbstractHudModule> COMPONENTS = List.of(WATERMARK, COORDINATES, MUSIC, TARGET, COMBO, INVENTORY, HUD_BINDS);

	private static final TopBar ISLAND = new TopBar();

	private HudManager() { }

	public static void register() {
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "pvp_visuals"),
				HudManager::render);
	}

	public static LivingEntity currentTarget(Minecraft mc) {
		if (mc.player == null) {
			return null;
		}
		if (mc.crosshairPickEntity instanceof LivingEntity le && le != mc.player) {
			return le;
		}
		LivingEntity last = CombatState.lastTarget;
		if (last != null && last.isAlive() && CombatState.now - CombatState.lastTargetTime < 60) {
			return last;
		}
		return null;
	}

	private static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}

		float pt = tracker.getGameTimeDeltaPartialTick(false);

		TrajectoryRenderer.renderHud(g, mc, pt);
		DamageNumbers.render(g, mc, pt);
		CritEffect.render(g, mc, pt);

		// Островок сверху всегда виден; в меню (EchoScreen) он скрыт.
		if (PvpConfig.get().showIsland && !(mc.screen instanceof EchoScreen)) {
			ISLAND.render(g, mc.font, g.guiWidth());
		}

		if (!(mc.screen instanceof EchoScreen)) {
			NotificationToast.render(g, mc.font, g.guiWidth(), g.guiHeight());
		}

		// В чате элементы рисует HudChatEditor (превью + перетаскивание), здесь не дублируем.
		if (HudChatEditor.active(mc)) {
			return;
		}

		int w = g.guiWidth();
		int h = g.guiHeight();
		for (AbstractHudModule m : COMPONENTS) {
			if (m.enabled()) {
				m.resolve(w, h);
				m.render(g, m.bounds.x, m.bounds.y, false);
			}
		}
	}
}
