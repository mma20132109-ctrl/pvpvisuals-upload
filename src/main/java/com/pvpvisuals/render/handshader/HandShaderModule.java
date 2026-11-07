package com.pvpvisuals.render.handshader;

import com.pvpvisuals.config.HandShaderSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hand Shader: условия показа эффекта и GUI-описание. Сам эффект рисует HandShaderRenderer после прохода рук
 * (хук GameRendererBobMixin: renderItemInHand RETURN), а факт, что проход рук в этом кадре был, отмечает
 * ItemInHandRendererMixin (renderHandsWithItems HEAD).
 */
public final class HandShaderModule {
	private static boolean handPass;

	private HandShaderModule() { }

	public static HandShaderSettings settings() {
		return PvpConfig.get().handShader;
	}

	/** Вызывается, когда ванильный код начал рисовать руки/предметы от первого лица. */
	public static void markHandPass() {
		handPass = true;
	}

	/** Забирает отметку «проход рук был в этом кадре». */
	public static boolean consumeHandPass() {
		boolean h = handPass;
		handPass = false;
		return h;
	}

	public static boolean isWeapon(ItemStack st) {
		return !st.isEmpty() && (st.is(ItemTags.SWORDS) || st.is(ItemTags.AXES) || st.is(ItemTags.SPEARS)
				|| st.is(Items.BOW) || st.is(Items.CROSSBOW) || st.is(Items.TRIDENT) || st.is(Items.MACE));
	}

	/** Нужно ли рисовать эффект в этом кадре (с учётом affectHand и onlyWhenHoldingWeapon). */
	public static boolean shouldDraw() {
		HandShaderSettings s = settings();
		if (s == null || !s.enabled) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		ItemStack main = mc.player.getMainHandItem();
		ItemStack off = mc.player.getOffhandItem();
		if (s.onlyWhenHoldingWeapon && !isWeapon(main)) {
			return false;
		}
		// без affectHand пустая рука (и карта вместо предмета) остаётся ванильной: нужен реальный предмет
		return s.affectHand || !(main.isEmpty() && off.isEmpty());
	}

	public static ModuleDef def(PvpConfig pc) {
		HandShaderSettings s = pc.handShader;
		ModuleDef dropdown = ModuleDef.group(Category.VISUAL, () -> "Style: " + HandShaderStyle.byName(s.shaderType).title);
		for (HandShaderStyle t : HandShaderStyle.values()) {
			dropdown.child(ModuleDef.toggle(t.title, Category.VISUAL, () -> HandShaderStyle.byName(s.shaderType) == t, v -> {
				if (v) {
					s.shaderType = t.name();
				}
			}));
		}
		return ModuleDef.toggle("Hand Shader", Category.VISUAL, () -> s.enabled, v -> {
			s.enabled = v;
			if (v) {
				HandShaderRenderer.reset();
			}
		})
				.child(dropdown)
				.child(ModuleDef.slider("R", Category.VISUAL, 0, 255, 0, () -> (s.colorPrimary >> 16) & 0xFF,
						v -> s.colorPrimary = (s.colorPrimary & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("G", Category.VISUAL, 0, 255, 0, () -> (s.colorPrimary >> 8) & 0xFF,
						v -> s.colorPrimary = (s.colorPrimary & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("B", Category.VISUAL, 0, 255, 0, () -> s.colorPrimary & 0xFF,
						v -> s.colorPrimary = (s.colorPrimary & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Alpha", Category.VISUAL, 0.05, 1.0, 2,
						() -> s.alphaPrimary, v -> s.alphaPrimary = (float) v))
				.child(ModuleDef.slider("Color 2 R", Category.VISUAL, 0, 255, 0, () -> (s.colorSecondary >> 16) & 0xFF,
						v -> s.colorSecondary = (s.colorSecondary & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("Color 2 G", Category.VISUAL, 0, 255, 0, () -> (s.colorSecondary >> 8) & 0xFF,
						v -> s.colorSecondary = (s.colorSecondary & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("Color 2 B", Category.VISUAL, 0, 255, 0, () -> s.colorSecondary & 0xFF,
						v -> s.colorSecondary = (s.colorSecondary & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Color 2 Alpha", Category.VISUAL, 0.05, 1.0, 2,
						() -> s.alphaSecondary, v -> s.alphaSecondary = (float) v))
				.child(ModuleDef.slider("Animation Speed", Category.VISUAL, 0.1, 4.0, 2,
						() -> s.animationSpeed, v -> s.animationSpeed = (float) v))
				.child(ModuleDef.toggle("Affect Hand", Category.VISUAL, () -> s.affectHand, v -> s.affectHand = v))
				.child(ModuleDef.toggle("Only Holding Weapon", Category.VISUAL,
						() -> s.onlyWhenHoldingWeapon, v -> s.onlyWhenHoldingWeapon = v));
	}
}
