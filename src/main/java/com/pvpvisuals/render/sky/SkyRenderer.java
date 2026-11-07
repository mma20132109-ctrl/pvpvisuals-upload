package com.pvpvisuals.render.sky;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.ShaderSkySettings;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

/**
 * Рисует небо шейдером: один полноэкранный треугольник, без depth-теста и записи глубины, прямо в главный
 * render target в момент, когда ваниль рисует небесный диск. Направление луча восстанавливается в вершинном
 * шейдере из матрицы, в которой оставлено только вращение камеры, поэтому небо следует за взглядом и не
 * двигается при перемещении игрока (и при покачивании камеры при ходьбе).
 *
 * <p>В 1.21.11 нет ShaderInstance/JSON-шейдеров: шейдер = RenderPipeline + файлы .vsh/.fsh. Состояние GL
 * (depth/cull/blend) задаётся самим pipeline, а не вызовами RenderSystem.disableDepthTest() и т.п.
 */
public final class SkyRenderer {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/sky");
	private static final String NS = "pvpvisuals";
	private static final String UNIFORM_BLOCK = "EchoSkyData";
	private static final int UNIFORM_BYTES = 64; // четыре vec4 (std140)

	private static final Map<ShaderType, RenderPipeline> PIPELINES = new EnumMap<>(ShaderType.class);
	private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
	private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f ZERO = new Vector3f();
	private static final Matrix4f IDENTITY = new Matrix4f();

	private static GpuBuffer vertexBuffer;
	private static GpuBuffer uniformBuffer;

	private static boolean failed;
	private static boolean loggedCapture;
	private static boolean loggedDraw;
	private static long lastNanos;
	private static double animTime;

	/** Заполняется миксином каждый кадр: высота солнца -1..1 и измерение (0 — обычный, 1 — Незер, 2 — Энд). */
	private static volatile float sunHeight = -1f;
	private static volatile int dimension;

	private SkyRenderer() { }

	/** Вызывается из миксина (extractRenderState). sunAngleRad — угол солнца в радианах (0 — полдень). */
	public static void capture(ClientLevel level, float sunAngleRad) {
		if (!loggedCapture) {
			loggedCapture = true;
			LOG.info("Shader Sky: extractRenderState hook works (sunAngle={})", sunAngleRad);
		}
		sunHeight = (float) Math.cos(sunAngleRad);
		var key = level.dimension();
		dimension = key == Level.NETHER ? 1 : key == Level.END ? 2 : 0;
	}

	public static boolean isFailed() {
		return failed;
	}

	/** Сброс после ошибки (например, при выключении и повторном включении модуля). */
	public static void reset() {
		failed = false;
		PIPELINES.clear();
	}

	private static RenderPipeline pipeline(ShaderType type) {
		return PIPELINES.computeIfAbsent(type, t -> RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/" + t.shader))
				.withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/echo_sky"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/" + t.shader))
				.withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withDepthWrite(false)
				.withCull(false)
				.build());
	}

	private static void ensureBuffers(GpuDevice device) {
		if (vertexBuffer == null) {
			ByteBuffer v = ByteBuffer.allocateDirect(3 * 3 * Float.BYTES).order(ByteOrder.nativeOrder());
			// один большой треугольник, накрывающий весь экран
			v.putFloat(-1f).putFloat(-1f).putFloat(0f);
			v.putFloat(3f).putFloat(-1f).putFloat(0f);
			v.putFloat(-1f).putFloat(3f).putFloat(0f);
			v.flip();
			vertexBuffer = device.createBuffer(() -> "Echo shader sky triangle", GpuBuffer.USAGE_VERTEX, v);
		}
		if (uniformBuffer == null) {
			uniformBuffer = device.createBuffer(() -> "Echo shader sky uniforms",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
		}
	}

	/** Рисует небо. Безопасно вызывать из миксина: любая ошибка отключает модуль до перезапуска. */
	public static void draw() {
		if (!loggedDraw) {
			LOG.info("Shader Sky: draw() called by the sky mixin");
		}
		if (failed) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ShaderSkySettings s = PvpConfig.get().shaderSky;
		try {
			RenderTarget target = mc.getMainRenderTarget();
			if (target == null || mc.gameRenderer == null) {
				return;
			}
			Camera camera = mc.gameRenderer.getMainCamera();
			if (camera == null) {
				return;
			}
			long now = System.nanoTime();
			double dt = lastNanos == 0 ? 0.0 : Math.min(0.1, (now - lastNanos) / 1e9);
			lastNanos = now;
			animTime += dt * s.speed; // накапливаем, чтобы ползунок скорости не «перематывал» анимацию

			GpuDevice device = RenderSystem.getDevice();
			ensureBuffers(device);

			SCRATCH.clear();
			SCRATCH.putFloat((float) animTime).putFloat(s.brightness).putFloat(sunHeight).putFloat(dimension);
			SCRATCH.putFloat(s.timeOfDayLock ? 1f : 0f).putFloat(0f).putFloat(0f).putFloat(0f);
			// собственная проекция неба: FOV из настроек (вертикальный) и пропорции главного буфера кадра
			float fov = (float) Math.toRadians(mc.options.fov().get());
			float aspect = target.height > 0 ? (float) target.width / (float) target.height : 16f / 9f;
			SCRATCH.putFloat((float) Math.tan(fov / 2.0)).putFloat(aspect).putFloat(0f).putFloat(0f);
			SCRATCH.putFloat(s.colorHue / 360f).putFloat(s.colorSaturation).putFloat(s.customColor ? 1f : 0f).putFloat(0f);
			SCRATCH.flip();
			CommandEncoder encoder = device.createCommandEncoder();
			encoder.writeToBuffer(uniformBuffer.slice(), SCRATCH);

			// только вращение камеры: небо «на бесконечности»
			Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
			var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);

			try (RenderPass pass = encoder.createRenderPass(() -> "Echo shader sky", target.getColorTextureView(),
					OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
				pass.setPipeline(pipeline(ShaderType.byName(s.shaderType)));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				pass.setUniform(UNIFORM_BLOCK, uniformBuffer.slice());
				pass.setVertexBuffer(0, vertexBuffer);
				pass.draw(0, 3);
			}
			if (!loggedDraw) {
				loggedDraw = true;
				LOG.info("Shader Sky: first frame drawn with shader {}", s.shaderType);
			}
		} catch (Throwable e) {
			failed = true;
			LOG.error("Shader Sky failed and was disabled until restart", e);
		}
	}
}
