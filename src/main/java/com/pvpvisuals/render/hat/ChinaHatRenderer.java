package com.pvpvisuals.render.hat;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pvpvisuals.render.hat.ChinaHatModule.Frame;
import com.pvpvisuals.render.hat.ChinaHatModule.Hat;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Фаза rendering (WorldRenderEvents.END_MAIN). Конус из треугольников + круговое основание.
 * Треугольник отправляется как четырёхугольник с повторенной вершиной (тип рендера — QUADS).
 * Координаты уже относительно камеры (см. ChinaHatModule#extract).
 */
public final class ChinaHatRenderer {
	private static final int SEGMENTS = 32;

	private ChinaHatRenderer() { }

	public static void render(WorldRenderContext ctx) {
		Frame f = ChinaHatModule.frame();
		if (!f.valid) {
			return;
		}

		Matrix4f m = ctx.matrices().last().pose();
		// ПРОВЕРИТЬ: RenderTypes.debugQuads() — имя фабрики в 1.21.11 (см. WorldParticlesRenderer).
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());

		for (Hat h : f.hats) {
			draw(f, h, vc, m);
		}
	}

	private static void draw(Frame f, Hat h, VertexConsumer vc, Matrix4f m) {
		double r = f.radius;
		double ax = h.x;
		double ay = h.y + f.height;
		double az = h.z;

		int topA = f.alpha;
		double[] rx = new double[SEGMENTS + 1];
		double[] rz = new double[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double a = h.spin + i * (Math.PI * 2 / SEGMENTS);
			rx[i] = h.x + Math.cos(a) * r;
			rz[i] = h.z + Math.sin(a) * r;
		}

		for (int i = 0; i < SEGMENTS; i++) {
			// чередующиеся полосы, чтобы вращение было видно
			float stripe = (i & 1) == 0 ? 1.0f : 0.82f;
			int apex = color(f.rgb, 1.2f * stripe, topA);
			int base = color(f.rgb, 0.7f * stripe, f.alpha);
			// боковая грань: вершина -> i -> i+1
			tri(vc, m, ax, ay, az, apex, rx[i], h.y, rz[i], base, rx[i + 1], h.y, rz[i + 1], base);
			// основание: центр -> i+1 -> i (нормаль вниз), темнее
			int bc = color(f.rgb, 0.55f, f.alpha);
			tri(vc, m, h.x, h.y, h.z, bc, rx[i + 1], h.y, rz[i + 1], bc, rx[i], h.y, rz[i], bc);
		}

		if (f.outline) {
			int lc = color(f.rgb, 1.3f, Math.min(255, f.alpha + 90));
			double w = f.outlineWidth;
			for (int i = 0; i < SEGMENTS; i++) {
				line(vc, m, rx[i], h.y, rz[i], rx[i + 1], h.y, rz[i + 1], w, lc); // кромка
				if (i % 4 == 0) {
					line(vc, m, ax, ay, az, rx[i], h.y, rz[i], w, lc); // рёбра конуса
				}
			}
		}
	}

	/** Цвет с множителем яркости (с обрезкой до 255) и альфой. */
	private static int color(int rgb, float k, int alpha) {
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
		int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, (int) ((rgb & 0xFF) * k));
		return (alpha << 24) | (r << 16) | (g << 8) | b;
	}

	private static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, int argb) {
		vc.addVertex(m, (float) x, (float) y, (float) z).setColor(argb);
	}

	private static void tri(VertexConsumer vc, Matrix4f m,
			double x1, double y1, double z1, int c1,
			double x2, double y2, double z2, int c2,
			double x3, double y3, double z3, int c3) {
		v(vc, m, x1, y1, z1, c1);
		v(vc, m, x2, y2, z2, c2);
		v(vc, m, x3, y3, z3, c3);
		v(vc, m, x3, y3, z3, c3);
	}

	/** Линия как узкая полоса, повёрнутая к камере (камера в начале координат). */
	private static void line(VertexConsumer vc, Matrix4f m,
			double x1, double y1, double z1, double x2, double y2, double z2, double width, int argb) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double dz = z2 - z1;
		double mx = (x1 + x2) * 0.5;
		double my = (y1 + y2) * 0.5;
		double mz = (z1 + z2) * 0.5;
		// side = dir x view
		double sx = dy * mz - dz * my;
		double sy = dz * mx - dx * mz;
		double sz = dx * my - dy * mx;
		double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-9) {
			return;
		}
		double k = width * 0.5 / len;
		sx *= k;
		sy *= k;
		sz *= k;
		v(vc, m, x1 - sx, y1 - sy, z1 - sz, argb);
		v(vc, m, x1 + sx, y1 + sy, z1 + sz, argb);
		v(vc, m, x2 + sx, y2 + sy, z2 + sz, argb);
		v(vc, m, x2 - sx, y2 - sy, z2 - sz, argb);
	}
}
