package com.pvpvisuals.mixin;

import com.pvpvisuals.gui.echo.LiquidGlassRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 1.21.11 GUI is submitted first and drawn later. This hook runs after the
 * backdrop draw pass has completed, but before the glass pass starts, so the
 * copied texture contains the actual menu background rather than a stale frame.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererLiquidGlassMixin {
    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void pvpvisuals$captureGlassBackdrop(CallbackInfo ci) {
        // The split is only used as a render-order barrier for our custom blur.
        // Skip Minecraft's full-screen blur so pixels outside glass remain sharp.
        if (LiquidGlassRenderer.capturePendingBackground()) {
            ci.cancel();
        }
    }
}
