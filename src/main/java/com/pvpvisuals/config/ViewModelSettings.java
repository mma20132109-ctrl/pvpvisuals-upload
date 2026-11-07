package com.pvpvisuals.config;

/** Настройки ViewModel (положение, размер, поворот руки/предмета от первого лица и анимации). */
public class ViewModelSettings {
	public boolean enabled = false;

	/** Смещение в блоках, -2..2. По X и поворотам Y/Z знак зеркалится для левой руки. */
	public float posX = 0f;
	public float posY = 0f;
	public float posZ = 0f;
	/** Размер 0.1..2. */
	public float scaleX = 1f;
	public float scaleY = 1f;
	public float scaleZ = 1f;
	/** Поворот в градусах, -180..180. */
	public float rotX = 0f;
	public float rotY = 0f;
	public float rotZ = 0f;

	/** Убрать покачивание рук при ходьбе (view bobbing в проходе отрисовки рук). */
	public boolean disableSway = true;
	/** Убрать анимацию смены предмета (предмет сразу поднят). */
	public boolean disableEquipAnimation = false;
	/** Скорость взмаха 0.5..2 (1 = ванильная). */
	public float swingSpeed = 1f;
	/** Размах взмаха 0..2 (1 = ванильный). */
	public float swingAmplitude = 1f;
	/** VANILLA, SLIDE, SCALE, ROTATE, NONE. */
	public String customSwing = "VANILLA";
}
