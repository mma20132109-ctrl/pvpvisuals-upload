package com.pvpvisuals.hud.inventory;

/**
 * Настройки Inventory HUD (внутри PvpConfig; отсутствующие в старом JSON поля берут значения отсюда).
 * Позиция, масштаб и прозрачность — общие настройки HUD-элемента (PvpConfig#hud, подменю Layout);
 * цвета берутся из выбранной темы меню.
 */
public class InventoryHudConfig {
	public boolean enabled = false;

	public int slotSize = 18;
	public boolean showBackground = true;
	public int backgroundAlpha = 90;
	public boolean showBorder = true;
	public int borderWidth = 1;
	public boolean showCount = true;
	public boolean showDurability = true;
	public String durabilityMode = "BAR";
	public boolean highlightActive = true;

	public boolean showMain = true;
	public boolean showHotbar = true;
	public boolean showArmor = true;
	public boolean showOffhand = true;
	public String armorLayout = "LEFT";

	public boolean hideInChat = true;
	public boolean hideInInventory = true;
	public boolean hideInMenu = true;
	public boolean hideInF1 = true;
}
