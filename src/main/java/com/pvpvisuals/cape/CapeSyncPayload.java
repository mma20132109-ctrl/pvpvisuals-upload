package com.pvpvisuals.cape;

import java.util.UUID;

import com.pvpvisuals.PvpVisualsClient;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Канал pvpvisuals:cape_sync — UUID игрока и флаг «плащ включён». */
public record CapeSyncPayload(UUID uuid, boolean enabled) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CapeSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "cape_sync"));

	// ПРОВЕРИТЬ: UUIDUtil.STREAM_CODEC (StreamCodec<ByteBuf, UUID>) в 1.21.11.
	public static final StreamCodec<ByteBuf, CapeSyncPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, CapeSyncPayload::uuid,
			ByteBufCodecs.BOOL, CapeSyncPayload::enabled,
			CapeSyncPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
