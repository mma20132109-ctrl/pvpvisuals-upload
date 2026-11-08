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
 * Volumetric three-spirit Target ESP.
 *
 * Each spirit is a real 3D mesh: an ellipsoid head, a tapered wavy body and a
 * curved chain of smaller 3D spirits. The shape is not a camera-facing sprite.
 */
public final class GhostEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp/ghosts");

    private static final int MAX_TRAIL_GHOSTS = 12;
    private static final int SPARKS_PER_WISP = 3;
    private static final int HEAD_LAT = 8;
    private static final int HEAD_LON = 12;
    private static final int BODY_RINGS = 8;
    private static final int BODY_SIDES = 12;
    private static final int SPARK_LAT = 4;
    private static final int SPARK_LON = 6;
    private static final int MAX_QUADS = 10_000;

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

    private static final class Orientation {
        final float tx, ty, tz;
        final float sx, sy, sz;
        final float fx, fy, fz;

        Orientation(float tx, float ty, float tz,
                float sx, float sy, float sz,
                float fx, float fy, float fz) {
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.fx = fx; this.fy = fy; this.fz = fz;
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
            double pitch = Math.toRadians(mc.player.getViewXRot(partialTick));
            float cameraX = (float) (Math.sin(yaw) * Math.cos(pitch));
            float cameraY = (float) Math.sin(pitch);
            float cameraZ = (float) (-Math.cos(yaw) * Math.cos(pitch));
            float cameraLeftX = (float) Math.cos(yaw);
            float cameraLeftZ = (float) Math.sin(yaw);
            float cameraUpX = -cameraY * cameraLeftZ;
            float cameraUpY = (float) (Math.cos(pitch));
            float cameraUpZ = cameraY * cameraLeftX;

            int accent = TargetEspRenderer.resolveColor(settings, frame);
            int trailCount = Math.max(4, Math.min(MAX_TRAIL_GHOSTS, settings.ghostCount / 2));
            float speed = clamp(settings.animationSpeed, 0.1f, 4.0f);
            double time = System.nanoTime() / 1_000_000_000.0;
            double headPhase = time * speed * 0.82;

            DATA.clear();
            vertexCount = 0;

            for (int wisp = 0; wisp < 3; wisp++) {
                int color = blendColor(WISP_COLORS[wisp], accent, 0.12f);
                float red = ((color >> 16) & 255) / 255.0f;
                float green = ((color >> 8) & 255) / 255.0f;
                float blue = (color & 255) / 255.0f;

                float[] head = samplePath(wisp, headPhase, centerX, centerZ, minY, width, depth, height);
                Orientation headOrientation = orientation(wisp, headPhase, centerX, centerZ,
                        minY, width, depth, height, cameraX, cameraY, cameraZ);

                // Outer low-alpha shell gives a soft aura; the inner mesh carries the solid volume.
                appendSpirit(head, headOrientation, 1.12f, frame.fade * 0.20f,
                        red, green, blue, cameraX, cameraY, cameraZ, false, false);
                appendSpirit(head, headOrientation, 1.0f, frame.fade * 0.84f,
                        red, green, blue, cameraX, cameraY, cameraZ, true, true);

                // Smaller complete 3D spirits follow a curved route and fade out individually.
                for (int trail = 1; trail <= trailCount; trail++) {
                    float age = trail / (float) (trailCount + 1);
                    double lag = age * 1.20;
                    double phase = headPhase - lag;
                    float[] p = samplePath(wisp, phase, centerX, centerZ, minY, width, depth, height);
                    Orientation direction = orientation(wisp, phase, centerX, centerZ,
                            minY, width, depth, height, cameraX, cameraY, cameraZ);
                    float sway = (float) Math.sin(time * 1.8 + trail * 1.7 + wisp * 2.1) * 0.025f;
                    p[0] += sway;
                    p[1] -= sway * 0.35f;
                    p[2] -= sway;
                    float scale = 0.56f * (1.0f - age * 0.35f);
                    float alpha = frame.fade * (float) Math.pow(1.0f - age, 1.45) * 0.46f;
                    appendSpirit(p, direction, scale, alpha,
                            red, green, blue, cameraX, cameraY, cameraZ, false, false);
                }

                // A few tiny 3D sparks float around the spirits.
                for (int spark = 0; spark < SPARKS_PER_WISP; spark++) {
                    double phase = headPhase - (0.25 + spark * 0.21);
                    float[] p = samplePath(wisp, phase, centerX, centerZ, minY, width, depth, height);
                    float drift = (float) Math.sin(time * 2.2 + spark * 2.1 + wisp) * 0.08f;
                    appendEllipsoid(p[0] + drift, p[1] + drift * 0.45f, p[2] - drift,
                            0.018f + spark * 0.004f, 0.022f + spark * 0.004f, 0.018f + spark * 0.004f,
                            cameraLeftX, 0.0f, cameraLeftZ,
                            cameraUpX, cameraUpY, cameraUpZ,
                            -cameraX, -cameraY, -cameraZ,
                            red, green, blue, frame.fade * (0.32f - spark * 0.06f),
                            -cameraX, -cameraY, -cameraZ, SPARK_LAT, SPARK_LON);
                }
            }

            flush(frame.view, settings.throughWalls, target);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Volumetric Ghost ESP renderer failed and was disabled until re-enabled", error);
        }
    }

    private static Orientation orientation(int wisp, double phase,
            float centerX, float centerZ, float minY, float width, float depth, float height,
            float cameraX, float cameraY, float cameraZ) {
        float[] before = samplePath(wisp, phase - 0.02, centerX, centerZ, minY, width, depth, height);
        float[] after = samplePath(wisp, phase + 0.02, centerX, centerZ, minY, width, depth, height);
        float tx = after[0] - before[0];
        float ty = after[1] - before[1];
        float tz = after[2] - before[2];
        float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1.0e-5f) {
            tx = 0.0f; ty = 0.0f; tz = 1.0f; len = 1.0f;
        }
        tx /= len; ty /= len; tz /= len;

        // Project the direction to the camera onto the plane perpendicular to the flight axis.
        float dot = -cameraX * tx - cameraY * ty - cameraZ * tz;
        float fx = -cameraX - tx * dot;
        float fy = -cameraY - ty * dot;
        float fz = -cameraZ - tz * dot;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1.0e-4f) {
            fx = 0.0f; fy = 0.0f; fz = 1.0f; fl = 1.0f;
        }
        fx /= fl; fy /= fl; fz /= fl;

        float sx = ty * fz - tz * fy;
        float sy = tz * fx - tx * fz;
        float sz = tx * fy - ty * fx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0e-5f) {
            sx = 1.0f; sy = 0.0f; sz = 0.0f; sl = 1.0f;
        }
        sx /= sl; sy /= sl; sz /= sl;

        // Rebuild the front vector to ensure an orthonormal 3D basis.
        fx = sy * tz - sz * ty;
        fy = sz * tx - sx * tz;
        fz = sx * ty - sy * tx;
        return new Orientation(tx, ty, tz, sx, sy, sz, fx, fy, fz);
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

    private static void appendSpirit(float[] center, Orientation o, float scale, float alpha,
            float red, float green, float blue,
            float cameraX, float cameraY, float cameraZ, boolean includeEyes, boolean inner) {
        if (alpha <= 0.002f) {
            return;
        }
        float bodyAlpha = alpha * (inner ? 0.86f : 0.42f);
        appendBody(center, o, scale, bodyAlpha,
                red, green, blue, cameraX, cameraY, cameraZ, 1.7f);
        float tailX = center[0] - o.tx * (0.415f * scale);
        float tailY = center[1] - o.ty * (0.415f * scale);
        float tailZ = center[2] - o.tz * (0.415f * scale);
        appendEllipsoid(tailX, tailY, tailZ,
                0.052f * scale, 0.046f * scale, 0.052f * scale,
                o.sx, o.sy, o.sz, o.tx, o.ty, o.tz, o.fx, o.fy, o.fz,
                red, green, blue, bodyAlpha * 0.72f,
                -cameraX, -cameraY, -cameraZ, SPARK_LAT, SPARK_LON);
        float headX = center[0] + o.tx * (0.12f * scale);
        float headY = center[1] + o.ty * (0.12f * scale);
        float headZ = center[2] + o.tz * (0.12f * scale);
        float headAlpha = alpha * (inner ? 0.96f : 0.32f);
        appendEllipsoid(headX, headY, headZ,
                0.145f * scale, 0.158f * scale, 0.132f * scale,
                o.sx, o.sy, o.sz, o.tx, o.ty, o.tz, o.fx, o.fy, o.fz,
                red, green, blue, headAlpha,
                -cameraX, -cameraY, -cameraZ, HEAD_LAT, HEAD_LON);

        if (includeEyes) {
            float eyeBaseX = headX + o.fx * (0.123f * scale) + o.tx * (0.01f * scale);
            float eyeBaseY = headY + o.fy * (0.123f * scale) + o.ty * (0.01f * scale);
            float eyeBaseZ = headZ + o.fz * (0.123f * scale) + o.tz * (0.01f * scale);
            for (int eye = -1; eye <= 1; eye += 2) {
                appendEllipsoid(
                        eyeBaseX + o.sx * eye * (0.052f * scale),
                        eyeBaseY + o.sy * eye * (0.052f * scale),
                        eyeBaseZ + o.sz * eye * (0.052f * scale),
                        0.018f * scale, 0.025f * scale, 0.009f * scale,
                        o.sx, o.sy, o.sz, o.tx, o.ty, o.tz, o.fx, o.fy, o.fz,
                        0.045f, 0.075f, 0.12f, alpha * 0.90f,
                        -cameraX, -cameraY, -cameraZ, 3, 8);
            }
        }
    }

    private static void appendBody(float[] center, Orientation o, float scale, float alpha,
            float red, float green, float blue,
            float cameraX, float cameraY, float cameraZ, float phase) {
        float[] previous = new float[(BODY_SIDES + 1) * 7];
        float[] next = new float[(BODY_SIDES + 1) * 7];
        buildBodyRing(previous, 0.0f, center, o, scale, phase);
        for (int ring = 0; ring < BODY_RINGS; ring++) {
            float t0 = ring / (float) BODY_RINGS;
            float t1 = (ring + 1) / (float) BODY_RINGS;
            buildBodyRing(next, t1, center, o, scale, phase);
            float a0 = alpha * (1.0f - t0 * 0.34f);
            float a1 = alpha * (1.0f - t1 * 0.34f);
            for (int side = 0; side < BODY_SIDES; side++) {
                int i0 = side * 7;
                int i1 = (side + 1) * 7;
                emitQuad(
                        previous[i0], previous[i0+1], previous[i0+2],
                        previous[i1], previous[i1+1], previous[i1+2],
                        next[i1], next[i1+1], next[i1+2],
                        next[i0], next[i0+1], next[i0+2],
                        previous[i0+3], previous[i0+4], previous[i0+5],
                        previous[i1+3], previous[i1+4], previous[i1+5],
                        next[i1+3], next[i1+4], next[i1+5],
                        next[i0+3], next[i0+4], next[i0+5],
                        (float) side / BODY_SIDES, t0, (float) (side+1) / BODY_SIDES, t0,
                        (float) (side+1) / BODY_SIDES, t1, (float) side / BODY_SIDES, t1,
                        red, green, blue, a0, a0, a1, a1, cameraX, cameraY, cameraZ);
            }
            float[] swap = previous; previous = next; next = swap;
        }
    }

    /** Ring values are position xyz + normal xyz + angular u. */
    private static void buildBodyRing(float[] out, float t, float[] center,
            Orientation o, float scale, float phase) {
        float localY = (0.075f - t * 0.49f) * scale;
        float wobbleSide = (float) Math.sin(t * 4.8f + phase) * 0.026f * t * scale;
        float wobbleFace = (float) Math.cos(t * 4.1f + phase) * 0.021f * t * scale;
        float radius = (0.118f - t * 0.062f) * scale;
        for (int i = 0; i <= BODY_SIDES; i++) {
            float angle = (float) (Math.PI * 2.0 * i / BODY_SIDES);
            float wave = 1.0f + (0.035f + 0.13f * t) * (float) Math.sin(angle * 3.0f + phase + t * 2.0f);
            float c = (float) Math.cos(angle);
            float sn = (float) Math.sin(angle);
            float r = radius * wave;
            int p = i * 7;
            out[p] = center[0] + o.tx * localY + o.sx * (wobbleSide + c * r)
                    + o.fx * (wobbleFace + sn * r * 0.82f);
            out[p+1] = center[1] + o.ty * localY + o.sy * (wobbleSide + c * r)
                    + o.fy * (wobbleFace + sn * r * 0.82f);
            out[p+2] = center[2] + o.tz * localY + o.sz * (wobbleSide + c * r)
                    + o.fz * (wobbleFace + sn * r * 0.82f);
            float nx = o.sx * c + o.fx * sn * 0.82f + o.tx * 0.12f;
            float ny = o.sy * c + o.fy * sn * 0.82f + o.ty * 0.12f;
            float nz = o.sz * c + o.fz * sn * 0.82f + o.tz * 0.12f;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            out[p+3] = nx / nl;
            out[p+4] = ny / nl;
            out[p+5] = nz / nl;
            out[p+6] = (float) i / BODY_SIDES;
        }
    }

    private static void appendEllipsoid(float cx, float cy, float cz,
            float radiusX, float radiusY, float radiusZ,
            float sx, float sy, float sz, float tx, float ty, float tz,
            float fx, float fy, float fz,
            float red, float green, float blue, float alpha,
            float cameraX, float cameraY, float cameraZ,
            int latSegments, int lonSegments) {
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
                        cx + sx * x00 * radiusX + tx * s0 * radiusY + fx * z00 * radiusZ,
                        cy + sy * x00 * radiusX + ty * s0 * radiusY + fy * z00 * radiusZ,
                        cz + sz * x00 * radiusX + tz * s0 * radiusY + fz * z00 * radiusZ,
                        cx + sx * x01 * radiusX + tx * s0 * radiusY + fx * z01 * radiusZ,
                        cy + sy * x01 * radiusX + ty * s0 * radiusY + fy * z01 * radiusZ,
                        cz + sz * x01 * radiusX + tz * s0 * radiusY + fz * z01 * radiusZ,
                        cx + sx * x11 * radiusX + tx * s1 * radiusY + fx * z11 * radiusZ,
                        cy + sy * x11 * radiusX + ty * s1 * radiusY + fy * z11 * radiusZ,
                        cz + sz * x11 * radiusX + tz * s1 * radiusY + fz * z11 * radiusZ,
                        cx + sx * x10 * radiusX + tx * s1 * radiusY + fx * z10 * radiusZ,
                        cy + sy * x10 * radiusX + ty * s1 * radiusY + fy * z10 * radiusZ,
                        cz + sz * x10 * radiusX + tz * s1 * radiusY + fz * z10 * radiusZ,
                        sx * x00 + tx * s0 + fx * z00,
                        sy * x00 + ty * s0 + fy * z00,
                        sz * x00 + tz * s0 + fz * z00,
                        sx * x01 + tx * s0 + fx * z01,
                        sy * x01 + ty * s0 + fy * z01,
                        sz * x01 + tz * s0 + fz * z01,
                        sx * x11 + tx * s1 + fx * z11,
                        sy * x11 + ty * s1 + fy * z11,
                        sz * x11 + tz * s1 + fz * z11,
                        sx * x10 + tx * s1 + fx * z10,
                        sy * x10 + ty * s1 + fy * z10,
                        sz * x10 + tz * s1 + fz * z10,
                        (float) lon / lonSegments, (float) lat / latSegments,
                        (float) (lon+1) / lonSegments, (float) lat / latSegments,
                        (float) (lon+1) / lonSegments, (float) (lat+1) / latSegments,
                        (float) lon / lonSegments, (float) (lat+1) / latSegments,
                        red, green, blue, alpha, alpha, alpha, alpha, cameraX, cameraY, cameraZ);
            }
        }
    }

    private static void emitQuad(
            float x0,float y0,float z0, float x1,float y1,float z1,
            float x2,float y2,float z2, float x3,float y3,float z3,
            float nx0,float ny0,float nz0, float nx1,float ny1,float nz1,
            float nx2,float ny2,float nz2, float nx3,float ny3,float nz3,
            float u0,float v0, float u1,float v1, float u2,float v2, float u3,float v3,
            float red,float green,float blue,
            float a0,float a1,float a2,float a3,
            float cameraX,float cameraY,float cameraZ) {
        if (DATA.remaining() < 4 * VERTEX_BYTES) {
            return;
        }
        putLitVertex(x0,y0,z0,u0,v0,nx0,ny0,nz0,red,green,blue,a0,cameraX,cameraY,cameraZ);
        putLitVertex(x1,y1,z1,u1,v1,nx1,ny1,nz1,red,green,blue,a1,cameraX,cameraY,cameraZ);
        putLitVertex(x2,y2,z2,u2,v2,nx2,ny2,nz2,red,green,blue,a2,cameraX,cameraY,cameraZ);
        putLitVertex(x3,y3,z3,u3,v3,nx3,ny3,nz3,red,green,blue,a3,cameraX,cameraY,cameraZ);
        vertexCount += 4;
    }

    private static void putLitVertex(float x,float y,float z,float u,float v,
            float nx,float ny,float nz,float red,float green,float blue,float alpha,
            float cameraX,float cameraY,float cameraZ) {
        float light = Math.max(0.0f, nx * -0.35f + ny * 0.72f + nz * 0.60f);
        float facing = Math.max(0.0f, nx * cameraX + ny * cameraY + nz * cameraZ);
        float rim = (float) Math.pow(1.0f - facing, 2.0);
        float shade = 0.48f + 0.48f * light + 0.22f * rim;
        DATA.putFloat(x).putFloat(y).putFloat(z);
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
            vertexBuffer = device.createBuffer(() -> "PvPVisuals volumetric Ghost ESP",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.writeToBuffer(vertexBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENTITY);
        try (RenderPass pass = encoder.createRenderPass(() -> "PvPVisuals volumetric Ghost ESP",
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
