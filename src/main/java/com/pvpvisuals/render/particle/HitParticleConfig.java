package com.pvpvisuals.render.particle;

/** Настройки Hit Particle. */
public class HitParticleConfig extends ParticleConfig {
	public int normalColor = 0xFFFFFF;
	public int critColor = 0xFF5522;
	public int enchantColor = 0x8F6BFF;
	public boolean critFlash = true;

	public HitParticleConfig() {
		style = "SPARK";
		count = 14;
		size = 0.12f;
		life = 18;
		spread = 0.6f;
		velocity = 0.8f;
		gravity = 0.2f;
		friction = 0.88f;
	}
}
