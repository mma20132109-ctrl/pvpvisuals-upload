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
 * Three independent Target ESP looks:
 * - Ghosts: small floating particles around the lower half of the target.
 * - Circle: one glowing ring on the ground beneath the target.
 * - Square: corner brackets around the target's 3D bounds.
 *
 * All three styles use camera-relative geometry and a shared lightweight GPU buffer.
 */
public final class TargetEspStyleRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp");
    private static final String NS = "pvpvisuals";
    private static final int RING_SEGMENTS = 72;
    private static final int GHOST_SEGMENTS = 16;
    private static final int MAX_GHOSTS = 48;
    private static final int VERTEX_BYTES = 16;
    private static final int BUFFER_BYTES = 180_000;
    private static final double TAU = Math.PI * 2.0;
    private static final float CORNER_FRACTION = 0.24f;

    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];

    private static GpuBuffer vertexBuffer;
    private static boolean failed;
    private static int vertexCount;

    private TargetEspStyleRenderer() { }

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
                            "pipeline/target_esp" + (throughWalls ? "_xray" : "")))
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
            float thickness = clamp(settings.lineThickness, 0.7f, 4.0f);
            float speed = clamp(settings.animationSpeed, 0.1f, 4.0f);
            float fade = clamp01(frame.fade);
            int color = resolveColor(settings, frame);
            double time = System.nanoTime() / 1_000_000_000.0 * speed;

            DATA.clear();
            vertexCount = 0;

            switch (TargetEspMode.byName(settings.mode)) {
                case GHOSTS -> drawGhosts(centerX, centerZ, y0, height, width, depth,
                        settings, time, color, fade, worldPerPixel, size);
                case CIRCLE -> drawCircle(centerX, centerZ, y0, width, depth,
                        settings, time, color, fade, worldPerPixel, size, thickness);
                case SQUARE -> drawSquare(x0, y0, z0, x1, y1, z1,
                        color, fade, worldPerPixel, thickness, settings.auraGlow);
            }

            flush(frame.view, target);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Target ESP renderer failed and was disabled until the module is toggled again", error);
        }
    }

    // ---------------------------------------------------------------------
    // Ghosts: independent glowing particles, not connected into a spiral.
    // ---------------------------------------------------------------------

    private static void drawGhosts(float cx, float cz, float y0, float height, float width, float depth,
            TargetEspSettings settings, double time, int rgb, float fade, float worldPerPixel, float size) {
        int count = Math.max(4, Math.min(MAX_GHOSTS, settings.ghostCount));
        float radiusX = Math.max(0.48f, width * 0.5f + 0.20f) * size;
        float radiusZ = Math.max(0.48f, depth * 0.5f + 0.20f) * size;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;

        for (int i = 0; i < count; i++) {
            double phase = time * 0.56 + TAU * i / count;
            float bob = (float) Math.sin(phase * 1.7 + i * 0.73);
            float orbit = 1.0f + 0.12f * (float) Math.sin(time * 0.9 + i * 1.9);
            float x = cx + (float) Math.cos(phase) * radiusX * orbit;
            float z = cz + (float) Math.sin(phase) * radiusZ * orbit;
            float y = y0 + height * (0.24f + 0.42f * (0.5f + 0.5f * bob));

            float distance = (float) Math.sqrt(x * x + y * y + z * z);
            float particleRadius = clamp(worldPerPixel * Math.max(1.0f, distance) * 11.0f, 0.045f, 0.15f);
            float pulse = 0.78f + 0.22f * (0.5f + 0.5f * (float) Math.sin(time * 2.3 + i));
            int alpha = clampByte(Math.round(235f * fade * pulse));

            // Tiny tail drifting behind each moving particle.
            float tailX = cx + (float) Math.cos(phase - 0.12) * radiusX * orbit;
            float tailZ = cz + (float) Math.sin(phase - 0.12) * radiusZ * orbit;
            float tailY = y - height * 0.035f;
            if (settings.auraGlow) {
                segment(tailX, tailY, tailZ, x, y, z, 2.2f, worldPerPixel,
                        r, g, b, clampByte(Math.round(alpha * 0.22f)));
            }

            drawSoftParticle(x, y, z, particleRadius * 1.75f,
                    r, g, b, clampByte(Math.round(alpha * 0.16f)), 0, GHOST_SEGMENTS);
            drawSoftParticle(x, y, z, particleRadius,
                    brighten(rgb, 0.15f) >> 16 & 255,
                    brighten(rgb, 0.15f) >> 8 & 255,
                    brighten(rgb, 0.15f) & 255,
                    alpha, clampByte(Math.round(alpha * 0.06f)), GHOST_SEGMENTS);
        }
    }

    /** Camera-facing radial particle with interpolated alpha from the centre to its edge. */
    private static void drawSoftParticle(float x, float y, float z, float radius,
            int r, int g, int b, int centerAlpha, int edgeAlpha, int segments) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-5f) {
            return;
        }
        float nx = -x / length, ny = -y / length, nz = -z / length;
        float rx = nz, ry = 0f, rz = -nx;
        float rightLength = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rightLength < 1.0e-4f) {
            rx = 1f; ry = 0f; rz = 0f;
            rightLength = 1f;
        }
        rx /= rightLength; ry /= rightLength; rz /= rightLength;
        float ux = ny * rz - nz * ry;
        float uy = nz * rx - nx * rz;
        float uz = nx * ry - ny * rx;

        for (int i = 0; i < segments; i++) {
            double a0 = TAU * i / segments;
            double a1 = TAU * (i + 1) / segments;
            float ax = x + (float) Math.cos(a0) * radius * rx + (float) Math.sin(a0) * radius * ux;
            float ay = y + (float) Math.cos(a0) * radius * ry + (float) Math.sin(a0) * radius * uy;
            float az = z + (float) Math.cos(a0) * radius * rz + (float) Math.sin(a0) * radius * uz;
            float bx = x + (float) Math.cos(a1) * radius * rx + (float) Math.sin(a1) * radius * ux;
            float by = y + (float) Math.cos(a1) * radius * ry + (float) Math.sin(a1) * radius * uy;
            float bz = z + (float) Math.cos(a1) * radius * rz + (float) Math.sin(a1) * radius * uz;
            vertex(x, y, z, r, g, b, centerAlpha);
            vertex(ax, ay, az, r, g, b, edgeAlpha);
            vertex(bx, by, bz, r, g, b, edgeAlpha);
        }
    }

    // ---------------------------------------------------------------------
    // Circle: a single ground ring with a broad, restrained glow.
    // ---------------------------------------------------------------------

    private static void drawCircle(float cx, float cz, float y0, float width, float depth,
            TargetEspSettings settings, double time, int rgb, float fade, float worldPerPixel,
            float size, float thickness) {
        float radiusX = Math.max(0.46f, width * 0.5f + 0.16f) * size;
        float radiusZ = Math.max(0.46f, depth * 0.5f + 0.16f) * size;
        float pulse = 1.0f + (float) Math.sin(time * 1.25) * 0.025f;
        float y = y0 + 0.035f;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int alpha = clampByte(Math.round(245f * fade));

        float px = cx + radiusX * pulse, pz = cz;
        for (int i = 1; i <= RING_SEGMENTS; i++) {
            double angle = TAU * i / RING_SEGMENTS;
            float x = cx + (float) Math.cos(angle) * radiusX * pulse;
            float z = cz + (float) Math.sin(angle) * radiusZ * pulse;
            if (settings.auraGlow) {
                segment(px, y, pz, x, y, z, thickness * 5.0f, worldPerPixel,
                        r, g, b, clampByte(Math.round(alpha * 0.14f)));
            }
            segment(px, y, pz, x, y, z, thickness, worldPerPixel,
                    r, g, b, alpha);
            px = x;
            pz = z;
        }
    }

    // ---------------------------------------------------------------------
    // Square: eight 3D corners, each made from short L-shaped brackets.
    // ---------------------------------------------------------------------

    private static void drawSquare(float x0, float y0, float z0, float x1, float y1, float z1,
            int rgb, float fade, float worldPerPixel, float thickness, boolean glow) {
        float[][] corners = {
            {x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},
            {x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}
        };
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
        };
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int alpha = clampByte(Math.round(245f * fade));

        for (int[] edge : edges) {
            float[] a = corners[edge[0]], c = corners[edge[1]];
            drawCorner(a, c, CORNER_FRACTION, thickness, worldPerPixel, r, g, b, alpha, glow);
            drawCorner(c, a, CORNER_FRACTION, thickness, worldPerPixel, r, g, b, alpha, glow);
        }
    }

    private static void drawCorner(float[] from, float[] toward, float fraction, float thickness,
            float worldPerPixel, int r, int g, int b, int alpha, boolean glow) {
        float x2 = from[0] + (toward[0] - from[0]) * fraction;
        float y2 = from[1] + (toward[1] - from[1]) * fraction;
        float z2 = from[2] + (toward[2] - from[2]) * fraction;
        if (glow) {
            segment(from[0], from[1], from[2], x2, y2, z2,
                    thickness * 4.0f, worldPerPixel, r, g, b,
                    clampByte(Math.round(alpha * 0.15f)));
        }
        segment(from[0], from[1], from[2], x2, y2, z2,
                thickness, worldPerPixel, r, g, b, alpha);
    }

    // ---------------------------------------------------------------------
    // Shared GPU drawing helpers.
    // ---------------------------------------------------------------------

    private static void flush(Matrix4f view, RenderTarget target) {
        if (vertexCount == 0) {
            return;
        }
        DATA.flip();
        GpuDevice device = RenderSystem.getDevice();
        if (vertexBuffer == null) {
            vertexBuffer = device.createBuffer(() -> "PvPVisuals Target ESP vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }
        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);
        try (RenderPass pass = encoder.createRenderPass(() -> "PvPVisuals Target ESP",
                target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline(false));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    /** Camera-facing ribbon with a constant width in screen pixels. */
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

    private static void vertex(float x, float y, float z, int r, int g, int b, int alpha) {
        if (DATA.remaining() < VERTEX_BYTES) {
            return;
        }
        DATA.putFloat(x).putFloat(y).putFloat(z);
        DATA.put((byte) r).put((byte) g).put((byte) b).put((byte) alpha);
        vertexCount++;
    }

    private static int resolveColor(TargetEspSettings settings, Frame frame) {
        String mode = settings.colorMode == null ? "HEALTH" : settings.colorMode.toUpperCase(java.util.Locale.ROOT);
        return switch (mode) {
            case "STATIC" -> settings.color;
            case "DISTANCE" -> blend(0x45E6A2, 0xFF5B68,
                    clamp01(frame.distance / Math.max(1.0f, settings.maxDistance)));
            case "RAINBOW" -> rainbow(System.nanoTime() / 1_000_000_000.0
                    * clamp(settings.animationSpeed, 0.1f, 4.0f));
            case "HEALTH" -> healthColor(frame.health);
            default -> settings.color;
        };
    }

    private static int healthColor(float health) {
        float value = clamp01(health);
        return value >= 0.5f
                ? blend(0xF2C65C, 0x49E39E, (value - 0.5f) * 2.0f)
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
        return blend(color, 0xFFFFFF, amount);
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
