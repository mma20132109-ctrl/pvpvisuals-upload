package com.pvpvisuals.config;

/** Настройки Freelook. Все поля с дефолтами: старый конфиг читается без ошибок. */
public class FreelookSettings {
	public boolean enabled = false;
	/** HOLD — камера свободна, пока клавиша зажата; TOGGLE — по нажатию вкл/выкл. */
	public String activationMode = "HOLD";
	public boolean invertY = false;
	/** Плавно возвращать камеру за спину при отпускании. */
	public boolean smoothReturn = true;
	/** Множитель чувствительности мыши в режиме Freelook: 0.2 .. 3.0 */
	public float sensitivity = 1.0f;
	/** При активации переключать на вид от третьего лица (и вернуть прежний вид после возврата). */
	public boolean forceThirdPerson = true;
	/** false — пока камера отвязана, атака и использование предметов блокируются. */
	public boolean allowActions = true;
}
