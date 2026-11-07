package com.pvpvisuals.render.trajectory;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.render.Projection;
import com.pvpvisuals.render.trajectory.TrajectoryModule.Frame;
import com.pvpvisuals.render.trajectory.TrajectorySimulator.Result;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Рисование траекторий. Мир (WorldRenderEvents.END_MAIN): линия лентами, повёрнутыми к камере, маркеры-диски,
 * подсветка грани и хитбокса. Текст у конечной точки — в HUD-слое через экранную проекцию.
 */
public final class TrajectoryRenderer {
	private TrajectoryRenderer() { }

	// камера (billboard) на кадр
	private static double lx;
	private static double lz;
	private static double ux;
	private static double uy;
	private static double uz;

	public static void render(WorldRenderContext ctx) {
		PvpConfig pc = PvpConfig.get();
		Frame f = TrajectoryModule.frame();
		if (!pc.trajectory || f.results.isEmpty()) {
			return;
		}
		TrajectoryConfig c = pc.traj;

		Matrix4f m = ctx.matrices().last().pose();
		// ПРОВЕРИТЬ: RenderTypes.debugQuads() (см. WorldParticlesRenderer). Сквозь стены и additive-glow
		// эта версия не рисует: нужен свой RenderPipeline (NO_DEPTH_TEST / BlendFunction.ADDITIVE),
		// пример — Fabric docs "Rendering in the World".
		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());

		double yr = Math.toRadians(f.yaw);
		double pr = Math.toRadians(f.pitch);
		double fx = -Math.sin(yr) * Math.cos(pr);
		double fy = -Math.sin(pr);
		double fz = Math.cos(yr) * Math.cos(pr);
		lx = Math.cos(yr);
		lz = Math.sin(yr);
		ux = fy * lz;
		uy = fz * lx - fx * lz;
		uz = -fy * lx;

		Vec3 cam = new Vec3(f.camX, f.camY, f.camZ);
		for (Result r : f.results) {
			drawResult(vc, m, r, c, cam);
		}
	}

	private static void drawResult(VertexConsumer vc, Matrix4f m, Result r, TrajectoryConfig c, Vec3 cam) {
		TrajectoryStyle style = TrajectoryStyle.byName(c.style);
		List<Vec3> pts = r.points;
		int n = pts.size();
		double w = c.width;

		// --- линия ---
		for (int i = 0; i < n - 1; i++) {
			float t = i / (float) Math.max(1, n - 1);
			int rgb = style == TrajectoryStyle.GRADIENT ? lerp(c.colorStart, c.colorEnd, t) : c.colorStart;
			int alpha = (int) (230 * (c.fade ? 1.0 - 0.75 * t : 1.0));
			Vec3 a = pts.get(i).subtract(cam);
			Vec3 b = pts.get(i + 1).subtract(cam);

			switch (style) {
				case DOTTED -> {
					if (i % c.detail == 0) {
						disc(vc, m, a, (float) (w * 1.1), rgb, alpha, 0.6f);
					}
				}
				case DASHED -> {
					if ((i / c.detail) % 2 == 0) {
						ribbon(vc, m, a, b, w, argb(rgb, alpha));
					}
				}
				default -> ribbon(vc, m, a, b, w, argb(rgb, alpha));
			}
			if (c.glow && style != TrajectoryStyle.DOTTED) {
				// псевдо-свечение: широкая полупрозрачная лента вокруг линии
				if (style != TrajectoryStyle.DASHED || (i / c.detail) % 2 == 0) {
					ribbon(vc, m, a, b, w * 3.0, argb(rgb, alpha / 6));
				}
			}
		}

		// --- маркер старта ---
		if (c.showStart && !r.tickPoints.isEmpty()) {
			disc(vc, m, r.tickPoints.get(0).subtract(cam), 0.07f, c.colorStart, 220, 0.5f);
		}

		// --- прогресс-точки ---
		if (c.showProgress) {
			int step = Math.max(1, c.progressInterval);
			for (int k = step - 1; k < r.tickPoints.size() - 1; k += step) {
				disc(vc, m, r.tickPoints.get(k).subtract(cam), (float) Math.max(0.04, w * 1.6), 0xFFFFFF, 210, 0.5f);
			}
		}

		// --- пик ---
		if (c.showPeak && r.peak != null) {
			disc(vc, m, r.peak.subtract(cam), 0.09f, 0x55CCFF, 230, 0.5f);
		}

		// --- падение / столкновение ---
		ImpactMarker im = r.impact;
		Vec3 end = r.end.subtract(cam);
		float s = c.landingSize;

		if (im.type() == ImpactType.BLOCK && c.showCollision) {
			disc(vc, m, end, s * 1.6f, c.collisionColor, 150, 0.35f);
			if (c.faceHighlight && im.block() != null && im.face() != null) {
				faceHighlight(vc, m, im, cam, c.collisionColor);
			}
		} else if (im.type() == ImpactType.ENTITY && c.showCollision) {
			disc(vc, m, end, s * 1.6f, 0xFF9A20, 170, 0.35f);
			if (im.box() != null) {
				boxOutline(vc, m, im.box().move(-cam.x, -cam.y, -cam.z), 0xFFB030);
			}
		}
		if (c.showLanding) {
			disc(vc, m, end, s, im.type() == ImpactType.ENTITY ? 0xFFD060 : c.colorEnd, 235, 0.55f);
			disc(vc, m, end, s * 0.45f, 0xFFFFFF, 245, 0.9f);

			if (c.dropLine && r.ground != null) {
				Vec3 g = r.ground.subtract(cam);
				double len = end.y - g.y;
				for (double d = 0; d < len - 0.05; d += 0.4) {
					Vec3 a = new Vec3(end.x, end.y - d, end.z);
					Vec3 b = new Vec3(end.x, Math.max(g.y, end.y - d - 0.2), end.z);
					ribbon(vc, m, a, b, 0.015, argb(0xFFFFFF, 170));
				}
				disc(vc, m, g.add(0, 0.02, 0), s * 0.6f, c.colorEnd, 130, 0.4f);
			}
		}
	}

	// ---------- примитивы ----------

	private static int argb(int rgb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, int argb) {
		vc.addVertex(m, (float) x, (float) y, (float) z).setColor(argb);
	}

	/** Лента между двумя точками (координаты относительно камеры), повёрнутая к камере. */
	private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double width, int argb) {
		if (((argb >>> 24) & 0xFF) == 0) {
			return;
		}
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double mx = (a.x + b.x) * 0.5;
		double my = (a.y + b.y) * 0.5;
		double mz = (a.z + b.z) * 0.5;
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
		v(vc, m, a.x - sx, a.y - sy, a.z - sz, argb);
		v(vc, m, a.x + sx, a.y + sy, a.z + sz, argb);
		v(vc, m, b.x + sx, b.y + sy, b.z + sz, argb);
		v(vc, m, b.x - sx, b.y - sy, b.z - sz, argb);
	}

	/** Круглый billboard-диск: в центре alpha, к краю alpha * rim (мягкий край). */
	private static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, float size, int rgb, int alpha, float rim) {
		final int seg = 14;
		int center = argb(rgb, alpha);
		int edge = argb(rgb, (int) (alpha * rim));
		double px = 0;
		double py = 0;
		for (int i = 0; i <= seg; i++) {
			double ang = i * Math.PI * 2 / seg;
			double ax = Math.cos(ang) * size;
			double ay = Math.sin(ang) * size;
			if (i > 0) {
				v(vc, m, c.x, c.y, c.z, center);
				v(vc, m, c.x + lx * px + ux * py, c.y + uy * py, c.z + lz * px + uz * py, edge);
				v(vc, m, c.x + lx * ax + ux * ay, c.y + uy * ay, c.z + lz * ax + uz * ay, edge);
				v(vc, m, c.x + lx * ax + ux * ay, c.y + uy * ay, c.z + lz * ax + uz * ay, edge);
			}
			px = ax;
			py = ay;
		}
	}

	/** Подсветка грани блока: полупрозрачная заливка и контур. */
	private static void faceHighlight(VertexConsumer vc, Matrix4f m, ImpactMarker im, Vec3 cam, int rgb) {
		Direction d = im.face();
		double x0 = im.block().getX() - cam.x;
		double y0 = im.block().getY() - cam.y;
		double z0 = im.block().getZ() - cam.z;
		double e = 0.004;
		Vec3 p1;
		Vec3 p2;
		Vec3 p3;
		Vec3 p4;
		switch (d.getAxis()) {
			case X -> {
				double x = x0 + (d.getStepX() > 0 ? 1 + e : -e);
				p1 = new Vec3(x, y0, z0);
				p2 = new Vec3(x, y0 + 1, z0);
				p3 = new Vec3(x, y0 + 1, z0 + 1);
				p4 = new Vec3(x, y0, z0 + 1);
			}
			case Y -> {
				double y = y0 + (d.getStepY() > 0 ? 1 + e : -e);
				p1 = new Vec3(x0, y, z0);
				p2 = new Vec3(x0 + 1, y, z0);
				p3 = new Vec3(x0 + 1, y, z0 + 1);
				p4 = new Vec3(x0, y, z0 + 1);
			}
			default -> {
				double z = z0 + (d.getStepZ() > 0 ? 1 + e : -e);
				p1 = new Vec3(x0, y0, z);
				p2 = new Vec3(x0 + 1, y0, z);
				p3 = new Vec3(x0 + 1, y0 + 1, z);
				p4 = new Vec3(x0, y0 + 1, z);
			}
		}
		int fill = argb(rgb, 85);
		v(vc, m, p1.x, p1.y, p1.z, fill);
		v(vc, m, p2.x, p2.y, p2.z, fill);
		v(vc, m, p3.x, p3.y, p3.z, fill);
		v(vc, m, p4.x, p4.y, p4.z, fill);
		int line = argb(rgb, 230);
		ribbon(vc, m, p1, p2, 0.02, line);
		ribbon(vc, m, p2, p3, 0.02, line);
		ribbon(vc, m, p3, p4, 0.02, line);
		ribbon(vc, m, p4, p1, 0.02, line);
	}

	/** Рёбра хитбокса (координаты уже относительно камеры). */
	private static void boxOutline(VertexConsumer vc, Matrix4f m, AABB b, int rgb) {
		int col = argb(rgb, 235);
		double w = 0.02;
		Vec3[] p = {
				new Vec3(b.minX, b.minY, b.minZ), new Vec3(b.maxX, b.minY, b.minZ),
				new Vec3(b.maxX, b.minY, b.maxZ), new Vec3(b.minX, b.minY, b.maxZ),
				new Vec3(b.minX, b.maxY, b.minZ), new Vec3(b.maxX, b.maxY, b.minZ),
				new Vec3(b.maxX, b.maxY, b.maxZ), new Vec3(b.minX, b.maxY, b.maxZ)};
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] e : edges) {
			ribbon(vc, m, p[e[0]], p[e[1]], w, col);
		}
	}

	// ---------- текст у конечной точки (HUD-слой) ----------

	public static void renderHud(GuiGraphics g, Minecraft mc, float pt) {
		PvpConfig pc = PvpConfig.get();
		Frame f = TrajectoryModule.frame();
		if (!pc.trajectory || f.results.isEmpty() || mc.player == null) {
			return;
		}
		TrajectoryConfig c = pc.traj;
		Font font = mc.font;

		for (Result r : f.results) {
			String[] lines = new String[3];
			int n = 0;
			if (c.showDistance) {
				lines[n++] = String.format(java.util.Locale.ROOT, "%.1f%s", r.distance, c.unitsMeters ? "m" : " blocks");
			}
			if (c.showTime) {
				lines[n++] = c.timeSeconds
						? String.format(java.util.Locale.ROOT, "%.1fs", r.ticks / 20.0)
						: r.ticks + " ticks";
			}
			if (c.showDamage && r.damageMin >= 0) {
				lines[n++] = r.damageMin == r.damageMax ? "~" + r.damageMin + " dmg" : "~" + r.damageMin + "-" + r.damageMax + " dmg";
			}
			if (n == 0) {
				continue;
			}
			double[] s = Projection.project(mc, r.end, pt, g.guiWidth(), g.guiHeight());
			if (s == null) {
				continue;
			}

			int w = 0;
			for (int i = 0; i < n; i++) {
				w = Math.max(w, font.width(lines[i]));
			}
			int x = (int) Math.round(s[0]) + 10;
			int y = (int) Math.round(s[1]) - n * 5 - 2;
			RoundedRender.fill(g, x - 3, y - 3, w + 6, n * 10 + 5, 3, 0xA0101010);
			for (int i = 0; i < n; i++) {
				g.drawString(font, lines[i], x, y + i * 10, i == 0 ? 0xFFFFFFFF : 0xFFE8C87A, true);
			}
		}
	}
}
