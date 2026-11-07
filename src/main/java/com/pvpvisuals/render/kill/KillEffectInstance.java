package com.pvpvisuals.render.kill;

/** Один активный эффект. Параметры копируются из конфига при создании, чтобы эффект не менялся на лету. */
public final class KillEffectInstance {
	public final double x;
	/** Y ног. */
	public final double y;
	public final double z;
	/** Высота центра тела над ногами. */
	public final float centerOffset;
	public final int life;
	public final KillEffectStyle style;
	public final int seed;
	public final int rgb;
	public final float size;
	public final boolean glow;
	public int age;

	public KillEffectInstance(double x, double y, double z, float centerOffset, int life, KillEffectStyle style,
			int seed, int rgb, float size, boolean glow) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.centerOffset = centerOffset;
		this.life = Math.max(1, life);
		this.style = style;
		this.seed = seed;
		this.rgb = rgb & 0xFFFFFF;
		this.size = size;
		this.glow = glow;
	}
}
