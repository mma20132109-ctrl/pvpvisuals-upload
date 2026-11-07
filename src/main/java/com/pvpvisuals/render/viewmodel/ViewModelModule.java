package com.pvpvisuals.render.viewmodel;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.ViewModelSettings;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.util.Mth;

/**
 * ViewModel: положение/размер/поворот руки и предмета от первого лица, отключение покачивания и смены предмета,
 * настройка взмаха. Рисует ничего сам: значения читают миксины ItemInHandRendererMixin и GameRendererBobMixin.
 */
public final class ViewModelModule {
	/** Пресеты анимации удара (SwingAnimation). */
	public enum SwingStyle {
		VANILLA("Vanilla"), SMOOTH("Smooth"), SLIDE("Slide"), SCALE_PUNCH("Scale Punch"),
		ROTATE_SPIN("Rotate Spin"), FADE_SWING("Fade Swing"), TILT("Tilt Forward"), NONE("None");

		public final String title;

		SwingStyle(String title) {
			this.title = title;
		}

		public static SwingStyle byName(String n) {
			if (n == null) {
				return VANILLA;
			}
			// имена из прошлых версий конфига
			if (n.equalsIgnoreCase("SCALE")) {
				return SCALE_PUNCH;
			}
			if (n.equalsIgnoreCase("ROTATE") || n.equalsIgnoreCase("SPIN")) {
				return ROTATE_SPIN;
			}
			for (SwingStyle s : values()) {
				if (s.name().equalsIgnoreCase(n)) {
					return s;
				}
			}
			return VANILLA;
		}
	}

	/** ease-out-cubic: быстрый старт, мягкое окончание; концы 0 и 1 не меняются. */
	public static float easeOutCubic(float p) {
		float q = 1f - Mth.clamp(p, 0f, 1f);
		return 1f - q * q * q;
	}

	private ViewModelModule() { }

	public static ViewModelSettings settings() {
		return PvpConfig.get().viewModel;
	}

	/** Включён ли модуль (миксины не вмешиваются в ванильный рендер, пока это false). */
	public static boolean active() {
		ViewModelSettings s = PvpConfig.get().viewModel;
		return s != null && s.enabled;
	}

	/**
	 * Перекладка прогресса взмаха: концы 0 и 1 остаются на месте, а скорость меняется степенной кривой.
	 * speed > 1: быстрый старт и мягкое окончание; speed < 1: медленный старт и быстрое окончание.
	 */
	public static float remapSwing(float p, float speed) {
		if (p <= 0f || p >= 1f || Math.abs(speed - 1f) < 1e-3f) {
			return p;
		}
		return (float) Math.pow(p, 1.0 / Mth.clamp(speed, 0.25f, 4f));
	}

	public static void reset(ViewModelSettings s) {
		s.posX = s.posY = s.posZ = 0f;
		s.scaleX = s.scaleY = s.scaleZ = 1f;
		s.rotX = s.rotY = s.rotZ = 0f;
	}

	/** Пресет для PvP: предмет правее и ниже, чуть меньше, чтобы не закрывал обзор. */
	public static void presetPvp(ViewModelSettings s) {
		s.posX = 0.22f;
		s.posY = -0.16f;
		s.posZ = 0.05f;
		s.scaleX = s.scaleY = s.scaleZ = 0.85f;
		s.rotX = 0f;
		s.rotY = 0f;
		s.rotZ = 0f;
	}

	/** Выпадающий список пресетов анимации удара. */
	private static ModuleDef swingDropdown(ViewModelSettings s) {
		ModuleDef dd = ModuleDef.group(Category.VISUAL, () -> "Swing Animation: " + SwingStyle.byName(s.customSwing).title);
		for (SwingStyle t : SwingStyle.values()) {
			dd.child(ModuleDef.toggle(t.title, Category.VISUAL, () -> SwingStyle.byName(s.customSwing) == t, v -> {
				if (v) {
					s.customSwing = t.name();
				}
			}));
		}
		return dd;
	}

	public static ModuleDef def(PvpConfig pc) {
		ViewModelSettings s = pc.viewModel;
		return ModuleDef.toggle("ViewModel", Category.VISUAL, () -> s.enabled, v -> s.enabled = v)
				.child(ModuleDef.slider("Pos X", Category.VISUAL, -2.0, 2.0, 2, () -> s.posX, v -> s.posX = (float) v))
				.child(ModuleDef.slider("Pos Y", Category.VISUAL, -2.0, 2.0, 2, () -> s.posY, v -> s.posY = (float) v))
				.child(ModuleDef.slider("Pos Z", Category.VISUAL, -2.0, 2.0, 2, () -> s.posZ, v -> s.posZ = (float) v))
				.child(ModuleDef.slider("Scale X", Category.VISUAL, 0.1, 2.0, 2, () -> s.scaleX, v -> s.scaleX = (float) v))
				.child(ModuleDef.slider("Scale Y", Category.VISUAL, 0.1, 2.0, 2, () -> s.scaleY, v -> s.scaleY = (float) v))
				.child(ModuleDef.slider("Scale Z", Category.VISUAL, 0.1, 2.0, 2, () -> s.scaleZ, v -> s.scaleZ = (float) v))
				.child(ModuleDef.slider("Rot X", Category.VISUAL, -180, 180, 0, () -> s.rotX, v -> s.rotX = (float) v))
				.child(ModuleDef.slider("Rot Y", Category.VISUAL, -180, 180, 0, () -> s.rotY, v -> s.rotY = (float) v))
				.child(ModuleDef.slider("Rot Z", Category.VISUAL, -180, 180, 0, () -> s.rotZ, v -> s.rotZ = (float) v))
				.child(ModuleDef.toggle("Disable Sway", Category.VISUAL, () -> s.disableSway, v -> s.disableSway = v))
				.child(ModuleDef.toggle("Disable Equip Animation", Category.VISUAL,
						() -> s.disableEquipAnimation, v -> s.disableEquipAnimation = v))
				.child(ModuleDef.slider("Swing Speed", Category.VISUAL, 0.5, 2.0, 2,
						() -> s.swingSpeed, v -> s.swingSpeed = (float) v))
				.child(ModuleDef.slider("Swing Amplitude", Category.VISUAL, 0.0, 2.0, 2,
						() -> s.swingAmplitude, v -> s.swingAmplitude = (float) v))
				.child(swingDropdown(s))
				.child(ModuleDef.cycle(Category.VISUAL, () -> "Reset", () -> reset(s)))
				.child(ModuleDef.cycle(Category.VISUAL, () -> "Preset: PvP", () -> presetPvp(s)));
	}
}
