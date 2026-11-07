package com.pvpvisuals.render.hat;

import java.util.ArrayList;
import java.util.List;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * China Hat. Extraction (WorldRenderEvents.END_EXTRACTION): собираем список шляп с готовыми
 * координатами относительно камеры. Rendering (END_MAIN): {@link ChinaHatRenderer} рисует по снимку.
 */
public final class ChinaHatModule {
	/** Одна шляпа в снимке кадра (координаты центра основания — относительно камеры). */
	public static final class Hat {
		public double x;
		public double y;
		public double z;
		public double spin;
	}

	public static final class Frame {
		public boolean valid;
		public final List<Hat> hats = new ArrayList<>();
		public float radius;
		public float height;
		public int rgb;
		public int alpha;
		public boolean outline;
		public float outlineWidth;
	}

	private static final Frame FRAME = new Frame();

	private ChinaHatModule() { }

	public static Frame frame() {
		return FRAME;
	}

	public static void register() {
		WorldRenderEvents.END_EXTRACTION.register(ChinaHatModule::extract);
		WorldRenderEvents.END_MAIN.register(ChinaHatRenderer::render);
	}

	private static void extract(WorldExtractionContext context) {
		Frame f = FRAME;
		f.valid = false;
		f.hats.clear();

		PvpConfig c = PvpConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!c.chinaHat || mc.player == null || mc.level == null) {
			return;
		}

		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.worldState().cameraRenderState.pos;
		HatTarget mode = HatTarget.byName(c.hatTarget);
		HatAnimation anim = HatAnimation.byName(c.hatAnim);
		double time = (mc.level.getGameTime() + pt) / 20.0;

		f.radius = c.hatRadius;
		f.height = c.hatHeight;
		f.rgb = c.hatColor & 0xFFFFFF;
		f.alpha = Mth.clamp(c.hatAlpha, 0, 255);
		f.outline = c.hatOutline;
		f.outlineWidth = c.hatOutlineWidth;

		// ПРОВЕРИТЬ: CameraType#isFirstPerson() в Mojang mappings 1.21.11.
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();

		for (AbstractClientPlayer p : mc.level.players()) {
			boolean self = p == mc.player;
			if (self ? mode == HatTarget.OTHERS : mode == HatTarget.SELF) {
				continue;
			}
			if (self && firstPerson) {
				continue; // от первого лица шляпа только мешает обзору
			}
			if (p.isInvisible() || p.isSpectator()) {
				continue;
			}
			// Скрываем при плавании, полёте на элитрах и ползании.
			if (p.isSwimming() || p.isFallFlying() || p.isVisuallyCrawling()) {
				continue;
			}

			double px = Mth.lerp((double) pt, p.xo, p.getX());
			double py = Mth.lerp((double) pt, p.yo, p.getY());
			double pz = Mth.lerp((double) pt, p.zo, p.getZ());

			// Смещения X (вправо) и Z (вперёд) считаются относительно поворота тела.
			double yaw = Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
			double fwdX = -Math.sin(yaw);
			double fwdZ = Math.cos(yaw);
			double rightX = -Math.cos(yaw);
			double rightZ = -Math.sin(yaw);

			double bob = anim.bob ? Math.sin(time * 2.0) * 0.1 * c.hatAmp : 0.0;

			Hat h = new Hat();
			h.x = px + rightX * c.hatOffX + fwdX * c.hatOffZ - cam.x;
			// bbHeight уже учитывает присед; 0.12 — зазор над головой, чтобы шляпа парила.
			h.y = py + p.getBbHeight() + 0.12 + c.hatOffY + bob - cam.y;
			h.z = pz + rightZ * c.hatOffX + fwdZ * c.hatOffZ - cam.z;
			h.spin = anim.spin ? time * 1.5 * c.hatAmp : 0.0;
			f.hats.add(h);
		}
		f.valid = !f.hats.isEmpty();
	}

	/** Описание модуля для категории Visual. */
	public static ModuleDef def(PvpConfig c) {
		ModuleDef target = ModuleDef.group(Category.VISUAL, () -> "Show: " + HatTarget.byName(c.hatTarget).title);
		for (HatTarget t : HatTarget.values()) {
			target.child(ModuleDef.toggle(t.title, Category.VISUAL,
					() -> HatTarget.byName(c.hatTarget) == t, v -> c.hatTarget = t.name()));
		}
		ModuleDef anim = ModuleDef.group(Category.VISUAL, () -> "Anim: " + HatAnimation.byName(c.hatAnim).title);
		for (HatAnimation a : HatAnimation.values()) {
			anim.child(ModuleDef.toggle(a.title, Category.VISUAL,
					() -> HatAnimation.byName(c.hatAnim) == a, v -> c.hatAnim = a.name()));
		}

		return ModuleDef.toggle("China Hat", Category.VISUAL, () -> c.chinaHat, v -> c.chinaHat = v)
				.child(target)
				.child(anim)
				.child(ModuleDef.slider("Amplitude", Category.VISUAL, 0.1, 2.0, 1, () -> c.hatAmp, v -> c.hatAmp = (float) v))
				.child(ModuleDef.slider("Radius", Category.VISUAL, 0.2, 1.0, 2, () -> c.hatRadius, v -> c.hatRadius = (float) v))
				.child(ModuleDef.slider("Height", Category.VISUAL, 0.1, 1.0, 2, () -> c.hatHeight, v -> c.hatHeight = (float) v))
				.child(ModuleDef.slider("R", Category.VISUAL, 0, 255, 0, () -> (c.hatColor >> 16) & 0xFF,
						v -> c.hatColor = (c.hatColor & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("G", Category.VISUAL, 0, 255, 0, () -> (c.hatColor >> 8) & 0xFF,
						v -> c.hatColor = (c.hatColor & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("B", Category.VISUAL, 0, 255, 0, () -> c.hatColor & 0xFF,
						v -> c.hatColor = (c.hatColor & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Alpha", Category.VISUAL, 0, 255, 0, () -> c.hatAlpha, v -> c.hatAlpha = (int) v))
				.child(ModuleDef.slider("Offset X", Category.VISUAL, -1.0, 1.0, 2, () -> c.hatOffX, v -> c.hatOffX = (float) v))
				.child(ModuleDef.slider("Offset Y", Category.VISUAL, -0.5, 1.0, 2, () -> c.hatOffY, v -> c.hatOffY = (float) v))
				.child(ModuleDef.slider("Offset Z", Category.VISUAL, -1.0, 1.0, 2, () -> c.hatOffZ, v -> c.hatOffZ = (float) v))
				.child(ModuleDef.toggle("Outline", Category.VISUAL, () -> c.hatOutline, v -> c.hatOutline = v))
				.child(ModuleDef.slider("Line Width", Category.VISUAL, 0.005, 0.05, 3, () -> c.hatOutlineWidth,
						v -> c.hatOutlineWidth = (float) v));
	}
}
