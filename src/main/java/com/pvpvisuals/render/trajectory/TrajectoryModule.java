package com.pvpvisuals.render.trajectory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.render.trajectory.TrajectorySimulator.Result;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * Trajectory. Считает траектории и отдаёт их рендереру.
 * Свой выстрел: пересчёт по ключу (предмет, натяжение, позиция, поворот) в фазе extraction — только при
 * изменении. Снаряды в полёте: пересчёт раз в тик (END_CLIENT_TICK). Рисует {@link TrajectoryRenderer}.
 */
public final class TrajectoryModule {
	/** Снимок для рендера. */
	public static final class Frame {
		public final List<Result> results = new ArrayList<>();
		public double camX;
		public double camY;
		public double camZ;
		public float yaw;
		public float pitch;
	}

	private record Key(Object item, int use, double x, double y, double z, float yaw, float pitch, boolean off) { }

	private static final Frame FRAME = new Frame();
	private static final List<Result> FLYING = new ArrayList<>();
	private static Key lastKey;
	private static Result selfResult;

	private TrajectoryModule() { }

	public static Frame frame() {
		return FRAME;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TrajectoryModule::tick);
		WorldRenderEvents.END_EXTRACTION.register(TrajectoryModule::extract);
		WorldRenderEvents.END_MAIN.register(TrajectoryRenderer::render);
	}

	// ---------- снаряды в полёте (раз в тик) ----------

	private static void tick(Minecraft mc) {
		FLYING.clear();
		PvpConfig pc = PvpConfig.get();
		TrajectoryConfig c = pc.traj;
		String source = c.source;
		if (!pc.trajectory || mc.player == null || mc.level == null || "HAND".equalsIgnoreCase(source)) {
			return;
		}
		boolean all = "ALL".equalsIgnoreCase(source);
		double maxSq = Math.pow(c.maxDistance, 2);

		int count = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			ProjectileType type = ProjectileType.of(e.getType());
			if (type == null || !type.enabled(c) || !(e instanceof Projectile pr)) {
				continue;
			}
			if (!all && !(pr.getOwner() instanceof net.minecraft.world.entity.player.Player)) {
				continue;
			}
			Vec3 vel = e.getDeltaMovement();
			if (vel.lengthSqr() < 0.0025 || e.distanceToSqr(mc.player) > maxSq) {
				continue; // застрявшие и далёкие пропускаем
			}
			Result r = TrajectorySimulator.simulate(mc.level, e, e.position(), vel, type, c.maxDistance, c.detail);
			FLYING.add(r);
			if (++count >= 24) {
				break;
			}
		}
	}

	// ---------- свой выстрел и снимок кадра ----------

	private static void extract(WorldExtractionContext context) {
		FRAME.results.clear();
		PvpConfig pc = PvpConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!pc.trajectory || mc.player == null || mc.level == null) {
			lastKey = null;
			selfResult = null;
			return;
		}
		TrajectoryConfig c = pc.traj;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		LocalPlayer player = mc.player;
		Vec3 eye = player.getEyePosition(pt);
		float yaw = player.getViewYRot(pt);
		float pitch = player.getViewXRot(pt);

		Launch launch = heldLaunch(mc, player, c, pt);
		if (launch == null) {
			lastKey = null;
			selfResult = null;
		} else {
			Key key = new Key(launch.stack.getItem(), player.getTicksUsingItem(), eye.x, eye.y, eye.z, yaw, pitch, launch.offHand);
			if (!Objects.equals(key, lastKey) || selfResult == null) {
				lastKey = key;
				selfResult = TrajectorySimulator.simulate(mc.level, player, launch.start, launch.velocity,
						launch.type, c.maxDistance, c.detail);
				applyDamage(mc, selfResult, launch);
			}
			FRAME.results.add(selfResult);
		}
		FRAME.results.addAll(FLYING);

		Vec3 cam = context.worldState().cameraRenderState.pos;
		FRAME.camX = cam.x;
		FRAME.camY = cam.y;
		FRAME.camZ = cam.z;
		FRAME.yaw = yaw;
		FRAME.pitch = pitch;
	}

	private record Launch(ProjectileType type, Vec3 start, Vec3 velocity, ItemStack stack, boolean offHand,
			double bowSpeed, boolean fullCharge, boolean fromBow) { }

	private static Launch heldLaunch(Minecraft mc, LocalPlayer p, TrajectoryConfig c, float pt) {
		Launch l = launchFor(p, p.getMainHandItem(), false, c, pt);
		return l != null ? l : launchFor(p, p.getOffhandItem(), true, c, pt);
	}

	private static Launch launchFor(LocalPlayer p, ItemStack stack, boolean off, TrajectoryConfig c, float pt) {
		ProjectileType type;
		double speed;
		float pitchOffset = 0f;
		boolean full = false;
		boolean bow = false;

		if (stack.is(Items.BOW)) {
			if (!p.isUsingItem() || p.getUseItem() != stack) {
				return null;
			}
			float f = p.getTicksUsingItem() / 20.0f;
			f = (f * f + f * 2.0f) / 3.0f;
			if (f < 0.1f) {
				return null;
			}
			f = Math.min(f, 1.0f);
			type = ProjectileType.ARROW;
			speed = f * 3.0;
			full = f >= 1.0f;
			bow = true;
		} else if (stack.is(Items.CROSSBOW)) {
			// ПРОВЕРИТЬ: CrossbowItem.isCharged(ItemStack) в 1.21.11. Фейерверк (скорость 1.6) не различается.
			if (!CrossbowItem.isCharged(stack)) {
				return null;
			}
			type = ProjectileType.ARROW;
			speed = 3.15;
			full = true;
			bow = true;
		} else if (stack.is(Items.TRIDENT)) {
			if (!p.isUsingItem() || p.getUseItem() != stack || p.getTicksUsingItem() < 10) {
				return null;
			}
			type = ProjectileType.TRIDENT;
			speed = 2.5;
		} else if (stack.is(Items.ENDER_PEARL)) {
			type = ProjectileType.ENDER_PEARL;
			speed = 1.5;
		} else if (stack.is(Items.SNOWBALL)) {
			type = ProjectileType.SNOWBALL;
			speed = 1.5;
		} else if (stack.is(Items.EGG)) {
			type = ProjectileType.EGG;
			speed = 1.5;
		} else if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
			type = ProjectileType.POTION;
			speed = 0.5;
			pitchOffset = -20f;
		} else if (stack.is(Items.EXPERIENCE_BOTTLE)) {
			type = ProjectileType.EXP_BOTTLE;
			speed = 0.7;
			pitchOffset = -20f;
		} else {
			return null;
		}
		if (!type.enabled(c)) {
			return null;
		}

		double yaw = Math.toRadians(p.getViewYRot(pt));
		double pitch = Math.toRadians(p.getViewXRot(pt));
		Vec3 dir = new Vec3(
				-Math.sin(yaw) * Math.cos(pitch),
				-Math.sin(Math.toRadians(p.getViewXRot(pt) + pitchOffset)),
				Math.cos(yaw) * Math.cos(pitch)).normalize().scale(speed);

		// как в ванили: к скорости добавляется движение стрелка (по Y — только в воздухе)
		Vec3 m = p.getDeltaMovement();
		Vec3 vel = dir.add(m.x, p.onGround() ? 0.0 : m.y, m.z);

		Vec3 start = p.getEyePosition(pt).subtract(0, 0.1, 0);
		return new Launch(type, start, vel, stack, off, speed, full, bow);
	}

	/** Оценка урона стрелы: базовый 2.0, Power добавляет 0.5 * (уровень + 1), крит у полного натяжения. */
	private static void applyDamage(Minecraft mc, Result r, Launch l) {
		r.damageMin = -1;
		r.damageMax = -1;
		if (!l.fromBow || r.type != ProjectileType.ARROW) {
			return;
		}
		int power = 0;
		try {
			// ПРОВЕРИТЬ: registryAccess().lookupOrThrow(...).getOrThrow(ResourceKey) и
			// EnchantmentHelper.getItemEnchantmentLevel(Holder, ItemStack) в 1.21.11.
			var holder = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER);
			power = EnchantmentHelper.getItemEnchantmentLevel(holder, l.stack);
		} catch (RuntimeException ignored) {
			// без зачарований — просто базовый урон
		}
		double base = 2.0 + (power > 0 ? power * 0.5 + 0.5 : 0.0);
		int dmg = Mth.ceil(Mth.clamp(r.impactSpeed * base, 0.0, 2.147483647E9));
		r.damageMin = dmg;
		r.damageMax = l.fullCharge ? dmg + dmg / 2 + 1 : dmg;
	}

	// ---------- GUI ----------

	public static ModuleDef def(PvpConfig c) {
		TrajectoryConfig t = c.traj;
		Category cat = Category.VISUAL;

		ModuleDef shows = ModuleDef.group(cat, () -> "Projectiles");
		for (ProjectileType p : ProjectileType.values()) {
			shows.child(ModuleDef.toggle(p.title, cat, () -> p.enabled(t), v -> p.setEnabled(t, v)));
		}
		shows.child(ModuleDef.action("All", cat, () -> {
			for (ProjectileType p : ProjectileType.values()) {
				p.setEnabled(t, true);
			}
		}));

		ModuleDef source = ModuleDef.group(cat, () -> "Source: " + sourceTitle(t.source));
		source.child(sourceToggle("Hand", "HAND", t, cat));
		source.child(sourceToggle("Players", "PLAYERS", t, cat));
		source.child(sourceToggle("All", "ALL", t, cat));

		ModuleDef style = ModuleDef.group(cat, () -> "Style: " + TrajectoryStyle.byName(t.style).title);
		for (TrajectoryStyle s : TrajectoryStyle.values()) {
			style.child(ModuleDef.toggle(s.title, cat, () -> TrajectoryStyle.byName(t.style) == s, v -> t.style = s.name()));
		}
		ModuleDef line = ModuleDef.group(cat, () -> "Line")
				.child(style)
				.child(ModuleDef.slider("Width", cat, 0.005, 0.12, 3, () -> t.width, v -> t.width = (float) v))
				.child(rgb(cat, "Start Color", () -> t.colorStart, v -> t.colorStart = v))
				.child(rgb(cat, "End Color", () -> t.colorEnd, v -> t.colorEnd = v))
				.child(ModuleDef.toggle("Fade", cat, () -> t.fade, v -> t.fade = v))
				.child(ModuleDef.toggle("Glow", cat, () -> t.glow, v -> t.glow = v))
				.child(ModuleDef.slider("Detail", cat, 1, 8, 0, () -> t.detail, v -> t.detail = (int) v))
				.child(ModuleDef.slider("Max Dist", cat, 16, 128, 0, () -> t.maxDistance, v -> t.maxDistance = (int) v));

		ModuleDef markers = ModuleDef.group(cat, () -> "Markers")
				.child(ModuleDef.toggle("Start", cat, () -> t.showStart, v -> t.showStart = v))
				.child(ModuleDef.toggle("Landing", cat, () -> t.showLanding, v -> t.showLanding = v))
				.child(ModuleDef.slider("Land Size", cat, 0.05, 0.5, 2, () -> t.landingSize, v -> t.landingSize = (float) v))
				.child(ModuleDef.toggle("Drop Line", cat, () -> t.dropLine, v -> t.dropLine = v))
				.child(ModuleDef.toggle("Collision", cat, () -> t.showCollision, v -> t.showCollision = v))
				.child(rgb(cat, "Hit Color", () -> t.collisionColor, v -> t.collisionColor = v))
				.child(ModuleDef.toggle("Face Glow", cat, () -> t.faceHighlight, v -> t.faceHighlight = v))
				.child(ModuleDef.toggle("Progress", cat, () -> t.showProgress, v -> t.showProgress = v))
				.child(ModuleDef.slider("Interval", cat, 1, 20, 0, () -> t.progressInterval, v -> t.progressInterval = (int) v))
				.child(ModuleDef.toggle("Peak", cat, () -> t.showPeak, v -> t.showPeak = v));

		ModuleDef info = ModuleDef.group(cat, () -> "Info")
				.child(ModuleDef.toggle("Distance", cat, () -> t.showDistance, v -> t.showDistance = v))
				.child(ModuleDef.toggle("Time", cat, () -> t.showTime, v -> t.showTime = v))
				.child(ModuleDef.toggle("Damage", cat, () -> t.showDamage, v -> t.showDamage = v))
				.child(ModuleDef.cycle(cat, () -> "Units: " + (t.unitsMeters ? "m" : "blocks"), () -> t.unitsMeters = !t.unitsMeters))
				.child(ModuleDef.cycle(cat, () -> "Time: " + (t.timeSeconds ? "sec" : "ticks"), () -> t.timeSeconds = !t.timeSeconds));

		return ModuleDef.toggle("Trajectory", cat, () -> c.trajectory, v -> c.trajectory = v)
				.child(shows).child(source).child(line).child(markers).child(info);
	}

	private static String sourceTitle(String s) {
		return "ALL".equalsIgnoreCase(s) ? "All" : "PLAYERS".equalsIgnoreCase(s) ? "Players" : "Hand";
	}

	private static ModuleDef sourceToggle(String title, String id, TrajectoryConfig t, Category cat) {
		return ModuleDef.toggle(title, cat, () -> id.equalsIgnoreCase(t.source), v -> t.source = id);
	}

	private static ModuleDef rgb(Category cat, String name, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
		return ModuleDef.group(cat, () -> name)
				.child(ModuleDef.slider("R", cat, 0, 255, 0, () -> (get.getAsInt() >> 16) & 0xFF,
						v -> set.accept((get.getAsInt() & 0x00FFFF) | ((int) v << 16))))
				.child(ModuleDef.slider("G", cat, 0, 255, 0, () -> (get.getAsInt() >> 8) & 0xFF,
						v -> set.accept((get.getAsInt() & 0xFF00FF) | ((int) v << 8))))
				.child(ModuleDef.slider("B", cat, 0, 255, 0, () -> get.getAsInt() & 0xFF,
						v -> set.accept((get.getAsInt() & 0xFFFF00) | (int) v)));
	}
}
