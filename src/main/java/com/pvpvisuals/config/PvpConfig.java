package com.pvpvisuals.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;


import com.pvpvisuals.hud.inventory.InventoryHudConfig;
import com.pvpvisuals.render.particle.BlockParticleConfig;
import com.pvpvisuals.render.particle.HitParticleConfig;
import com.pvpvisuals.render.trajectory.TrajectoryConfig;

/**
 * Единый объект настроек мода (сериализуется GSON). Все поля имеют значения по умолчанию,
 * поэтому отсутствующие в старом JSON поля подставляются автоматически.
 * Чтение/запись файлов — {@link ConfigIO}, жизненный цикл — {@link ConfigManager}.
 */
public final class PvpConfig {
	private static final PvpConfig instance = new PvpConfig();

	// ---------- метаданные ----------
	/** Версия формата файла. 0 = файл старой версии без метаданных. См. {@link ConfigMigrator}. */
	public int configVersion = 0;
	/** Версия мода на момент сохранения. */
	public String modVersion = "";
	/** Время последнего сохранения (ISO-8601). */
	public String savedAt = "";
	/** Имя профиля (для нескольких наборов настроек). */
	public String profile = "default";

	// Модули
	public boolean hitParticles = true;
	public boolean blockParticles = true;
	public boolean targetHud = true;
	/** Отображать броню и предметы в Target HUD. */
	public boolean targetHudItems = true;
	/** Показывать дистанцию до цели в Target HUD. */
	public boolean targetHudDistance = true;
	/** Выделять сущность, на которую наведён прицел, отдельным Target ESP. */
	public boolean targetEsp = false;
	public boolean damageNumbers = true;
	public boolean critEffect = true;
	public boolean trajectory = true;
	public boolean comboCounter = true;

	/** Двигать HUD-элементы мышью, пока открыт чат. */
	public boolean chatHudEdit = true;
	public boolean showIsland = true;
	/** Виджет «Сейчас играет» в Island HUD. */
	public NowPlayingSettings nowPlaying = new NowPlayingSettings();
	/** Отдельная карточка Music HUD с синхронизированными субтитрами. */
	public MusicHudSettings musicHud = new MusicHudSettings();
	public ShaderSkySettings shaderSky = new ShaderSkySettings();
	public FreelookSettings freelook = new FreelookSettings();
	public SplashSettings splash = new SplashSettings();
	public BlockOverlaySettings blockOverlay = new BlockOverlaySettings();
	public HitBoxSettings hitBox = new HitBoxSettings();
	/** Независимые настройки Target ESP (не затрагивают обычный HitBox). */
	public TargetEspSettings targetEspSettings = new TargetEspSettings();
	public RadialMenuSettings radial = new RadialMenuSettings();
	public ViewModelSettings viewModel = new ViewModelSettings();
	public HandShaderSettings handShader = new HandShaderSettings();
	public String theme = "GOLD";

	// World Particles (по умолчанию выключен)
	public boolean worldParticles = false;
	public String wpStyle = "STARS";
	public int wpColor = 0xFFD060;
	public float wpSize = 0.06f;
	public float wpSpeed = 1.0f;
	public float wpRadius = 1.5f;
	public boolean wpTurbulence = false;
	public boolean wpOnSelf = true;
	public boolean wpOnTarget = false;

	// China Hat (по умолчанию выключен)
	public boolean chinaHat = false;
	public String hatTarget = "SELF";
	public String hatAnim = "SPIN";
	public float hatAmp = 1.0f;
	public float hatRadius = 0.5f;
	public float hatHeight = 0.35f;
	public int hatColor = 0xE8C87A;
	public int hatAlpha = 170;
	public float hatOffX = 0.0f;
	public float hatOffY = 0.0f;
	public float hatOffZ = 0.0f;
	public boolean hatOutline = true;
	public float hatOutlineWidth = 0.015f;

	// Inventory HUD (по умолчанию выключен)
	public InventoryHudConfig invHud = new InventoryHudConfig();

	// Trajectory: включение — trajectory выше, остальное здесь
	public TrajectoryConfig traj = new TrajectoryConfig();

	// Hit / Block Hit частицы: настройки вложены (включение — hitParticles / blockParticles выше)
	public HitParticleConfig hit = new HitParticleConfig();
	public BlockParticleConfig block = new BlockParticleConfig();

	// Универсальные настройки HUD-элементов: id -> позиция/якорь/масштаб/прозрачность
	public Map<String, HudSettings> hud = new HashMap<>();
	/** Параметры редактора HUD. */
	public boolean hudGrid = false;
	public boolean hudSnap = false;
	public boolean hudGuides = true;

	/** Устаревшее хранилище позиций (старые версии мода): читается один раз при миграции в {@link #hud}. */
	public Map<String, int[]> hudPositions = new HashMap<>();

	// ---------- бинды и уведомления ----------
	public BindSettings binds = new BindSettings();

	// ---------- Custom Crosshair ----------
	public CrosshairSettings crosshair = new CrosshairSettings();

	// ---------- HUD Binds ----------
	public HudBindsSettings hudBinds = new HudBindsSettings();

	// ---------- Fullbright ----------
	public FullbrightSettings fullbright = new FullbrightSettings();

	// ---------- Kill Effect ----------
	public KillEffectSettings killEffect = new KillEffectSettings();

	// ---------- плащ ----------
	public CapeSettings cape = new CapeSettings();

	// ---------- логотип и Watermark ----------
	public boolean watermark = true;
	/** Дополнительные HUD-виджеты в стиле Svintus. */
	public boolean coordinatesHud = true;
	public boolean moduleListHud = true;
	public boolean coordinatesDirection = true;
	public boolean coordinatesNether = false;
	public boolean moduleListGlow = true;
	/** Сегменты Watermark в стиле Svintus. */
	public boolean watermarkShowNick = true;
	public boolean watermarkShowFps = true;
	public boolean watermarkShowPing = true;
	public boolean watermarkShowTime = true;
	/** Отдельный HUD брони и предметов рук. */
	public boolean armorHud = false;
	public boolean armorHudHands = true;
	public boolean armorHudPercent = true;
	public LogoSettings logo = new LogoSettings();

	// ---------- оформление меню ----------
	public GuiSettings gui = new GuiSettings();
	/** Раскрытые строки меню (путь -> true). */
	public Map<String, Boolean> menuExpanded = new HashMap<>();

	// ---------- кейбинды: id -> имя клавиши (например "key.keyboard.right.shift") ----------
	public Map<String, String> keys = new HashMap<>();

	/** Живой экземпляр. Он не подменяется (на него ссылаются лямбды GUI) — импорт копирует поля внутрь. */
	public static PvpConfig get() {
		return instance;
	}

	public static void load() {
		ConfigManager.getInstance().load();
	}

	/** Помечает конфиг изменённым без немедленной записи (автосохранение раз в 5 минут). */
	public void markDirtyQuiet() {
		ConfigManager.getInstance().markDirty();
	}

	/** Сохраняет конфиг (запись в файл идёт в фоновом потоке). */
	public void save() {
		ConfigManager.getInstance().save();
	}

	/** Копирует все поля из {@code src} в этот экземпляр (src после вызова больше не используется). */
	public void applyFrom(PvpConfig src) {
		for (Field f : PvpConfig.class.getDeclaredFields()) {
			int m = f.getModifiers();
			if (Modifier.isStatic(m) || Modifier.isTransient(m)) {
				continue;
			}
			try {
				f.set(this, f.get(src));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
