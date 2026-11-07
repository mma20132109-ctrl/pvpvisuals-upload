package com.pvpvisuals.render.engine;

import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Mod visual render dispatcher for Minecraft 1.21.11.
 *
 * Features only enqueue geometry; this class owns the Fabric render callback,
 * obtains the shared vertex consumer and flushes all primitives together.
 * Geometry is camera-relative and uses the current world pose matrix.
 */
public final class SvintusRenderEngine {
    private static final CopyOnWriteArrayList<RenderFeature> FEATURES = new CopyOnWriteArrayList<>();
    private static final RenderQueue QUEUE = new RenderQueue();
    private static boolean initialized;

    private SvintusRenderEngine() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.END_MAIN.register(SvintusRenderEngine::renderFrame);
    }

    public static void register(RenderFeature feature) {
        if (feature != null) FEATURES.addIfAbsent(feature);
    }

    public static void unregister(RenderFeature feature) {
        FEATURES.remove(feature);
    }

    private static void renderFrame(WorldRenderContext minecraft) {
        QUEUE.clear();
        RenderContext context = new RenderContext(minecraft, QUEUE);

        for (RenderFeature feature : FEATURES) {
            try {
                feature.render(minecraft, QUEUE);
            } catch (RuntimeException ex) {
                System.err.println("[PvPVisuals] Render feature failed: " + feature.getClass().getName());
                ex.printStackTrace();
            }
        }

        if (QUEUE.commands().isEmpty()) return;

        Matrix4f pose = minecraft.matrices().last().pose();
        VertexConsumer vertices = minecraft.consumers().getBuffer(RenderTypes.debugQuads());

        for (RenderQueue.Command command : QUEUE.commands()) {
            if (command instanceof RenderQueue.Line line) {
                drawLine(vertices, pose, line.ax(), line.ay(), line.az(),
                        line.bx(), line.by(), line.bz(), line.width(), line.argb());
            } else if (command instanceof RenderQueue.Box box) {
                drawBox(vertices, pose, box);
            }
        }
        QUEUE.clear();
    }

    private static void drawBox(VertexConsumer vc, Matrix4f m, RenderQueue.Box b) {
        float x0=b.minX(), y0=b.minY(), z0=b.minZ();
        float x1=b.maxX(), y1=b.maxY(), z1=b.maxZ();
        float[][] p = {
            {x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},
            {x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}
        };
        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7}
        };
        for (int[] edge : edges) {
            float[] a=p[edge[0]], c=p[edge[1]];
            drawLine(vc,m,a[0],a[1],a[2],c[0],c[1],c[2],b.width(),b.argb());
        }
    }

    /** Camera-facing quad ribbon. Coordinates are relative to the camera. */
    private static void drawLine(VertexConsumer vc, Matrix4f m,
            float ax,float ay,float az,float bx,float by,float bz,float width,int color) {
        float dx=bx-ax, dy=by-ay, dz=bz-az;
        float mx=(ax+bx)*0.5f, my=(ay+by)*0.5f, mz=(az+bz)*0.5f;
        float sx=dy*mz-dz*my, sy=dz*mx-dx*mz, sz=dx*my-dy*mx;
        float len=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);
        if (len < 1.0e-6f) return;
        float k=(width*0.5f)/len;
        sx*=k; sy*=k; sz*=k;
        vertex(vc,m,ax-sx,ay-sy,az-sz,color);
        vertex(vc,m,ax+sx,ay+sy,az+sz,color);
        vertex(vc,m,bx+sx,by+sy,bz+sz,color);
        vertex(vc,m,bx-sx,by-sy,bz-sz,color);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x,float y,float z,int color) {
        vc.addVertex(m,x,y,z).setColor(color);
    }
}
