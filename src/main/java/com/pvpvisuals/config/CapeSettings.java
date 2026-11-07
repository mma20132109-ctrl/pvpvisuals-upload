package com.pvpvisuals.config;

/** Настройки модуля Cape. */
public class CapeSettings {
	public boolean enabled = false;
	/** Полный Identifier текстуры, например pvpvisuals:textures/gui/cape.png. */
	public String texture = "pvpvisuals:textures/gui/cape.png";
	public boolean showOnSelf = true;
	/** Показывать плащи других игроков с модом (нужен syncEnabled и сервер-ретранслятор). */
	public boolean showOnOthers = false;
	/** Вариант B: обмен состоянием через Custom Payload. */
	public boolean syncEnabled = false;
	/** Скрывать ванильный плащ, когда виден наш (mixin в CapeLayer). */
	public boolean hideVanillaCape = true;
	public float brightness = 1.0f;
	public float alpha = 1.0f;
}
