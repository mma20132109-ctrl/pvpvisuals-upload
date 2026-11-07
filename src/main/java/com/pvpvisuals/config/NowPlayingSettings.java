package com.pvpvisuals.config;

/** Настройки строки «Сейчас играет» в Island HUD. Все поля с значениями по умолчанию (старые конфиги читаются без ошибок). */
public class NowPlayingSettings {
	public boolean showNowPlaying = true;
	/** Обложка 16×16 слева от названия. */
	public boolean showCoverArt = true;
	/** Таймкод 00:00 / 03:00 по центру строки. */
	public boolean showTrackTime = true;
	/** Автор справа. */
	public boolean showTrackArtist = true;
	/** Сжимать остров, когда трек на паузе. */
	public boolean hideWhenPaused = false;
	/** Максимальная ширина названия песни, px (40..200). */
	public int maxTrackWidth = 110;
}
