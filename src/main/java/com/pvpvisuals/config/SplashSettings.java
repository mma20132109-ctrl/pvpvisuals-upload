package com.pvpvisuals.config;

/** Настройки экрана загрузки Echo Visual. Все поля с дефолтами: старый конфиг читается без ошибок. */
public class SplashSettings {
	/** Заменять экран загрузки Mojang на экран Echo Visual. */
	public boolean customSplashScreen = true;
	/** Искры вокруг логотипа. */
	public boolean showSplashParticles = true;
	/** DARK — сплошной тёмный фон; GRADIENT — фиолетовый градиент; SHADER — анимированный фон (движущиеся «туманности»). */
	public String splashBackground = "GRADIENT";
}
