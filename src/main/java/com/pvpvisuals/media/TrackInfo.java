package com.pvpvisuals.media;

import java.awt.image.BufferedImage;

/** Текущий трек. Неизменяемый; обложка уже приведена к квадрату 96×96 со скруглёнными углами. */
public final class TrackInfo {
	public final String key;
	public final String title;
	public final String artist;
	public final String app;
	public final int durationSeconds;
	/** Обложка (может быть null — тогда рисуется заглушка). */
	public final BufferedImage coverArt;
	/** PNG-байты той же обложки для загрузки в текстуру. */
	public final byte[] coverPng;
	public final int coverHash;

	public TrackInfo(String key, String title, String artist, String app, int durationSeconds, BufferedImage coverArt,
			byte[] coverPng, int coverHash) {
		this.key = key;
		this.title = title;
		this.artist = artist;
		this.app = app;
		this.durationSeconds = durationSeconds;
		this.coverArt = coverArt;
		this.coverPng = coverPng;
		this.coverHash = coverHash;
	}
}
