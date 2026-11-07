package com.pvpvisuals.config;

/** Настройки Kill Effect. style / target — имена enum'ов (строки: переживают переименования). */
public class KillEffectSettings {
	public boolean enabled = false;
	public String style = "EXPLOSION";
	public String target = "PLAYERS_ONLY";
	public boolean onlyMyKills = true;
	/** Длительность в тиках. */
	public int duration = 30;
	public int color = 0xFFD700;
	public float size = 1.0f;
	public boolean glow = true;
	public boolean sound = false;
}
