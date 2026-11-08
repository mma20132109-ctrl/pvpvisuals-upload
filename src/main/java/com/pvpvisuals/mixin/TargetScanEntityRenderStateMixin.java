package com.pvpvisuals.mixin;

import com.pvpvisuals.render.targetesp.TargetScanRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class TargetScanEntityRenderStateMixin implements TargetScanRenderState {
    @Unique
    private boolean pvpvisuals$targetScanTarget;

    @Override
    public boolean pvpvisuals$isTargetScanTarget() {
        return this.pvpvisuals$targetScanTarget;
    }

    @Override
    public void pvpvisuals$setTargetScanTarget(boolean target) {
        this.pvpvisuals$targetScanTarget = target;
    }
}
