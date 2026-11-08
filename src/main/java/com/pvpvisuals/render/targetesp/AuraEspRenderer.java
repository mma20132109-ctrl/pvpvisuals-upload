package com.pvpvisuals.render.targetesp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.TargetEspSettings;
import com.pvpvisuals.render.targetesp.TargetEspModule.Frame;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Volumetric Aura made from three animated energy spirals.
 * Each strand climbs around the complete target instead of drawing flat horizontal rings.
 */
public final class AuraEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp/aura");
    private static final String NS = "pvpvisuals";
    private static final int HELIX_SEGMENTS = 180;
    private static final int STRANDS = 3;
    private static final int PASSES = 2; // soft glow + crisp core
    private static final int VERTEX_BYTES = 16;
    private static final int BUFFER_BYTES = HELIX_SEGMENTS * STRANDS * PASSES * 6 * VERTEX_BYTES;
    private static final double TAU = Math.PI * 2.0;

    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];

    private static GpuBuffer vertexBuffer;
    private static boolean failed;
    private static int vertexCount;

    private AuraEspRenderer() { }

    public static void reset() {
        failed = false;
        PIPELINES[0] = null;
        PIPELINES[1] = null;
    }

    private static RenderPipeline pipeline(boolean throughWalls) {
        int index = throughWalls ? 1 : 0;
        if (PIPELINES[index] == null) {
            PIPELINES[index] = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(NS,
                            "pipeline/target_aura" + (throughWalls ? "_xray" : "")))
                    .withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/hitbox"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/hitbox"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(throughWalls
                            ? DepthTestFunction.NO_DEPTH_TEST
                            : DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
        }
        return PIPELINES[index];
    }

    public static void render(WorldRenderContext context) {
        Frame frame = TargetEspModule.frame();
        if (!frame.visible || failed) {
            return;
        }

        try {
            TargetEspSettings settings = PvpConfig.get().targetEspSettings;
            Minecraft mc = Minecraft.getInstance();
            RenderTarget target = mc.getMainRenderTarget();
            if (target == null || target.height <= 0) {
                return;
            }

            float fov = (float) Math.toRadians(mc.options.fov().get());
            float worldPerPixel = (float) (2.0 * Math.tan(fov / 2.0) / target.height);
            float[] bounds = frame.bounds;
            float x0 = bounds[0], y0 = bounds[1], z0 = bounds[2];
            float x1 = bounds[3], y1 = bounds[4], z1 = bounds[5];
            float width = Math.max(0.1f, x1 - x0);
            float height = Math.max(0.1f, y1 - y0);
            float depth = Math.max(0.1f, z1 - z0);
            float centerX = (x0 + x1) * 0.5f;
            float centerZ = (z0 + z1) * 0.5f;

            float size = clamp(settings.auraSize, 0.65f, 1.8f);
            float thickness = clamp(settings.lineThickness, 0.7f, 3.0f);
            float speed = clamp(settings.animationSpeed, 0.1f, 3.0f);
            float fade = clamp01(frame.fade);
            int color = resolveColor(settings, frame);
            double time = System.nanoTime() / 1_000_000_000.0 * speed;

            float radiusX = Math.max(0.46f, width * 0.5f + 0.20f) * size;
            float radiusZ = Math.max(0.46f, depth * 0.5f + 0.20f) * size;
            float bottom = y0 + Math.max(0.04f, height * 0.025f);
            float top = y1 - Math.max(0.04f, height * 0.025f);

            DATA.clear();
            vertexCount = 0;

            // Three intertwined strands wrap from feet to head. No flat rings are drawn.
            drawHelix(centerX, centerZ, bottom, top, radiusX, radiusZ,
                    0.0, time, 2.25f, color, fade, thickness, worldPerPixel, settings.auraGlow);

            drawHelix(centerX, centerZ, bottom, top, radiusX * 0.94f, radiusZ * 0.94f,
                    TAU / 3.0, -time * 0.78, 2.48f, brighten(color, 0.16f),
                    fade * 0.88f, thickness * 0.86f, worldPerPixel, settings.auraGlow);

            drawHelix(centerX, centerZ, bottom, top, radiusX * 1.06f, radiusZ * 1.06f,
                    TAU * 2.0 / 3.0, time * 0.62, 2.12f, brighten(color, 0.30f),
                    fade * 0.76f, thickness * 0.72f, worldPerPixel, settings.auraGlow);

            flush(frame.view, settings.throughWalls, target);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Target ESP Aura renderer failed and was disabled until the module is toggled again", error);
        }
    }

    private static void drawHelix(float centerX, float centerZ, float bottom, float top,
            float radiusX, float radiusZ, double phase, double time, float turns,
            int rgb, float fade, float thickness, float worldPerPixel, boolean glow) {
        float previousX = 0f, previousY = 0f, previousZ = 0f;
        int baseR = (rgb >> 16) & 255;
        int baseG = (rgb >> 8) & 255;
        int baseB = rgb & 255;

        for (int i = 0; i <= HELIX_SEGMENTS; i++) {
            float progress = i / (float) HELIX_SEGMENTS;
            double angle = phase + time + progress * TAU * turns;
            float breathing = 1.0f + (float) Math.sin(time * 1.35 + phase + progress * TAU * 1.4) * 0.035f;
            float x = centerX + (float) Math.cos(angle) * radiusX * breathing;
            float z = centerZ + (float) Math.sin(angle) * radiusZ * breathing;
            float verticalWave = (float) Math.sin(angle * 0.55 + time * 0.8) * (top - bottom) * 0.012f;
            float y = bottom + (top - bottom) * progress + verticalWave;

            if (i > 0) {
                float highlight = 0.5f + 0.5f * (float) Math.sin(progress * TAU * 2.0 - time * 1.7 + phase);
                float lift = 0.08f + highlight * 0.24f;
                int r = mix(baseR, 255, lift);
                int g = mix(baseG, 255, lift);
                int b = mix(baseB, 255, lift);

                // Fade the very ends so each strand melts into the aura rather than stopping abruptly.
                float edgeFade = Math.min(1.0f, Math.min(progress * 9.0f, (1.0f - progress) * 9.0f));
                int alpha = clampByte(Math.round(255f * fade * edgeFade));
                if (glow) {
                    segment(previousX, previousY, previousZ, x, y, z,
                            thickness * 4.8f, worldPerPixel, r, g, b,
                            clampByte(Math.round(alpha * 0.14f)));
                }
                segment(previousX, previousY, previousZ, x, y, z,
                        thickness, worldPerPixel, r, g, b,
                        clampByte(Math.round(alpha * 0.92f)));
            }

            previousX = x;
            previousY = y;
            previousZ = z;
        }
    }

    private static void flush(Matrix4f view, boolean throughWalls, RenderTarget target) {
        if (vertexCount == 0) {
            return;
        }
        DATA.flip();
        GpuDevice device = RenderSystem.getDevice();
        if (vertexBuffer == null) {
            vertexBuffer = device.createBuffer(() -> "PvPVisuals Target ESP Aura vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }
        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);
        try (RenderPass pass = encoder.createRenderPass(() -> "PvPVisuals Target ESP Aura",
                target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline(throughWalls));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    /** Camera-facing ribbon with a constant screen-space width. */
    private static void segment(float x1, float y1, float z1, float x2, float y2, float z2,
            float pixels, float worldPerPixel, int r, int g, int b, int alpha) {
        if (alpha <= 0 || DATA.remaining() < 6 * VERTEX_BYTES) {
            return;
        }
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float mx = (x1 + x2) * 0.5f, my = (y1 + y2) * 0.5f, mz = (z1 + z2) * 0.5f;
        float sx = dy * mz - dz * my;
        float sy = dz * mx - dx * mz;
        float sz = dx * my - dy * mx;
        float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (length < 1.0e-8f) {
            return;
        }
        float distance = (float) Math.sqrt(mx * mx + my * my + mz * mz);
        float scale = pixels * worldPerPixel * distance * 0.5f / length;
        sx *= scale;
        sy *= scale;
        sz *= scale;

        vertex(x1 - sx, y1 - sy, z1 - sz, r, g, b, alpha);
        vertex(x1 + sx, y1 + sy, z1 + sz, r, g, b, alpha);
        vertex(x2 + sx, y2 + sy, z2 + sz, r, g, b, alpha);
        vertex(x1 - sx, y1 - sy, z1 - sz, r, g, b, alpha);
        vertex(x2 + sx, y2 + sy, z2 + sz, r, g, b, alpha);
        vertex(x2 - sx, y2 - sy, z2 - sz, r, g, b, alpha);
    }

    private static void vertex(float x, float y, float z, int r, int g, int b, int a) {
        if (DATA.remaining() < VERTEX_BYTES) {
            return;
        }
        DATA.putFloat(x).putFloat(y).putFloat(z);
        DATA.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
        vertexCount++;
    }

    private static int resolveColor(TargetEspSettings settings, Frame frame) {
        if (frame.invisible && settings.useInvisibleColor) {
            return settings.invisibleColor;
        }
        if (frame.allied) {
            return settings.friendColor;
        }
        String mode = settings.colorMode == null ? "HEALTH" : settings.colorMode.toUpperCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "STATIC" -> settings.color;
            case "DISTANCE" -> blend(0x55E6B5, 0xFF6578,
                    clamp01(frame.distance / Math.max(1.0f, settings.maxDistance)));
            case "RAINBOW" -> rainbow(System.nanoTime() / 1_000_000_000.0
                    * clamp(settings.animationSpeed, 0.1f, 3.0f));
            case "HEALTH" -> healthColor(frame.health);
            default -> settings.color;
        };
    }

    private static int healthColor(float health) {
        float value = clamp01(health);
        return value >= 0.5f
                ? blend(0xF2C65C, 0x4AE2A0, (value - 0.5f) * 2.0f)
                : blend(0xFF5968, 0xF2C65C, value * 2.0f);
    }

    private static int rainbow(double phase) {
        float h = (float) (phase - Math.floor(phase));
        int sector = (int) (h * 6.0f);
        float f = h * 6.0f - sector;
        float q = 1.0f - f;
        float t = f;
        float r, g, b;
        switch (sector % 6) {
            case 0 -> { r = 1f; g = t; b = 0f; }
            case 1 -> { r = q; g = 1f; b = 0f; }
            case 2 -> { r = 0f; g = 1f; b = t; }
            case 3 -> { r = 0f; g = q; b = 1f; }
            case 4 -> { r = t; g = 0f; b = 1f; }
            default -> { r = 1f; g = 0f; b = q; }
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    private static int brighten(int color, float amount) {
        return blend(color, 0xFFFFFF, clamp01(amount));
    }

    private static int blend(int a, int b, float amount) {
        float t = clamp01(amount);
        int r = mix((a >> 16) & 255, (b >> 16) & 255, t);
        int g = mix((a >> 8) & 255, (b >> 8) & 255, t);
        int bl = mix(a & 255, b & 255, t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int mix(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }
}
