package com.pvpvisuals.mixin;

import com.pvpvisuals.gui.echo.LiquidGlassRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the completed menu backdrop between the two GUI render passes.
 * Screen drawing in 1.21.11 is deferred, so copying the main target while a
 * Screen is still submitting GuiGraphics states only captures the previous frame.
 */
@Mixin(GuiRenderer.class)
public abstract class LiquidGlassCaptureMixin {
    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V",
                    shift = At.Shift.AFTER
            )
    )
    private void pvpvisuals$captureLiquidGlassBackdrop(CallbackInfo ci) {
        LiquidGlassRenderer.capturePendingBackground();
    }
}
