package com.pvpvisuals.gui.echo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.pvpvisuals.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;

/**
 * Liquid Glass renderer: captures the scene once per frame, samples it through a local
 * rounded blur shader, then layers translucent tint, reflection and a fine border.
 */
public final class LiquidGlassRenderer {
    private static TextureTarget backdrop;
    private static GpuSampler backdropSampler;

    private LiquidGlassRenderer() { }

    /** Copies the current scene into a texture before Echo submits its GUI elements. */
    public static void captureBackground() {
        RenderSystem.assertOnRenderThread();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0 || main.getColorTexture() == null) return;

        if (backdrop == null) {
            backdrop = new TextureTarget("pvpvisuals_liquid_glass_backdrop", width, height, false);
        } else if (backdrop.width != width || backdrop.height != height) {
            backdrop.resize(width, height);
        }

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                main.getColorTexture(), backdrop.getColorTexture(),
                0, 0, 0, 0, 0, width, height);
        backdropSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static void window(GuiGraphics graphics, float x, float y, float width, float height, float radius) {
        window(graphics, x, y, width, height, radius, 1f);
    }

    /**
     * Draws the glass window with an explicit transition opacity.
     * The opacity is applied to the blur tint, reflection and border colors,
     * so the custom GUI render pipeline follows the same fade as ordinary Gfx calls.
     */
    public static void window(GuiGraphics graphics, float x, float y, float width, float height,
            float radius, float opacity) {
        float alpha = Math.max(0f, Math.min(1f, opacity));
        blurRect(graphics, x, y, width, height, radius,
                withAlpha(EchoTheme.WIN_TOP, alpha), withAlpha(EchoTheme.WIN_BOTTOM, alpha));
        roundRect(graphics, x + 1f, y + 1f, width - 2f, Math.min(54f, height * 0.22f),
                Math.max(1f, radius - 1f),
                withAlpha(0x18FFFFFF, alpha), withAlpha(0x00FFFFFF, alpha), 0, 0f);
        roundRect(graphics, x + 0.6f, y + 0.6f, width - 1.2f, height - 1.2f,
                Math.max(1f, radius - 0.6f), 0, 0, withAlpha(0x55FFFFFF, alpha), 0.8f);
    }

    public static void surface(GuiGraphics graphics, float x, float y, float width, float height, float radius,
            int tint, int border) {
        blurRect(graphics, x, y, width, height, radius, tint, withAlpha(tint, 0.72f));
        if (border != 0) {
            roundRect(graphics, x, y, width, height, radius, 0, 0, border, 0.8f);
        }
    }

    /** Submits a locally blurred, rounded glass panel. */
    public static void blurRect(GuiGraphics graphics, float x, float y, float width, float height, float radius,
            int top, int bottom) {
        if (width <= 0f || height <= 0f) return;
        if (backdrop == null || backdropSampler == null || backdrop.getColorTextureView() == null) {
            roundRect(graphics, x, y, width, height, radius, top, bottom, 0, 0f);
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x), (int) Math.floor(y),
                Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)))
                .transformAxisAligned(pose);

        GpuTextureView view = backdrop.getColorTextureView();
        TextureSetup textureSetup = new TextureSetup(view, null, null, backdropSampler, null, null);
        float glassOpacity = Math.max(0f, Math.min(1f, com.pvpvisuals.config.PvpConfig.get().gui.panelOpacity));
        float tintStrength = 0.18f * glassOpacity;
        graphicsState(graphics).submitGuiElement(new LiquidGlassBlurState(pose, textureSetup,
                x, y, width, height, radius,
                tint(top, tintStrength), tint(top, tintStrength), tint(bottom, tintStrength), tint(bottom, tintStrength), bounds));
    }

    public static void roundRect(GuiGraphics graphics, float x, float y, float width, float height, float radius,
            int top, int bottom, int borderColor, float border) {
        if (width <= 0f || height <= 0f) return;

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x), (int) Math.floor(y),
                Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)))
                .transformAxisAligned(pose);

        var state = graphicsState(graphics);
        state.submitGuiElement(new LiquidGlassShapeState(pose, x, y, width, height,
                radius, 0f, top, top, bottom, bottom, bounds));

        if (border > 0f && (borderColor >>> 24) != 0) {
            state.submitGuiElement(new LiquidGlassShapeState(pose, x, y, width, height,
                    radius, border, borderColor, borderColor, borderColor, borderColor, bounds));
        }
    }

    public static void close() {
        if (backdrop != null) {
            backdrop.destroyBuffers();
            backdrop = null;
            backdropSampler = null;
        }
    }

    private static net.minecraft.client.gui.render.state.GuiRenderState graphicsState(GuiGraphics graphics) {
        return ((GuiGraphicsAccessor) graphics).pvpvisuals$getGuiRenderState();
    }

    private static int tint(int color, float amount) {
        float sourceAlpha = ((color >>> 24) & 0xFF) / 255f;
        float strength = Math.max(0f, Math.min(1f, amount * sourceAlpha));
        return (Math.round(255f * strength) << 24) | (color & 0x00FFFFFF);
    }

    private static int withAlpha(int color, float factor) {
        int alpha = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, factor)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
