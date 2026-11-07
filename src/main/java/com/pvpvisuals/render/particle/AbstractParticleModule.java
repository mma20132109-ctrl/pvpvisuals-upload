package com.pvpvisuals.render.particle;

import java.util.concurrent.ThreadLocalRandom;

import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.minecraft.world.phys.Vec3;

/**
 * Общая часть Hit Particle и Block Hit Particle: создание частицы по настройкам и построение
 * раскрывающегося списка подмодулей в GUI. Наследники дают свой конфиг и дополнительные настройки.
 */
public abstract class AbstractParticleModule {
	protected final Category category;

	protected AbstractParticleModule(Category category) {
		this.category = category;
	}

	protected abstract String title();

	protected abstract boolean enabled();

	protected abstract void setEnabled(boolean v);

	protected abstract ParticleConfig cfg();

	/** Добавляет в конец списка настройки, специфичные для модуля. */
	protected abstract void addExtraSettings(ModuleDef root);

	/** Показывать ли общие RGB-слайдеры «Color» (start/static). */
	protected boolean showMainColor() {
		return true;
	}

	/**
	 * Создаёт одну частицу и добавляет её в рендерер.
	 *
	 * @param origin  точка появления
	 * @param dir     основное направление разлёта (не обязательно нормированное)
	 * @param baseRgb цвет для режимов Static/Gradient (старт)
	 */
	protected final Particle spawnOne(Vec3 origin, Vec3 dir, int baseRgb) {
		ParticleConfig c = cfg();
		ThreadLocalRandom r = ThreadLocalRandom.current();

		Particle p = new Particle();
		p.x = origin.x;
		p.y = origin.y;
		p.z = origin.z;
		p.px = p.x;
		p.py = p.y;
		p.pz = p.z;

		Vec3 v = dir.normalize().add(randomUnit(r).scale(c.spread));
		if (v.lengthSqr() < 1e-6) {
			v = randomUnit(r);
		}
		double speed = c.velocity * 0.15 * (0.4 + 0.6 * r.nextDouble());
		v = v.normalize().scale(speed);
		p.vx = v.x;
		p.vy = v.y;
		p.vz = v.z;

		p.life = Math.max(1, c.life + r.nextInt(-2, 3));
		p.size = c.size * (0.8f + 0.4f * r.nextFloat());
		p.sizeEndMul = Math.max(0f, 1f + c.sizeGrow);
		p.alpha = c.alpha;
		p.gravity = c.gravity;
		p.friction = c.friction;
		p.rot = r.nextFloat() * (float) (Math.PI * 2);
		p.style = ParticleStyle.byName(c.style, ParticleStyle.SPARK);

		switch (ParticleColorMode.byName(c.colorMode)) {
			case GRADIENT -> {
				p.rgbStart = baseRgb;
				p.rgbEnd = c.color2;
			}
			case RANDOM -> {
				int lo = Math.min(c.hueMin, c.hueMax);
				int hi = Math.max(c.hueMin, c.hueMax);
				float hue = (lo + r.nextFloat() * (hi - lo)) / 360f;
				p.rgbStart = hsv(hue, 0.85f, 1f);
				p.rgbEnd = p.rgbStart;
			}
			default -> {
				p.rgbStart = baseRgb;
				p.rgbEnd = baseRgb;
			}
		}
		ParticleRenderer.add(p);
		return p;
	}

	protected static Vec3 randomUnit(ThreadLocalRandom r) {
		Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
		return v.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : v.normalize();
	}

	private static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		float h6 = h * 6f;
		int i = (int) h6;
		float f = h6 - i;
		float p = v * (1 - s);
		float q = v * (1 - f * s);
		float t = v * (1 - (1 - f) * s);
		float r;
		float g;
		float b;
		switch (i % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	// ---------- GUI ----------

	/** Корневая строка модуля с галочкой и раскрывающимися настройками. */
	public final ModuleDef def() {
		ParticleConfig c = cfg();
		ModuleDef root = ModuleDef.toggle(title(), category, this::enabled, this::setEnabled);

		ModuleDef style = ModuleDef.group(category, () -> "Style: " + ParticleStyle.byName(c.style, ParticleStyle.SPARK).title);
		for (ParticleStyle s : ParticleStyle.values()) {
			style.child(ModuleDef.toggle(s.title, category,
					() -> ParticleStyle.byName(c.style, ParticleStyle.SPARK) == s, v -> c.style = s.name()));
		}
		root.child(style);

		root.child(ModuleDef.slider("Count", category, 1, 40, 0, () -> c.count, v -> c.count = (int) v));
		root.child(ModuleDef.slider("Size", category, 0.02, 0.5, 2, () -> c.size, v -> c.size = (float) v));
		root.child(ModuleDef.slider("Grow", category, -1.0, 1.0, 1, () -> c.sizeGrow, v -> c.sizeGrow = (float) v));
		root.child(ModuleDef.slider("Life", category, 4, 60, 0, () -> c.life, v -> c.life = (int) v));
		root.child(ModuleDef.slider("Spread", category, 0.0, 2.0, 2, () -> c.spread, v -> c.spread = (float) v));
		root.child(ModuleDef.slider("Speed", category, 0.0, 2.0, 2, () -> c.velocity, v -> c.velocity = (float) v));
		root.child(ModuleDef.slider("Gravity", category, -1.0, 1.0, 2, () -> c.gravity, v -> c.gravity = (float) v));
		root.child(ModuleDef.slider("Friction", category, 0.5, 1.0, 2, () -> c.friction, v -> c.friction = (float) v));
		root.child(ModuleDef.slider("Alpha", category, 20, 255, 0, () -> c.alpha, v -> c.alpha = (int) v));

		ModuleDef mode = ModuleDef.group(category, () -> "Color: " + ParticleColorMode.byName(c.colorMode).title);
		for (ParticleColorMode m : ParticleColorMode.values()) {
			mode.child(ModuleDef.toggle(m.title, category,
					() -> ParticleColorMode.byName(c.colorMode) == m, v -> c.colorMode = m.name()));
		}
		root.child(mode);

		if (showMainColor()) {
			root.child(rgbGroup("Static / Start", () -> c.color1, v -> c.color1 = v));
		}
		root.child(rgbGroup("End Color", () -> c.color2, v -> c.color2 = v));
		root.child(ModuleDef.slider("Hue Min", category, 0, 360, 0, () -> c.hueMin, v -> c.hueMin = (int) v));
		root.child(ModuleDef.slider("Hue Max", category, 0, 360, 0, () -> c.hueMax, v -> c.hueMax = (int) v));

		addExtraSettings(root);
		return root;
	}

	/** Группа из трёх RGB-слайдеров для int-цвета 0xRRGGBB. */
	protected final ModuleDef rgbGroup(String name, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
		return ModuleDef.group(category, () -> name)
				.child(ModuleDef.slider("R", category, 0, 255, 0, () -> (get.getAsInt() >> 16) & 0xFF,
						v -> set.accept((get.getAsInt() & 0x00FFFF) | ((int) v << 16))))
				.child(ModuleDef.slider("G", category, 0, 255, 0, () -> (get.getAsInt() >> 8) & 0xFF,
						v -> set.accept((get.getAsInt() & 0xFF00FF) | ((int) v << 8))))
				.child(ModuleDef.slider("B", category, 0, 255, 0, () -> get.getAsInt() & 0xFF,
						v -> set.accept((get.getAsInt() & 0xFFFF00) | (int) v)));
	}
}
