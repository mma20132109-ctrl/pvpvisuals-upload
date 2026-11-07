package com.pvpvisuals.render.particle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pvpvisuals.render.particle.ParticleTextureRegistry.Shape;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Хранит живые частицы, двигает их раз в тик и рисует billboard-квадами.
 * Симуляция — END_CLIENT_TICK, extraction — WorldRenderEvents.END_EXTRACTION (интерполяция и перевод в
 * координаты камеры), rendering — WorldRenderEvents.END_MAIN (только вершины по готовому снимку).
 */
public final class ParticleRenderer {
	public static final int MAX_PARTICLES = 500;
	public static final double CULL_DISTANCE = 32.0;

	/** Готовая к рисованию частица (координаты относительно камеры). */
	private static final class Item {
		float x;
		float y;
		float z;
		float size;
		float rot;
		int rgb;
		int alpha;
		ParticleStyle style;
	}

	private static final List<Particle> PARTICLES = new ArrayList<>();
	private static final List<Item> ITEMS = new ArrayList<>();
	private static float yaw;
	private static float pitch;

	private ParticleRenderer() { }

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ParticleRenderer::tick);
		WorldRenderEvents.END_EXTRACTION.register(ParticleRenderer::extract);
		WorldRenderEvents.END_MAIN.register(ParticleRenderer::render);
	}

	public static void add(Particle p) {
		if (PARTICLES.size() >= MAX_PARTICLES) {
			PARTICLES.remove(0);
		}
		PARTICLES.add(p);
	}

	public static void clear() {
		PARTICLES.clear();
		ITEMS.clear();
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}
		Vec3 me = mc.player.position();
		double maxSq = CULL_DISTANCE * CULL_DISTANCE;

		Iterator<Particle> it = PARTICLES.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.px = p.x;
			p.py = p.y;
			p.pz = p.z;

			p.vx *= p.friction;
			p.vy *= p.friction;
			p.vz *= p.friction;
			p.vy -= p.gravity * 0.03;
			p.x += p.vx;
			p.y += p.vy;
			p.z += p.vz;
			p.age++;

			double dx = p.x - me.x;
			double dy = p.y - me.y;
			double dz = p.z - me.z;
			if (p.age >= p.life || dx * dx + dy * dy + dz * dz > maxSq) {
				it.remove();
			}
		}
	}

	private static void extract(WorldExtractionContext context) {
		ITEMS.clear();
		Minecraft mc = Minecraft.getInstance();
		if (PARTICLES.isEmpty() || mc.player == null || mc.level == null) {
			return;
		}
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.worldState().cameraRenderState.pos;
		yaw = mc.player.getViewYRot(pt);
		pitch = mc.player.getViewXRot(pt);

		for (Particle p : PARTICLES) {
			float t = Math.min(1f, (p.age + pt) / p.life);
			Item it = new Item();
			it.x = (float) (p.px + (p.x - p.px) * pt - cam.x);
			it.y = (float) (p.py + (p.y - p.py) * pt - cam.y);
			it.z = (float) (p.pz + (p.z - p.pz) * pt - cam.z);
			it.size = Math.max(0f, p.size * (1f + (p.sizeEndMul - 1f) * t));
			it.rot = p.rot;
			it.rgb = lerpRgb(p.rgbStart, p.rgbEnd, t);
			float fade = (1f - t);
			it.alpha = Math.max(0, Math.min(255, (int) (p.alpha * fade * Math.sqrt(fade))));
			it.style = p.style;
			ITEMS.add(it);
		}
	}

	private static int lerpRgb(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static void render(WorldRenderContext ctx) {
		if (ITEMS.isEmpty()) {
			return;
		}
		Matrix4f m = ctx.matrices().last().pose();
		// ПРОВЕРИТЬ: RenderTypes.debugQuads() (POSITION_COLOR, QUADS, translucent). Аддитивного смешивания
		// в готовых типах нет: для настоящего glow нужен свой RenderPipeline (см. Fabric docs,
		// "Rendering in the World") с BlendFunction.ADDITIVE.
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());

		// Базис камеры (billboard): left и up по yaw/pitch.
		double yr = Math.toRadians(yaw);
		double pr = Math.toRadians(pitch);
		double fx = -Math.sin(yr) * Math.cos(pr);
		double fy = -Math.sin(pr);
		double fz = Math.cos(yr) * Math.cos(pr);
		double lx = Math.cos(yr);
		double lz = Math.sin(yr);
		double ux = fy * lz;
		double uy = fz * lx - fx * lz;
		double uz = -fy * lx;

		for (Item it : ITEMS) {
			if (it.alpha > 0 && it.size > 0f) {
				draw(vc, m, it, lx, lz, ux, uy, uz);
			}
		}
	}

	private static void draw(VertexConsumer vc, Matrix4f m, Item it, double lx, double lz, double ux, double uy, double uz) {
		Shape sh = ParticleTextureRegistry.get(it.style);
		int n = sh.n();
		float cr = (float) Math.cos(it.rot);
		float sr = (float) Math.sin(it.rot);

		float[] ox = new float[n];
		float[] oy = new float[n];
		float[] oz = new float[n];
		for (int i = 0; i < n; i++) {
			float sx = (sh.x[i] * cr - sh.y[i] * sr) * it.size;
			float sy = (sh.x[i] * sr + sh.y[i] * cr) * it.size;
			ox[i] = it.x + (float) (lx * sx + ux * sy);
			oy[i] = it.y + (float) (uy * sy);
			oz[i] = it.z + (float) (lz * sx + uz * sy);
		}

		int center = argb(it, 1f);
		if (sh.inner > 0f) {
			float[] ix = new float[n];
			float[] iy = new float[n];
			float[] iz = new float[n];
			for (int i = 0; i < n; i++) {
				ix[i] = it.x + (ox[i] - it.x) * sh.inner;
				iy[i] = it.y + (oy[i] - it.y) * sh.inner;
				iz[i] = it.z + (oz[i] - it.z) * sh.inner;
			}
			int ic = argb(it, sh.innerAlpha);
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				v(vc, m, it.x, it.y, it.z, center);
				v(vc, m, ix[i], iy[i], iz[i], ic);
				v(vc, m, ix[j], iy[j], iz[j], ic);
				v(vc, m, ix[j], iy[j], iz[j], ic);

				v(vc, m, ix[i], iy[i], iz[i], ic);
				v(vc, m, ox[i], oy[i], oz[i], argb(it, sh.a[i]));
				v(vc, m, ox[j], oy[j], oz[j], argb(it, sh.a[j]));
				v(vc, m, ix[j], iy[j], iz[j], ic);
			}
		} else {
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				int cj = argb(it, sh.a[j]);
				v(vc, m, it.x, it.y, it.z, center);
				v(vc, m, ox[i], oy[i], oz[i], argb(it, sh.a[i]));
				v(vc, m, ox[j], oy[j], oz[j], cj);
				v(vc, m, ox[j], oy[j], oz[j], cj);
			}
		}
	}

	private static int argb(Item it, float factor) {
		int a = Math.max(0, Math.min(255, (int) (it.alpha * factor)));
		return (a << 24) | it.rgb;
	}

	private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
		vc.addVertex(m, x, y, z).setColor(argb);
	}
}
