package com.pvpvisuals.render.kill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.pvpvisuals.config.KillEffectSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Kill Effect: отслеживание убийств на клиенте, список активных эффектов и описание модуля для меню.
 *
 * Клиентского события смерти сущности в Fabric API нет, поэтому каждый тик ищем LivingEntity рядом,
 * у которых {@code isDeadOrDying()} стало true (сервер синхронизировал здоровье 0). «Моё убийство» —
 * сущность, которую я ударил за последние 5 секунд (см. {@link #onPlayerHit}); убийства стрелами и
 * способностями без удара так не определяются.
 */
public final class KillEffectModule {
	public static final int MAX_EFFECTS = 10;
	private static final int MY_KILL_WINDOW = 100;
	private static final double SCAN_RADIUS = 64.0;

	private static final List<KillEffectInstance> EFFECTS = new ArrayList<>();
	/** Сущности, смерть которых уже обработана. */
	private static final Set<Integer> SEEN = new HashSet<>();
	/** id сущности -> тик моего последнего удара. */
	private static final Map<Integer, Long> MY_HITS = new HashMap<>();
	private static long ticks;

	private KillEffectModule() { }

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(KillEffectModule::tick);
		WorldRenderEvents.END_EXTRACTION.register(KillEffectModule::extract);
		WorldRenderEvents.END_MAIN.register(KillEffectRenderer::render);
	}

	/** Вызывается из CombatEvents при моём ударе по сущности. */
	public static void onPlayerHit(LivingEntity target) {
		MY_HITS.put(target.getId(), ticks);
	}

	private static void extract(WorldExtractionContext context) {
		if (!PvpConfig.get().killEffect.enabled && EFFECTS.isEmpty()) {
			return;
		}
		KillEffectRenderer.extract(context, EFFECTS);
	}

	private static void tick(Minecraft mc) {
		KillEffectSettings c = PvpConfig.get().killEffect;
		if (mc.level == null || mc.player == null || !c.enabled) {
			EFFECTS.clear();
			SEEN.clear();
			MY_HITS.clear();
			return;
		}
		ticks++;

		for (int i = EFFECTS.size() - 1; i >= 0; i--) {
			KillEffectInstance e = EFFECTS.get(i);
			if (++e.age >= e.life) {
				EFFECTS.remove(i);
			}
		}

		KillEffectTarget filter = KillEffectTarget.byName(c.target);
		for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(SCAN_RADIUS))) {
			if (!(e instanceof LivingEntity le) || le == mc.player || !le.isDeadOrDying() || le.deathTime > 2) {
				continue;
			}
			if (!SEEN.add(le.getId())) {
				continue;
			}
			boolean isPlayer = le instanceof Player;
			if ((filter == KillEffectTarget.PLAYERS_ONLY && !isPlayer) || (filter == KillEffectTarget.MOBS_ONLY && isPlayer)) {
				continue;
			}
			if (c.onlyMyKills) {
				Long hit = MY_HITS.get(le.getId());
				if (hit == null || ticks - hit > MY_KILL_WINDOW) {
					continue;
				}
			}
			spawn(le.getX(), le.getY(), le.getZ(), le.getBbHeight() * 0.5f);
		}

		if (ticks % 40 == 0) {
			SEEN.removeIf(id -> mc.level.getEntity(id) == null);
			MY_HITS.entrySet().removeIf(en -> ticks - en.getValue() > MY_KILL_WINDOW * 2L);
		}
	}

	/** Создаёт эффект с текущими настройками (центр тела на высоте centerOffset над y). */
	public static void spawn(double x, double y, double z, float centerOffset) {
		KillEffectSettings c = PvpConfig.get().killEffect;
		if (EFFECTS.size() >= MAX_EFFECTS) {
			EFFECTS.remove(0);
		}
		KillEffectStyle style = KillEffectStyle.byName(c.style);
		EFFECTS.add(new KillEffectInstance(x, y, z, centerOffset, c.duration, style,
				ThreadLocalRandom.current().nextInt(), c.color, c.size, c.glow));
		if (c.sound) {
			// ПРОВЕРИТЬ: SimpleSoundInstance.forUI(SoundEvent, float pitch, float volume) и константы SoundEvents.
			var sound = style == KillEffectStyle.LIGHTNING ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.EXPERIENCE_ORB_PICKUP;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 0.5f));
		}
	}

	/** Пробный эффект перед игроком (кнопка Preview в меню). */
	private static void preview() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Vec3 p = mc.player.position().add(mc.player.getLookAngle().multiply(3, 0, 3).normalize().scale(3.0));
		spawn(p.x, mc.player.getY(), p.z, 0.9f);
	}

	public static ModuleDef def(PvpConfig pc) {
		KillEffectSettings c = pc.killEffect;
		ModuleDef style = ModuleDef.group(Category.PARTICLES, () -> "Style: " + KillEffectStyle.byName(c.style).title);
		for (KillEffectStyle s : KillEffectStyle.values()) {
			style.child(ModuleDef.toggle(s.title, Category.PARTICLES,
					() -> KillEffectStyle.byName(c.style) == s, v -> c.style = s.name()));
		}
		return ModuleDef.toggle("Kill Effect", Category.PARTICLES, () -> c.enabled, v -> c.enabled = v)
				.child(style)
				.child(ModuleDef.cycle(Category.PARTICLES, () -> "Targets: " + KillEffectTarget.byName(c.target).title,
						() -> c.target = KillEffectTarget.byName(c.target).next().name()))
				.child(ModuleDef.toggle("Only My Kills", Category.PARTICLES, () -> c.onlyMyKills, v -> c.onlyMyKills = v))
				.child(ModuleDef.slider("Duration", Category.PARTICLES, 10, 100, 0,
						() -> c.duration, v -> c.duration = (int) v))
				.child(ModuleDef.slider("R", Category.PARTICLES, 0, 255, 0,
						() -> (c.color >> 16) & 0xFF, v -> c.color = (c.color & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("G", Category.PARTICLES, 0, 255, 0,
						() -> (c.color >> 8) & 0xFF, v -> c.color = (c.color & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("B", Category.PARTICLES, 0, 255, 0,
						() -> c.color & 0xFF, v -> c.color = (c.color & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Size", Category.PARTICLES, 0.5, 2.5, 1,
						() -> c.size, v -> c.size = (float) v))
				.child(ModuleDef.toggle("Glow", Category.PARTICLES, () -> c.glow, v -> c.glow = v))
				.child(ModuleDef.toggle("Sound", Category.PARTICLES, () -> c.sound, v -> c.sound = v))
				.child(ModuleDef.action("Preview", Category.PARTICLES, KillEffectModule::preview));
	}
}
