package com.pvpvisuals.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pvpvisuals.config.PvpConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Владеет провайдером музыки: опрашивает его раз в 10 тиков (~500 мс), запускает только при включённом виджете. */
public final class MediaManager {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/media");

	private static MediaProvider provider;
	private static int ticks;

	private MediaManager() { }

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
	}

	private static void tick() {
		boolean wanted = PvpConfig.get().nowPlaying.showNowPlaying;
		if (!wanted) {
			if (provider != null) {
				shutdown();
			}
			return;
		}
		if (++ticks < 10) {
			return;
		}
		ticks = 0;
		try {
			if (provider == null) {
				provider = new SmtcMediaProvider();
				if (!provider.isAvailable()) {
					LOG.info("Now Playing: SMTC is available only on Windows");
				}
			}
			provider.tick();
		} catch (Throwable t) {
			LOG.warn("Now Playing disabled: {}", t.toString());
			shutdown();
			PvpConfig.get().nowPlaying.showNowPlaying = false;
		}
	}

	private static void shutdown() {
		if (provider != null) {
			provider.close();
			provider = null;
		}
		CoverArtCache.clear();
	}

	public static boolean available() {
		return provider != null && provider.isAvailable();
	}

	public static TrackInfo track() {
		return provider == null ? null : provider.getCurrentTrack();
	}

	public static boolean playing() {
		return provider != null && provider.isPlaying();
	}

	public static long positionMillis() {
		return positionSeconds() * 1000L;
	}

	public static int positionSeconds() {
		return provider == null ? 0 : provider.getPositionSeconds();
	}
}
