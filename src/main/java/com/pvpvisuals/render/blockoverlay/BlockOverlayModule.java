package com.pvpvisuals.render.blockoverlay;

import org.joml.Matrix4f;

import com.pvpvisuals.config.BlockOverlaySettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block Overlay. Extraction (WorldRenderEvents.END_EXTRACTION): берём блок под прицелом (Minecraft#hitResult),
 * считаем положение относительно камеры и копируем видовую матрицу. Rendering (END_MAIN) — {@link BlockOverlayRenderer}.
 * BEFORE_BLOCK_OUTLINE прячет ванильную чёрную обводку, пока работает наш оверлей.
 */
public final class BlockOverlayModule {
	/**
	 * Потолок дальности. Ваниль и так возвращает попадание по блоку только в пределах досягаемости
	 * (4.5 в выживании, 5 в творческом), поэтому здесь запас, а не точное значение.
	 */
	private static final double MAX_DISTANCE = 6.0;

	/** Снимок кадра: всё, что нужно рендеру. Один объект, переиспользуется (без аллокаций на кадр). */
	public static final class Frame {
		public boolean valid;
		/** Угол блока (min) относительно камеры. */
		public float offX, offY, offZ;
		/** Границы формы блока внутри ячейки 0..1. */
		public float minX, minY, minZ, maxX, maxY, maxZ;
		/** Видовая матрица мира (как у ванили), без перспективы. */
		public final Matrix4f view = new Matrix4f();
	}

	private static final Frame FRAME = new Frame();

	// кэш формы блока: пересчитываем только при смене блока или его состояния
	private static BlockPos lastPos;
	private static BlockState lastState;
	private static float[] box = new float[] {0, 0, 0, 1, 1, 1};

	private BlockOverlayModule() { }

	public static Frame frame() {
		return FRAME;
	}

	public static void register() {
		WorldRenderEvents.END_EXTRACTION.register(BlockOverlayModule::extract);
		WorldRenderEvents.END_MAIN.register(BlockOverlayRenderer::render);
		// false = отменить ванильную обводку (только если наш шейдерный оверлей реально рисуется)
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, state) -> !(FRAME.valid && BlockOverlayRenderer.works()));
	}

	private static void extract(WorldExtractionContext context) {
		Frame f = FRAME;
		f.valid = false;

		BlockOverlaySettings s = PvpConfig.get().blockOverlay;
		Minecraft mc = Minecraft.getInstance();
		if (!s.enabled || mc.level == null || mc.player == null) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}
		Vec3 cam = context.worldState().cameraRenderState.pos;
		if (mc.player.getEyePosition().distanceTo(hit.getLocation()) > MAX_DISTANCE) {
			return;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos); // чанк не загружен -> воздух
		if (state.isAir()) {
			return;
		}
		if (!pos.equals(lastPos) || state != lastState) {
			lastPos = pos.immutable();
			lastState = state;
			VoxelShape shape = state.getShape(mc.level, pos);
			if (shape.isEmpty()) {
				box = new float[] {0, 0, 0, 1, 1, 1};
			} else {
				AABB b = shape.bounds();
				box = new float[] {(float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY,
						(float) b.maxZ};
			}
		}

		f.offX = (float) (pos.getX() - cam.x);
		f.offY = (float) (pos.getY() - cam.y);
		f.offZ = (float) (pos.getZ() - cam.z);
		f.minX = box[0];
		f.minY = box[1];
		f.minZ = box[2];
		f.maxX = box[3];
		f.maxY = box[4];
		f.maxZ = box[5];
		f.view.set(context.viewMatrix());
		f.valid = true;
	}

	private static String qualityTitle(String q) {
		return "LOW".equalsIgnoreCase(q) ? "Low" : "HIGH".equalsIgnoreCase(q) ? "High" : "Medium";
	}

	private static String nextQuality(String q) {
		return "LOW".equalsIgnoreCase(q) ? "MEDIUM" : "MEDIUM".equalsIgnoreCase(q) || q == null ? "HIGH" : "LOW";
	}

	public static ModuleDef def(PvpConfig pc) {
		BlockOverlaySettings s = pc.blockOverlay;
		ModuleDef dropdown = ModuleDef.group(Category.VISUAL,
				() -> "Style: " + BlockOverlayStyle.byName(s.shaderType).title);
		for (BlockOverlayStyle t : BlockOverlayStyle.values()) {
			dropdown.child(ModuleDef.toggle(t.title, Category.VISUAL,
					() -> BlockOverlayStyle.byName(s.shaderType) == t,
					v -> {
						if (v) {
							s.shaderType = t.name();
						}
					}));
		}
		return ModuleDef.toggle("Block Overlay", Category.VISUAL, () -> s.enabled, v -> {
			s.enabled = v;
			if (v) {
				BlockOverlayRenderer.reset();
			}
		})
				.child(dropdown)
				.child(ModuleDef.slider("R", Category.VISUAL, 0, 255, 0, () -> (s.color >> 16) & 0xFF,
						v -> s.color = (s.color & 0x00FFFF) | ((int) v << 16)))
				.child(ModuleDef.slider("G", Category.VISUAL, 0, 255, 0, () -> (s.color >> 8) & 0xFF,
						v -> s.color = (s.color & 0xFF00FF) | ((int) v << 8)))
				.child(ModuleDef.slider("B", Category.VISUAL, 0, 255, 0, () -> s.color & 0xFF,
						v -> s.color = (s.color & 0xFFFF00) | (int) v))
				.child(ModuleDef.slider("Alpha", Category.VISUAL, 0.05, 1.0, 2,
						() -> s.alpha, v -> s.alpha = (float) v))
				.child(ModuleDef.slider("Pulse Speed", Category.VISUAL, 0.1, 4.0, 2,
						() -> s.pulseSpeed, v -> s.pulseSpeed = (float) v))
				.child(ModuleDef.slider("Line Thickness", Category.VISUAL, 0.5, 8.0, 1,
						() -> s.lineThickness, v -> s.lineThickness = (float) v))
				.child(ModuleDef.slider("R2", Category.VISUAL, 0, 255, 0, () -> (s.colorSecondary >> 16) & 0xFF,
						v -> s.colorSecondary = (s.colorSecondary & 0x00FFFF) | ((int) v << 16))
						.visibleWhen(() -> BlockOverlayStyle.byName(s.shaderType).cosmic))
				.child(ModuleDef.slider("G2", Category.VISUAL, 0, 255, 0, () -> (s.colorSecondary >> 8) & 0xFF,
						v -> s.colorSecondary = (s.colorSecondary & 0xFF00FF) | ((int) v << 8))
						.visibleWhen(() -> BlockOverlayStyle.byName(s.shaderType).cosmic))
				.child(ModuleDef.slider("B2", Category.VISUAL, 0, 255, 0, () -> s.colorSecondary & 0xFF,
						v -> s.colorSecondary = (s.colorSecondary & 0xFFFF00) | (int) v)
						.visibleWhen(() -> BlockOverlayStyle.byName(s.shaderType).cosmic))
				.child(ModuleDef.slider("Star Density", Category.VISUAL, 0.2, 3.0, 1,
						() -> s.starDensity, v -> s.starDensity = (float) v)
						.visibleWhen(() -> BlockOverlayStyle.byName(s.shaderType).stars))
				.child(ModuleDef.cycle(Category.VISUAL, () -> "Quality: " + qualityTitle(s.renderQuality),
						() -> s.renderQuality = nextQuality(s.renderQuality))
						.visibleWhen(() -> BlockOverlayStyle.byName(s.shaderType).cosmic))
				.child(ModuleDef.toggle("Through Walls", Category.VISUAL,
						() -> s.showThroughWalls, v -> s.showThroughWalls = v))
				.child(ModuleDef.toggle("Outline Only", Category.VISUAL,
						() -> s.outlineOnly, v -> s.outlineOnly = v));
	}
}
