package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/** Narrow accessor used only to replace the local offline identity selected in the account manager. */
@Mixin(Minecraft.class)
public interface MinecraftUserAccessor {
    @Mutable
    @Accessor("user")
    void pvpvisuals$setUser(User user);
}
