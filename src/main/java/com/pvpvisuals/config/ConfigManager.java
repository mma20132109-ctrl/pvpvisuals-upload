package com.pvpvisuals.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonParseException;
import com.pvpvisuals.PvpVisualsClient;
import com.pvpvisuals.gui.rival.NotificationToast;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Загрузка, сохранение, экспорт, импорт и сброс настроек.
 * Работает с единственным экземпляром {@link PvpConfig#get()}; запись на диск идёт в фоновом потоке.
 */
public final class ConfigManager {
	private static final ConfigManager INSTANCE = new ConfigManager();
	private static final int AUTOSAVE_TICKS = 20 * 60 * 5;

	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "pvpvisuals-config-writer");
		t.setDaemon(true);
		return t;
	});
	private volatile boolean dirty;
	private int ticks;

	private ConfigManager() { }

	public static ConfigManager getInstance() {
		return INSTANCE;
	}

	public PvpConfig getConfig() {
		return PvpConfig.get();
	}

	// ---------- загрузка ----------

	/** Читает основной файл (или старый config/pvpvisuals.json), мигрирует и нормализует. */
	public void load() {
		PvpConfig live = PvpConfig.get();
		boolean needSave = false;
		Path main = ConfigIO.mainFile();
		try {
			PvpConfig loaded = null;
			if (Files.exists(main)) {
				loaded = ConfigIO.read(main);
			} else if (Files.exists(ConfigIO.legacyFile())) {
				loaded = ConfigIO.read(ConfigIO.legacyFile());
				needSave = true;
			} else {
				needSave = true; // первый запуск
			}
			if (loaded != null) {
				live.applyFrom(loaded);
			}
		} catch (IOException | JsonParseException e) {
			System.err.println("[pvpvisuals] Не удалось прочитать конфиг, используются значения по умолчанию: " + e);
			backupBroken(main);
			needSave = true;
		}
		if (ConfigMigrator.migrate(live)) {
			needSave = true;
		}
		if (needSave) {
			save();
		}
	}

	private static void backupBroken(Path file) {
		try {
			if (Files.exists(file)) {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			System.err.println("[pvpvisuals] Не удалось сделать копию повреждённого конфига: " + e);
		}
	}

	// ---------- сохранение ----------

	public void markDirty() {
		dirty = true;
	}

	/** Сохраняет, только если после прошлого сохранения были изменения. */
	public void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	/** Сериализует в вызывающем потоке (быстро) и пишет файл в фоновом. */
	public void save() {
		dirty = false;
		String json = snapshotJson();
		writer.execute(() -> {
			try {
				ConfigIO.write(ConfigIO.mainFile(), json);
			} catch (IOException e) {
				System.err.println("[pvpvisuals] Не удалось сохранить конфиг: " + e);
				NotificationToast.show("Failed to save config", false);
			}
		});
	}

	/** Вызывается каждый клиентский тик: автосохранение раз в 5 минут, если есть изменения. */
	public void tick() {
		if (++ticks >= AUTOSAVE_TICKS) {
			ticks = 0;
			saveIfDirty();
		}
	}

	/** При выходе из игры: дописать очередь записи и сохранить синхронно. */
	public void shutdown() {
		writer.shutdown(); // сначала дождаться отложенных записей, иначе они затрут свежий файл
		try {
			writer.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		try {
			ConfigIO.write(ConfigIO.mainFile(), snapshotJson());
		} catch (IOException e) {
			System.err.println("[pvpvisuals] Не удалось сохранить конфиг при выходе: " + e);
		}
	}

	/** Обновляет метаданные и кейбинды, возвращает JSON. */
	private String snapshotJson() {
		PvpConfig c = PvpConfig.get();
		c.configVersion = ConfigMigrator.CURRENT;
		c.modVersion = FabricLoader.getInstance().getModContainer(PvpVisualsClient.MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
		c.savedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		com.pvpvisuals.keybind.KeybindManager.capture(c.keys);
		return ConfigIO.toJson(c);
	}

	// ---------- экспорт / импорт ----------

	public List<ExportEntry> listExports() {
		return ConfigIO.listExports();
	}

	/** Экспорт в config/pvpvisuals/exports/<name>.json. */
	public boolean exportToFile(String name) {
		try {
			Path p = ConfigIO.exportPath(name);
			ConfigIO.write(p, snapshotJson());
			NotificationToast.show("Config exported as " + p.getFileName(), true);
			return true;
		} catch (IllegalArgumentException e) {
			NotificationToast.show("Invalid file name", false);
		} catch (IOException e) {
			System.err.println("[pvpvisuals] Экспорт не удался: " + e);
			NotificationToast.show("Export failed", false);
		}
		return false;
	}

	public boolean importFromFile(String name) {
		try {
			return apply(ConfigIO.read(ConfigIO.exportPath(name)), "Config imported: " + name);
		} catch (IllegalArgumentException | JsonParseException e) {
			NotificationToast.show("Invalid config", false);
		} catch (IOException e) {
			System.err.println("[pvpvisuals] Импорт не удался: " + e);
			NotificationToast.show("Import failed", false);
		}
		return false;
	}

	public boolean copyToClipboard() {
		Minecraft.getInstance().keyboardHandler.setClipboard(snapshotJson());
		NotificationToast.show("Config copied to clipboard", true);
		return true;
	}

	public boolean pasteFromClipboard() {
		try {
			String text = Minecraft.getInstance().keyboardHandler.getClipboard();
			return apply(ConfigIO.parse(text), "Config imported from clipboard");
		} catch (JsonParseException e) {
			NotificationToast.show("Invalid config", false);
			return false;
		}
	}

	public void resetToDefault() {
		apply(new PvpConfig(), "Config reset to default");
		com.pvpvisuals.keybind.KeybindManager.resetAll();
	}

	/** Мигрирует, копирует в живой конфиг, применяет кейбинды и сохраняет. */
	private boolean apply(PvpConfig loaded, String message) {
		ConfigMigrator.migrate(loaded);
		double keepGamma = PvpConfig.get().fullbright.savedGamma; // служебное поле: не берём из чужого файла
		PvpConfig.get().applyFrom(loaded);
		PvpConfig.get().fullbright.savedGamma = keepGamma;
		com.pvpvisuals.keybind.KeybindManager.apply(PvpConfig.get().keys);
		com.pvpvisuals.cape.CapeSyncManager.broadcast();
		save();
		NotificationToast.show(message, true);
		return true;
	}
}
