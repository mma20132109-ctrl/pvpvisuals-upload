package com.pvpvisuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.OptionInstance;

/**
 * Прямая запись в поле value. OptionInstance#set проверяет диапазон: для гаммы допустимо только 0..1,
 * а любое другое значение заменяется на исходное, поэтому для яркости >1 нужен обход валидации.
 */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("value")
	void pvpvisuals$setValue(Object value);
}
