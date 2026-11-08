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
 * Аура-ESP как на скриншоте TargetESP (anomalith): три светящихся кольца вокруг игрока.
 * <ul>
 *   <li>Горизонтальное кольцо у ног</li>
 *   <li>Вертикальная дуга по оси X (спереди/сзади)</li>
 *   <li>Вертикальная дуга по оси Z (слева/справа), слегка наклонённая</li>
 * </ul>
 * Каждое кольцо — это 3D-трубка (torus tube mesh) с UV-координатами для шейдера аур.
 * Шейдер target_aura.vsh/fsh даёт мягкое свечение и пульсацию по длине дуги.
 */
public final class AuraEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp/aura");

    // Геометрия трубки: 48 сегментов по длине, 10 граней по периметру — хороший баланс качество/перформанс
    private static final int ARC_SEG   = 48;   // сегменты по длине дуги
    private static final int TUBE_SIDE = 10;   // грани вокруг сечения трубки
    private static final int QUADS     = ARC_SEG * TUBE_SIDE;
    private static final int VERTS     = QUADS * 4;

    // POSITION (3×4=12) + UV0 (2×4=8) + COLOR (4×1=4) = 24 байт на вершину (DefaultVertexFormat.POSITION_TEX_COLOR)
    private static final int VERTEX_BYTES = 24;
    private static final int BUFFER_BYTES = VERTS * VERTEX_BYTES * 6; // 6 колец максимум

    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());
    private static final Vector4f WHITE  = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO   = new Vector3f();
    private static final Matrix4f IDENT  = new Matrix4f();

    private static GpuBuffer gpuBuffer;
    private static RenderPipeline pipeline;
    private static RenderPipeline pipelineXray;
    private static boolean failed;
    private static int quadCount;

    private AuraEspRenderer() {}

    public static void reset() {
        failed = false;
        pipeline = null;
        pipelineXray = null;
    }

    // ─────────────────────────── pipeline ────────────────────────────

    private static RenderPipeline pipeline(boolean xray) {
        if (xray) {
            if (pipelineXray == null) {
                pipelineXray = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/target_aura_xray"))
                        .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_aura"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_aura"))
                        .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .build();
            }
            return pipelineXray;
        }
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals", "pipeline/target_aura"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_aura"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/target_aura"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
        }
        return pipeline;
    }

    // ─────────────────────────── entry point ─────────────────────────

    public static void render(WorldRenderContext context) {
        TargetEspModule.Frame frame = TargetEspModule.frame();
        if (!frame.visible || failed) return;

        try {
            TargetEspSettings s = PvpConfig.get().targetEspSettings;
            Minecraft mc = Minecraft.getInstance();
            RenderTarget target = mc.getMainRenderTarget();
            if (target == null || target.height <= 0) return;

            float[] b = frame.bounds;
            float cx = (b[0] + b[3]) * 0.5f;
            float cy = (b[1] + b[4]) * 0.5f;
            float cz = (b[2] + b[5]) * 0.5f;
            float width  = Math.max(0.3f, b[3] - b[0]);
            float height = Math.max(0.3f, b[4] - b[1]);
            float depth  = Math.max(0.3f, b[5] - b[2]);

            // Радиусы аур: чуть выходят за hitbox
            float rx = width  * 0.5f + 0.14f;
            float rz = depth  * 0.5f + 0.14f;
            float ry = height * 0.5f + 0.14f;
            float tube = clamp(s.lineThickness * 0.055f, 0.04f, 0.18f);

            int rgb  = TargetEspRenderer.resolveColor(s, frame);
            float r  = ((rgb >> 16) & 255) / 255f;
            float g  = ((rgb >> 8)  & 255) / 255f;
            float bl = ( rgb        & 255) / 255f;
            float a  = clamp01(frame.fade * 0.82f);

            // Анимация: скорость вращения зависит от animationSpeed
            long now = System.nanoTime();
            double t  = now / 1_000_000_000.0 * clamp(s.animationSpeed, 0.1f, 4.0f);
            float rot1 = (float) (t * 0.55);
            float rot2 = (float) (t * 0.72 + Math.PI * 0.5);
            float rot3 = (float) (t * 0.41 + Math.PI);

            DATA.clear();
            quadCount = 0;

            // 1. Горизонтальное кольцо у нижней трети тела
            float ringY = b[1] + height * 0.28f;
            addRing(cx, ringY, cz,  rx, rz,  tube, r, g, bl, a,
                    new float[]{1,0,0}, new float[]{0,0,1}, rot1);

            // 2. Вертикальная дуга, ось X (плоскость XY)
            addRing(cx, cy, cz,  ry, rz,  tube, r, g, bl, a,
                    new float[]{0,1,0}, new float[]{0,0,1}, rot2);

            // 3. Вертикальная дуга, ось Z (плоскость YZ), наклон 25°
            float an = 0.436f; // ~25°
            float[] ax3u = {(float)Math.cos(an), (float)Math.sin(an), 0};
            float[] ax3v = {0, 0, 1};
            addRing(cx, cy, cz,  ry, rx,  tube, r, g, bl, a,  ax3u, ax3v, rot3);

            if (quadCount == 0) return;

            flush(frame.view, s.throughWalls, target);

        } catch (Throwable err) {
            failed = true;
            LOG.error("AuraEspRenderer failed, disabling until module retoggle", err);
        }
    }

    // ─────────────────────── ring builder ────────────────────────────

    /**
     * Добавляет кольцо (тор) в буфер.
     * @param cx,cy,cz   центр кольца
     * @param ru,rv      радиусы по осям u и v
     * @param tube       радиус сечения трубки
     * @param axU,axV    два вектора плоскости кольца (не обязательно ортогональны, нормируются)
     * @param phase      начальный угол (анимация вращения)
     */
    private static void addRing(float cx, float cy, float cz,
                                 float ru, float rv, float tube,
                                 float r, float g, float b, float a,
                                 float[] axU, float[] axV,
                                 float phase) {
        // нормируем оси
        float[] u = norm(axU);
        float[] v = norm(axV);
        // нормаль к плоскости кольца
        float[] n = cross(u, v);
        n = norm(n);

        for (int seg = 0; seg < ARC_SEG; seg++) {
            float t0 = (float) seg       / ARC_SEG;
            float t1 = (float)(seg + 1)  / ARC_SEG;
            double a0 = t0 * Math.PI * 2 + phase;
            double a1 = t1 * Math.PI * 2 + phase;

            // Центры сечений на кольце
            float px0 = cx + (float)(Math.cos(a0) * ru) * u[0] + (float)(Math.sin(a0) * rv) * v[0];
            float py0 = cy + (float)(Math.cos(a0) * ru) * u[1] + (float)(Math.sin(a0) * rv) * v[1];
            float pz0 = cz + (float)(Math.cos(a0) * ru) * u[2] + (float)(Math.sin(a0) * rv) * v[2];
            float px1 = cx + (float)(Math.cos(a1) * ru) * u[0] + (float)(Math.sin(a1) * rv) * v[0];
            float py1 = cy + (float)(Math.cos(a1) * ru) * u[1] + (float)(Math.sin(a1) * rv) * v[1];
            float pz1 = cz + (float)(Math.cos(a1) * ru) * u[2] + (float)(Math.sin(a1) * rv) * v[2];

            // Вектор "вперёд" вдоль кольца
            float[] fwd0 = norm(new float[]{px1-px0, py1-py0, pz1-pz0});
            float[] fwd1 = fwd0;

            for (int side = 0; side < TUBE_SIDE; side++) {
                float s0 = (float) side       / TUBE_SIDE;
                float s1 = (float)(side + 1)  / TUBE_SIDE;
                double b0 = s0 * Math.PI * 2;
                double b1 = s1 * Math.PI * 2;

                // Четыре угла одного quad'а трубки
                // Нормали сечения: комбинация вектора «радиус» и нормали плоскости кольца
                // В UV: x=позиция вдоль дуги, y=позиция вокруг трубки (-1..1)
                float[] ra0 = sectionOffset(fwd0, n, b0, tube);
                float[] ra1 = sectionOffset(fwd0, n, b1, tube);
                float[] rb0 = sectionOffset(fwd1, n, b0, tube);
                float[] rb1 = sectionOffset(fwd1, n, b1, tube);

                // UV.y: -1 у края, 0 у центра (нормализуем sin угла сечения)
                float wy0 = (float)Math.sin(b0);
                float wy1 = (float)Math.sin(b1);

                quad(
                    px0+ra0[0], py0+ra0[1], pz0+ra0[2],  t0, wy0,
                    px1+rb0[0], py1+rb0[1], pz1+rb0[2],  t1, wy0,
                    px1+rb1[0], py1+rb1[1], pz1+rb1[2],  t1, wy1,
                    px0+ra1[0], py0+ra1[1], pz0+ra1[2],  t0, wy1,
                    r, g, b, a
                );
            }
        }
    }

    /** Вектор смещения вокруг сечения трубки. fwd = вектор вдоль кольца, refN = нормаль к плоскости. */
    private static float[] sectionOffset(float[] fwd, float[] refN, double angle, float radius) {
        // Два базисных вектора сечения: perp1 ⊥ fwd (лежит в плоскости fwd & refN), perp2 = fwd × perp1
        float[] perp1 = norm(cross(fwd, refN));
        float[] perp2 = norm(cross(fwd, perp1));
        float c = (float)Math.cos(angle);
        float s = (float)Math.sin(angle);
        return new float[]{
            (perp1[0]*c + perp2[0]*s) * radius,
            (perp1[1]*c + perp2[1]*s) * radius,
            (perp1[2]*c + perp2[2]*s) * radius,
        };
    }

    // ─────────────────────── geometry helpers ────────────────────────

    private static void quad(
            float ax, float ay, float az, float au, float av,
            float bx, float by, float bz, float bu, float bv,
            float cx, float cy, float cz, float cu, float cv,
            float dx, float dy, float dz, float du, float dv,
            float r, float g, float bl, float a) {
        if (DATA.remaining() < 4 * VERTEX_BYTES) return;
        vert(ax, ay, az, au, av, r, g, bl, a);
        vert(bx, by, bz, bu, bv, r, g, bl, a);
        vert(cx, cy, cz, cu, cv, r, g, bl, a);
        vert(dx, dy, dz, du, dv, r, g, bl, a);
        quadCount++;
    }

    private static void vert(float x, float y, float z, float u, float v,
                              float r, float g, float b, float a) {
        if (DATA.remaining() < VERTEX_BYTES) return;
        DATA.putFloat(x).putFloat(y).putFloat(z);
        DATA.putFloat(u).putFloat(v);
        DATA.put(toB(r)).put(toB(g)).put(toB(b)).put(toB(a));
    }

    private static byte toB(float v) { return (byte)(int)(clamp01(v) * 255f + 0.5f); }

    // ─────────────────────── GPU flush ───────────────────────────────

    private static void flush(Matrix4f view, boolean xray, RenderTarget target) {
        if (quadCount == 0) return;
        DATA.flip();
        GpuDevice device = RenderSystem.getDevice();
        if (gpuBuffer == null) {
            gpuBuffer = device.createBuffer(() -> "PvPVisuals AuraESP",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, BUFFER_BYTES);
        }
        CommandEncoder enc = device.createCommandEncoder();
        enc.writeToBuffer(gpuBuffer.slice(0, DATA.remaining()), DATA);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(view, WHITE, ZERO, IDENT);
        try (RenderPass pass = enc.createRenderPass(() -> "PvPVisuals AuraESP",
                target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline(xray));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, gpuBuffer);
            pass.draw(0, quadCount * 4);
        }
    }

    // ─────────────────────── math utils ──────────────────────────────

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
            a[1]*b[2] - a[2]*b[1],
            a[2]*b[0] - a[0]*b[2],
            a[0]*b[1] - a[1]*b[0]
        };
    }

    private static float[] norm(float[] v) {
        float len = (float)Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len < 1e-7f) return new float[]{0, 1, 0};
        return new float[]{v[0]/len, v[1]/len, v[2]/len};
    }

    private static float clamp01(float v)                      { return Math.max(0f, Math.min(1f, v)); }
    private static float clamp(float v, float lo, float hi)   { return Math.max(lo, Math.min(hi, v)); }
}
