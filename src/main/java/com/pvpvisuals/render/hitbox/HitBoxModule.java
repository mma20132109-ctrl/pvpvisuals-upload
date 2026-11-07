package com.pvpvisuals.render.hitbox;

import org.joml.Matrix4f;

import com.pvpvisuals.config.HitBoxSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * HitBox. Extraction (WorldRenderEvents.END_EXTRACTION): обходит сущности клиентского мира, фильтрует игроков и мобов,
 * определяет цель под прицелом (Minecraft#hitResult), двигает прогресс цвета каждой сущности и складывает готовые
 * координаты коробок (относительно камеры) в переиспользуемый массив. Rendering (END_MAIN) — {@link HitBoxRenderer}.
 */
public final class HitBoxModule {
	public static final int MAX_BOXES = 128;
	/** minX, minY, minZ, maxX, maxY, maxZ (относительно камеры) + прогресс цвета. */
	public static final int STRIDE = 7;

	public static final class Frame {
		public int count;
		public final float[] boxes = new float[MAX_BOXES * STRIDE];
		public final Matrix4f view = new Matrix4f();
	}

	private static final Frame FRAME = new Frame();
	private static long lastNanos;

	private HitBoxModule() { }

	public static Frame frame() {
		return FRAME;
	}

	public static void register() {
		WorldRenderEvents.END_EXTRACTION.register(HitBoxModule::extract);
		WorldRenderEvents.END_MAIN.register(HitBoxRenderer::render);
	}

	private static void extract(WorldExtractionContext context) {
		Frame f = FRAME;
		f.count = 0;

		PvpConfig config = PvpConfig.get();
		HitBoxSettings s = config.hitBox;
		boolean targetEsp = config.targetEsp;
		Minecraft mc = Minecraft.getInstance();
		if ((!s.enabled && !targetEsp) || mc.level == null || mc.player == null) {
			HitBoxState.clear();
			lastNanos = 0;
			return;
		}

		long now = System.nanoTime();
		float dtTicks = lastNanos == 0 ? 1f : Math.min(5f, (now - lastNanos) / 50_000_000f); // 50 мс = 1 тик
		lastNanos = now;

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.worldState().cameraRenderState.pos;
		Entity targeted = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null;
		Frustum frustum = context.frustum();
		double maxSq = (double) s.maxDistance * s.maxDistance;

		HitBoxState.beginFrame();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (f.count >= MAX_BOXES) {
				break;
			}
			if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()) {
				continue;
			}
			boolean player = e instanceof Player;
			// Target ESP is target-only; it does not highlight every nearby entity.
			if (targetEsp && (e != targeted || !player)) {
				continue;
			}
			if (!targetEsp && (player ? !s.showPlayers : !s.showMobs)) {
				continue;
			}
			if (e.distanceToSqr(mc.player) > maxSq) {
				continue;
			}

			// коробка на момент partialTick: сдвиг от позиции тика к интерполированной
			AABB b = e.getBoundingBox();
			double dx = Mth.lerp(pt, e.xo, e.getX()) - e.getX();
			double dy = Mth.lerp(pt, e.yo, e.getY()) - e.getY();
			double dz = Mth.lerp(pt, e.zo, e.getZ()) - e.getZ();
			double minX = b.minX + dx, minY = b.minY + dy, minZ = b.minZ + dz;
			double maxX = b.maxX + dx, maxY = b.maxY + dy, maxZ = b.maxZ + dz;
			if (frustum != null && !s.throughWalls && !frustum.isVisible(b)) {
				continue; // за кадром (без through-walls это безопасно; сквозь стены тоже можно, но оставляем простую логику)
			}

			float progress = targetEsp ? 1f : HitBoxState.update(e.getUUID(), e == targeted, s.transitionSpeed, dtTicks);
			int o = f.count * STRIDE;
			f.boxes[o] = (float) (minX - cam.x);
			f.boxes[o + 1] = (float) (minY - cam.y);
			f.boxes[o + 2] = (float) (minZ - cam.z);
			f.boxes[o + 3] = (float) (maxX - cam.x);
			f.boxes[o + 4] = (float) (maxY - cam.y);
			f.boxes[o + 5] = (float) (maxZ - cam.z);
			f.boxes[o + 6] = progress;
			f.count++;
		}
		HitBoxState.endFrame();
		f.view.set(context.viewMatrix());
	}

	public static ModuleDef def(PvpConfig pc) {
		HitBoxSettings s = pc.hitBox;
		return ModuleDef.toggle("HitBox", Category.VISUAL, () -> s.enabled, v -> {
			s.enabled = v;
			if (v) {
				HitBoxRenderer.reset();
			}
		})
				.child(ModuleDef.toggle("Players", Category.VISUAL, () -> s.showPlayers, v -> s.showPlayers = v))
				.child(ModuleDef.toggle("Mobs", Category.VISUAL, () -> s.showMobs, v -> s.showMobs = v))
				.child(ModuleDef.slider("R", Category.VISUAL, 0, 255, 0, () -> (s.baseColor >> 16) & 0xFF,
						v -> s.baseColor = (s.baseColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("G", Category.VISUAL, 0, 255, 0, () -> (s.baseColor >> 8) & 0xFF,
						v -> s.baseColor = (s.baseColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("B", Category.VISUAL, 0, 255, 0, () -> s.baseColor & 0xFF,
						v -> s.baseColor = (s.baseColor & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Alpha", Category.VISUAL, 0.05, 1.0, 2,
						() -> s.baseAlpha, v -> s.baseAlpha = (float) v))
				.child(ModuleDef.slider("Target R", Category.VISUAL, 0, 255, 0, () -> (s.targetColor >> 16) & 0xFF,
						v -> s.targetColor = (s.targetColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("Target G", Category.VISUAL, 0, 255, 0, () -> (s.targetColor >> 8) & 0xFF,
						v -> s.targetColor = (s.targetColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("Target B", Category.VISUAL, 0, 255, 0, () -> s.targetColor & 0xFF,
						v -> s.targetColor = (s.targetColor & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Target Alpha", Category.VISUAL, 0.05, 1.0, 2,
						() -> s.targetAlpha, v -> s.targetAlpha = (float) v))
				.child(ModuleDef.slider("Line Thickness", Category.VISUAL, 0.5, 4.0, 1,
						() -> s.lineThickness, v -> s.lineThickness = (float) v))
				.child(ModuleDef.slider("Transition Speed", Category.VISUAL, 0.02, 0.5, 2,
						() -> s.transitionSpeed, v -> s.transitionSpeed = (float) v))
				.child(ModuleDef.slider("Max Distance", Category.VISUAL, 8, 128, 0,
						() -> s.maxDistance, v -> s.maxDistance = (float) v))
				.child(ModuleDef.toggle("Through Walls", Category.VISUAL, () -> s.throughWalls, v -> s.throughWalls = v))
				.child(ModuleDef.toggle("Corners Only", Category.VISUAL, () -> s.cornerOnly, v -> s.cornerOnly = v))
				.child(ModuleDef.toggle("Glow", Category.VISUAL, () -> s.glow, v -> s.glow = v));
	}
}
