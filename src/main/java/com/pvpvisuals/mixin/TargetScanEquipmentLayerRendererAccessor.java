package com.pvpvisuals.mixin;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EquipmentLayerRenderer.class)
public interface TargetScanEquipmentLayerRendererAccessor {
    @Accessor("equipmentAssets")
    EquipmentAssetManager pvpvisuals$getEquipmentAssets();
}
