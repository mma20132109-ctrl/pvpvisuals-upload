package com.pvpvisuals.cape;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.pvpvisuals.config.CapeSettings;
import com.pvpvisuals.config.PvpConfig;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.Minecraft;

/**
 * Вариант B: состояние плащей других игроков. Ванильный/Paper-сервер payload'ы между клиентами не пересылает.
 * Ретрансляцию берёт на себя встроенный сервер хозяина LAN-игры (если у хозяина установлен мод);
 * на выделенном сервере нужен серверный ретранслятор. Без него ничего не отправляется.
 */
public final class CapeSyncManager {
	private static final Map<UUID, Boolean> STATES = new ConcurrentHashMap<>();
	/** Было ли отправлено «включено» (чтобы при выключенной синхронизации не создавать трафик). */
	private static boolean announced;

	private CapeSyncManager() { }

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CapeSyncPayload.TYPE, CapeSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CapeSyncPayload.TYPE, CapeSyncPayload.CODEC);

		// Ретранслятор: работает, когда этот клиент — хозяин игры по локальной сети (встроенный сервер)
		// или одиночного мира; на чужом/выделенном сервере без этого мода ничего не делает.
		ServerPlayNetworking.registerGlobalReceiver(CapeSyncPayload.TYPE, (payload, context) -> {
			ServerPlayer sender = context.player();
			// UUID берём у отправителя, а не из payload: так нельзя включить плащ от чужого имени
			CapeSyncPayload relayed = new CapeSyncPayload(sender.getUUID(), payload.enabled());
			// ПРОВЕРИТЬ: MinecraftServer#getPlayerList().getPlayers() в 1.21.11.
			for (ServerPlayer other : context.server().getPlayerList().getPlayers()) {
				if (other != sender && ServerPlayNetworking.canSend(other, CapeSyncPayload.TYPE)) {
					ServerPlayNetworking.send(other, relayed);
				}
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(CapeSyncPayload.TYPE,
				(payload, context) -> onReceive(payload, context.client()));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			STATES.clear();
			announced = false;
			broadcast();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			STATES.clear();
			announced = false;
		});
	}

	/** Потокобезопасно: читается из рендера, пишется из сетевого/клиентского потока. */
	public static boolean isEnabled(UUID id) {
		return STATES.getOrDefault(id, Boolean.FALSE);
	}

	private static void onReceive(CapeSyncPayload p, Minecraft mc) {
		CapeSettings c = PvpConfig.get().cape;
		if (!c.syncEnabled || mc.player == null || p.uuid().equals(mc.player.getUUID())) {
			return;
		}
		Boolean prev = STATES.put(p.uuid(), p.enabled());
		if (prev == null && p.enabled()) {
			broadcast(); // новый участник: представляемся в ответ (один раз, цикла нет)
		}
	}

	/** Отправляет своё состояние, если сервер принимает этот канал. Ошибки игнорируются. */
	public static void broadcast() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) {
			return;
		}
		CapeSettings c = PvpConfig.get().cape;
		boolean want = c.enabled && c.syncEnabled;
		if (!want && !announced) {
			return;
		}
		try {
			if (ClientPlayNetworking.canSend(CapeSyncPayload.TYPE)) {
				ClientPlayNetworking.send(new CapeSyncPayload(mc.player.getUUID(), want));
				announced = want;
			}
		} catch (RuntimeException e) {
			System.err.println("[pvpvisuals] cape_sync не отправлен: " + e);
		}
	}
}
