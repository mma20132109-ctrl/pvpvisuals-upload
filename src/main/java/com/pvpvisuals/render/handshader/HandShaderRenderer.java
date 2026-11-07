package com.pvpvisuals.render.handshader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

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
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pvpvisuals.config.HandShaderSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Рисует эффект на предмет/руку от первого лица. Способ: пост-проход (а не замена RenderType предмета, у которой
 * в 1.21.11 слишком много внутренних зависимостей): после отрисовки рук один полноэкранный треугольник с
 * фрагментным шейдером. Маска «где рука/предмет» берётся из буфера глубины главного render target: перед проходом
 * рук глубина очищается до 1.0, поэтому пиксели с глубиной меньше 1 и есть рука/предмет (ItemInHandRendererMixin
 * дополнительно очищает глубину сам в начале прохода рук, чтобы это было верно).
 *
 * <p>Глубина читается как текстура ({@code uniform sampler2D Depth}), поэтому проход создаётся без depth-вложения
 * (иначе текстура одновременно читалась бы и была вложением). Pipeline и буферы создаются один раз.
 * Если что-то падает, пишем ошибку в лог один раз и эффект отключается до перезапуска/повторного включения модуля.
 */
public final class HandShaderRenderer {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/handshader");
	private static final String NS = "pvpvisuals";
	private static final String UNIFORM_BLOCK = "EchoHandData";
	private static final int UNIFORM_BYTES = 48; // три vec4 (std140)

	private static final Map<HandShaderStyle, RenderPipeline> PIPELINES = new EnumMap<>(HandShaderStyle.class);
	private static final ByteBuffer SCRATCH = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());

	private static GpuBuffer vertexBuffer;
	private static GpuBuffer uniformBuffer;
	private static boolean failed;
	private static boolean loggedDraw;
	private static long lastNanos;
	private static double animTime;

	private HandShaderRenderer() { }

	public static void reset() {
		failed = false;
		PIPELINES.clear();
	}

	private static RenderPipeline pipeline(HandShaderStyle style) {
		return PIPELINES.computeIfAbsent(style, k -> RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/" + style.shader))
				.withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/hand_overlay"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/" + style.shader))
				.withSampler("Depth")
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
			// один треугольник, покрывающий весь экран (clip-координаты)
			ByteBuffer v = ByteBuffer.allocateDirect(3 * 3 * Float.BYTES).order(ByteOrder.nativeOrder());
			v.putFloat(-1f).putFloat(-1f).putFloat(0f);
			v.putFloat(3f).putFloat(-1f).putFloat(0f);
			v.putFloat(-1f).putFloat(3f).putFloat(0f);
			v.flip();
			vertexBuffer = device.createBuffer(() -> "Echo hand shader triangle", GpuBuffer.USAGE_VERTEX, v);
		}
		if (uniformBuffer == null) {
			uniformBuffer = device.createBuffer(() -> "Echo hand shader uniforms",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
		}
	}

	/** Вызывается после прохода рук. Безопасно: любые ошибки гасятся и логируются один раз. */
	public static void render() {
		if (failed || !HandShaderModule.consumeHandPass() || !HandShaderModule.shouldDraw()) {
			return;
		}
		try {
			draw(HandShaderModule.settings());
		} catch (Throwable e) {
			failed = true;
			LOG.error("Hand Shader failed and was disabled until restart or re-enabling the module", e);
		}
	}

	private static void draw(HandShaderSettings s) {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget target = mc.getMainRenderTarget();
		if (target == null || target.getDepthTextureView() == null) {
			return;
		}
		HandShaderStyle style = HandShaderStyle.byName(s.shaderType);

		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.0 : Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		animTime += dt * s.animationSpeed;

		GpuDevice device = RenderSystem.getDevice();
		ensureBuffers(device);

		SCRATCH.clear();
		putColor(s.colorPrimary, Math.max(0.05f, Math.min(1f, s.alphaPrimary)));
		putColor(s.colorSecondary, Math.max(0.05f, Math.min(1f, s.alphaSecondary)));
		SCRATCH.putFloat((float) animTime).putFloat(target.width).putFloat(target.height).putFloat(0f);
		SCRATCH.flip();

		CommandEncoder encoder = device.createCommandEncoder();
		encoder.writeToBuffer(uniformBuffer.slice(), SCRATCH);

		try (RenderPass pass = encoder.createRenderPass(() -> "Echo hand shader", target.getColorTextureView(),
				OptionalInt.empty())) {
			pass.setPipeline(pipeline(style));
			pass.setUniform(UNIFORM_BLOCK, uniformBuffer.slice());
			pass.bindTexture("Depth", target.getDepthTextureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setVertexBuffer(0, vertexBuffer);
			pass.draw(0, 3);
		}
		if (!loggedDraw) {
			loggedDraw = true;
			LOG.info("Hand Shader: first frame drawn with shader {}", style.shader);
		}
	}

	private static void putColor(int rgb, float alpha) {
		SCRATCH.putFloat(((rgb >> 16) & 0xFF) / 255f).putFloat(((rgb >> 8) & 0xFF) / 255f)
				.putFloat((rgb & 0xFF) / 255f).putFloat(alpha);
	}
}
