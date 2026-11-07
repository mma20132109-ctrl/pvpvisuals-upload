package com.pvpvisuals.render.world;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.hud.HudManager;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * World Particles: регистрация событий рендера мира, фаза extraction и описание модуля для GUI.
 *
 * Extraction (END_EXTRACTION): здесь читается состояние игры (якорь, камера, конфиг) и кладётся
 * в {@link Frame}. Rendering (END_MAIN): {@link WorldParticlesRenderer} рисует только по Frame,
 * не обращаясь к миру напрямую.
 */
public final class WorldParticlesModule {
	/** Снимок кадра. Заполняется в extraction, читается в render. */
	public static final class Frame {
		public boolean valid;
		public ParticleStyle style = ParticleStyle.STARS;
		/** Центры эффекта (игрок и/или цель): x, y, z, половина высоты. */
		public final double[][] anchors = new double[2][4];
		public int anchorCount;
		/** Половина высоты текущего якоря (выставляется рендерером в цикле). */
		public double halfHeight = 0.9;
		public double time;
		public double camX;
		public double camY;
		public double camZ;
		public float yaw;
		public float pitch;
		public int rgb;
		public float size;
		public float speed;
		public float radius;
		public boolean turbulence;
	}

	private static final Frame FRAME = new Frame();

	private WorldParticlesModule() { }

	public static Frame frame() {
		return FRAME;
	}

	public static void register() {
		WorldRenderEvents.END_EXTRACTION.register(WorldParticlesModule::extract);
		WorldRenderEvents.END_MAIN.register(WorldParticlesRenderer::render);
	}

	private static void extract(WorldExtractionContext context) {
		Frame f = FRAME;
		f.valid = false;

		PvpConfig c = PvpConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!c.worldParticles || mc.player == null || mc.level == null) {
			return;
		}

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		f.anchorCount = 0;
		if (c.wpOnSelf) {
			addAnchor(f, mc.player, pt);
		}
		if (c.wpOnTarget) {
			LivingEntity target = HudManager.currentTarget(mc);
			if (target != null) {
				addAnchor(f, target, pt);
			}
		}
		if (f.anchorCount == 0) {
			return;
		}

		f.time = (mc.level.getGameTime() + pt) / 20.0;

		// Позиция камеры из состояния рендера (как в документации Fabric 1.21.11).
		Vec3 cam = context.worldState().cameraRenderState.pos;
		f.camX = cam.x;
		f.camY = cam.y;
		f.camZ = cam.z;
		f.yaw = mc.player.getViewYRot(pt);
		f.pitch = mc.player.getViewXRot(pt);

		f.style = ParticleStyle.byName(c.wpStyle);
		f.rgb = c.wpColor & 0xFFFFFF;
		f.size = c.wpSize;
		f.speed = c.wpSpeed;
		f.radius = c.wpRadius;
		f.turbulence = c.wpTurbulence;
		f.valid = true;
	}

	private static void addAnchor(Frame f, Entity e, float pt) {
		double[] a = f.anchors[f.anchorCount++];
		a[0] = Mth.lerp((double) pt, e.xo, e.getX());
		a[1] = Mth.lerp((double) pt, e.yo, e.getY()) + e.getBbHeight() * 0.5;
		a[2] = Mth.lerp((double) pt, e.zo, e.getZ());
		a[3] = e.getBbHeight() * 0.5 + 0.15;
	}

	/** Описание модуля для категории Particles: галочка + подпункты (стиль, цвет, размер ...). */
	public static ModuleDef def(PvpConfig c) {
		ModuleDef style = ModuleDef.group(Category.PARTICLES, () -> "Style: " + ParticleStyle.byName(c.wpStyle).title);
		for (ParticleStyle s : ParticleStyle.values()) {
			style.child(ModuleDef.toggle(s.title, Category.PARTICLES,
					() -> ParticleStyle.byName(c.wpStyle) == s,
					v -> c.wpStyle = s.name()));
		}

		return ModuleDef.toggle("World Particles", Category.PARTICLES, () -> c.worldParticles, v -> c.worldParticles = v)
				.child(style)
				.child(ModuleDef.slider("R", Category.PARTICLES, 0, 255, 0,
						() -> (c.wpColor >> 16) & 0xFF,
						v -> c.wpColor = (c.wpColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("G", Category.PARTICLES, 0, 255, 0,
						() -> (c.wpColor >> 8) & 0xFF,
						v -> c.wpColor = (c.wpColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("B", Category.PARTICLES, 0, 255, 0,
						() -> c.wpColor & 0xFF,
						v -> c.wpColor = (c.wpColor & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Size", Category.PARTICLES, 0.02, 0.30, 2,
						() -> c.wpSize, v -> c.wpSize = (float) v))
				.child(ModuleDef.slider("Speed", Category.PARTICLES, 0.2, 3.0, 1,
						() -> c.wpSpeed, v -> c.wpSpeed = (float) v))
				.child(ModuleDef.slider("Radius", Category.PARTICLES, 0.5, 4.0, 1,
						() -> c.wpRadius, v -> c.wpRadius = (float) v))
				.child(ModuleDef.toggle("Turbulence", Category.PARTICLES, () -> c.wpTurbulence, v -> c.wpTurbulence = v))
				.child(ModuleDef.toggle("On Self", Category.PARTICLES, () -> c.wpOnSelf, v -> c.wpOnSelf = v))
				.child(ModuleDef.toggle("On Target", Category.PARTICLES, () -> c.wpOnTarget, v -> c.wpOnTarget = v));
	}
}
