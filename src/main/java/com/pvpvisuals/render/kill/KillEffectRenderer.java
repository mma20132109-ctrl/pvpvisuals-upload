package com.pvpvisuals.render.kill;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Геометрия эффектов. Extraction (END_EXTRACTION): по списку эффектов строится буфер квадов в координатах
 * камеры. Rendering (END_MAIN): буфер просто выписывается в VertexConsumer. Всё без текстур и без аллокаций в кадре.
 * Свечение имитируется широкими полупрозрачными копиями (настоящий additive blending требует своего RenderPipeline).
 */
final class KillEffectRenderer {
	private static final QuadBuffer BUF = new QuadBuffer();
	private static final int RING_SEGS = 48;
	private static final float[] COS = new float[RING_SEGS + 1];
	private static final float[] SIN = new float[RING_SEGS + 1];
	private static final int BOLT_N = 12;
	private static final float[] PX = new float[BOLT_N + 1];
	private static final float[] PY = new float[BOLT_N + 1];
	private static final float[] PZ = new float[BOLT_N + 1];

	/** 8x8 пиксельный череп ('#' — закрашено). */
	private static final String[] SKULL = {
			"..####..",
			".######.",
			"#..##..#",
			"#..##..#",
			".######.",
			"..#..#..",
			"..####..",
			"..#.#.#."
	};

	// базис billboard'а для текущей точки (заполняет basis())
	private static float rX, rY, rZ, uX, uY, uZ;

	static {
		for (int i = 0; i <= RING_SEGS; i++) {
			double a = Math.PI * 2.0 * i / RING_SEGS;
			COS[i] = (float) Math.cos(a);
			SIN[i] = (float) Math.sin(a);
		}
	}

	private KillEffectRenderer() { }

	// ---------- extraction ----------

	static void extract(WorldExtractionContext context, List<KillEffectInstance> list) {
		BUF.clear();
		if (list.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.worldState().cameraRenderState.pos;
		for (int i = 0; i < list.size(); i++) {
			KillEffectInstance e = list.get(i);
			float t = Mth.clamp((e.age + pt) / e.life, 0f, 1f);
			float ox = (float) (e.x - cam.x);
			float oy = (float) (e.y - cam.y);
			float oz = (float) (e.z - cam.z);
			switch (e.style) {
				case LIGHTNING -> lightning(e, t, ox, oy, oz);
				case EXPLOSION -> explosion(e, t, ox, oy + e.centerOffset, oz, (e.age + pt) / 20f);
				case SKULL -> skull(e, t, ox, oy + e.centerOffset, oz);
				case STAR_BURST -> starBurst(e, t, ox, oy + e.centerOffset, oz);
				case SHOCKWAVE -> shockwave(e, t, ox, oy, oz);
			}
		}
	}

	// ---------- rendering ----------

	static void render(WorldRenderContext ctx) {
		if (BUF.quads == 0) {
			return;
		}
		Matrix4f m = ctx.matrices().last().pose();
		// ПРОВЕРИТЬ: RenderTypes.debugQuads() (POSITION_COLOR, QUADS, translucent) в 1.21.11.
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());
		float[] p = BUF.pos;
		int[] c = BUF.col;
		for (int q = 0; q < BUF.quads; q++) {
			for (int k = 0; k < 4; k++) {
				int pi = (q * 4 + k) * 3;
				vc.addVertex(m, p[pi], p[pi + 1], p[pi + 2]).setColor(c[q * 4 + k]);
			}
		}
	}

	// ---------- общие помощники ----------

	private static float rnd(int seed, int i) {
		int h = seed * 0x9E3779B1 + i * 0x85EBCA6B;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		h ^= h >>> 15;
		return (h >>> 8) / 16777216f;
	}

	private static float easeOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return 1f - (1f - t) * (1f - t);
	}

	private static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int col(int rgb, float alpha) {
		int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Лента между двумя точками, развёрнутая к камере (камера в начале координат). */
	private static void ribbon(float ax, float ay, float az, float wa, int ca,
			float bx, float by, float bz, float wb, int cb) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float mx = (ax + bx) * 0.5f;
		float my = (ay + by) * 0.5f;
		float mz = (az + bz) * 0.5f;
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-6f) {
			return;
		}
		sx /= len;
		sy /= len;
		sz /= len;
		BUF.quad(ax + sx * wa, ay + sy * wa, az + sz * wa, ca,
				ax - sx * wa, ay - sy * wa, az - sz * wa, ca,
				bx - sx * wb, by - sy * wb, bz - sz * wb, cb,
				bx + sx * wb, by + sy * wb, bz + sz * wb, cb);
	}

	/** Считает «вправо/вверх» плоскости, обращённой к камере, для точки (cx, cy, cz) в координатах камеры. */
	private static void basis(float cx, float cy, float cz) {
		float l = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (l < 1e-5f) {
			rX = 1; rY = 0; rZ = 0; uX = 0; uY = 1; uZ = 0;
			return;
		}
		float nx = cx / l;
		float ny = cy / l;
		float nz = cz / l;
		// r = up0 x n  (up0 = (0,1,0))
		float rx = nz;
		float ry = 0f;
		float rz = -nx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);
		if (rl < 1e-4f) {
			rX = 1; rY = 0; rZ = 0; uX = 0; uY = 0; uZ = 1;
			return;
		}
		rx /= rl;
		rz /= rl;
		rX = rx; rY = ry; rZ = rz;
		// u = n x r
		uX = ny * rz - nz * ry;
		uY = nz * rx - nx * rz;
		uZ = nx * ry - ny * rx;
	}

	/** Ромб в плоскости billboard'а (basis() уже вызван). */
	private static void diamond(float cx, float cy, float cz, float hx, float hy, int c) {
		BUF.quad(cx + rX * hx, cy + rY * hx, cz + rZ * hx, c,
				cx + uX * hy, cy + uY * hy, cz + uZ * hy, c,
				cx - rX * hx, cy - rY * hx, cz - rZ * hx, c,
				cx - uX * hy, cy - uY * hy, cz - uZ * hy, c);
	}

	/** Четырёхконечная звезда из двух вытянутых ромбов. */
	private static void star4(float cx, float cy, float cz, float s, int c) {
		basis(cx, cy, cz);
		diamond(cx, cy, cz, s, s * 0.18f, c);
		diamond(cx, cy, cz, s * 0.18f, s, c);
	}

	// ---------- стили ----------

	private static void lightning(KillEffectInstance e, float t, float ox, float oy, float oz) {
		float s = e.size;
		float height = 12f * s;
		int fs = e.seed + e.age / 2; // «мерцание»: форма меняется каждые 2 тика
		float env = t < 0.15f ? 1f : 1f - (t - 0.15f) / 0.85f;
		env *= 0.65f + 0.35f * rnd(fs, 99);
		int core = mix(e.rgb, 0xFFFFFF, 0.75f);

		for (int k = 0; k <= BOLT_N; k++) {
			float f = k / (float) BOLT_N;
			float amp = 0.8f * s * (float) Math.sin(Math.PI * f);
			PX[k] = ox + (rnd(fs, k * 2) * 2f - 1f) * amp;
			PY[k] = oy + height * (1f - f);
			PZ[k] = oz + (rnd(fs, k * 2 + 1) * 2f - 1f) * amp;
		}
		float w = 0.09f * s;
		for (int k = 0; k < BOLT_N; k++) {
			bolt(PX[k], PY[k], PZ[k], PX[k + 1], PY[k + 1], PZ[k + 1], w, env, core, e);
		}
		// ветки
		for (int b = 0; b < 3; b++) {
			int sk = 3 + (int) (rnd(e.seed, b + 50) * 5f);
			float dx = (rnd(e.seed, b + 60) * 2f - 1f) * 0.9f * s;
			float dz = (rnd(e.seed, b + 70) * 2f - 1f) * 0.9f * s;
			float px = PX[sk];
			float py = PY[sk];
			float pz = PZ[sk];
			for (int j = 1; j <= 5; j++) {
				float nx = px + dx + (rnd(fs, b * 10 + j) * 2f - 1f) * 0.35f * s;
				float ny = py - 1.1f * s;
				float nz = pz + dz + (rnd(fs, b * 10 + j + 5) * 2f - 1f) * 0.35f * s;
				bolt(px, py, pz, nx, ny, nz, w * (1f - j * 0.15f), env * 0.8f, core, e);
				px = nx;
				py = ny;
				pz = nz;
			}
		}
		// вспышка в точке удара
		star4(ox, oy + 0.1f, oz, 1.3f * s * (1f - t * 0.6f), col(core, env * 0.9f));
	}

	private static void bolt(float ax, float ay, float az, float bx, float by, float bz, float w, float a,
			int core, KillEffectInstance e) {
		if (e.glow) {
			ribbon(ax, ay, az, w * 3.2f, col(e.rgb, a * 0.22f), bx, by, bz, w * 3.2f, col(e.rgb, a * 0.22f));
		}
		ribbon(ax, ay, az, w * 1.4f, col(e.rgb, a * 0.8f), bx, by, bz, w * 1.4f, col(e.rgb, a * 0.8f));
		ribbon(ax, ay, az, w * 0.6f, col(core, a), bx, by, bz, w * 0.6f, col(core, a));
	}

	private static void explosion(KillEffectInstance e, float t, float cx, float cy, float cz, float sec) {
		float s = e.size;
		float drag = (1f - (float) Math.exp(-3.0 * sec)) / 3f;
		float fade = (float) Math.pow(1f - t, 1.2);
		int hot = mix(e.rgb, 0xFFFFFF, 0.6f);
		int c = mix(hot, e.rgb, Math.min(1f, t * 2f));

		for (int i = 0; i < 36; i++) {
			float theta = rnd(e.seed, i * 4) * Mth.TWO_PI;
			float cosP = rnd(e.seed, i * 4 + 1) * 2f - 1f;
			float dy = cosP * 0.8f + 0.25f;
			float r = (float) Math.sqrt(Math.max(0f, 1f - cosP * cosP));
			float speed = (3f + 4f * rnd(e.seed, i * 4 + 2)) * s;
			float px = cx + r * (float) Math.cos(theta) * speed * drag;
			float py = cy + dy * speed * drag - 3.0f * sec * sec * s * 0.5f;
			float pz = cz + r * (float) Math.sin(theta) * speed * drag;
			float sz = (0.10f + 0.10f * rnd(e.seed, i * 4 + 3)) * s * (1f - 0.7f * t);
			basis(px, py, pz);
			if (e.glow) {
				diamond(px, py, pz, sz * 2.4f, sz * 2.4f, col(e.rgb, fade * 0.18f));
			}
			diamond(px, py, pz, sz, sz, col(c, fade * 0.95f));
		}
		if (t < 0.3f) {
			star4(cx, cy, cz, 1.4f * s * easeOut(t / 0.3f), col(hot, (1f - t / 0.3f) * 0.9f));
		}
	}

	private static void skull(KillEffectInstance e, float t, float cx, float cy, float cz) {
		float s = e.size;
		float env = t < 0.15f ? t / 0.15f : t > 0.5f ? (1f - t) / 0.5f : 1f;
		float y = cy + 0.5f * s * easeOut(t);
		float cell = 0.13f * s;
		basis(cx, y, cz);
		int c = col(mix(e.rgb, 0xFFFFFF, 0.25f), env);
		if (e.glow) {
			diamond(cx, y, cz, cell * 7f, cell * 7f, col(e.rgb, env * 0.14f));
		}
		for (int row = 0; row < 8; row++) {
			String line = SKULL[row];
			for (int col = 0; col < 8; col++) {
				if (line.charAt(col) != '#') {
					continue;
				}
				float x0 = (col - 4) * cell;
				float x1 = x0 + cell;
				float y0 = (4 - row) * cell;
				float y1 = y0 - cell;
				BUF.quad(cx + rX * x0 + uX * y0, y + rY * x0 + uY * y0, cz + rZ * x0 + uZ * y0, c,
						cx + rX * x1 + uX * y0, y + rY * x1 + uY * y0, cz + rZ * x1 + uZ * y0, c,
						cx + rX * x1 + uX * y1, y + rY * x1 + uY * y1, cz + rZ * x1 + uZ * y1, c,
						cx + rX * x0 + uX * y1, y + rY * x0 + uY * y1, cz + rZ * x0 + uZ * y1, c);
			}
		}
	}

	private static void starBurst(KillEffectInstance e, float t, float cx, float cy, float cz) {
		float s = e.size;
		float fade = (float) Math.pow(1f - t, 0.8);
		int core = mix(e.rgb, 0xFFFFFF, 0.7f);
		for (int i = 0; i < 14; i++) {
			float theta = rnd(e.seed, i * 3) * Mth.TWO_PI;
			float el = (rnd(e.seed, i * 3 + 1) * 2f - 1f) * 0.9f;
			float r = (float) Math.sqrt(Math.max(0f, 1f - el * el));
			float dx = r * (float) Math.cos(theta);
			float dz = r * (float) Math.sin(theta);
			float len = (1.6f + 1.4f * rnd(e.seed, i * 3 + 2)) * s;
			float head = len * easeOut(t / 0.55f);
			float tail = len * 0.9f * easeOut((t - 0.25f) / 0.75f);
			if (head - tail < 0.01f) {
				continue;
			}
			float w = 0.06f * s;
			float ax = cx + dx * tail;
			float ay = cy + el * tail;
			float az = cz + dz * tail;
			float bx = cx + dx * head;
			float by = cy + el * head;
			float bz = cz + dz * head;
			if (e.glow) {
				ribbon(ax, ay, az, w * 3f, col(e.rgb, fade * 0.2f), bx, by, bz, w * 1.2f, col(e.rgb, fade * 0.2f));
			}
			ribbon(ax, ay, az, w, col(e.rgb, fade * 0.9f), bx, by, bz, w * 0.2f, col(core, fade));
		}
		star4(cx, cy, cz, 0.9f * s * (0.4f + 0.6f * easeOut(t)), col(core, (1f - t) * (1f - t)));
	}

	private static void shockwave(KillEffectInstance e, float t, float ox, float oy, float oz) {
		float s = e.size;
		float y = oy + 0.06f;
		for (int k = 0; k < 2; k++) {
			float tk = Mth.clamp((t - 0.18f * k) / (1f - 0.18f * k), 0f, 1f);
			if (tk <= 0f || tk >= 1f) {
				continue;
			}
			float radius = (3.2f - 1.2f * k) * s * easeOut(tk);
			float thick = (0.35f - 0.1f * k) * s * (1f - 0.6f * tk);
			float a = (float) Math.pow(1f - tk, 1.3) * (1f - 0.25f * k);
			ring(ox, y, oz, radius, thick, e.rgb, a);
			if (e.glow) {
				ring(ox, y, oz, radius + thick * 0.6f, thick * 2.4f, e.rgb, a * 0.35f);
			}
		}
		star4(ox, y + 0.5f * s, oz, 0.8f * s * (1f - t), col(mix(e.rgb, 0xFFFFFF, 0.7f), (1f - t) * 0.8f));
	}

	/** Плоское кольцо: внешний край яркий, внутренний (хвост) почти прозрачный. */
	private static void ring(float cx, float y, float cz, float radius, float thick, int rgb, float a) {
		float ro = radius;
		float ri = Math.max(0f, radius - thick);
		int co = col(rgb, a);
		int ci = col(rgb, a * 0.1f);
		for (int i = 0; i < RING_SEGS; i++) {
			BUF.quad(cx + COS[i] * ri, y, cz + SIN[i] * ri, ci,
					cx + COS[i] * ro, y, cz + SIN[i] * ro, co,
					cx + COS[i + 1] * ro, y, cz + SIN[i + 1] * ro, co,
					cx + COS[i + 1] * ri, y, cz + SIN[i + 1] * ri, ci);
		}
	}
}
