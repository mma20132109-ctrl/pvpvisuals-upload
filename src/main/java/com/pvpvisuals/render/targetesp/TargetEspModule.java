package com.pvpvisuals.render.targetesp;

import org.joml.Matrix4f;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.TargetEspSettings;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Target ESP extraction for Minecraft 1.21.11.
 *
 * Entity/world reads happen in END_EXTRACTION. END_MAIN only consumes this immutable frame
 * and submits GPU geometry. The last target bounds are kept briefly for a smooth fade-out.
 */
public final class TargetEspModule {
    public static final class Frame {
        public boolean visible;
        /** Camera-relative minX,minY,minZ,maxX,maxY,maxZ. */
        public final float[] bounds = new float[6];
        public final Matrix4f view = new Matrix4f();
        public float fade;
        public float health = 1.0f;
        public float distance;
        public boolean allied;
        public boolean invisible;
    }

    private static final Frame FRAME = new Frame();
    private static final double[] LAST_BOUNDS = new double[6];
    private static long lastNanos;
    private static boolean hasBounds;
    private static float fade;
    private static float health = 1.0f;
    private static float distance;
    private static boolean allied;
    private static boolean invisible;
    private static long lastGhostParticleNanos;
    private static int ghostParticleIndex;

    private TargetEspModule() { }

    public static Frame frame() {
        return FRAME;
    }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(TargetEspModule::extract);
        WorldRenderEvents.END_MAIN.register(TargetEspRenderer::render);
    }

    private static void extract(WorldExtractionContext context) {
        Minecraft mc = Minecraft.getInstance();
        PvpConfig config = PvpConfig.get();
        TargetEspSettings settings = config.targetEspSettings;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.05f : Math.min(0.10f, Math.max(0.0f, (now - lastNanos) / 1_000_000_000.0f));
        lastNanos = now;

        if (!config.targetEsp || mc.level == null || mc.player == null) {
            fade = 0.0f;
            hasBounds = false;
            lastGhostParticleNanos = 0L;
            FRAME.visible = false;
            return;
        }

        Entity hit = mc.hitResult instanceof EntityHitResult result ? result.getEntity() : null;
        if (hit instanceof LivingEntity target && target != mc.player && target.isAlive()
                && isAllowed(target, settings)
                && target.distanceToSqr(mc.player) <= (double) settings.maxDistance * settings.maxDistance) {
            AABB box = interpolatedBox(target, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            Frustum frustum = context.frustum();
            if (settings.throughWalls || frustum == null || frustum.isVisible(box)) {
                LAST_BOUNDS[0] = box.minX;
                LAST_BOUNDS[1] = box.minY;
                LAST_BOUNDS[2] = box.minZ;
                LAST_BOUNDS[3] = box.maxX;
                LAST_BOUNDS[4] = box.maxY;
                LAST_BOUNDS[5] = box.maxZ;
                hasBounds = true;

                float maxHealth = Math.max(1.0f, target.getMaxHealth());
                health = Mth.clamp(target.getHealth() / maxHealth, 0.0f, 1.0f);
                distance = (float) Math.sqrt(target.distanceToSqr(mc.player));
                allied = target.isAlliedTo(mc.player);
                invisible = target.isInvisible();
                fade = Math.min(1.0f, fade + dt * 6.0f);
                emitSpiritParticle(mc, box, settings, now);
            } else {
                fadeOut(dt, settings);
            }
        } else {
            fadeOut(dt, settings);
        }

        if (!hasBounds || fade <= 0.001f) {
            FRAME.visible = false;
            return;
        }

        Vec3 camera = context.worldState().cameraRenderState.pos;
        FRAME.bounds[0] = (float) (LAST_BOUNDS[0] - camera.x);
        FRAME.bounds[1] = (float) (LAST_BOUNDS[1] - camera.y);
        FRAME.bounds[2] = (float) (LAST_BOUNDS[2] - camera.z);
        FRAME.bounds[3] = (float) (LAST_BOUNDS[3] - camera.x);
        FRAME.bounds[4] = (float) (LAST_BOUNDS[4] - camera.y);
        FRAME.bounds[5] = (float) (LAST_BOUNDS[5] - camera.z);
        FRAME.view.set(context.viewMatrix());
        FRAME.fade = fade;
        FRAME.health = health;
        FRAME.distance = distance;
        FRAME.allied = allied;
        FRAME.invisible = invisible;
        FRAME.visible = true;
    }

    /**
     * Emits the actual vanilla SOUL particle, not ESP line geometry.
     * One particle is spawned at a time at a point on a rising helix around the target.
     * ClientLevel.addParticle(ParticleOptions, x, y, z, vx, vy, vz) exists in 1.21.11 Mojang mappings.
     */
    private static void emitSpiritParticle(Minecraft mc, AABB box,
            TargetEspSettings settings, long now) {
        if (TargetEspMode.byName(settings.mode) != TargetEspMode.GHOSTS || mc.level == null) {
            return;
        }

        // ghostCount is treated as the emission rate (particles per second), capped to avoid particle spam.
        int rate = Mth.clamp(settings.ghostCount, 4, 48);
        long interval = 1_000_000_000L / rate;
        if (lastGhostParticleNanos != 0L && now - lastGhostParticleNanos < interval) {
            return;
        }
        lastGhostParticleNanos = now;

        float speed = Mth.clamp(settings.animationSpeed, 0.1f, 4.0f);
        double phase = now * 1.0e-9 * speed * 2.25
                + ghostParticleIndex * 2.399963229728653;
        double height = Math.max(1.0, box.getYsize());
        double rise = (ghostParticleIndex * 0.6180339887498949) % 1.0;
        double y = box.minY + 0.10 + rise * Math.max(0.25, height - 0.15);
        double radiusX = Math.max(0.28, box.getXsize() * 0.68);
        double radiusZ = Math.max(0.28, box.getZsize() * 0.68);
        double x = (box.minX + box.maxX) * 0.5 + Math.cos(phase) * radiusX;
        double z = (box.minZ + box.maxZ) * 0.5 + Math.sin(phase) * radiusZ;

        // A slight tangential drift plus upward velocity creates a continuous spiral.
        double drift = 0.018 * speed;
        double vx = -Math.sin(phase) * drift;
        double vz = Math.cos(phase) * drift;
        double vy = 0.025 + 0.018 * (0.5 + 0.5 * Math.sin(phase * 0.7));

        mc.level.addParticle(ParticleTypes.SOUL, x, y, z, vx, vy, vz);
        ghostParticleIndex++;
    }

    private static boolean isAllowed(LivingEntity entity, TargetEspSettings settings) {
        return entity instanceof Player ? settings.showPlayers : settings.showMobs;
    }

    private static AABB interpolatedBox(Entity entity, float partialTick) {
        AABB box = entity.getBoundingBox();
        double dx = Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX();
        double dy = Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY();
        double dz = Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ();
        return box.move(dx, dy, dz);
    }

    private static void fadeOut(float dt, TargetEspSettings settings) {
        if (!hasBounds) {
            fade = 0.0f;
            return;
        }
        float duration = Mth.clamp(settings.fadeOutSeconds, 0.05f, 2.0f);
        fade = Math.max(0.0f, fade - dt / duration);
        if (fade <= 0.001f) {
            hasBounds = false;
        }
    }
}
