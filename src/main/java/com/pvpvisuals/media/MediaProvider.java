package com.pvpvisuals.media;

/**
 * Источник данных о воспроизводимой музыке. Основная реализация — {@link SmtcMediaProvider} (Windows SMTC);
 * позже можно добавить Spotify/Яндекс.Музыку как отдельные реализации.
 */
public interface MediaProvider {
	boolean isPlaying();

	/** Текущий трек или null, если плеера нет. */
	TrackInfo getCurrentTrack();

	/** Позиция в секундах (между обновлениями экстраполируется, пока трек играет). */
	int getPositionSeconds();

	/** Обновляет кэшированное состояние. Вызывать раз в ~500 мс, не каждый кадр. */
	void tick();

	/** false, если источник недоступен (не Windows, нет PowerShell и т.п.). */
	default boolean isAvailable() {
		return true;
	}

	/** Освобождает ресурсы (процессы, потоки). */
	default void close() { }
}
