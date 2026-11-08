package com.pvpvisuals.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the local account selector to replace Minecraft's session user. */
@Mixin(Minecraft.class)
public interface MinecraftUserAccessor {
    @Mutable
    @Accessor("user")
    void pvpvisuals$setUser(User user);
}
