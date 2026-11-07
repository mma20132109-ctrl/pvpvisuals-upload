package com.pvpvisuals.render.particle;

import java.util.EnumMap;
import java.util.Map;

/**
 * «Текстуры» частиц без файлов: каждая форма — процедурный многоугольник с альфой по вершинам.
 * Центр имеет альфу 1, вершины контура — свой коэффициент; между центром и контуром при необходимости
 * есть внутреннее кольцо. Градиент альфы по вершинам даёт мягкий радиальный спад без жёстких пикселей.
 */
public final class ParticleTextureRegistry {
	/** Контур единичного размера; координаты в долях размера частицы. */
	public static final class Shape {
		public final float[] x;
		public final float[] y;
		public final float[] a;
		/** Масштаб внутреннего кольца относительно контура (0 — кольца нет). */
		public final float inner;
		public final float innerAlpha;

		Shape(float[] x, float[] y, float[] a, float inner, float innerAlpha) {
			this.x = x;
			this.y = y;
			this.a = a;
			this.inner = inner;
			this.innerAlpha = innerAlpha;
		}

		public int n() {
			return x.length;
		}
	}

	private static final Map<ParticleStyle, Shape> SHAPES = new EnumMap<>(ParticleStyle.class);

	static {
		SHAPES.put(ParticleStyle.CIRCLE, polar(12, i -> 1f, i -> 0f, 0.5f, 0.55f));
		SHAPES.put(ParticleStyle.SQUARE, new Shape(
				new float[] {-0.7f, 0.7f, 0.7f, -0.7f},
				new float[] {-0.7f, -0.7f, 0.7f, 0.7f},
				new float[] {0.9f, 0.9f, 0.9f, 0.9f}, 0f, 0f));
		SHAPES.put(ParticleStyle.SPARK, new Shape(
				new float[] {0f, -0.25f, 0f, 0.25f},
				new float[] {1.7f, 0f, -1.7f, 0f},
				new float[] {0f, 0.2f, 0f, 0.2f}, 0f, 0f));
		SHAPES.put(ParticleStyle.STAR, polar(8, i -> (i & 1) == 0 ? 1.3f : 0.38f, i -> (i & 1) == 0 ? 0.1f : 0.5f, 0f, 0f));

		int n = 18;
		float[] hx = new float[n];
		float[] hy = new float[n];
		float[] ha = new float[n];
		for (int i = 0; i < n; i++) {
			double t = i * Math.PI * 2 / n;
			double sx = 16 * Math.pow(Math.sin(t), 3);
			double sy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
			hx[i] = (float) (sx / 16.0);
			hy[i] = (float) ((sy + 2.0) / 16.0);
			ha[i] = 0.9f;
		}
		SHAPES.put(ParticleStyle.HEART, new Shape(hx, hy, ha, 0f, 0f));
	}

	private interface IntToFloat {
		float get(int i);
	}

	private static Shape polar(int n, IntToFloat radius, IntToFloat alpha, float inner, float innerAlpha) {
		float[] x = new float[n];
		float[] y = new float[n];
		float[] a = new float[n];
		for (int i = 0; i < n; i++) {
			double ang = Math.PI / 2 + i * Math.PI * 2 / n;
			x[i] = (float) Math.cos(ang) * radius.get(i);
			y[i] = (float) Math.sin(ang) * radius.get(i);
			a[i] = alpha.get(i);
		}
		return new Shape(x, y, a, inner, innerAlpha);
	}

	private ParticleTextureRegistry() { }

	public static Shape get(ParticleStyle style) {
		return SHAPES.get(style);
	}
}
