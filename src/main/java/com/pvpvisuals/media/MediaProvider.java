package com.pvpvisuals.media;

/**
 * Источник данных о воспроизводимой музыке. Основная реализация — {@link SmtcMediaProvider} (Windows SMTC);
 * позже можно добавить Spotify/Яндекс.Музыку как отдельные реализации.
 */
public interface MediaProvider {
	boolean isPlaying();

	/** Текущий трек или null, если плеера нет. */
	TrackInfo getCurrentTrack();

	/** Позиция в секундах (для таймеров и отображения времени). */
	int getPositionSeconds();

	/**
	 * Позиция с миллисекундной точностью. Провайдеры с непрерывной шкалой времени должны
	 * переопределять этот метод; значение по умолчанию сохраняет совместимость старых реализаций.
	 */
	default long getPositionMillis() {
		return getPositionSeconds() * 1000L;
	}

	/** Обновляет кэшированное состояние. Вызывать раз в ~500 мс, не каждый кадр. */
	void tick();

	/** false, если источник недоступен (не Windows, нет PowerShell и т.п.). */
	default boolean isAvailable() {
		return true;
	}

	/** Освобождает ресурсы (процессы, потоки). */
	default void close() { }
}
