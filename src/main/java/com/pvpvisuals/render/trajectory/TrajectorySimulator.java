package com.pvpvisuals.render.trajectory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Пошаговая симуляция полёта: перемещение на скорость, затем сопротивление и гравитация (порядок как у
 * ванильных снарядов). Блоки — ванильный Level#clip, сущности — пересечение расширенных хитбоксов.
 * Сопротивление воды и ускорение огненных шаров не моделируются.
 */
public final class TrajectorySimulator {
	public static final int MAX_TICKS = 200;

	/** Результат одной симуляции. */
	public static final class Result {
		public ProjectileType type;
		/** Точки вдоль линии (detail штук на тик). */
		public final List<Vec3> points = new ArrayList<>();
		/** Позиция в конце каждого тика (индекс = номер тика - 1), для прогресс-точек. */
		public final List<Vec3> tickPoints = new ArrayList<>();
		public Vec3 start;
		public Vec3 end;
		public ImpactMarker impact;
		public int ticks;
		public double distance;
		/** Самая высокая точка или null, если снаряд только падал. */
		public Vec3 peak;
		/** Поверхность под точкой падения (для вертикальной линии) или null. */
		public Vec3 ground;
		/** Скорость в последний тик (для оценки урона). */
		public double impactSpeed;
		/** Оценка урона стрелы: min..max, -1 если не считается. */
		public int damageMin = -1;
		public int damageMax = -1;
	}

	private TrajectorySimulator() { }

	public static Result simulate(Level level, Entity owner, Vec3 start, Vec3 velocity, ProjectileType type,
			double maxDistance, int detail) {
		Result r = new Result();
		r.type = type;
		r.start = start;

		Vec3 pos = start;
		Vec3 vel = velocity;
		double maxSq = maxDistance * maxDistance;
		double topY = start.y;
		Vec3 peak = null;
		ImpactMarker impact = null;

		r.points.add(pos);
		for (int tick = 0; tick < MAX_TICKS; tick++) {
			Vec3 next = pos.add(vel);

			BlockHitResult blockHit = level.clip(new ClipContext(pos, next,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			Vec3 limit = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : next;

			// ближайшая сущность на отрезке pos -> limit
			Entity hitEntity = null;
			Vec3 entityPoint = null;
			double best = pos.distanceToSqr(limit) + 1e-9;
			AABB sweep = new AABB(pos, limit).inflate(1.0);
			for (Entity e : level.getEntities(owner, sweep, en -> !en.isSpectator() && en.isPickable())) {
				if (tick < 2 && e == owner) {
					continue;
				}
				AABB box = e.getBoundingBox().inflate(0.3 + e.getPickRadius());
				var p = box.clip(pos, limit);
				if (p.isPresent()) {
					double d = pos.distanceToSqr(p.get());
					if (d < best) {
						best = d;
						hitEntity = e;
						entityPoint = p.get();
					}
				}
			}

			Vec3 end = limit;
			if (hitEntity != null) {
				end = entityPoint;
				impact = new ImpactMarker(ImpactType.ENTITY, entityPoint, null, null, hitEntity.getBoundingBox());
			} else if (blockHit.getType() != HitResult.Type.MISS) {
				impact = new ImpactMarker(ImpactType.BLOCK, blockHit.getLocation(), blockHit.getBlockPos(),
						blockHit.getDirection(), null);
			}

			for (int s = 1; s <= detail; s++) {
				r.points.add(pos.lerp(end, s / (double) detail));
			}
			r.tickPoints.add(end);
			r.ticks = tick + 1;
			r.impactSpeed = vel.length();

			if (impact != null) {
				pos = end;
				break;
			}

			pos = next;
			vel = vel.scale(type.drag).subtract(0, type.gravity, 0);
			if (pos.y > topY) {
				topY = pos.y;
				peak = pos;
			}
			if (pos.distanceToSqr(start) > maxSq) {
				break;
			}
		}

		r.end = pos;
		r.impact = impact != null ? impact : ImpactMarker.none(pos);
		r.distance = pos.distanceTo(start);
		// пик показываем, только если снаряд сначала поднимался выше старта и потом пошёл вниз
		if (peak != null && peak.y > start.y + 0.3 && peak.y > pos.y + 0.3) {
			r.peak = peak;
		}

		// поверхность под точкой падения
		BlockHitResult down = level.clip(new ClipContext(pos, pos.subtract(0, 64, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (down.getType() != HitResult.Type.MISS && pos.y - down.getLocation().y > 0.4) {
			r.ground = down.getLocation();
		}
		return r;
	}
}
