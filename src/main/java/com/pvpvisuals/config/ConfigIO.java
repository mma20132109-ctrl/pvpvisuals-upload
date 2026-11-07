package com.pvpvisuals.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/** Работа с файлами и JSON. Не знает о GUI и о состоянии мода. */
public final class ConfigIO {
	/**
	 * Gson по умолчанию игнорирует неизвестные поля (отдельного setIgnoreUnknownFields в Gson нет),
	 * а отсутствующие поля остаются со значениями из инициализаторов {@link PvpConfig}.
	 */
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_JSON_CHARS = 1_000_000;
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT)
			.withZone(ZoneId.systemDefault());

	private ConfigIO() { }

	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("pvpvisuals");
	}

	public static Path mainFile() {
		return root().resolve("config.json");
	}

	public static Path exportsDir() {
		return root().resolve("exports");
	}

	/** Файл старых версий мода (до появления папки). */
	public static Path legacyFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("pvpvisuals.json");
	}

	public static String toJson(PvpConfig c) {
		return GSON.toJson(c);
	}

	/** @throws JsonParseException если это не объект JSON или объект пустой */
	public static PvpConfig parse(String json) {
		if (json == null || json.isBlank() || json.length() > MAX_JSON_CHARS) {
			throw new JsonParseException("empty or too large");
		}
		try {
			JsonObject obj = JsonParser.parseString(json.trim()).getAsJsonObject();
			if (obj.size() == 0) {
				throw new JsonParseException("empty object");
			}
			PvpConfig c = GSON.fromJson(obj, PvpConfig.class);
			if (c == null) {
				throw new JsonParseException("null");
			}
			return c;
		} catch (IllegalStateException e) { // не объект
			throw new JsonParseException(e);
		}
	}

	public static PvpConfig read(Path file) throws IOException {
		return parse(Files.readString(file, StandardCharsets.UTF_8));
	}

	/** Атомарная запись: во временный файл, затем переименование. */
	public static void write(Path file, String json) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, json, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Приводит имя к безопасному виду (без путей и спецсимволов). @return null, если имя пустое. */
	public static String sanitizeName(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.toLowerCase(Locale.ROOT).endsWith(".json")) {
			s = s.substring(0, s.length() - 5);
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length() && sb.length() < 48; i++) {
			char ch = s.charAt(i);
			if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == ' ' || (ch == '.' && sb.length() > 0)) {
				sb.append(ch);
			}
		}
		String out = sb.toString().trim();
		return out.isEmpty() ? null : out;
	}

	/** Путь к экспорту; гарантирует, что файл лежит внутри папки exports. */
	public static Path exportPath(String name) {
		String clean = sanitizeName(name);
		if (clean == null) {
			throw new IllegalArgumentException("bad name");
		}
		Path dir = exportsDir().toAbsolutePath().normalize();
		Path p = dir.resolve(clean + ".json").normalize();
		if (!p.startsWith(dir)) {
			throw new IllegalArgumentException("bad name");
		}
		return p;
	}

	public static List<ExportEntry> listExports() {
		List<ExportEntry> out = new ArrayList<>();
		Path dir = exportsDir();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
			for (Path p : ds) {
				String file = p.getFileName().toString();
				String base = file.substring(0, file.length() - 5);
				if (!base.equals(sanitizeName(base))) {
					continue; // имя с недопустимыми символами: импортировать по имени нельзя
				}
				long mod = Files.getLastModifiedTime(p).toMillis();
				out.add(new ExportEntry(file.substring(0, file.length() - 5), mod, Files.size(p),
						WHEN.format(Instant.ofEpochMilli(mod))));
			}
		} catch (IOException e) {
			System.err.println("[pvpvisuals] Не удалось прочитать список экспортов: " + e);
		}
		out.sort(Comparator.comparingLong(ExportEntry::modified).reversed());
		return out;
	}
}
