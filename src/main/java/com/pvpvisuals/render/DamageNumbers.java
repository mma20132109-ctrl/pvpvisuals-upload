package com.pvpvisuals.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.state.CombatState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Всплывающие числа урона в стиле Echo Visual: лавандовый — обычный, яркий фиолетовый — крит, мятный — лечение. */
public final class DamageNumbers {
	private static final int LIFE_TICKS = 32;
	// ключи типа урона (в поле rgb записи Pop); реальные цвета берутся из темы при отрисовке
	private static final int GOLD = 1; // обычный
	private static final int RED = 2; // крит
	private static final int GREEN = 3; // лечение

	private record Pop(Vec3 origin, String text, int rgb, long born) { }

	private static final Map<Integer, Float> LAST_HEALTH = new HashMap<>();
	private static final List<Pop> POPS = new ArrayList<>();

	private DamageNumbers() { }

	public static void clear() {
		LAST_HEALTH.clear();
		POPS.clear();
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}

		PvpConfig cfg = PvpConfig.get();
		Set<Integer> seen = new HashSet<>();

		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity le) || e == mc.player) {
				continue;
			}
			if (le.distanceToSqr(mc.player) > 48.0 * 48.0) {
				continue;
			}

			seen.add(e.getId());
			float hp = le.getHealth();
			Float prev = LAST_HEALTH.put(e.getId(), hp);
			if (prev == null || !cfg.damageNumbers) {
				continue;
			}

			float diff = prev - hp;
			if (Math.abs(diff) < 0.05f) {
				continue;
			}

			boolean heal = diff < 0;
			Long critTick = CombatState.critHits.get(e.getId());
			boolean crit = !heal && critTick != null && CombatState.now - critTick <= 8;

			int rgb = heal ? GREEN : (crit ? RED : GOLD);
			String text = (heal ? "+" : "") + String.format("%.1f", Math.abs(diff));

			double jitter = (le.getRandom().nextDouble() - 0.5) * 0.6;
			Vec3 origin = new Vec3(le.getX() + jitter, le.getY() + le.getBbHeight() + 0.25, le.getZ() + jitter);
			POPS.add(new Pop(origin, text, rgb, CombatState.now));
		}

		LAST_HEALTH.keySet().retainAll(seen);
		POPS.removeIf(p -> CombatState.now - p.born() > LIFE_TICKS);
		CombatState.critHits.values().removeIf(t -> CombatState.now - t > 40);
	}

	private static int baseColor(int kind) {
		return switch (kind) {
			case RED -> EchoTheme.shift(0xFFB57BFF);
			case GREEN -> 0xFF7CF0C0;
			default -> 0xFFEDE4FF;
		};
	}

	private static int glowColor(int kind) {
		return switch (kind) {
			case RED -> EchoTheme.shift(0xFF8B3DFF);
			case GREEN -> 0xFF22B07D;
			default -> EchoTheme.shift(0xFF6D3FD0);
		};
	}

	public static void render(GuiGraphics g, Minecraft mc, float partialTick) {
		if (!PvpConfig.get().damageNumbers || POPS.isEmpty()) {
			return;
		}
		float prevAlpha = Gfx.alpha;
		try {
			EchoTheme.sync();
			int w = g.guiWidth();
			int h = g.guiHeight();

			for (Pop p : POPS) {
				float age = (CombatState.now - p.born()) + partialTick;
				float t = age / LIFE_TICKS;
				float fade = 1.0f - t * t;
				if (fade < 0.03f) {
					continue;
				}

				// плавный подъём с замедлением (ease-out) и лёгкий дрейф вбок
				float rise = 1f - (1f - t) * (1f - t);
				Vec3 pos = p.origin().add(Math.sin(p.born() * 1.7) * 0.25 * rise, rise * 0.9, 0);
				double[] s = Projection.project(mc, pos, partialTick, w, h);
				if (s == null) {
					continue;
				}

				boolean crit = p.rgb() == RED;
				float dist = (float) Math.max(0.8, Math.min(1.8, 8.0 / Math.max(s[2], 1.0)));
				// «pop»: появление с увеличением 1.5 -> 1.0 за первые ~4 тика
				float pop = Gfx.ease(age / 4f);
				float scale = dist * (crit ? 1.35f : 1.0f) * (1f + 0.5f * (1f - pop));

				String text = p.text();
				float tw = Gfx.width(mc.font, text) * scale;
				float th = 9f * scale;
				float x = (float) s[0] - tw / 2f;
				float y = (float) s[1] - th / 2f;

				Gfx.alpha = prevAlpha * Math.min(1f, fade * 1.3f);
				Gfx.glow(g, x - 3f * scale, y - 1f * scale, tw + 6f * scale, th + 2f * scale, 4f * scale,
						glowColor(p.rgb()), crit ? 5f : 3f);
				Gfx.textScaled(g, mc.font, text, x, y, scale, baseColor(p.rgb()), false);
			}
		} finally {
			Gfx.alpha = prevAlpha;
		}
	}
}
