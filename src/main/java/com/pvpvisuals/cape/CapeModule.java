package com.pvpvisuals.cape;

import com.pvpvisuals.config.CapeSettings;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.Category;
import com.pvpvisuals.gui.rival.ModuleDef;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Модуль Cape: регистрация слоя, логика видимости, кэш текстуры и описание для меню. */
public final class CapeModule {
	private static final long RECHECK_NANOS = 5_000_000_000L;

	private static String cachedString;
	private static Identifier cachedId;
	private static boolean present;
	private static long nextCheck;
	private static boolean warned;

	private CapeModule() { }

	@SuppressWarnings("unchecked")
	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				helper.register(new CapeFeatureRenderer(
						(RenderLayerParent<AvatarRenderState, PlayerModel>) (RenderLayerParent<?, ?>) avatarRenderer,
						context.getModelSet()));
			}
		});
	}

	/** Запасные namespace на случай опечатки в имени папки assets/... */
	private static final String[] FALLBACK_NAMESPACES = {"pvpvisuals", "pvpvisual", "echovisual"};

	/** Identifier текстуры или null, если файла нет (предупреждение пишется один раз). */
	public static Identifier texture() {
		String s = PvpConfig.get().cape.texture;
		if (!s.equals(cachedString)) {
			cachedString = s;
			cachedId = null;
			present = false;
			nextCheck = 0;
			warned = false;
		}
		long now = System.nanoTime();
		if (now >= nextCheck) {
			nextCheck = now + RECHECK_NANOS;
			cachedId = find(s);
			present = cachedId != null;
		}
		if (!present) {
			warnOnce("текстура плаща не найдена: " + s + " (положите файл в assets/pvpvisuals/textures/gui/cape.png)");
			return null;
		}
		return cachedId;
	}

	/** Ищет текстуру по настроенному пути, затем тот же путь в запасных namespace. */
	private static Identifier find(String configured) {
		var rm = Minecraft.getInstance().getResourceManager();
		Identifier base = Identifier.tryParse(configured);
		if (base == null) {
			return null;
		}
		if (rm.getResource(base).isPresent()) {
			return base;
		}
		for (String ns : FALLBACK_NAMESPACES) {
			Identifier id = Identifier.fromNamespaceAndPath(ns, base.getPath());
			if (rm.getResource(id).isPresent()) {
				return id;
			}
		}
		return null;
	}

	private static void warnOnce(String msg) {
		if (!warned) {
			warned = true;
			System.err.println("[pvpvisuals] " + msg);
		}
	}

	/** Рисовать ли плащ для этого игрока. */
	public static boolean visibleFor(AvatarRenderState state) {
		CapeSettings c = PvpConfig.get().cape;
		if (!c.enabled || state.isInvisible) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return false;
		}
		if (state.id == mc.player.getId()) {
			if (!c.showOnSelf) {
				return false;
			}
		} else {
			if (!c.showOnOthers || !c.syncEnabled) {
				return false;
			}
			Entity e = mc.level.getEntity(state.id);
			if (e == null || !CapeSyncManager.isEnabled(e.getUUID())) {
				return false;
			}
		}
		return true;
	}

	/** Нужно ли скрыть ванильный плащ (вызывается из CapeLayerMixin): наш плащ виден и текстура есть. */
	public static boolean replacesVanilla(AvatarRenderState state) {
		return PvpConfig.get().cape.hideVanillaCape && visibleFor(state) && texture() != null;
	}

	/** ARGB-множитель: яркость -> RGB, прозрачность -> альфа (умножается на альфу текстуры). */
	public static int tint() {
		CapeSettings c = PvpConfig.get().cape;
		int v = Math.round(Mth.clamp(c.brightness, 0f, 1f) * 255f);
		int a = Math.round(Mth.clamp(c.alpha, 0f, 1f) * 255f);
		return (a << 24) | (v << 16) | (v << 8) | v;
	}

	public static ModuleDef def(PvpConfig pc) {
		CapeSettings c = pc.cape;
		return ModuleDef.toggle("Cape", Category.CLIENT, () -> c.enabled, v -> {
					c.enabled = v;
					CapeSyncManager.broadcast();
				})
				.child(ModuleDef.toggle("On Self", Category.CLIENT, () -> c.showOnSelf, v -> c.showOnSelf = v))
				.child(ModuleDef.toggle("On Others", Category.CLIENT, () -> c.showOnOthers, v -> c.showOnOthers = v))
				.child(ModuleDef.toggle("Sync", Category.CLIENT, () -> c.syncEnabled, v -> {
					c.syncEnabled = v;
					CapeSyncManager.broadcast();
				}))
				.child(ModuleDef.toggle("Hide Vanilla Cape", Category.CLIENT,
						() -> c.hideVanillaCape, v -> c.hideVanillaCape = v))
				.child(ModuleDef.slider("Brightness", Category.CLIENT, 0.0, 1.0, 2,
						() -> c.brightness, v -> c.brightness = (float) v))
				.child(ModuleDef.slider("Alpha", Category.CLIENT, 0.05, 1.0, 2,
						() -> c.alpha, v -> c.alpha = (float) v));
	}
}
