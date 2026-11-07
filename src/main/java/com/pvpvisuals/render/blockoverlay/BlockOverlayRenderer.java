package com.pvpvisuals.render.blockoverlay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pvpvisuals.config.BlockOverlaySettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.render.blockoverlay.BlockOverlayModule.Frame;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Рисует оверлей шейдером прямо в главный render target в конце основного прохода мира (END_MAIN).
 *
 * <p>Геометрия — один статичный буфер: единичный куб, 6 граней = 12 треугольников (36 вершин, только Position).
 * Положение и размер блока приходят через uniform-блок, вершинный шейдер растягивает куб до формы блока, поэтому
 * за кадр пишется только 80 байт uniform; буферы и матрицы не создаются заново. Невидимые (обращённые от камеры)
 * грани отбрасывает фрагментный шейдер. Состояние GL (blend, depth, cull) задано самим RenderPipeline, а не
 * вызовами RenderSystem.enableBlend()/disableDepthTest(), которых в 1.21.11 нет.
 *
 * <p>Если шейдер не собрался или pass упал — один раз пишем ошибку в лог и дальше рисуем простую заливку граней
 * через RenderTypes.debugQuads (запасной вариант), ванильная обводка при этом не скрывается.
 */
public final class BlockOverlayRenderer {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/blockoverlay");
	private static final String NS = "pvpvisuals";
	private static final String UNIFORM_BLOCK = "EchoBlockData";
	private static final int UNIFORM_BYTES = 96; // шесть vec4 (std140)

	private static final Map<Integer, RenderPipeline> PIPELINES = new HashMap<>();
	private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
	private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f ZERO = new Vector3f();
	private static final Matrix4f IDENTITY = new Matrix4f();

	// грани в порядке: -X, +X, -Y, +Y, -Z, +Z; фрагментный шейдер определяет грань по gl_PrimitiveID / 2
	private static final float[][] FACES = {
			{0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
			{1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1},
			{0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
			{0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
			{0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0},
			{0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}};
	private static final int[] QUAD_TRIS = {0, 1, 2, 0, 2, 3};

	private static GpuBuffer vertexBuffer;
	private static GpuBuffer uniformBuffer;

	private static boolean failed;
	private static boolean loggedDraw;
	private static long lastNanos;
	private static double animTime;

	private BlockOverlayRenderer() { }

	/** true, пока шейдерный рендер работает (нужно, чтобы не прятать ванильную обводку при запасном варианте). */
	public static boolean works() {
		return !failed;
	}

	public static void reset() {
		failed = false;
		PIPELINES.clear();
	}

	private static RenderPipeline pipeline(BlockOverlayStyle style, boolean throughWalls) {
		int key = style.ordinal() * 2 + (throughWalls ? 1 : 0);
		return PIPELINES.computeIfAbsent(key, k -> RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/" + style.shader + (throughWalls ? "_xray" : "")))
				.withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/block_overlay"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/" + style.shader))
				.withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
				.withBlend(BlendFunction.TRANSLUCENT)
				// ПРОВЕРИТЬ: имя константы LEQUAL_DEPTH_TEST в DepthTestFunction (NO_DEPTH_TEST используется и в Shader Sky)
				.withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build());
	}

	private static void ensureBuffers(GpuDevice device) {
		if (vertexBuffer == null) {
			ByteBuffer v = ByteBuffer.allocateDirect(36 * 3 * Float.BYTES).order(ByteOrder.nativeOrder());
			for (float[] q : FACES) {
				for (int idx : QUAD_TRIS) {
					v.putFloat(q[idx * 3]).putFloat(q[idx * 3 + 1]).putFloat(q[idx * 3 + 2]);
				}
			}
			v.flip();
			vertexBuffer = device.createBuffer(() -> "Echo block overlay cube", GpuBuffer.USAGE_VERTEX, v);
		}
		if (uniformBuffer == null) {
			uniformBuffer = device.createBuffer(() -> "Echo block overlay uniforms",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
		}
	}

	public static void render(WorldRenderContext ctx) {
		Frame f = BlockOverlayModule.frame();
		if (!f.valid) {
			return;
		}
		BlockOverlaySettings s = PvpConfig.get().blockOverlay;
		if (failed) {
			fallback(ctx, f, s);
			return;
		}
		try {
			draw(f, s);
		} catch (Throwable e) {
			failed = true;
			LOG.error("Block Overlay shader failed, using the simple fill fallback until restart", e);
			fallback(ctx, f, s);
		}
	}

	private static void draw(Frame f, BlockOverlaySettings s) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.getMainRenderTarget();
		if (target == null) {
			return;
		}
		BlockOverlayStyle style = BlockOverlayStyle.byName(s.shaderType);

		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.0 : Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		animTime += dt * s.pulseSpeed; // накапливаем: ползунок скорости не «перематывает» анимацию

		GpuDevice device = RenderSystem.getDevice();
		ensureBuffers(device);

		float outset = style == BlockOverlayStyle.GLOW ? 0.16f : 0.002f;
		SCRATCH.clear();
		SCRATCH.putFloat(f.offX).putFloat(f.offY).putFloat(f.offZ).putFloat(outset);
		SCRATCH.putFloat(f.minX).putFloat(f.minY).putFloat(f.minZ).putFloat((float) animTime);
		SCRATCH.putFloat(f.maxX).putFloat(f.maxY).putFloat(f.maxZ).putFloat(0f);
		SCRATCH.putFloat(((s.color >> 16) & 0xFF) / 255f).putFloat(((s.color >> 8) & 0xFF) / 255f)
				.putFloat((s.color & 0xFF) / 255f).putFloat(Math.max(0.05f, Math.min(1f, s.alpha)));
		float quality = "LOW".equalsIgnoreCase(s.renderQuality) ? 0f : "HIGH".equalsIgnoreCase(s.renderQuality) ? 2f : 1f;
		SCRATCH.putFloat(Math.max(0.5f, Math.min(8f, s.lineThickness))).putFloat(s.outlineOnly ? 1f : 0f)
				.putFloat(quality).putFloat(Math.max(0.2f, Math.min(3f, s.starDensity)));
		SCRATCH.putFloat(((s.colorSecondary >> 16) & 0xFF) / 255f).putFloat(((s.colorSecondary >> 8) & 0xFF) / 255f)
				.putFloat((s.colorSecondary & 0xFF) / 255f).putFloat(0f);
		SCRATCH.flip();

		CommandEncoder encoder = device.createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), SCRATCH);
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(f.view, WHITE, ZERO, IDENTITY);

		try (RenderPass pass = encoder.createRenderPass(() -> "Echo block overlay", target.getColorTextureView(),
				OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline(style, s.showThroughWalls));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setUniform(UNIFORM_BLOCK, uniformBuffer.slice());
			pass.setVertexBuffer(0, vertexBuffer);
			pass.draw(0, 36);
		}
		if (!loggedDraw) {
			loggedDraw = true;
			LOG.info("Block Overlay: first frame drawn with shader {}", style.shader);
		}
	}

	// ---------- запасной вариант: простая заливка граней без своего шейдера ----------

	private static void fallback(WorldRenderContext ctx, Frame f, BlockOverlaySettings s) {
		try {
			Matrix4f m = ctx.matrices().last().pose();
			// ПРОВЕРИТЬ: RenderTypes.debugQuads() (то же имя, что в ChinaHatRenderer)
			VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugQuads());
			int a = Math.round(255f * Math.max(0.05f, Math.min(1f, s.alpha)) * 0.6f);
			int argb = (a << 24) | (s.color & 0xFFFFFF);
			float e = 0.002f;
			float x0 = f.offX + f.minX - e, x1 = f.offX + f.maxX + e;
			float y0 = f.offY + f.minY - e, y1 = f.offY + f.maxY + e;
			float z0 = f.offZ + f.minZ - e, z1 = f.offZ + f.maxZ + e;
			float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1},
					{x1, y1, z1}, {x0, y1, z1}};
			int[][] quads = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {3, 2, 6, 7}, {4, 5, 1, 0}};
			for (int[] q : quads) {
				for (int i : q) {
					vc.addVertex(m, c[i][0], c[i][1], c[i][2]).setColor(argb);
				}
			}
		} catch (Throwable ignored) {
			// даже запасной вариант не должен ронять рендер
		}
	}
}
