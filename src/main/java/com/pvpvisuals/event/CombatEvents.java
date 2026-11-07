package com.pvpvisuals.event;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.render.CritEffect;
import com.pvpvisuals.render.DamageNumbers;
import com.pvpvisuals.render.particle.BlockHitParticleModule;
import com.pvpvisuals.render.particle.HitParticleModule;
import com.pvpvisuals.state.CombatState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Обработчики боевых событий и клиентского тика. */
public final class CombatEvents {
	private static int blockCooldown;

	private CombatEvents() { }

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			// Событие вызывается и на интегрированном сервере — берём только клиентский мир.
			if (!(world instanceof ClientLevel level)) {
				return InteractionResult.PASS;
			}
			Minecraft mc = Minecraft.getInstance();
			if (player != mc.player || hand != InteractionHand.MAIN_HAND || !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}

			com.pvpvisuals.crosshair.CrosshairModule.onHit();
			boolean crit = isCrit(player);
			Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
			HitParticleModule.INSTANCE.onHit(player.getEyePosition(), at, crit, player.getMainHandItem());

			com.pvpvisuals.render.kill.KillEffectModule.onPlayerHit(target);
			CombatState.lastTarget = target;
			CombatState.lastTargetTime = CombatState.now;
			CombatState.combo = (CombatState.now - CombatState.lastHitTime <= 60) ? CombatState.combo + 1 : 1;
			CombatState.lastHitTime = CombatState.now;

			if (crit) {
				CombatState.critHits.put(target.getId(), CombatState.now);
				CombatState.critFlash = CritEffect.FLASH_TICKS;
			}
			return InteractionResult.PASS;
		});

		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
			if (!(world instanceof ClientLevel level) || player != Minecraft.getInstance().player || blockCooldown > 0) {
				return InteractionResult.PASS;
			}
			blockCooldown = 5;
			BlockHitParticleModule.INSTANCE.onBlockHit(level, pos, dir);
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(CombatEvents::onTick);
	}

	private static void onTick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			CombatState.reset();
			DamageNumbers.clear();
			return;
		}

		CombatState.now++;
		if (CombatState.critFlash > 0) {
			CombatState.critFlash--;
		}
		if (blockCooldown > 0) {
			blockCooldown--;
		}
		if (CombatState.combo > 0 && CombatState.now - CombatState.lastHitTime > 60) {
			CombatState.combo = 0;
		}

		DamageNumbers.tick(mc);
	}

	/** Условия критического удара из ванильного Player#attack. */
	private static boolean isCrit(Player p) {
		return p.getAttackStrengthScale(0.5f) > 0.9f
				&& p.fallDistance > 0.0
				&& !p.onGround()
				&& !p.onClimbable()
				&& !p.isInWater()
				&& !p.hasEffect(MobEffects.BLINDNESS)
				&& !p.isPassenger()
				&& !p.isSprinting();
	}
}
