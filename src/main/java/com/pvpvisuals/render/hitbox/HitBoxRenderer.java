package com.pvpvisuals.render.hitbox;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pvpvisuals.config.HitBoxSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.render.hitbox.HitBoxModule.Frame;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Рисует хитбоксы своим RenderPipeline в конце основного прохода мира (END_MAIN).
 *
 * <p>Линии — это повёрнутые к камере полосы (по два треугольника), ширина пересчитывается из пикселей экрана в
 * мир по расстоянию до середины ребра, поэтому толщина одинакова в пикселях с любого ракурса и расстояния
 * (RenderSystem.lineWidth() в 1.21.11 не используется). Все вершины пишутся в один заранее созданный буфер.
 * Сквозь стены: отдельный pipeline без depth-теста. Свои шейдеры: core/hitbox.vsh и core/hitbox.fsh.
 */
public final class HitBoxRenderer {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/hitbox");
	private static final String NS = "pvpvisuals";
	private static final int VERTEX_BYTES = 16; // Position (3 float) + Color (4 byte)
	private static final int MAX_SEGMENTS = HitBoxModule.MAX_BOXES * 24 * 2; // углы: 24 отрезка, x2 с glow
	private static final int BUFFER_BYTES = MAX_SEGMENTS * 6 * VERTEX_BYTES;
	/** Доля длины ребра, которую занимает отрезок угла в режиме «только углы». */
	private static final float CORNER_FRACTION = 0.28f;

	private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());
	private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f ZERO = new Vector3f();
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];

	private static GpuBuffer vertexBuffer;
	private static boolean failed;
	private static boolean loggedDraw;
	private static int vertexCount;

	private HitBoxRenderer() { }

	public static void reset() {
		failed = false;
		PIPELINES[0] = null;
		PIPELINES[1] = null;
	}

	private static RenderPipeline pipeline(boolean throughWalls) {
		int i = throughWalls ? 1 : 0;
		if (PIPELINES[i] == null) {
			PIPELINES[i] = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/hitbox" + (throughWalls ? "_xray" : "")))
					.withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/hitbox"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/hitbox"))
					.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
					.withBlend(BlendFunction.TRANSLUCENT)
					// ПРОВЕРИТЬ: имя LEQUAL_DEPTH_TEST (то же, что в BlockOverlayRenderer)
					.withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
					.withDepthWrite(false)
					.withCull(false)
					.build();
		}
		return PIPELINES[i];
	}

	public static void render(WorldRenderContext ctx) {
		Frame f = HitBoxModule.frame();
		if (f.count == 0 || failed) {
			return;
		}
		try {
			draw(f, PvpConfig.get().hitBox);
		} catch (Throwable e) {
			failed = true;
			LOG.error("HitBox renderer failed and was disabled until the module is toggled again", e);
		}
	}

	private static void draw(Frame f, HitBoxSettings s) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.getMainRenderTarget();
		if (target == null) {
			return;
		}
		// пиксели -> блоки на единицу расстояния: высота экрана в мире на расстоянии d = 2 * d * tan(fov / 2)
		float fov = (float) Math.toRadians(mc.options.fov().get());
		float worldPerPixelPerUnit = target.height > 0 ? (float) (2.0 * Math.tan(fov / 2.0) / target.height) : 0.001f;

		DATA.clear();
		vertexCount = 0;
		int br = (s.baseColor >> 16) & 0xFF, bg = (s.baseColor >> 8) & 0xFF, bb = s.baseColor & 0xFF;
		int tr = (s.targetColor >> 16) & 0xFF, tg = (s.targetColor >> 8) & 0xFF, tb = s.targetColor & 0xFF;
		float thickness = Math.max(0.5f, Math.min(4f, s.lineThickness));

		for (int i = 0; i < f.count; i++) {
			int o = i * HitBoxModule.STRIDE;
			float p = Math.max(0f, Math.min(1f, f.boxes[o + 6]));
			int r = Math.round(br + (tr - br) * p);
			int g = Math.round(bg + (tg - bg) * p);
			int b = Math.round(bb + (tb - bb) * p);
			float alpha = s.baseAlpha + (s.targetAlpha - s.baseAlpha) * p;
			int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
			float x0 = f.boxes[o], y0 = f.boxes[o + 1], z0 = f.boxes[o + 2];
			float x1 = f.boxes[o + 3], y1 = f.boxes[o + 4], z1 = f.boxes[o + 5];
			if (s.glow) {
				box(x0, y0, z0, x1, y1, z1, s.cornerOnly, thickness * 4f, worldPerPixelPerUnit, r, g, b, Math.round(a * 0.2f));
			}
			box(x0, y0, z0, x1, y1, z1, s.cornerOnly, thickness, worldPerPixelPerUnit, r, g, b, a);
		}
		if (vertexCount == 0) {
			return;
		}
		DATA.flip();

		GpuDevice device = RenderSystem.getDevice();
		if (vertexBuffer == null) {
			vertexBuffer = device.createBuffer(() -> "Echo hitbox vertices",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
		}
		CommandEncoder encoder = device.createCommandEncoder();
		// ПРОВЕРИТЬ: GpuBuffer#slice(int offset, int length) в 1.21.11 (в Shader Sky используется slice() без аргументов)
		encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(f.view, WHITE, ZERO, IDENTITY);

		try (RenderPass pass = encoder.createRenderPass(() -> "Echo hitbox", target.getColorTextureView(),
				OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline(s.throughWalls));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertexBuffer);
			pass.draw(0, vertexCount);
		}
		if (!loggedDraw) {
			loggedDraw = true;
			LOG.info("HitBox: first frame drawn ({} boxes, {} vertices)", f.count, vertexCount);
		}
	}

	// ---------- геометрия ----------

	private static void box(float x0, float y0, float z0, float x1, float y1, float z1, boolean corners, float px,
			float wpp, int r, int g, int b, int a) {
		if (a <= 0) {
			return;
		}
		// 12 рёбер: 4 по X, 4 по Y, 4 по Z
		for (int iy = 0; iy < 2; iy++) {
			float y = iy == 0 ? y0 : y1;
			for (int iz = 0; iz < 2; iz++) {
				float z = iz == 0 ? z0 : z1;
				edge(x0, y, z, x1, y, z, corners, px, wpp, r, g, b, a);
			}
		}
		for (int ix = 0; ix < 2; ix++) {
			float x = ix == 0 ? x0 : x1;
			for (int iz = 0; iz < 2; iz++) {
				float z = iz == 0 ? z0 : z1;
				edge(x, y0, z, x, y1, z, corners, px, wpp, r, g, b, a);
			}
		}
		for (int ix = 0; ix < 2; ix++) {
			float x = ix == 0 ? x0 : x1;
			for (int iy = 0; iy < 2; iy++) {
				float y = iy == 0 ? y0 : y1;
				edge(x, y, z0, x, y, z1, corners, px, wpp, r, g, b, a);
			}
		}
	}

	/** Ребро целиком или два коротких отрезка у его концов (режим «только углы»). */
	private static void edge(float ax, float ay, float az, float bx, float by, float bz, boolean corners, float px,
			float wpp, int r, int g, int b, int a) {
		if (!corners) {
			segment(ax, ay, az, bx, by, bz, px, wpp, r, g, b, a);
			return;
		}
		float k = CORNER_FRACTION;
		segment(ax, ay, az, ax + (bx - ax) * k, ay + (by - ay) * k, az + (bz - az) * k, px, wpp, r, g, b, a);
		segment(bx, by, bz, bx + (ax - bx) * k, by + (ay - by) * k, bz + (az - bz) * k, px, wpp, r, g, b, a);
	}

	/** Полоса вдоль отрезка, повёрнутая к камере (камера в начале координат); ширина = px пикселей на любом расстоянии. */
	private static void segment(float x1, float y1, float z1, float x2, float y2, float z2, float px, float wpp, int r,
			int g, int b, int a) {
		if (DATA.remaining() < 6 * VERTEX_BYTES) {
			return;
		}
		float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
		float mx = (x1 + x2) * 0.5f, my = (y1 + y2) * 0.5f, mz = (z1 + z2) * 0.5f;
		// side = dir x view
		float sx = dy * mz - dz * my;
		float sy = dz * mx - dx * mz;
		float sz = dx * my - dy * mx;
		float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1e-9f) {
			return;
		}
		float dist = (float) Math.sqrt(mx * mx + my * my + mz * mz);
		float k = px * wpp * dist * 0.5f / len;
		sx *= k;
		sy *= k;
		sz *= k;
		vert(x1 - sx, y1 - sy, z1 - sz, r, g, b, a);
		vert(x1 + sx, y1 + sy, z1 + sz, r, g, b, a);
		vert(x2 + sx, y2 + sy, z2 + sz, r, g, b, a);
		vert(x1 - sx, y1 - sy, z1 - sz, r, g, b, a);
		vert(x2 + sx, y2 + sy, z2 + sz, r, g, b, a);
		vert(x2 - sx, y2 - sy, z2 - sz, r, g, b, a);
	}

	private static void vert(float x, float y, float z, int r, int g, int b, int a) {
		DATA.putFloat(x).putFloat(y).putFloat(z);
		DATA.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
		vertexCount++;
	}
}
