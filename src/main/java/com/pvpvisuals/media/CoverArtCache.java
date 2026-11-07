package com.pvpvisuals.media;

import java.io.ByteArrayInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Превращает PNG обложки в динамическую текстуру и кэширует Identifier по хэшу обложки (текстура создаётся один раз,
 * а не каждый кадр). Хранит только последнюю обложку: предыдущая освобождается.
 *
 * <p>ПРОВЕРИТЬ при компиляции (1.21.11, Mojang): {@code new DynamicTexture(Supplier<String>, NativeImage)},
 * {@code TextureManager#register(Identifier, AbstractTexture)}, {@code TextureManager#release(Identifier)},
 * {@code NativeImage.read(InputStream)}.
 */
public final class CoverArtCache {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/cover");

	private static int currentHash;
	private static Identifier currentId;
	private static int failedHash;

	private CoverArtCache() { }

	/** @return Identifier текстуры обложки или null (нет обложки / не загрузилась) */
	public static Identifier get(TrackInfo t) {
		if (t == null || t.coverPng == null) {
			return null;
		}
		if (currentId != null && currentHash == t.coverHash) {
			return currentId;
		}
		if (failedHash == t.coverHash) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			NativeImage img = NativeImage.read(new ByteArrayInputStream(t.coverPng));
			DynamicTexture tex = new DynamicTexture(() -> "pvpvisuals_cover", img);
			tex.upload();
			Identifier id = Identifier.fromNamespaceAndPath("pvpvisuals",
					"now_playing/cover_" + Integer.toHexString(t.coverHash));
			if (currentId != null) {
				mc.getTextureManager().release(currentId);
			}
			mc.getTextureManager().register(id, tex);
			currentId = id;
			currentHash = t.coverHash;
			return id;
		} catch (Throwable e) {
			failedHash = t.coverHash;
			LOG.warn("Cover texture failed: {}", e.toString());
			return null;
		}
	}

	public static void clear() {
		if (currentId != null) {
			try {
				Minecraft.getInstance().getTextureManager().release(currentId);
			} catch (Throwable ignored) {
				// выход из игры
			}
			currentId = null;
		}
	}
}
