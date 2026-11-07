package com.pvpvisuals.render.particle;

/** Настройки Block Hit Particle. */
public class BlockParticleConfig extends ParticleConfig {
	/** true — цвет берётся из блока, false — ручной (color1). */
	public boolean autoColor = true;
	/** Отступ точки появления от грани блока, в блоках. */
	public float offset = 0.05f;

	public BlockParticleConfig() {
		style = "SQUARE";
		count = 8;
		size = 0.07f;
		sizeGrow = -0.3f;
		life = 22;
		spread = 0.9f;
		velocity = 0.5f;
		gravity = 0.6f;
		friction = 0.9f;
	}
}
