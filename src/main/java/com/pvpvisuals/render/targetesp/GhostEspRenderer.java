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

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Procedural three-wisp Target ESP inspired by ParticleFX Studio's Ghost Wisps effect.
 * It draws three floating spirit heads with separate, fading glow motes along figure-eight paths.
 * No flat ribbon geometry, vanilla particles, external textures or user-provided files are required.
 */
public final class GhostEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp/ghosts");
    private static final int MAX_TRAIL_MOTES = 20;
    private static final int MAX_QUADS = 3 * MAX_TRAIL_MOTES + 3;
    /** POSITION (3 floats) + UV (2 floats) + COLOR (4 normalized bytes). */
    private static final int VERTEX_BYTES = 24;
    private static final int BUFFER_BYTES = MAX_QUADS * 4 * VERTEX_BYTES;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final int[] WISP_COLORS = {0x59F1DF, 0x72CFFF, 0xA995FF};

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];
    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static boolean failed;

    private GhostEspRenderer() { }

    public static void reset() {
        failed = false;
        PIPELINES[0] = null;
        PIPELINES[1] = null;
    }

    private static RenderPipeline pipeline(boolean throughWalls) {
        int index = throughWalls ? 1 : 0;
        if (PIPELINES[index] == null) {
            PIPELINES[index] = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals",
                            "pipeline/target_esp_ghost" + (throughWalls ? "_xray" : "")))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_ghost"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_ghost"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
        }
        return PIPELINES[index];
    }

    public static void render(WorldRenderContext context) {
        if (failed) {
            return;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            TargetEspModule.Frame frame = TargetEspModule.frame();
            TargetEspSettings settings = PvpConfig.get().targetEspSettings;
            if (!frame.visible || frame.fade <= 0.001f || mc.player == null) {
                return;
            }

            RenderTarget target = mc.getMainRenderTarget();
            if (target == null) {
                return;
            }

            float[] bounds = frame.bounds;
            float minX = bounds[0], minY = bounds[1], minZ = bounds[2];
            float maxX = bounds[3], maxY = bounds[4], maxZ = bounds[5];
            float width = Math.max(0.2f, maxX - minX);
            float depth = Math.max(0.2f, maxZ - minZ);
            float height = Math.max(0.4f, maxY - minY);
            float centerX = (minX + maxX) * 0.5f;
            float centerZ = (minZ + maxZ) * 0.5f;

            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            double yaw = Math.toRadians(mc.player.getViewYRot(partialTick));
            double pitch = Math.toRadians(mc.player.getViewXRot(partialTick));
            double cosPitch = Math.cos(pitch);
            double forwardX = -Math.sin(yaw) * cosPitch;
            double forwardY = -Math.sin(pitch);
            double forwardZ = Math.cos(yaw) * cosPitch;
            double leftX = Math.cos(yaw);
            double leftZ = Math.sin(yaw);
            double upX = forwardY * leftZ;
            double upY = forwardZ * leftX - forwardX * leftZ;
            double upZ = -forwardY * leftX;

            int accent = TargetEspRenderer.resolveColor(settings, frame);
            int trailMotes = Math.max(6, Math.min(MAX_TRAIL_MOTES, settings.ghostCount));
            float speed = clamp(settings.animationSpeed, 0.1f, 4.0f);
            double time = System.nanoTime() / 1_000_000_000.0;

            DATA.clear();
            vertexCount = 0;

            // Three soft spirit heads drift independently; each leaves a dotted, fading aura.
            for (int wisp = 0; wisp < 3; wisp++) {
                int color = blendColor(WISP_COLORS[wisp], accent, 0.14f);
                float red = ((color >> 16) & 255) / 255.0f;
                float green = ((color >> 8) & 255) / 255.0f;
                float blue = (color & 255) / 255.0f;
                double headPhase = time * speed * 0.82;
                float[] head = samplePath(wisp, headPhase, centerX, centerZ, minY, width, depth, height);

                // The larger head is a soft flame-like spirit, not a flat rectangle or a ring.
                appendMote(head[0], head[1], head[2], 0.17f, frame.fade * 0.96f,
                        red, green, blue, leftX, leftZ, upX, upY, upZ, true);

                // Separate round motes dissolve behind the spirit instead of joining into a stripe.
                for (int trail = 1; trail <= trailMotes; trail++) {
                    float age = trail / (float) (trailMotes + 1);
                    double lag = age * 0.88;
                    float[] mote = samplePath(wisp, headPhase - lag,
                            centerX, centerZ, minY, width, depth, height);
                    float drift = (float) Math.sin(time * 1.8 + trail * 1.7 + wisp * 2.1) * 0.035f;
                    float size = 0.028f + 0.045f * (1.0f - age);
                    float alpha = frame.fade * (float) Math.pow(1.0f - age, 1.65) * 0.72f;
                    appendMote(mote[0] + drift, mote[1] - drift * 0.45f, mote[2] - drift,
                            size, alpha, red, green, blue, leftX, leftZ, upX, upY, upZ, false);
                }
            }

            flush(frame.view, settings.throughWalls, target);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Ghost Wisps renderer failed and was disabled until re-enabled", error);
        }
    }

    private static float[] samplePath(int wisp, double phase,
            float centerX, float centerZ, float minY,
            float width, float depth, float height) {
        double offset = wisp * (Math.PI * 2.0 / 3.0);
        double a = phase + offset;
        float radiusX = Math.max(0.48f, width * 0.68f + 0.20f);
        float radiusZ = Math.max(0.48f, depth * 0.68f + 0.20f);
        float x = centerX + (float) Math.sin(a) * radiusX;
        float z = centerZ + (float) (Math.sin(a) * Math.cos(a) * 2.0) * radiusZ;
        float vertical = 0.18f + 0.68f * (0.5f + 0.5f * (float) Math.sin(a * 1.15 + offset));
        float y = minY + vertical * height;
        return new float[] {x, y, z};
    }

    private static void appendMote(float cx, float cy, float cz, float size, float alpha,
            float red, float green, float blue,
            double leftX, double leftZ, double upX, double upY, double upZ, boolean spiritHead) {
        if (alpha <= 0.003f || DATA.remaining() < 4 * VERTEX_BYTES) {
            return;
        }
        float halfWidth = size * (spiritHead ? 0.78f : 1.0f);
        float halfHeight = size * (spiritHead ? 1.22f : 1.0f);
        float lx = (float) (leftX * halfWidth);
        float lz = (float) (leftZ * halfWidth);
        float ux = (float) (upX * halfHeight);
        float uy = (float) (upY * halfHeight);
        float uz = (float) (upZ * halfHeight);
        float uvBottom = spiritHead ? 4.0f : 2.0f;
        float uvTop = spiritHead ? 5.0f : 3.0f;
        appendQuad(
                cx - lx - ux, cy - uy, cz - lz - uz,
                cx + lx - ux, cy - uy, cz + lz - uz,
                cx + lx + ux, cy + uy, cz + lz + uz,
                cx - lx + ux, cy + uy, cz - lz + uz,
                alpha, alpha, alpha, alpha, red, green, blue, uvBottom, uvTop);
    }

    private static void appendQuad(
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float a0, float a1, float a2, float a3,
            float red, float green, float blue, float uvBottom, float uvTop) {
        if (DATA.remaining() < 4 * VERTEX_BYTES) {
            return;
        }
        putVertex(x0,y0,z0,0.0f,uvBottom,red,green,blue,a0);
        putVertex(x1,y1,z1,1.0f,uvBottom,red,green,blue,a1);
        putVertex(x2,y2,z2,1.0f,uvTop,red,green,blue,a2);
        putVertex(x3,y3,z3,0.0f,uvTop,red,green,blue,a3);
        vertexCount += 4;
    }

    private static void putVertex(float x, float y, float z, float u, float v,
            float red, float green, float blue, float alpha) {
        DATA.putFloat(x).putFloat(y).putFloat(z);
        DATA.putFloat(u).putFloat(v);
        DATA.put((byte) Math.round(clamp(red, 0.0f, 1.0f) * 255.0f));
        DATA.put((byte) Math.round(clamp(green, 0.0f, 1.0f) * 255.0f));
        DATA.put((byte) Math.round(clamp(blue, 0.0f, 1.0f) * 255.0f));
        DATA.put((byte) Math.round(clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    private static void flush(Matrix4f view, boolean throughWalls, RenderTarget target) {
        if (vertexCount == 0) {
            return;
        }

        DATA.flip();
        GpuDevice device = RenderSystem.getDevice();
        if (vertexBuffer == null) {
            vertexBuffer = device.createBuffer(() -> "PvPVisuals Ghost Wisps motes",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);
        try (RenderPass pass = encoder.createRenderPass(() -> "PvPVisuals Ghost Wisps motes",
                target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline(throughWalls));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    private static int blendColor(int base, int accent, float amount) {
        float inv = 1.0f - amount;
        int r = Math.round(((base >> 16) & 255) * inv + ((accent >> 16) & 255) * amount);
        int g = Math.round(((base >> 8) & 255) * inv + ((accent >> 8) & 255) * amount);
        int b = Math.round((base & 255) * inv + (accent & 255) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
