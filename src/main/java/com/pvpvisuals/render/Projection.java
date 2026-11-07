package com.pvpvisuals.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Проекция мировой точки на GUI-экран (от первого лица, по позиции и повороту игрока).
 * Нужна, чтобы рисовать числа урона и траекторию в HUD-слое без собственных render pipeline.
 * Динамические изменения FOV (спринт, зелья) и вид от третьего лица не учитываются.
 */
public final class Projection {
	private Projection() { }

	/** @return {x, y, distance} в GUI-координатах или null, если точка позади камеры. */
	public static double[] project(Minecraft mc, Vec3 world, float partialTick, int guiW, int guiH) {
		if (mc.player == null) {
			return null;
		}

		Vec3 eye = mc.player.getEyePosition(partialTick);
		double yaw = Math.toRadians(mc.player.getViewYRot(partialTick));
		double pitch = Math.toRadians(mc.player.getViewXRot(partialTick));

		double cosP = Math.cos(pitch);
		double fx = -Math.sin(yaw) * cosP;
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * cosP;

		// right = (-cos(yaw), 0, -sin(yaw)); up = right x forward
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double ux = -rz * fy; // ry = 0
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;

		double dx = world.x - eye.x;
		double dy = world.y - eye.y;
		double dz = world.z - eye.z;

		double cz = dx * fx + dy * fy + dz * fz;
		if (cz < 0.05) {
			return null;
		}
		double cx = dx * rx + dz * rz;
		double cy = dx * ux + dy * uy + dz * uz;

		double fov = mc.options.fov().get();
		double tanHalf = Math.tan(Math.toRadians(fov) / 2.0);
		double aspect = (double) guiW / (double) guiH;

		double ndcX = cx / (cz * tanHalf * aspect);
		double ndcY = cy / (cz * tanHalf);

		double sx = guiW / 2.0 * (1.0 + ndcX);
		double sy = guiH / 2.0 * (1.0 - ndcY);
		return new double[] {sx, sy, Math.sqrt(dx * dx + dy * dy + dz * dz)};
	}
}
