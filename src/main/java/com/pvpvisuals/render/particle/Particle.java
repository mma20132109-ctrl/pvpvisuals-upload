package com.pvpvisuals.render.particle;

/** Одна живая частица (данные симуляции). Координаты — мировые. */
public final class Particle {
	public double x;
	public double y;
	public double z;
	public double px;
	public double py;
	public double pz;
	public double vx;
	public double vy;
	public double vz;

	public int age;
	public int life = 20;

	public float size = 0.1f;
	/** Множитель размера к концу жизни относительно начального (1 + grow). */
	public float sizeEndMul = 0.5f;
	public int rgbStart = 0xFFFFFF;
	public int rgbEnd = 0xFFFFFF;
	public int alpha = 255;
	public float gravity;
	public float friction = 0.9f;
	public float rot;
	public ParticleStyle style = ParticleStyle.CIRCLE;
}
