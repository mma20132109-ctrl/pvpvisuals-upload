package com.pvpvisuals.render.trajectory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Точка столкновения: блок (с гранью) или сущность (с хитбоксом). */
public record ImpactMarker(ImpactType type, Vec3 pos, BlockPos block, Direction face, AABB box) {
	public static ImpactMarker none(Vec3 pos) {
		return new ImpactMarker(ImpactType.NONE, pos, null, null, null);
	}
}
