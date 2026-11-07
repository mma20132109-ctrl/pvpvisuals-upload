package com.pvpvisuals.crosshair;

import com.pvpvisuals.config.CrosshairSettings;
import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Рисует прицел прямоугольниками GuiGraphics#fill. Геометрия (список прямоугольников относительно центрального
 * пикселя) кэшируется и пересчитывается только при смене стиля или округлённых размеров, поэтому в кадре нет аллокаций.
 */
public final class CrosshairRenderer {
	private static final double HALF_PI = Math.PI / 2.0;

	// кэш геометрии
	private int[] x0 = new int[160];
	private int[] y0 = new int[160];
	private int[] x1 = new int[160];
	private int[] y1 = new int[160];
	private float[] gradT = new float[160];
	private int n;
	private long cacheKey = Long.MIN_VALUE;

	// состояние анимации и разброса
	private long hitNs;
	private float spreadShown = 1.0f;

	public void onHit() {
		hitNs = System.nanoTime();
	}

	// ---------- кадр ----------

	public void render(GuiGraphics g, CrosshairSettings s) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null) {
			return;
		}
		if (s.hideInGui && mc.screen != null) {
			return;
		}
		// ПРОВЕРИТЬ: Options#getCameraType() и CameraType#isFirstPerson() в 1.21.11.
		if (s.hideInThirdPerson && !mc.options.getCameraType().isFirstPerson()) {
			return;
		}

		CrosshairStyle style = CrosshairStyle.byName(s.style);
		CrosshairAnimation anim = CrosshairAnimation.byName(s.animation);

		// прогресс анимации удара: импульс sin(pi * t) — плавно туда и обратно
		float k = 0f;
		if (anim != CrosshairAnimation.NONE && hitNs != 0) {
			float ms = (System.nanoTime() - hitNs) / 1_000_000f;
			float t = ms / Math.max(1, s.animationDuration);
			if (t < 1f) {
				k = (float) Math.sin(t * Math.PI);
			}
		}

		float factor = spreadFactor(p, s);
		if (anim == CrosshairAnimation.EXPAND) {
			factor *= 1f + 0.5f * k;
		} else if (anim == CrosshairAnimation.SHRINK) {
			factor *= 1f - 0.5f * k;
		}

		int thick = s.thickness;
		int gapEff = Math.max(0, Math.round((s.gap + 2) * factor - 2));
		int radiusEff = Math.max(2, Math.round(s.circleRadius * factor));
		int dot = Math.max(1, s.dotSize);
		build(style, thick, s.length, gapEff, dot, radiusEff);

		int cx = (g.guiWidth() - 1) / 2;
		int cy = (g.guiHeight() - 1) / 2;

		g.pose().pushMatrix();
		g.pose().translate(cx + 0.5f, cy + 0.5f);
		if (anim == CrosshairAnimation.ROTATE && k > 0f) {
			// Поворот до 90° (прицел симметричен, конец анимации совпадает с началом); ease = k по синусу.
			// ПРОВЕРИТЬ: Matrix3x2fStack#rotate(float) и что fill() применяет поворот к прямоугольнику.
			g.pose().rotate((float) (k * HALF_PI));
		}
		g.pose().translate(-0.5f, -0.5f);

		int alpha = Math.round(Mth.clamp(s.alpha, 0f, 1f) * 255f);
		if (alpha > 0) {
			if (s.outline && s.outlineThickness > 0) {
				int oc = (alpha << 24) | (s.outlineColor & 0xFFFFFF);
				int o = s.outlineThickness;
				for (int i = 0; i < n; i++) {
					g.fill(x0[i] - o, y0[i] - o, x1[i] + o, y1[i] + o, oc);
				}
			}
			boolean gradient = CrosshairColorMode.byName(s.colorMode) == CrosshairColorMode.GRADIENT;
			boolean flash = anim == CrosshairAnimation.FLASH && k > 0f;
			for (int i = 0; i < n; i++) {
				int rgb = gradient ? lerp(s.colorPrimary, s.colorSecondary, gradT[i]) : s.colorPrimary & 0xFFFFFF;
				if (flash) {
					rgb = lerp(rgb, s.flashColor, k);
				}
				g.fill(x0[i], y0[i], x1[i], y1[i], (alpha << 24) | rgb);
			}
		}
		g.pose().popMatrix();
	}

	private static int lerp(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int r = ar + Math.round((((b >> 16) & 0xFF) - ar) * t);
		int gg = ag + Math.round((((b >> 8) & 0xFF) - ag) * t);
		int bb = ab + Math.round(((b & 0xFF) - ab) * t);
		return (r << 16) | (gg << 8) | bb;
	}

	// ---------- динамический разброс ----------

	private float spreadFactor(Player p, CrosshairSettings s) {
		float target = 1f;
		if (s.dynamicSpread) {
			float mod = 0f;
			// ПРОВЕРИТЬ: LivingEntity#isUsingItem(), #getTicksUsingItem(), #getUseItem(), ItemStack#is(Item).
			if (p.isUsingItem() && (p.getUseItem().is(Items.BOW) || p.getUseItem().is(Items.CROSSBOW))) {
				mod += Mth.clamp(p.getTicksUsingItem() / 20f, 0f, 1f);
			}
			// ПРОВЕРИТЬ: Entity#getDeltaMovement(), Vec3#horizontalDistance().
			mod += Mth.clamp((float) p.getDeltaMovement().horizontalDistance() * 1.5f, 0f, 0.5f);
			// ПРОВЕРИТЬ: Entity#onGround(), Entity#isShiftKeyDown().
			if (!p.onGround()) {
				mod += 0.5f;
			}
			if (p.isShiftKeyDown()) {
				mod -= 0.3f;
			}
			target = Math.max(0.3f, 1f + mod * s.spreadMultiplier);
		}
		spreadShown += (target - spreadShown) * 0.25f;
		return spreadShown;
	}

	// ---------- геометрия ----------

	private void add(int ax0, int ay0, int ax1, int ay1) {
		if (n == x0.length) {
			int cap = n * 2;
			x0 = java.util.Arrays.copyOf(x0, cap);
			y0 = java.util.Arrays.copyOf(y0, cap);
			x1 = java.util.Arrays.copyOf(x1, cap);
			y1 = java.util.Arrays.copyOf(y1, cap);
			gradT = java.util.Arrays.copyOf(gradT, cap);
		}
		x0[n] = ax0;
		y0[n] = ay0;
		x1[n] = ax1;
		y1[n] = ay1;
		n++;
	}

	private void build(CrosshairStyle style, int t, int len, int gap, int dot, int radius) {
		long key = ((((((style.ordinal() * 31L + t) * 31 + len) * 31 + gap) * 31 + dot) * 31) + radius);
		if (key == cacheKey) {
			return;
		}
		cacheKey = key;
		n = 0;
		int lo = -(t / 2);
		int hi = lo + t;
		switch (style) {
			case CROSS -> cross(lo, hi, len, gap);
			case DOT -> dot(dot);
			case CROSS_DOT -> {
				cross(lo, hi, len, gap);
				dot(dot);
			}
			case CIRCLE -> circle(radius, t);
			case SQUARE -> square(Math.max(2, gap + len / 2), t);
			case ARROWS -> arrows(t, len, gap);
		}
		// градиент: расстояние центра прямоугольника от центра, нормированное на максимум
		float max = 0.0001f;
		for (int i = 0; i < n; i++) {
			float cxr = (x0[i] + x1[i]) * 0.5f - 0.5f;
			float cyr = (y0[i] + y1[i]) * 0.5f - 0.5f;
			float d = (float) Math.sqrt(cxr * cxr + cyr * cyr);
			gradT[i] = d;
			max = Math.max(max, d);
		}
		for (int i = 0; i < n; i++) {
			gradT[i] = gradT[i] / max;
		}
	}

	private void cross(int lo, int hi, int len, int gap) {
		add(hi + gap, lo, hi + gap + len, hi);       // право
		add(lo - gap - len, lo, lo - gap, hi);        // лево
		add(lo, lo - gap - len, hi, lo - gap);        // верх
		add(lo, hi + gap, hi, hi + gap + len);        // низ
	}

	private void dot(int d) {
		int lo = -(d / 2);
		add(lo, lo, lo + d, lo + d);
	}

	/** Кольцо: строка за строкой, внутренняя и внешняя границы по радиусу (центр пикселя — начало координат). */
	private void circle(int r, int t) {
		double ri = Math.max(0.5, r - t / 2.0);
		double ro = ri + t;
		int rows = (int) Math.ceil(ro);
		for (int dy = -rows; dy <= rows; dy++) {
			double oy = (double) dy * dy;
			if (oy > ro * ro) {
				continue;
			}
			int a = (int) Math.floor(Math.sqrt(ro * ro - oy));
			if (oy < ri * ri) {
				int b = (int) Math.floor(Math.sqrt(ri * ri - oy));
				if (b + 1 <= a) {
					add(-a, dy, -b, dy + 1);
					add(b + 1, dy, a + 1, dy + 1);
				}
			} else {
				add(-a, dy, a + 1, dy + 1);
			}
		}
	}

	/** Полый квадрат со стороной 2*half+1 и толщиной рамки t. */
	private void square(int half, int t) {
		int e = half + 1;
		add(-half, -half, e, -half + t);     // верх
		add(-half, e - t, e, e);             // низ
		add(-half, -half + t, -half + t, e - t); // лево
		add(e - t, -half + t, e, e - t);     // право
	}

	/** Четыре шеврона остриём к центру: ступенчатая диагональ из квадратиков t×t. */
	private void arrows(int t, int len, int gap) {
		int steps = Math.max(2, len / 2);
		int lo = -(t / 2);
		for (int i = 0; i < steps; i++) {
			int d = gap + i * t;       // вдоль направления (от центра)
			int pOff = i * t;          // в стороны
			// верх: острие вверх к центру внизу
			add(lo + pOff, -d - t, lo + pOff + t, -d);
			add(lo - pOff, -d - t, lo - pOff + t, -d);
			// низ
			add(lo + pOff, d + 1, lo + pOff + t, d + 1 + t);
			add(lo - pOff, d + 1, lo - pOff + t, d + 1 + t);
			// право
			add(d + 1, lo + pOff, d + 1 + t, lo + pOff + t);
			add(d + 1, lo - pOff, d + 1 + t, lo - pOff + t);
			// лево
			add(-d - t, lo + pOff, -d, lo + pOff + t);
			add(-d - t, lo - pOff, -d, lo - pOff + t);
		}
	}
}
