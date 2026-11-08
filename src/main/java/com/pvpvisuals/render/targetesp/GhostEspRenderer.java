package com.pvpvisuals.render.targetesp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Comparator;
import java.util.List;
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
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.TargetEspSettings;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Textured Ghost ESP renderer for 1.21.11.
 *
 * The source PNGs stay in the project-root target_esp/ directory. build.gradle copies them
 * into assets/pvpvisuals/textures/target_esp/ in build resources and in the final mod JAR.
 *
 * This renderer draws camera-facing textured quads using the user's PNG artwork.
 * It does not use Minecraft's vanilla particle engine and does not draw entity bounding boxes.
 */
public final class GhostEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp/ghosts");
    private static final String TEXTURE_DIRECTORY = "textures/target_esp";
    private static final int MAX_SPRITES = 48;
    /** POSITION (3 floats) + UV (2 floats) + COLOR (4 normalized bytes). */
    private static final int VERTEX_BYTES = 24;
    private static final int BUFFER_BYTES = MAX_SPRITES * 4 * VERTEX_BYTES;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];
    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static Identifier spriteId;
    private static long nextTextureScan;
    private static boolean warnedMissing;
    private static boolean failed;

    private GhostEspRenderer() { }

    public static void reset() {
        failed = false;
        PIPELINES[0] = null;
        PIPELINES[1] = null;
        spriteId = null;
        nextTextureScan = 0L;
    }

    private static RenderPipeline pipeline(boolean throughWalls) {
        int index = throughWalls ? 1 : 0;
        if (PIPELINES[index] == null) {
            PIPELINES[index] = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals",
                            "pipeline/target_esp_ghost" + (throughWalls ? "_xray" : "")))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_ghost"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_esp_ghost"))
                    .withSampler("Sampler0")
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

            Identifier ringId = findNamedTexture(mc, "target-esp1.png");
            if (ringId == null) {
                ringId = findSprite(mc);
            }
            if (ringId == null) {
                if (!warnedMissing) {
                    warnedMissing = true;
                    LOG.warn("No PNG found in project-root target_esp/. Add target-esp1.png or another PNG texture.");
                }
                return;
            }

            Identifier glowId = findNamedTexture(mc, "glow.png");
            AbstractTexture ringTexture = mc.getTextureManager().getTexture(ringId);
            AbstractTexture glowTexture = glowId == null ? null : mc.getTextureManager().getTexture(glowId);
            if (ringTexture.getTextureView() == null) {
                return;
            }
            if (glowTexture != null && glowTexture.getTextureView() == null) {
                glowTexture = null;
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

            // Same camera-facing basis used by the project's world-particle renderer.
            double forwardX = -Math.sin(yaw) * cosPitch;
            double forwardY = -Math.sin(pitch);
            double forwardZ = Math.cos(yaw) * cosPitch;
            double leftX = Math.cos(yaw);
            double leftZ = Math.sin(yaw);
            double upX = forwardY * leftZ;
            double upY = forwardZ * leftX - forwardX * leftZ;
            double upZ = -forwardY * leftX;

            int rgb = TargetEspRenderer.resolveColor(settings, frame);
            int red = (rgb >> 16) & 255;
            int green = (rgb >> 8) & 255;
            int blue = rgb & 255;
            int count = Math.max(4, Math.min(MAX_SPRITES, settings.ghostCount));
            float speed = clamp(settings.animationSpeed, 0.1f, 4.0f);
            double time = System.nanoTime() / 1_000_000_000.0;

            DATA.clear();
            vertexCount = 0;
            if (glowTexture != null) {
                appendSprites(minX, minY, minZ, width, depth, height, centerX, centerZ,
                        count, speed, time, frame.fade, red, green, blue,
                        leftX, leftZ, upX, upY, upZ, 1.8f, 0.22f);
                flush(frame.view, settings.throughWalls, target, glowTexture);
            }

            DATA.clear();
            vertexCount = 0;
            appendSprites(minX, minY, minZ, width, depth, height, centerX, centerZ,
                    count, speed, time, frame.fade, red, green, blue,
                    leftX, leftZ, upX, upY, upZ, 1.0f, 0.72f);
            flush(frame.view, settings.throughWalls, target, ringTexture);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Textured Ghost ESP renderer failed and was disabled until re-enabled", error);
        }
    }

    private static void appendSprites(float minX, float minY, float minZ,
            float width, float depth, float height, float centerX, float centerZ,
            int count, float speed, double time, float fade,
            int red, int green, int blue,
            double leftX, double leftZ, double upX, double upY, double upZ,
            float sizeMultiplier, float alphaMultiplier) {
        for (int i = 0; i < count; i++) {
            int orbit = i % 3;
            double phase = time * speed * (orbit == 1 ? -1.15 : 1.0)
                    + (Math.PI * 2.0 * i / count) + orbit * 1.7;

            double lift = fract(time * speed * 0.22 + i * 0.6180339887498949);
            float y = minY + (float) (0.12 + lift * 0.78) * height;
            float radiusX = width * (0.42f + orbit * 0.13f) + 0.12f;
            float radiusZ = depth * (0.42f + orbit * 0.13f) + 0.12f;
            float x = centerX + (float) Math.cos(phase) * radiusX;
            float z = centerZ + (float) Math.sin(phase) * radiusZ;

            float pulse = 0.82f + 0.18f * (float) Math.sin(time * speed * 2.4 + i);
            float size = (0.28f + orbit * 0.065f) * pulse * sizeMultiplier;
            float alpha = fade * (0.28f + 0.58f * (float) Math.sin(Math.PI * lift));
            alpha *= (0.78f + 0.22f * pulse) * alphaMultiplier;

            float rotation = (float) (phase * 0.38 + Math.sin(phase * 0.7) * 0.25);
            drawBillboard(x, y, z, size, rotation, alpha, red, green, blue,
                    leftX, leftZ, upX, upY, upZ);
        }
    }

    /**
     * Resolve PNGs packaged from the root target_esp/ folder.
     * Explicit ghost names take priority; if the folder has differently named PNGs, use its first PNG.
     */
    private static Identifier findNamedTexture(Minecraft mc, String file) {
        Identifier id = Identifier.fromNamespaceAndPath("pvpvisuals", TEXTURE_DIRECTORY + "/" + file);
        return mc.getResourceManager().getResource(id).isPresent() ? id : null;
    }

    private static Identifier findSprite(Minecraft mc) {
        long now = System.nanoTime();
        if (spriteId != null && now < nextTextureScan
                && mc.getResourceManager().getResource(spriteId).isPresent()) {
            return spriteId;
        }
        if (now < nextTextureScan) {
            return null;
        }
        nextTextureScan = now + 2_000_000_000L;

        Map<Identifier, ?> files = mc.getResourceManager().listResources(TEXTURE_DIRECTORY,
                id -> id.getNamespace().equals("pvpvisuals") && id.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".png"));

        List<Identifier> ids = files.keySet().stream()
                .sorted(Comparator.comparingInt(GhostEspRenderer::texturePriority)
                        .thenComparing(Identifier::getPath))
                .toList();
        spriteId = ids.isEmpty() ? null : ids.get(0);
        if (spriteId != null) {
            warnedMissing = false;
            LOG.info("Using custom Ghost ESP sprite: {}", spriteId);
        }
        return spriteId;
    }

    private static int texturePriority(Identifier id) {
        String path = id.getPath().toLowerCase(java.util.Locale.ROOT);
        String file = path.substring(path.lastIndexOf('/') + 1);
        return switch (file) {
            case "target-esp1.png" -> 0;
            case "ghost.png" -> 1;
            case "ghost_glow.png" -> 2;
            case "ghost_particle.png" -> 3;
            case "spirit.png" -> 4;
            case "ghost_trail.png" -> 5;
            case "soul.png" -> 6;
            case "target-esp2.png" -> 7;
            case "glow.png" -> 8;
            default -> 20;
        };
    }

    private static void drawBillboard(float cx, float cy, float cz, float size, float rotation, float alpha,
            int red, int green, int blue,
            double leftX, double leftZ, double upX, double upY, double upZ) {
        if (alpha <= 0.002f || DATA.remaining() < 4 * VERTEX_BYTES) {
            return;
        }

        float c = (float) Math.cos(rotation);
        float s = (float) Math.sin(rotation);
        float half = size * 0.5f;
        float[][] corners = {
            {-half, -half, 0f, 1f},
            { half, -half, 1f, 1f},
            { half,  half, 1f, 0f},
            {-half,  half, 0f, 0f}
        };

        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        for (float[] corner : corners) {
            float px = corner[0] * c - corner[1] * s;
            float py = corner[0] * s + corner[1] * c;
            float x = cx + (float) (leftX * px + upX * py);
            float y = cy + (float) (upY * py);
            float z = cz + (float) (leftZ * px + upZ * py);
            DATA.putFloat(x).putFloat(y).putFloat(z);
            DATA.putFloat(corner[2]).putFloat(corner[3]);
            DATA.put((byte) red).put((byte) green).put((byte) blue).put((byte) a);
            vertexCount++;
        }
    }

    private static void flush(Matrix4f view, boolean throughWalls, RenderTarget target, AbstractTexture texture) {
        if (vertexCount == 0) {
            return;
        }

        DATA.flip();
        GpuDevice device = RenderSystem.getDevice();
        if (vertexBuffer == null) {
            vertexBuffer = device.createBuffer(() -> "PvPVisuals textured Ghost ESP",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);
        try (RenderPass pass = encoder.createRenderPass(() -> "PvPVisuals textured Ghost ESP",
                target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline(throughWalls));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", texture.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    private static double fract(double value) {
        return value - Math.floor(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
