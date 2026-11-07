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
 * GPU renderer for Target ESP. Uses the same 1.21.11 pipeline family as HitBox,
 * but owns a separate buffer and settings so the two modules remain independent.
 */
public final class TargetEspRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp");
    private static final int VERTEX_BYTES = 16;
    private static final int BUFFER_BYTES = 1_048_576;
    private static final ByteBuffer DATA = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[2];

    private static GpuBuffer vertexBuffer;
    private static boolean failed;
    private static int vertexCount;

    private TargetEspRenderer() { }

    public static void reset() {
        failed = false;
        PIPELINES[0] = null;
        PIPELINES[1] = null;
        GhostEspRenderer.reset();
    }

    private static RenderPipeline pipeline(boolean throughWalls) {
        int index = throughWalls ? 1 : 0;
        if (PIPELINES[index] == null) {
            PIPELINES[index] = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("pvpvisuals",
                            "pipeline/target_esp" + (throughWalls ? "_xray" : "")))
                    .withVertexShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/hitbox"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("pvpvisuals", "core/hitbox"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build();
        }
        return PIPELINES[index];
    }

    public static void render(WorldRenderContext context) {
        TargetEspModule.Frame frame = TargetEspModule.frame();
        if (!frame.visible || failed) {
            return;
        }

        try {
            TargetEspSettings settings = PvpConfig.get().targetEspSettings;
            // Ghosts are native Minecraft particles emitted during END_EXTRACTION.
            // Never submit ESP geometry for this style.
            TargetEspMode selectedMode = TargetEspMode.byName(settings.mode);
            if (selectedMode == TargetEspMode.GHOSTS) {
                GhostEspRenderer.render(context);
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            RenderTarget target = mc.getMainRenderTarget();
            if (target == null || target.height <= 0) {
                return;
            }

            float fov = (float) Math.toRadians(mc.options.fov().get());
            float worldPerPixel = (float) (2.0 * Math.tan(fov / 2.0) / target.height);
            float[] b = frame.bounds;
            float x0 = b[0], y0 = b[1], z0 = b[2];
            float x1 = b[3], y1 = b[4], z1 = b[5];
            float width = Math.max(0.1f, x1 - x0);
            float depth = Math.max(0.1f, z1 - z0);
            float height = Math.max(0.1f, y1 - y0);
            float cx = (x0 + x1) * 0.5f;
            float cy = (y0 + y1) * 0.5f;
            float cz = (z0 + z1) * 0.5f;

            int rgb = resolveColor(settings, frame);
            int r = (rgb >> 16) & 255;
            int g = (rgb >> 8) & 255;
            int bl = rgb & 255;
            int alpha = clampByte(Math.round(255f * frame.fade));
            float thickness = clamp(settings.lineThickness, 0.5f, 5.0f);

            DATA.clear();
            vertexCount = 0;
            TargetEspMode mode = TargetEspMode.byName(settings.mode);
            long now = System.nanoTime();

            switch (mode) {
                case GHOSTS -> { /* handled by GhostEspRenderer before the geometry modes */ }
                case CIRCLE -> drawCircle(cx, y0, cz, width, depth, now,
                        frame.health, settings, worldPerPixel, r, g, bl, alpha);
                case SQUARE -> drawRotatingSquare(cx, cy, cz, width, depth, height, now,
                        settings, worldPerPixel, r, g, bl, alpha);
                case BOX_3D -> {
                    if (settings.fillBox) {
                        int fillA = clampByte(Math.round(settings.fillAlpha * frame.fade * 255f));
                        solidBox(x0, y0, z0, x1, y1, z1, r, g, bl, fillA);
                    }
                    wireBox(x0, y0, z0, x1, y1, z1, thickness, worldPerPixel, r, g, bl, alpha);
                }
            }

            flush(frame.view, settings.throughWalls, target);
        } catch (Throwable error) {
            failed = true;
            LOG.error("Target ESP renderer failed and was disabled until the module is toggled again", error);
        }
    }

    static int resolveColor(TargetEspSettings s, TargetEspModule.Frame f) {
        if (f.invisible && s.useInvisibleColor) {
            return s.invisibleColor;
        }
        if (f.allied) {
            return s.friendColor;
        }

        String mode = s.colorMode == null ? "HEALTH" : s.colorMode.toUpperCase();
        return switch (mode) {
            case "STATIC" -> s.color;
            case "DISTANCE" -> blend(0x46E6A0, 0xFF5C68,
                    clamp01(f.distance / Math.max(1.0f, s.maxDistance)));
            case "RAINBOW" -> rainbow(System.nanoTime() / 1_000_000_000.0 * clamp(s.animationSpeed, 0.1f, 4.0f));
            case "HEALTH" -> healthColor(f.health);
            default -> s.color;
        };
    }

    private static int healthColor(float health) {
        float h = clamp01(health);
        return h >= 0.5f
                ? blend(0xF5C85B, 0x48E69C, (h - 0.5f) * 2.0f)
                : blend(0xFF5968, 0xF5C85B, h * 2.0f);
    }

    private static int blend(int a, int b, float t) {
        t = clamp01(t);
        int r = Math.round(((a >> 16) & 255) * (1f - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1f - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1f - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
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

    private static void drawCircle(float cx, float minY, float cz, float width, float depth, long now,
            float health, TargetEspSettings s, float wpp, int r, int g, int b, int alpha) {
        int segments = 64;
        float radiusX = Math.max(0.45f, width * 0.5f + 0.18f);
        float radiusZ = Math.max(0.45f, depth * 0.5f + 0.18f);
        float pulse = 1.0f + (float) Math.sin(now / 1_000_000_000.0 * 4.0) * 0.045f * (1.0f - clamp01(health));
        float y = minY + 0.055f;
        float prevX = cx + radiusX * pulse;
        float prevZ = cz;
        for (int i = 1; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            float x = cx + (float) Math.cos(angle) * radiusX * pulse;
            float z = cz + (float) Math.sin(angle) * radiusZ * pulse;
            segment(prevX, y, prevZ, x, y, z, Math.max(1.2f, s.lineThickness), wpp, r, g, b, alpha);
            prevX = x;
            prevZ = z;
        }
    }

    private static void drawRotatingSquare(float cx, float cy, float cz, float width, float depth, float height,
            long now, TargetEspSettings s, float wpp, int r, int g, int b, int alpha) {
        double angle = now / 1_000_000_000.0 * clamp(s.animationSpeed, 0.1f, 4.0f);
        float half = Math.max(0.4f, Math.max(width, depth) * 0.62f);
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        float[] x = new float[4];
        float[] z = new float[4];
        float[] localX = {-half, half, half, -half};
        float[] localZ = {-half, -half, half, half};
        for (int i = 0; i < 4; i++) {
            x[i] = cx + localX[i] * cos - localZ[i] * sin;
            z[i] = cz + localX[i] * sin + localZ[i] * cos;
        }
        float top = cy + height * 0.5f;
        float bottom = cy - height * 0.5f;
        for (int i = 0; i < 4; i++) {
            int n = (i + 1) & 3;
            segment(x[i], bottom, z[i], x[n], bottom, z[n], s.lineThickness, wpp, r, g, b, alpha);
            segment(x[i], top, z[i], x[n], top, z[n], s.lineThickness, wpp, r, g, b, alpha);
            segment(x[i], bottom, z[i], x[i], top, z[i], s.lineThickness, wpp, r, g, b, alpha);
        }
    }

    private static void wireBox(float x0, float y0, float z0, float x1, float y1, float z1,
            float thickness, float wpp, int r, int g, int b, int alpha) {
        float[][] p = {
            {x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},
            {x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}
        };
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
        };
        for (int[] edge : edges) {
            float[] a = p[edge[0]], c = p[edge[1]];
            segment(a[0],a[1],a[2],c[0],c[1],c[2],thickness,wpp,r,g,b,alpha);
        }
    }

    private static void solidBox(float x0, float y0, float z0, float x1, float y1, float z1,
            int r, int g, int b, int alpha) {
        if (alpha <= 0) return;
        // Six translucent faces, two triangles each. Culling is disabled in the pipeline.
        quad(x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, r,g,b,alpha);
        quad(x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, r,g,b,alpha);
        quad(x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, r,g,b,alpha);
        quad(x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, r,g,b,alpha);
        quad(x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, r,g,b,alpha);
        quad(x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, r,g,b,alpha);
    }

    private static void quad(float ax,float ay,float az, float bx,float by,float bz,
            float cx,float cy,float cz, float dx,float dy,float dz, int r,int g,int b,int a) {
        triangle(ax,ay,az,bx,by,bz,cx,cy,cz,r,g,b,a);
        triangle(ax,ay,az,cx,cy,cz,dx,dy,dz,r,g,b,a);
    }

    private static void triangle(float ax,float ay,float az, float bx,float by,float bz,
            float cx,float cy,float cz, int r,int g,int b,int a) {
        vert(ax,ay,az,r,g,b,a); vert(bx,by,bz,r,g,b,a); vert(cx,cy,cz,r,g,b,a);
    }

    /** Camera-facing ribbon; width remains close to constant in screen pixels. */
    private static void segment(float x1, float y1, float z1, float x2, float y2, float z2,
            float pixels, float wpp, int r, int g, int b, int a) {
        if (a <= 0 || DATA.remaining() < 6 * VERTEX_BYTES) return;
        float dx=x2-x1, dy=y2-y1, dz=z2-z1;
        float mx=(x1+x2)*0.5f, my=(y1+y2)*0.5f, mz=(z1+z2)*0.5f;
        float sx=dy*mz-dz*my, sy=dz*mx-dx*mz, sz=dx*my-dy*mx;
        float len=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);
        if (len < 1.0e-8f) return;
        float dist=(float)Math.sqrt(mx*mx+my*my+mz*mz);
        float k=pixels*wpp*dist*0.5f/len;
        sx*=k; sy*=k; sz*=k;
        vert(x1-sx,y1-sy,z1-sz,r,g,b,a);
        vert(x1+sx,y1+sy,z1+sz,r,g,b,a);
        vert(x2+sx,y2+sy,z2+sz,r,g,b,a);
        vert(x1-sx,y1-sy,z1-sz,r,g,b,a);
        vert(x2+sx,y2+sy,z2+sz,r,g,b,a);
        vert(x2-sx,y2-sy,z2-sz,r,g,b,a);
    }

    private static void vert(float x, float y, float z, int r, int g, int b, int a) {
        if (DATA.remaining() < VERTEX_BYTES) return;
        DATA.putFloat(x).putFloat(y).putFloat(z);
        DATA.put((byte)r).put((byte)g).put((byte)b).put((byte)a);
        vertexCount++;
    }

    private static void flush(Matrix4f view, boolean throughWalls, RenderTarget target) {
        if (vertexCount == 0) return;
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
            pass.setPipeline(pipeline(throughWalls));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer);
            pass.draw(0, vertexCount);
        }
    }

    private static int clampByte(int value) { return Math.max(0, Math.min(255, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp01(float value) { return clamp(value, 0f, 1f); }
}
