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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** TargetESP 1.0.0 effect port, using the 1.21.11 extraction and GPU rendering APIs. */
public final class TargetEspStyleRenderer {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/target-esp");
    private static final String NS = "pvpvisuals";
    private static final Identifier FIREFLY = Identifier.fromNamespaceAndPath(NS, "textures/targetesp/firefly.png");
    private static final Identifier CAPTURE = Identifier.fromNamespaceAndPath(NS, "textures/targetesp/capture.png");
    private static final int SEGMENTS = 30, COLOR_VERTEX_BYTES = 16, TEX_VERTEX_BYTES = 24;
    private static final int COLOR_BYTES = 262_144, TEX_BYTES = 32_768;
    private static final double TAU = Math.PI * 2.0;
    private static final ByteBuffer COLOR_DATA = ByteBuffer.allocateDirect(COLOR_BYTES).order(ByteOrder.nativeOrder());
    private static final ByteBuffer TEX_DATA = ByteBuffer.allocateDirect(TEX_BYTES).order(ByteOrder.nativeOrder());
    private static final Vector4f WHITE = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final RenderPipeline[] COLOR_PIPELINES = new RenderPipeline[1];
    private static RenderPipeline texturePipeline;
    private static GpuBuffer colorBuffer, textureBuffer;
    private static boolean failed;
    private static int colorVertices, texVertices;

    private TargetEspStyleRenderer() { }

    public static void reset() {
        failed = false;
        COLOR_PIPELINES[0] = null;
        texturePipeline = null;
    }

    private static RenderPipeline colorPipeline() {
        if (COLOR_PIPELINES[0] == null) {
            COLOR_PIPELINES[0] = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/target_esp"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/hitbox"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/hitbox"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false).withCull(false).build();
        }
        return COLOR_PIPELINES[0];
    }

    private static RenderPipeline texturePipeline() {
        if (texturePipeline == null) {
            texturePipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(NS, "pipeline/target_esp_texture"))
                    .withVertexShader(Identifier.fromNamespaceAndPath(NS, "core/target_esp_texture"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(NS, "core/target_esp_texture"))
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false).withCull(false).build();
        }
        return texturePipeline;
    }

    public static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext context) {
        Frame frame = TargetEspModule.frame();
        if (!frame.visible || failed) return;
        try {
            TargetEspSettings s = PvpConfig.get().targetEspSettings;
            Minecraft mc = Minecraft.getInstance();
            RenderTarget target = mc.getMainRenderTarget();
            if (target == null || target.height <= 0) return;

            float fov = (float) Math.toRadians(mc.options.fov().get());
            float worldPerPixel = (float) (2.0 * Math.tan(fov / 2.0) / target.height);
            float[] b = frame.bounds;
            float x0=b[0], y0=b[1], z0=b[2], x1=b[3], y1=b[4], z1=b[5];
            float width=Math.max(0.1f,x1-x0), height=Math.max(0.1f,y1-y0), depth=Math.max(0.1f,z1-z0);
            float cx=(x0+x1)*0.5f, cz=(z0+z1)*0.5f;
            float size=clamp(s.auraSize,0.5f,2.0f), fade=clamp01(frame.fade);
            double time=System.nanoTime()/1_000_000_000.0*clamp(s.animationSpeed,0.1f,4.0f);
            double ghostTime=time*20.0;
            Matrix4f inverseView = new Matrix4f(frame.view).invert();
            Vector3f right=inverseView.transformDirection(new Vector3f(1,0,0)).normalize();
            Vector3f up=inverseView.transformDirection(new Vector3f(0,1,0)).normalize();

            COLOR_DATA.clear(); TEX_DATA.clear(); colorVertices=0; texVertices=0;
            switch (TargetEspMode.byName(s.mode)) {
                case GHOSTS -> drawGhosts(cx,cz,y0,height,width,depth,s,frame,ghostTime,fade,size,right,up);
                case KVADRAT -> drawKvadrat(cx,cz,y0,height,s,frame,time,fade,size,right,up);
                case KRUG -> drawKrug(cx,cz,y0,height,width,depth,s,frame,time,fade,size,worldPerPixel);
            }
            flushColor(frame.view,target);
            if (texVertices>0) {
                Identifier texture = TargetEspMode.byName(s.mode)==TargetEspMode.KVADRAT ? CAPTURE : FIREFLY;
                flushTexture(frame.view,target,mc,texture);
            }
        } catch (Throwable error) {
            failed=true;
            LOG.error("Target ESP renderer failed and was disabled until the module is toggled again",error);
        }
    }

    /** Textured firefly sprites follow the original multi-orbit path, with a per-layer phase offset. */
    private static void drawGhosts(float cx,float cz,float y0,float height,float width,float depth,
            TargetEspSettings s,Frame frame,double time,float fade,float size,Vector3f right,Vector3f up) {
        int layers=clamp(s.ghostLayers,1,3), length=clamp(s.ghostLength,4,32);
        float radius=clamp(s.ghostRadius,0.1f,2.5f)*size;
        float rx=Math.max(radius,width*0.52f+0.10f), rz=Math.max(radius,depth*0.52f+0.10f);
        float shaking=clamp(s.ghostShaking,0.2f,8f), amplitude=clamp(s.ghostAmplitude,0.1f,8f);
        float factor=clamp(s.ghostFactor,1f,24f), speed=clamp(s.ghostSpeed,0.1f,8f);

        // Original Ghosts mode pulses max alpha between 70% and 100%, then multiplies
        // each particle alpha by its normalized position in the orbit trail.
        float peakAlpha=clamp(s.maxAlpha,100,255);
        if(s.enableAlphaAnimation) {
            double periodMs=clamp(s.alphaAnimationMs,80,3000);
            double phase=(System.currentTimeMillis()%periodMs)/periodMs;
            float pulse=0.7f+0.3f*(float)Math.sin(phase*Math.PI*clamp(s.alphaFactor,0.25f,8f)*0.5);
            peakAlpha=clamp(peakAlpha*pulse,100f,255f);
        }

        for(int layer=0;layer<layers;layer++) {
            for(int j=0;j<=length;j++) {
                float progress=j/(float)length;
                double angle=Math.toRadians((((j/1.5+time*speed)*factor+layer*120.0)%(factor*360.0)));
                double bobPhase=Math.toRadians(time*speed*(j*(layer+1.0))*amplitude);
                float bob=(float)Math.sin(bobPhase)/shaking;
                float x=cx+(float)Math.cos(angle)*rx, z=cz+(float)Math.sin(angle)*rz, y=y0+1.0f+bob;
                float sizeFactor=Math.max(clamp(s.minSizeFactor,0.05f,1f),progress);
                float sprite=clamp(s.ghostSize,0.04f,0.6f)*size*sizeFactor;
                int alpha=clampByte(Math.round(peakAlpha*progress*fade));
                int rgb=resolveColor(s,frame,progress);
                billboard(x,y,z,sprite,right,up,rgb,alpha);
            }
        }
    }

    /** Rotating capture-corner texture used by the original Kvadrat style. */
    private static void drawKvadrat(float cx,float cz,float y0,float height,TargetEspSettings s,Frame frame,
            double time,float fade,float size,Vector3f cameraRight,Vector3f cameraUp) {
        double angle=Math.toRadians(Math.sin(time*0.85)*24.0);
        float c=(float)Math.cos(angle), sn=(float)Math.sin(angle);
        Vector3f right=new Vector3f(cameraRight).mul(c).add(new Vector3f(cameraUp).mul(sn));
        Vector3f up=new Vector3f(cameraUp).mul(c).sub(new Vector3f(cameraRight).mul(sn));
        int color=resolveColor(s,frame,0.35f), alpha=clampByte(Math.round(255*fade));
        billboard(cx,y0+Math.min(0.85f,height*0.62f),cz,0.75f*size,right,up,color,alpha);
    }

    /** Krug is a travelling, vertical ribbon (two sinusoidal edges), not a flat ground circle. */
    private static void drawKrug(float cx,float cz,float y0,float height,float width,float depth,
            TargetEspSettings s,Frame frame,double time,float fade,float size,float worldPerPixel) {
        // Original TargetESP draws a TRIANGLE_STRIP between two concentric loops.
        // The two heights pulse together; they do not oscillate around the azimuth.
        float radius=Math.max(0.15f,width*0.80f);
        float phase=(float)(time*1.7);
        float upperY=y0+(float)Math.abs(Math.sin(phase))*height;
        float lowerY=y0+(float)Math.abs(Math.sin(phase-0.45f))*height;
        int edgeAlpha=clampByte(Math.round(170.0f*fade));
        int transparentAlpha=0;

        float previousX=cx+radius;
        float previousZ=cz;
        int previousColor=resolveColor(s,frame,0.0f);
        for(int i=1;i<=SEGMENTS;i++) {
            double angle=TAU*i/SEGMENTS;
            float x=cx+(float)Math.cos(angle)*radius;
            float z=cz+(float)Math.sin(angle)*radius;
            int color=resolveColor(s,frame,i/(float)SEGMENTS);

            // Top ring has alpha 170*fade, lower ring fades to fully transparent.
            triangle(previousX,upperY,previousZ,previousColor,edgeAlpha,
                    x,upperY,z,color,edgeAlpha,
                    x,lowerY,z,color,transparentAlpha);
            triangle(previousX,upperY,previousZ,previousColor,edgeAlpha,
                    x,lowerY,z,color,transparentAlpha,
                    previousX,lowerY,previousZ,previousColor,transparentAlpha);

            previousX=x;
            previousZ=z;
            previousColor=color;
        }
    }

    private static void billboard(float x,float y,float z,float half,Vector3f right,Vector3f up,int rgb,int alpha) {
        if(alpha<=0 || TEX_DATA.remaining()<4*TEX_VERTEX_BYTES) return;
        float rx=right.x*half,ry=right.y*half,rz=right.z*half;
        float ux=up.x*half,uy=up.y*half,uz=up.z*half;
        texVertex(x-rx-ux,y-ry-uy,z-rz-uz,0,1,rgb,alpha);
        texVertex(x+rx-ux,y+ry-uy,z+rz-uz,1,1,rgb,alpha);
        texVertex(x+rx+ux,y+ry+uy,z+rz+uz,1,0,rgb,alpha);
        texVertex(x-rx+ux,y-ry+uy,z-rz+uz,0,0,rgb,alpha);
    }

    private static void texVertex(float x,float y,float z,float u,float v,int rgb,int alpha) {
        if(TEX_DATA.remaining()<TEX_VERTEX_BYTES) return;
        TEX_DATA.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
        TEX_DATA.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)alpha);
        texVertices++;
    }

    private static void triangle(float ax,float ay,float az,int ac,int aa,float bx,float by,float bz,int bc,int ba,
            float cx,float cy,float cz,int cc,int ca) {
        vertex(ax,ay,az,ac,aa); vertex(bx,by,bz,bc,ba); vertex(cx,cy,cz,cc,ca);
    }

    private static void flushColor(Matrix4f view,RenderTarget target) {
        if(colorVertices==0) return;
        COLOR_DATA.flip();
        GpuDevice device=RenderSystem.getDevice();
        if(colorBuffer==null) colorBuffer=device.createBuffer(()->"PvPVisuals Target ESP vertices",
                GpuBuffer.USAGE_VERTEX|GpuBuffer.USAGE_COPY_DST,COLOR_BYTES);
        CommandEncoder encoder=device.createCommandEncoder();
        encoder.writeToBuffer(colorBuffer.slice(0,COLOR_DATA.remaining()),COLOR_DATA);
        var transforms=RenderSystem.getDynamicUniforms().writeTransform(view,WHITE,ZERO,IDENTITY);
        try(RenderPass pass=encoder.createRenderPass(()->"PvPVisuals Target ESP",target.getColorTextureView(),
                OptionalInt.empty(),target.getDepthTextureView(),OptionalDouble.empty())) {
            pass.setPipeline(colorPipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",transforms);
            pass.setVertexBuffer(0,colorBuffer);
            pass.draw(0,colorVertices);
        }
    }

    private static void flushTexture(Matrix4f view,RenderTarget target,Minecraft mc,Identifier id) {
        if(texVertices==0) return;
        TEX_DATA.flip();
        GpuDevice device=RenderSystem.getDevice();
        if(textureBuffer==null) textureBuffer=device.createBuffer(()->"PvPVisuals Target ESP textured vertices",
                GpuBuffer.USAGE_VERTEX|GpuBuffer.USAGE_COPY_DST,TEX_BYTES);
        AbstractTexture texture=mc.getTextureManager().getTexture(id);
        CommandEncoder encoder=device.createCommandEncoder();
        encoder.writeToBuffer(textureBuffer.slice(0,TEX_DATA.remaining()),TEX_DATA);
        var transforms=RenderSystem.getDynamicUniforms().writeTransform(view,WHITE,ZERO,IDENTITY);
        try(RenderPass pass=encoder.createRenderPass(()->"PvPVisuals Target ESP textured",target.getColorTextureView(),
                OptionalInt.empty(),target.getDepthTextureView(),OptionalDouble.empty())) {
            pass.setPipeline(texturePipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms",transforms);
            pass.bindTexture("Sampler0",texture.getTextureView(),texture.getSampler());
            pass.setVertexBuffer(0,textureBuffer);
            pass.draw(0,texVertices);
        }
    }

    private static void segment(float x1,float y1,float z1,float x2,float y2,float z2,float pixels,float wpp,int rgb,int alpha) {
        if(alpha<=0 || COLOR_DATA.remaining()<6*COLOR_VERTEX_BYTES) return;
        float dx=x2-x1,dy=y2-y1,dz=z2-z1,mx=(x1+x2)*0.5f,my=(y1+y2)*0.5f,mz=(z1+z2)*0.5f;
        float sx=dy*mz-dz*my,sy=dz*mx-dx*mz,sz=dx*my-dy*mx;
        float len=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);
        if(len<1e-8f) return;
        float distance=(float)Math.sqrt(mx*mx+my*my+mz*mz),scale=pixels*wpp*distance*0.5f/len;
        sx*=scale;sy*=scale;sz*=scale;
        vertex(x1-sx,y1-sy,z1-sz,rgb,alpha);vertex(x1+sx,y1+sy,z1+sz,rgb,alpha);vertex(x2+sx,y2+sy,z2+sz,rgb,alpha);
        vertex(x1-sx,y1-sy,z1-sz,rgb,alpha);vertex(x2+sx,y2+sy,z2+sz,rgb,alpha);vertex(x2-sx,y2-sy,z2-sz,rgb,alpha);
    }

    private static void vertex(float x,float y,float z,int rgb,int alpha) {
        if(COLOR_DATA.remaining()<COLOR_VERTEX_BYTES) return;
        COLOR_DATA.putFloat(x).putFloat(y).putFloat(z);
        COLOR_DATA.put((byte)(rgb>>16)).put((byte)(rgb>>8)).put((byte)rgb).put((byte)alpha);
        colorVertices++;
    }

    private static int resolveColor(TargetEspSettings s,Frame frame,float progress) {
        String mode=s.colorMode==null?"HEALTH":s.colorMode.toUpperCase(java.util.Locale.ROOT);
        return switch(mode) {
            case "STATIC" -> s.color;
            case "DISTANCE" -> blend(0x45E6A2,0xFF5B68,clamp01(frame.distance/Math.max(1f,s.maxDistance)));
            case "RAINBOW" -> rainbow(System.nanoTime()/1_000_000_000.0*clamp(s.animationSpeed,0.1f,4f)+progress*0.22f);
            case "PRESET" -> preset(s.colorPreset,progress);
            case "HEALTH" -> healthColor(frame.health);
            default -> s.color;
        };
    }
    private static int preset(String name,float progress) {
        int a,b;
        switch(name==null?"CYAN":name.toUpperCase(java.util.Locale.ROOT)) {
            case "PINK"->{a=0xFF00FF;b=0xFF64C8;}
            case "PURPLE"->{a=0xB400FF;b=0x7800FF;}
            case "GREEN"->{a=0x00FF00;b=0x64FF64;}
            case "ORANGE"->{a=0xFFA500;b=0xFF6400;}
            case "RED"->{a=0xFF0000;b=0xC80000;}
            case "BLUE"->{a=0x0064FF;b=0x0032C8;}
            case "YELLOW"->{a=0xFFFF00;b=0xC8C800;}
            default->{a=0x00FFFF;b=0x0096FF;}
        }
        return blend(a,b,0.5f+0.5f*(float)Math.sin(progress*TAU));
    }
    private static int healthColor(float health) {
        float h=clamp01(health);
        return h>=0.5f?blend(0xFFFF00,0x00FF00,(h-0.5f)*2f):blend(0xFF0000,0xFFFF00,h*2f);
    }
    private static int rainbow(double phase) {
        float h=(float)(phase-Math.floor(phase));int sector=(int)(h*6f);float f=h*6f-sector,q=1f-f,r,g,b;
        switch(sector%6) {
            case 0->{r=1;g=f;b=0;} case 1->{r=q;g=1;b=0;} case 2->{r=0;g=1;b=f;}
            case 3->{r=0;g=q;b=1;} case 4->{r=f;g=0;b=1;} default->{r=1;g=0;b=q;}
        }
        return (Math.round(r*255)<<16)|(Math.round(g*255)<<8)|Math.round(b*255);
    }
    private static int blend(int a,int b,float t) {
        t=clamp01(t);
        int r=Math.round(((a>>16)&255)+(((b>>16)&255)-((a>>16)&255))*t);
        int g=Math.round(((a>>8)&255)+(((b>>8)&255)-((a>>8)&255))*t);
        int bl=Math.round((a&255)+((b&255)-(a&255))*t);
        return (r<<16)|(g<<8)|bl;
    }
    private static int clampByte(int x){return Math.max(0,Math.min(255,x));}
    private static int clamp(int x,int min,int max){return Math.max(min,Math.min(max,x));}
    private static float clamp(float x,float min,float max){return Math.max(min,Math.min(max,x));}
    private static float clamp01(float x){return clamp(x,0,1);}
}
