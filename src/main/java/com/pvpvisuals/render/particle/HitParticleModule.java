package com.pvpvisuals.render.particle;

import java.util.concurrent.ThreadLocalRandom;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;

/** Hit Particle: частицы разлетаются от точки удара по направлению от игрока. */
public final class HitParticleModule extends AbstractParticleModule {
	public static final HitParticleModule INSTANCE = new HitParticleModule();

	private HitParticleModule() {
		super(Category.PARTICLES);
	}

	@Override protected String title() { return "Hit Particles"; }
	@Override protected boolean enabled() { return PvpConfig.get().hitParticles; }
	@Override protected void setEnabled(boolean v) { PvpConfig.get().hitParticles = v; }
	@Override protected ParticleConfig cfg() { return PvpConfig.get().hit; }
	@Override protected boolean showMainColor() { return false; } // старт задают Normal/Crit/Enchant

	@Override
	protected void addExtraSettings(ModuleDef root) {
		HitParticleConfig h = PvpConfig.get().hit;
		root.child(rgbGroup("Normal", () -> h.normalColor, v -> h.normalColor = v));
		root.child(rgbGroup("Crit", () -> h.critColor, v -> h.critColor = v));
		root.child(rgbGroup("Enchant", () -> h.enchantColor, v -> h.enchantColor = v));
		root.child(ModuleDef.toggle("Crit Flash", category, () -> h.critFlash, v -> h.critFlash = v));
	}

	/** Удар по цели: from — глаза игрока, at — точка удара. */
	public void onHit(Vec3 from, Vec3 at, boolean crit, ItemStack weapon) {
		if (!enabled()) {
			return;
		}
		HitParticleConfig h = PvpConfig.get().hit;
		boolean enchanted = hasDamageEnchant(weapon);
		int rgb = crit ? h.critColor : (enchanted ? h.enchantColor : h.normalColor);

		Vec3 dir = at.subtract(from);
		if (dir.lengthSqr() < 1e-6) {
			dir = new Vec3(0, 1, 0);
		}
		for (int i = 0; i < h.count; i++) {
			spawnOne(at, dir, rgb);
		}

		if (crit && h.critFlash) {
			Particle f = new Particle();
			f.x = at.x;
			f.y = at.y;
			f.z = at.z;
			f.px = f.x;
			f.py = f.y;
			f.pz = f.z;
			f.life = 5;
			f.size = Math.max(0.3f, h.size * 6f);
			f.sizeEndMul = 1.6f;
			f.alpha = 255;
			f.friction = 1f;
			f.style = ParticleStyle.CIRCLE;
			f.rgbStart = 0xFFFFFF;
			f.rgbEnd = h.critColor;
			f.rot = ThreadLocalRandom.current().nextFloat() * 6.28f;
			ParticleRenderer.add(f);
		}
	}

	/** Острота / Небесная кара / Бич членистоногих на оружии. */
	private static boolean hasDamageEnchant(ItemStack stack) {
		// ПРОВЕРИТЬ: ItemStack#getEnchantments(), ItemEnchantments#keySet(), Enchantments.SMITE /
		// BANE_OF_ARTHROPODS в Mojang mappings 1.21.11.
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ItemEnchantments e = stack.getEnchantments();
		for (Holder<Enchantment> holder : e.keySet()) {
			if (holder.is(Enchantments.SHARPNESS) || holder.is(Enchantments.SMITE)
					|| holder.is(Enchantments.BANE_OF_ARTHROPODS)) {
				return true;
			}
		}
		return false;
	}
}
