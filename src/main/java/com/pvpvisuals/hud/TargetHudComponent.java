package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.hud.inventory.SlotRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Target HUD в стиле Svintus: стеклянная панель, плитка с головой цели (при ударе вспыхивает красным), ник и дистанция,
 * тонкая полоса здоровья с золотой накладкой поглощения, внизу — предметы в руках и броня с полосками прочности.
 */
public final class TargetHudComponent extends AbstractHudModule {
	private static final int W = 150;
	private static final int PAD = 7;
	private static final int HEAD = 30;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private float shownRatio = 1.0f;
	private float shownAbs;
	private float hurt;
	private LivingEntity lastTarget;

	@Override public String id() { return "target_hud"; }
	@Override public String label() { return "Target HUD"; }
	@Override public int defaultX() { return 240; }
	@Override public int defaultY() { return 130; }
	@Override protected int contentWidth() { return W; }
	@Override protected int contentHeight() { return PAD * 2 + HEAD; }
	@Override public boolean enabled() { return PvpConfig.get().targetHud; }

	@Override
	protected void drawContent(GuiGraphics g, boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		LivingEntity target = preview ? null : HudManager.currentTarget(mc);
		if (!preview && target == null) {
			lastTarget = null;
			return;
		}
		EchoTheme.sync();
		Font font = mc.font;

		String name;
		float hp;
		float max;
		float abs;
		float dist;
		boolean hit;
		if (target != null) {
			name = target.getName().getString();
			hp = target.getHealth();
			max = Math.max(1.0f, target.getMaxHealth());
			abs = target.getAbsorptionAmount();
			dist = mc.player.distanceTo(target);
			hit = target.hurtTime > 0;
			if (target != lastTarget) { // новая цель: полоса не «ползёт» от прошлого значения
				shownRatio = Mth.clamp(hp / max, 0f, 1f);
				shownAbs = 0f;
				lastTarget = target;
			}
		} else {
			name = "Steve";
			hp = 14.5f;
			max = 20.0f;
			abs = 0f;
			dist = 3.2f;
			hit = false;
		}
		shownRatio += (Mth.clamp(hp / max, 0f, 1f) - shownRatio) * 0.15f;
		shownAbs += (Mth.clamp(abs / max, 0f, 1f) - shownAbs) * 0.15f;
		hurt += ((hit ? 1f : 0f) - hurt) * 0.25f;

		int w = W;
		int h = contentHeight();
		HudStyle.panel(g, 0, 0, w, h);

		// плитка с головой
		int hx = PAD;
		int hy = PAD;
		int glow = RoundedRender.lerp(RoundedRender.scaleAlpha(EchoTheme.NEON, 0.30f), 0x80FF3B3B, hurt);
		Gfx.glow(g, hx, hy, HEAD, HEAD, 6, glow, 3);
		Gfx.rrect(g, hx, hy, HEAD, HEAD, 6f, HudAlpha.mul(0x30000000), HudAlpha.mul(0x30000000), 0, 0f);
		drawHead(g, font, target, name, hx + 2, hy + 2, HEAD - 4);
		if (hurt > 0.02f) {
			Gfx.rrect(g, hx + 2, hy + 2, HEAD - 4, HEAD - 4, 2f, HudAlpha.mul(RoundedRender.scaleAlpha(0xFFFF3030, 0.45f * hurt)),
					HudAlpha.mul(RoundedRender.scaleAlpha(0xFFFF3030, 0.45f * hurt)), 0, 0f);
		}
		Gfx.ring(g, hx, hy, HEAD, HEAD, 6, 1, HudAlpha.mul(RoundedRender.scaleAlpha(EchoTheme.NEON, 0.5f)));

		// ник + дистанция
		int tx = hx + HEAD + 7;
		int right = w - PAD;
		int rowW = right - tx;
		String distText = String.format("%.1fm", dist);
		int distW = Gfx.width(font, distText) + 4;
		String shown = name;
		while (shown.length() > 1 && Gfx.width(font, shown) > rowW - distW) {
			shown = shown.substring(0, shown.length() - 1);
		}
		g.drawString(font, shown, tx, hy + 1, HudAlpha.mul(HudStyle.TEXT), false);
		g.drawString(font, distText, right - distW + 4, hy + 1, HudAlpha.mul(EchoTheme.DIM), false);

		// полоса здоровья + значение
		int barY = hy + 12;
		int barH = 4;
		int bw = rowW - 26;
		Gfx.rrect(g, tx, barY, bw, barH, barH / 2f, HudAlpha.mul(0x30FFFFFF), HudAlpha.mul(0x30FFFFFF), 0, 0f);
		if (shownRatio > 0.005f) {
			int fw = Math.max(barH, Math.round(bw * shownRatio));
			Gfx.glow(g, tx, barY, fw, barH, barH / 2f, HudAlpha.mul(RoundedRender.scaleAlpha(EchoTheme.NEON, 0.35f)), 3f);
			Gfx.rrect(g, tx, barY, fw, barH, barH / 2f, HudAlpha.mul(EchoTheme.ACCENT_B), HudAlpha.mul(EchoTheme.ACCENT_A), 0, 0f);
		}
		if (shownAbs > 0.005f) {
			int aw = Math.max(barH, Math.round(bw * shownAbs));
			Gfx.rrect(g, tx, barY, aw, barH, barH / 2f, HudAlpha.mul(0xD0F5C542), HudAlpha.mul(0xD0F5C542), 0, 0f);
		}
		HudIcons.heart(g, right - 21, barY + barH / 2f, 2.6f, HudAlpha.mul(0xFFFF5A6A));
		g.drawString(font, String.format("%.1f", hp + abs), right - 15, barY - 2, HudAlpha.mul(HudStyle.TEXT), false);

		// предметы и броня
		if (target != null) {
			int iy = hy + 20;
			int step = 13;
			int ix = tx;
			ix = slot(g, target.getMainHandItem(), ix, iy) + step;
			ix = slot(g, target.getOffhandItem(), ix, iy) + step + 3;
			for (EquipmentSlot s : ARMOR) {
				ix = slot(g, target.getItemBySlot(s), ix, iy) + step;
			}
		}
	}


	/** Голова: у игрока — лицо скина, у остального моба — буква ника. */
	private void drawHead(GuiGraphics g, Font font, LivingEntity target, String name, int x, int y, int size) {
		Gfx.rrect(g, x, y, size, size, 3f, HudAlpha.mul(0x40000000), HudAlpha.mul(0x40000000), 0, 0f);
		if (target instanceof AbstractClientPlayer player) {
			PlayerFaceRenderer.draw(g, player.getSkin(), x, y, size); // ПРОВЕРИТЬ: сигнатура draw(GuiGraphics, PlayerSkin, int, int, int)
			return;
		}
		String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
		g.pose().pushMatrix();
		g.pose().translate(x + size / 2f - Gfx.width(font, letter) * 1.4f / 2f, y + size / 2f - 5f);
		g.pose().scale(1.4f, 1.4f);
		g.drawString(font, letter, 0, 0, HudAlpha.mul(EchoTheme.ACCENT_TEXT), false);
		g.pose().popMatrix();
	}

	/** Слот 10×10 с предметом и полоской прочности; возвращает x слота. */
	private static int slot(GuiGraphics g, ItemStack stack, int x, int y) {
		Gfx.rrect(g, x - 1, y - 1, 12, 12, 2.5f, HudAlpha.mul(0x14FFFFFF), HudAlpha.mul(0x14FFFFFF), 0, 0f);
		if (stack.isEmpty()) {
			return x;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(10f / 16f, 10f / 16f);
		g.renderItem(stack, 0, 0); // ПРОВЕРИТЬ: GuiGraphics#renderItem(ItemStack, int, int) в 1.21.11
		g.pose().popMatrix();
		if (stack.isDamageableItem() && stack.getDamageValue() > 0) {
			int max = stack.getMaxDamage();
			float ratio = Mth.clamp((max - stack.getDamageValue()) / (float) max, 0f, 1f);
			int by = y + 12;
			Gfx.rrect(g, x, by, 10, 2, 1f, HudAlpha.mul(0x50000000), HudAlpha.mul(0x50000000), 0, 0f);
			int c = SlotRenderer.durabilityColor(ratio);
			Gfx.rrect(g, x, by, Math.max(2, Math.round(10 * ratio)), 2, 1f, HudAlpha.mul(c), HudAlpha.mul(c), 0, 0f);
		}
		return x;
	}
}
