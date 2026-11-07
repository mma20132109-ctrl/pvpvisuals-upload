package com.pvpvisuals.render.world;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pvpvisuals.render.world.WorldParticlesModule.Frame;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Фаза rendering (WorldRenderEvents.END_MAIN). Рисует камера-ориентированные квады по снимку
 * {@link Frame}. Все точки — функции от времени и индекса (без состояния между кадрами).
 * Координаты вершин передаются относительно камеры, как требует WorldRenderContext#consumers().
 */
public final class WorldParticlesRenderer {
	private static final int STAR_COUNT = 48;
	private static final int COMETS = 6;
	private static final int COMET_TAIL = 7;
	private static final int RING_COUNT = 48;
	private static final int SPIRAL_COUNT = 32;
	private static final int RAY_COUNT = 14;
	private static final int RAY_POINTS = 7;

	private WorldParticlesRenderer() { }

	/** Приёмник точек: индекс нужен для турбулентности. */
	private interface Sink {
		void point(int idx, double x, double y, double z, float sizeMul, float alpha);
	}

	public static void render(WorldRenderContext ctx) {
		Frame f = WorldParticlesModule.frame();
		if (!f.valid) {
			return;
		}

		PoseStack matrices = ctx.matrices();
		Matrix4f m = matrices.last().pose();

		// ПРОВЕРИТЬ: RenderType для цветных квадов (POSITION_COLOR, QUADS, без culling).
		// Здесь используется RenderTypes.debugQuads(); если метода нет в 1.21.11 — подставьте
		// другой тип из net.minecraft.client.renderer.rendertype.RenderTypes (например debugFilledBox()).
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());

		// Базис камеры по yaw/pitch (в градусах, как в Minecraft).
		double yaw = Math.toRadians(f.yaw);
		double pitch = Math.toRadians(f.pitch);
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double lx = Math.cos(yaw);
		double lz = Math.sin(yaw);
		// up = forward x left (left.y = 0)
		double ux = fy * lz;
		double uy = fz * lx - fx * lz;
		double uz = -fy * lx;

		double t = f.time * f.speed;

		for (int ai = 0; ai < f.anchorCount; ai++) {
			double[] anchor = f.anchors[ai];
			f.halfHeight = anchor[3];
			drawAnchor(f, vc, m, anchor[0] - f.camX, anchor[1] - f.camY, anchor[2] - f.camZ,
					lx, lz, ux, uy, uz, t);
		}
	}

	private static void drawAnchor(Frame f, VertexConsumer vc, Matrix4f m, double ox, double oy, double oz,
			double lx, double lz, double ux, double uy, double uz, double t) {
		Sink sink = (idx, x, y, z, sizeMul, alpha) -> {
			if (f.turbulence) {
				double k = 0.22 * f.radius;
				x += Math.sin(f.time * 1.9 * f.speed + idx * 1.31) * k;
				y += Math.sin(f.time * 2.3 * f.speed + idx * 0.73) * k;
				z += Math.sin(f.time * 1.7 * f.speed + idx * 2.11) * k;
			}
			int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
			if (a == 0) {
				return;
			}
			float s = f.size * sizeMul;
			float cx = (float) (ox + x);
			float cy = (float) (oy + y);
			float cz = (float) (oz + z);
			float lxs = (float) (lx * s);
			float lzs = (float) (lz * s);
			float uxs = (float) (ux * s);
			float uys = (float) (uy * s);
			float uzs = (float) (uz * s);
			int argb = (a << 24) | f.rgb;

			vc.addVertex(m, cx - lxs - uxs, cy - uys, cz - lzs - uzs).setColor(argb);
			vc.addVertex(m, cx + lxs - uxs, cy - uys, cz + lzs - uzs).setColor(argb);
			vc.addVertex(m, cx + lxs + uxs, cy + uys, cz + lzs + uzs).setColor(argb);
			vc.addVertex(m, cx - lxs + uxs, cy + uys, cz - lzs + uzs).setColor(argb);
		};

		switch (f.style) {
			case STARS -> stars(f, t, sink);
			case RING -> ring(f, t, sink);
			case SPIRAL -> spiral(f, t, sink);
			case RAYS -> rays(f, t, sink);
		}
	}

	/** Псевдослучайное число [0,1) по индексу и соли. */
	private static double rnd(int i, int salt) {
		double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
		return v - Math.floor(v);
	}

	private static void stars(Frame f, double t, Sink sink) {
		double r = f.radius;
		double rot = t * 0.15;
		double cr = Math.cos(rot);
		double sr = Math.sin(rot);

		for (int i = 0; i < STAR_COUNT; i++) {
			double theta = rnd(i, 1) * Math.PI * 2;
			double cosPhi = rnd(i, 2) * 2 - 1;
			double sinPhi = Math.sqrt(1 - cosPhi * cosPhi);
			double rad = r * (0.45 + 0.55 * rnd(i, 3));
			double x = rad * sinPhi * Math.cos(theta);
			double y = rad * cosPhi * 0.7;
			double z = rad * sinPhi * Math.sin(theta);
			double rx = x * cr - z * sr;
			double rz = x * sr + z * cr;
			double tw = Math.abs(Math.sin(t * 2.0 + i));
			sink.point(i, rx, y, rz, (float) (0.6 + 0.6 * tw), (float) (0.35 + 0.65 * tw));
		}

		// Кометы: голова летит по прямой, за ней хвост из затухающих квадов.
		for (int k = 0; k < COMETS; k++) {
			double phase = (t * 0.5 + rnd(k, 7)) % 1.0;
			double envelope = Math.sin(Math.PI * phase);

			double sx = (rnd(k, 8) - 0.5) * 2.0 * r;
			double sy = r * 1.2;
			double sz = (rnd(k, 9) - 0.5) * 2.0 * r;
			double dx = rnd(k, 10) - 0.5;
			double dy = -(0.6 + 0.4 * rnd(k, 11));
			double dz = rnd(k, 12) - 0.5;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			dx /= len;
			dy /= len;
			dz /= len;

			double travel = r * 2.4;
			double hx = sx + dx * travel * phase;
			double hy = sy + dy * travel * phase;
			double hz = sz + dz * travel * phase;

			for (int m = 0; m < COMET_TAIL; m++) {
				double back = m * 0.12 * r;
				float fade = (float) ((1.0 - m / (double) COMET_TAIL) * envelope);
				sink.point(STAR_COUNT + k * COMET_TAIL + m,
						hx - dx * back, hy - dy * back, hz - dz * back,
						1.4f - 0.15f * m, fade);
			}
		}
	}

	private static void ring(Frame f, double t, Sink sink) {
		for (int i = 0; i < RING_COUNT; i++) {
			double ang = i / (double) RING_COUNT * Math.PI * 2 + t;
			double pulse = 0.75 + 0.25 * Math.sin(t * 3 + i * 0.6);
			sink.point(i, f.radius * Math.cos(ang), -f.halfHeight * 0.55, f.radius * Math.sin(ang),
					(float) pulse, 1.0f);
		}
	}

	private static void spiral(Frame f, double t, Sink sink) {
		for (int s = 0; s < 2; s++) {
			for (int i = 0; i < SPIRAL_COUNT; i++) {
				double p = i / (double) SPIRAL_COUNT;
				double h = (p + t * 0.15) % 1.0;
				double ang = p * Math.PI * 4 + s * Math.PI + t * 1.5;
				double rad = f.radius * (0.6 + 0.4 * Math.sin(Math.PI * h));
				double y = (h * 2 - 1) * f.halfHeight * 1.2;
				sink.point(s * SPIRAL_COUNT + i, rad * Math.cos(ang), y, rad * Math.sin(ang),
						1.0f, (float) Math.sin(Math.PI * h));
			}
		}
	}

	private static void rays(Frame f, double t, Sink sink) {
		double golden = Math.PI * (3 - Math.sqrt(5));
		double rot = t * 0.3;
		double cr = Math.cos(rot);
		double sr = Math.sin(rot);
		double pulse = (t * 1.5) % 1.0;

		for (int k = 0; k < RAY_COUNT; k++) {
			double yy = 1 - (k + 0.5) / RAY_COUNT * 2;
			double rr = Math.sqrt(1 - yy * yy);
			double a = k * golden;
			double dx = Math.cos(a) * rr;
			double dz = Math.sin(a) * rr;
			double rx = dx * cr - dz * sr;
			double rz = dx * sr + dz * cr;

			for (int j = 0; j < RAY_POINTS; j++) {
				double s = (j + pulse) / RAY_POINTS;
				double rad = f.radius * (0.25 + 0.75 * s);
				sink.point(k * RAY_POINTS + j, rx * rad, yy * rad, rz * rad,
						(float) (1.1 - 0.6 * s), (float) (1.0 - s));
			}
		}
	}
}
