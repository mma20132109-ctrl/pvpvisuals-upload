package com.pvpvisuals.render.particle;

import java.util.concurrent.ThreadLocalRandom;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Block Hit Particle: осколки или искры по грани, в которую ударили. Цвет из блока или ручной. */
public final class BlockHitParticleModule extends AbstractParticleModule {
	public static final BlockHitParticleModule INSTANCE = new BlockHitParticleModule();

	private BlockHitParticleModule() {
		super(Category.WORLD);
	}

	@Override protected String title() { return "Block Hit Particles"; }
	@Override protected boolean enabled() { return PvpConfig.get().blockParticles; }
	@Override protected void setEnabled(boolean v) { PvpConfig.get().blockParticles = v; }
	@Override protected ParticleConfig cfg() { return PvpConfig.get().block; }

	@Override
	protected void addExtraSettings(ModuleDef root) {
		BlockParticleConfig b = PvpConfig.get().block;
		ModuleDef src = ModuleDef.group(category, () -> "Source: " + (b.autoColor ? "Auto" : "Manual"));
		src.child(ModuleDef.toggle("Auto (block)", category, () -> b.autoColor, v -> b.autoColor = true));
		src.child(ModuleDef.toggle("Manual", category, () -> !b.autoColor, v -> b.autoColor = false));
		root.child(src);
		root.child(ModuleDef.slider("Offset", category, 0.0, 0.5, 2, () -> b.offset, v -> b.offset = (float) v));
	}

	/** Удар по блоку (начало разрушения) в грань dir. */
	public void onBlockHit(ClientLevel level, BlockPos pos, Direction dir) {
		if (!enabled()) {
			return;
		}
		BlockParticleConfig b = PvpConfig.get().block;

		int rgb = b.color1;
		if (b.autoColor) {
			// ПРОВЕРИТЬ: BlockState#getMapColor(BlockGetter, BlockPos) и поле MapColor#col в 1.21.11.
			rgb = level.getBlockState(pos).getMapColor(level, pos).col & 0xFFFFFF;
		}

		Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
		Vec3 center = Vec3.atCenterOf(pos).add(normal.scale(0.5 + b.offset));
		// два вектора вдоль грани для разброса точек появления
		Vec3 t1 = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 t2 = normal.cross(t1);
		t1 = t2.cross(normal);

		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < b.count; i++) {
			Vec3 origin = center
					.add(t1.scale((r.nextDouble() - 0.5) * 0.8))
					.add(t2.scale((r.nextDouble() - 0.5) * 0.8));
			spawnOne(origin, normal, rgb);
		}
	}
}
