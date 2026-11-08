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
 * Ghost-style Target ESP inspired by the original TargetESP Modrinth gallery.
 * Three short, smoky energy wisps orbit the target's waist. Each wisp is built
 * from a tapered 3D tube mesh with a soft glow shell and drifting sparks.
 * No flat sprites, rectangular quads facing the camera, or hard-cut texture masks.
 */
public final class GhostEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp/ghosts");

    private static final int WISP_COUNT = 3;
    private static final int ARC_SEGMENTS = 42;
    private static final int TUBE_SIDES = 8;
    private static final int MAX_SPARKS = 18;
    private static final int SPARK_LAT = 4;
    private static final int SPARK_LON = 7;
    private static final int MAX_QUADS = 10_000;

    /** POSITION (3 floats) + UV (2 floats) + COLOR (4 normalized bytes). */
    private static final int VERTEX_BYTES = 24;
    private static final int BUFFER_BYTES = MAX_QUADS * 4 * VERTEX_BYTES;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());

    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    // The gallery's default appearance is a pink-violet glow. The configured Target ESP
    // color is blended in lightly so the user's chosen color mode still affects the effect.
    private static final int[] WISP_COLORS = {0xFF55C9, 0xBA58FF, 0x7967FF};

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];
    private static GpuBuffer vertexBuffer;
    private static int vertexCount;
    private static boolean failed;

    private GhostEspRenderer() { }

    private static final class Vec3 {
        final float x, y, z;

        Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        Vec3 sub(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        Vec3 mul(float value) {
            return new Vec3(x * value, y * value, z * value);
        }

        float length() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        Vec3 normalized() {
            float len = length();
            return len < 1.0e-6f ? new Vec3(0, 1, 0) : mul(1.0f / len);
        }

        Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }
    }

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
            float cameraX = (float) (-Math.sin(yaw));
            float cameraZ = (float) Math.cos(yaw);

            int accent = TargetEspRenderer.resolveColor(settings, frame);
            float speed = clamp(settings.animationSpeed, 0.1f, 4.0f);
            double time = System.nanoTime() / 1_000_000_000.0;
            double orbitPhase = time * speed * 0.92;

            // Gallery reference places the energy around the waist/hips, not around the
            // head or feet. Keep the ribbons in a narrow horizontal band with gentle bobbing.
            float waistY = minY + height * 0.52f;
            float radiusX = width * 0.56f + 0.19f;
            float radiusZ = depth * 0.56f + 0.19f;

            DATA.clear();
            vertexCount = 0;

            for (int wisp = 0; wisp < WISP_COUNT; wisp++) {
                int color = blendColor(WISP_COLORS[wisp], accent, 0.22f);
                float red = ((color >> 16) & 255) / 255.0f;
                float green = ((color >> 8) & 255) / 255.0f;
                float blue = (color & 255) / 255.0f;
                double phase = orbitPhase + wisp * (Math.PI * 2.0 / WISP_COUNT);

                // Outer volume is deliberately faint and wider, giving the edge a smoky falloff.
                appendWispTube(centerX, centerZ, waistY, radiusX, radiusZ, phase,
                        2.20f, 0.105f, frame.fade * 0.16f,
                        red, green, blue, cameraX, cameraZ, 0.52f);

                // The bright inner core is a smaller, tapered volumetric strand.
                appendWispTube(centerX, centerZ, waistY, radiusX, radiusZ, phase,
                        2.20f, 0.044f, frame.fade * 0.76f,
                        red, green, blue, cameraX, cameraZ, 0.24f);

                // A bright moving head anchors each wisp; it is a small glowing mote, not a ghost model.
                Vec3 head = sampleWisp(centerX, centerZ, waistY, radiusX, radiusZ, phase, 1.0, 0.52f);
                appendEllipsoid(head.x, head.y, head.z,
                        0.070f, 0.055f, 0.070f,
                        red, green, blue, frame.fade * 0.86f,
                        cameraX, 0.0f, cameraZ, 5, 8);

                // Fine sparks detach from the fading part of the trail.
                int sparks = Math.max(6, Math.min(MAX_SPARKS, settings.ghostCount / 2));
                for (int i = 0; i < sparks; i++) {
                    double age = (i + 1.0) / (sparks + 1.0);
                    Vec3 p = sampleWisp(centerX, centerZ, waistY, radiusX, radiusZ,
                            phase, 1.0 - age, 0.52f);
                    float drift = (float) Math.sin(time * 2.0 + i * 1.37 + wisp * 2.0) * 0.055f;
                    float size = 0.012f + (float) (1.0 - age) * 0.018f;
                    float alpha = frame.fade * (float) Math.pow(1.0 - age, 1.4) * 0.40f;
                    appendEllipsoid(p.x + drift, p.y + drift * 0.6f, p.z - drift,
                            size, size * 1.3f, size,
                            red, green, blue, alpha,
                            cameraX, 0.0f, cameraZ, SPARK_LAT, SPARK_LON);
                }
            }

            flush(frame.view, settings.throughWalls, target);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Ghost ESP wisp renderer failed and was disabled until re-enabled", error);
        }
    }

    private static Vec3 sampleWisp(float centerX, float centerZ, float waistY,
            float radiusX, float radiusZ, double phase, double t, float verticalWave) {
        // A partial orbit rather than a closed ring leaves visible airy gaps, like the gallery.
        double angle = phase + (t - 1.0) * 2.20;
        float radialWave = 1.0f + 0.055f * (float) Math.sin(angle * 3.0 + phase * 0.35);
        float x = centerX + (float) Math.cos(angle) * radiusX * radialWave;
        float z = centerZ + (float) Math.sin(angle) * radiusZ * radialWave;
        float y = waistY
                + (float) Math.sin(angle * 1.65 + phase * 0.55) * verticalWave
                + (float) Math.sin(t * Math.PI) * 0.045f;
        return new Vec3(x, y, z);
    }

    private static void appendWispTube(float centerX, float centerZ, float waistY,
            float radiusX, float radiusZ, double phase, double sweep, float tubeRadius,
            float baseAlpha, float red, float green, float blue,
            float cameraX, float cameraZ, float wave) {
        Vec3[] previous = null;
        Vec3[] previousNormals = null;

        for (int segment = 0; segment <= ARC_SEGMENTS; segment++) {
            float t = segment / (float) ARC_SEGMENTS;
            Vec3 center = sampleWisp(centerX, centerZ, waistY, radiusX, radiusZ, phase, t, wave);

            double angle = phase + (t - 1.0) * sweep;
            float eps = 0.008f;
            Vec3 before = sampleWisp(centerX, centerZ, waistY, radiusX, radiusZ, phase, Math.max(0, t - eps), wave);
            Vec3 after = sampleWisp(centerX, centerZ, waistY, radiusX, radiusZ, phase, Math.min(1, t + eps), wave);
            Vec3 tangent = after.sub(before).normalized();

            Vec3 referenceUp = new Vec3(0.0f, 1.0f, 0.0f);
            Vec3 side = tangent.cross(referenceUp).normalized();
            if (side.length() < 1.0e-5f) {
                side = new Vec3(1.0f, 0.0f, 0.0f);
            }
            Vec3 normal = tangent.cross(side).normalized();

            // Rounded ends are tapered smoothly instead of being cut off by a quad boundary.
            float envelope = (float) Math.pow(Math.sin(Math.PI * t), 0.62);
            float pulse = 0.88f + 0.12f * (float) Math.sin(angle * 2.0 + phase);
            float radius = tubeRadius * (0.10f + 0.90f * envelope) * pulse;
            float alpha = baseAlpha * (0.08f + 0.92f * envelope);

            Vec3[] ring = new Vec3[TUBE_SIDES + 1];
            Vec3[] normals = new Vec3[TUBE_SIDES + 1];
            for (int sideIndex = 0; sideIndex <= TUBE_SIDES; sideIndex++) {
                float a = (float) (Math.PI * 2.0 * sideIndex / TUBE_SIDES);
                Vec3 radial = side.mul((float) Math.cos(a)).add(normal.mul((float) Math.sin(a))).normalized();
                ring[sideIndex] = center.add(radial.mul(radius));
                normals[sideIndex] = radial;
            }

            if (previous != null && previousNormals != null) {
                float u0 = (segment - 1) / (float) ARC_SEGMENTS;
                float u1 = t;
                for (int sideIndex = 0; sideIndex < TUBE_SIDES; sideIndex++) {
                    Vec3 p0 = previous[sideIndex];
                    Vec3 p1 = previous[sideIndex + 1];
                    Vec3 p2 = ring[sideIndex + 1];
                    Vec3 p3 = ring[sideIndex];

                    emitQuad(
                            p0, p1, p2, p3,
                            previousNormals[sideIndex], previousNormals[sideIndex + 1],
                            normals[sideIndex + 1], normals[sideIndex],
                            sideIndex / (float) TUBE_SIDES, u0,
                            (sideIndex + 1) / (float) TUBE_SIDES, u0,
                            (sideIndex + 1) / (float) TUBE_SIDES, u1,
                            sideIndex / (float) TUBE_SIDES, u1,
                            red, green, blue,
                            alpha, alpha, baseAlpha * (0.08f + 0.92f * (float) Math.pow(Math.sin(Math.PI * Math.max(0, t - 1.0f / ARC_SEGMENTS)), 0.62)) * pulse,
                            cameraX, 0.0f, cameraZ);
                }
            }

            previous = ring;
            previousNormals = normals;
        }
    }

    private static void appendEllipsoid(float cx, float cy, float cz,
            float rx, float ry, float rz,
            float red, float green, float blue, float alpha,
            float cameraX, float cameraY, float cameraZ, int latSegments, int lonSegments) {
        for (int lat = 0; lat < latSegments; lat++) {
            float lat0 = (float) (-Math.PI * 0.5 + Math.PI * lat / latSegments);
            float lat1 = (float) (-Math.PI * 0.5 + Math.PI * (lat + 1) / latSegments);
            for (int lon = 0; lon < lonSegments; lon++) {
                float a0 = (float) (Math.PI * 2.0 * lon / lonSegments);
                float a1 = (float) (Math.PI * 2.0 * (lon + 1) / lonSegments);
                float c0 = (float) Math.cos(lat0), s0 = (float) Math.sin(lat0);
                float c1 = (float) Math.cos(lat1), s1 = (float) Math.sin(lat1);
                float x00 = c0 * (float) Math.cos(a0), z00 = c0 * (float) Math.sin(a0);
                float x01 = c0 * (float) Math.cos(a1), z01 = c0 * (float) Math.sin(a1);
                float x10 = c1 * (float) Math.cos(a0), z10 = c1 * (float) Math.sin(a0);
                float x11 = c1 * (float) Math.cos(a1), z11 = c1 * (float) Math.sin(a1);

                emitQuad(
                        new Vec3(cx + x00 * rx, cy + s0 * ry, cz + z00 * rz),
                        new Vec3(cx + x01 * rx, cy + s0 * ry, cz + z01 * rz),
                        new Vec3(cx + x11 * rx, cy + s1 * ry, cz + z11 * rz),
                        new Vec3(cx + x10 * rx, cy + s1 * ry, cz + z10 * rz),
                        new Vec3(x00, s0, z00), new Vec3(x01, s0, z01),
                        new Vec3(x11, s1, z11), new Vec3(x10, s1, z10),
                        (float) lon / lonSegments, (float) lat / latSegments,
                        (float) (lon + 1) / lonSegments, (float) lat / latSegments,
                        (float) (lon + 1) / lonSegments, (float) (lat + 1) / latSegments,
                        (float) lon / lonSegments, (float) (lat + 1) / latSegments,
                        red, green, blue, alpha, alpha, alpha, alpha,
                        cameraX, cameraY, cameraZ);
            }
        }
    }

    private static void emitQuad(
            Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
            Vec3 n0, Vec3 n1, Vec3 n2, Vec3 n3,
            float u0, float v0, float u1, float v1,
            float u2, float v2, float u3, float v3,
            float red, float green, float blue,
            float a0, float a1, float a2, float a3,
            float cameraX, float cameraY, float cameraZ) {
        if (DATA.remaining() < 4 * VERTEX_BYTES) {
            return;
        }
        putLitVertex(p0,u0,v0,n0,red,green,blue,a0,cameraX,cameraY,cameraZ);
        putLitVertex(p1,u1,v1,n1,red,green,blue,a1,cameraX,cameraY,cameraZ);
        putLitVertex(p2,u2,v2,n2,red,green,blue,a2,cameraX,cameraY,cameraZ);
        putLitVertex(p3,u3,v3,n3,red,green,blue,a3,cameraX,cameraY,cameraZ);
        vertexCount += 4;
    }

    private static void putLitVertex(Vec3 p, float u, float v, Vec3 n,
            float red, float green, float blue, float alpha,
            float cameraX, float cameraY, float cameraZ) {
        float light = Math.max(0.0f, n.x * -0.35f + n.y * 0.72f + n.z * 0.60f);
        float facing = Math.max(0.0f, n.x * cameraX + n.y * cameraY + n.z * cameraZ);
        float rim = (float) Math.pow(1.0f - facing, 2.0);
        float shade = 0.50f + 0.42f * light + 0.22f * rim;
        DATA.putFloat(p.x).putFloat(p.y).putFloat(p.z);
        DATA.putFloat(u).putFloat(v);
        DATA.put((byte) Math.round(clamp(red * shade, 0.0f, 1.0f) * 255.0f));
        DATA.put((byte) Math.round(clamp(green * shade, 0.0f, 1.0f) * 255.0f));
        DATA.put((byte) Math.round(clamp(blue * shade, 0.0f, 1.0f) * 255.0f));
        DATA.put((byte) Math.round(clamp(alpha, 0.0f, 1.0f) * 255.0f));
    }

    private static void flush(Matrix4f view, boolean throughWalls, RenderTarget target) {
        if (vertexCount == 0) {
            return;
        }

        DATA.flip();
        GpuDevice device = RenderSystem.getDevice();
        if (vertexBuffer == null) {
            vertexBuffer = device.createBuffer(() -> "PvPVisuals Ghost ESP volume wisps",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);
        try (RenderPass pass = encoder.createRenderPass(() -> "PvPVisuals Ghost ESP volume wisps",
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
