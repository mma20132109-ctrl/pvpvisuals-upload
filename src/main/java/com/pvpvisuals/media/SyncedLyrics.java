package com.pvpvisuals.media;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Загружает синхронизированные LRC-субтитры из LRCLIB в отдельном потоке. */
public final class SyncedLyrics {
	private static final Pattern STAMP = Pattern.compile("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]");
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	public record Word(long startMs, long endMs, String text) { }

	/** Character range of the currently sung word inside its line. */
	public record WordTiming(int startChar, int endChar, float progress) { }

	public record Line(long startMs, long endMs, String text, List<Word> words) {
		public Line {
			text = text == null ? "" : text;
			words = words == null ? List.of() : List.copyOf(words);
		}

		/** Compatibility constructor for ordinary line-timed LRC. */
		public Line(long startMs, String text) {
			this(startMs, -1L, text, List.of());
		}
	}

	private static final class MutableWord {
		String text = "";
		long startMs = -1L;
		long endMs = -1L;
	}

	private static final class MutableLine {
		String text = "";
		long startMs = -1L;
		long endMs = -1L;
		final List<Word> words = new ArrayList<>();
	}

	private static volatile String activeKey = "";
	private static volatile String requestedKey = "";
	private static volatile List<Line> lines = List.of();

	private SyncedLyrics() { }

	/** Запрашивает субтитры не чаще одного раза на трек; сеть никогда не блокирует рендер. */
	public static void ensure(String key, String artist, String title) {
		if (key == null || key.isBlank() || title == null || title.isBlank()) {
			return;
		}
		if (key.equals(activeKey) || key.equals(requestedKey)) {
			return;
		}
		activeKey = key;
		requestedKey = key;
		lines = List.of();

		Thread worker = new Thread(() -> {
			List<Line> result = fetch(artist == null ? "" : artist, title);
			if (key.equals(activeKey)) {
				lines = result;
			}
		}, "PvPVisuals-Lyrics");
		worker.setDaemon(true);
		worker.start();
	}

	public static boolean has(String key) {
		return key != null && key.equals(activeKey) && !lines.isEmpty();
	}

	public static Line current(String key, long positionMs) {
		if (!has(key)) {
			return null;
		}
		List<Line> snapshot = lines;
		int low = 0;
		int high = snapshot.size() - 1;
		int found = -1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (snapshot.get(mid).startMs() <= positionMs) {
				found = mid;
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		return found < 0 ? null : snapshot.get(found);
	}

	/** Прогресс заполнения текущей строки субтитров, от 0 до 1. */
	public static float progress(String key, long positionMs) {
		if (!has(key)) {
			return 0f;
		}
		List<Line> snapshot = lines;
		for (int i = 0; i < snapshot.size(); i++) {
			Line line = snapshot.get(i);
			if (line.startMs() > positionMs) {
				break;
			}
			long inferredEnd = i + 1 < snapshot.size()
					? snapshot.get(i + 1).startMs()
					: line.startMs() + 4000L;
			long end = line.endMs() > line.startMs() ? line.endMs() : inferredEnd;
			if (positionMs < end) {
				return Math.max(0f, Math.min(1f,
						(positionMs - line.startMs()) / (float) Math.max(1L, end - line.startMs())));
			}
		}
		return 0f;
	}

	/**
	 * Exact word timing from LRCLIB Lyricsfile. Returns null when the track has only ordinary
	 * line-synced LRC, so the HUD can use its estimated line progress as a fallback.
	 */
	public static WordTiming wordTiming(String key, long positionMs) {
		Line line = current(key, positionMs);
		if (line == null || line.words().isEmpty()) {
			return null;
		}

		int charOffset = 0;
		Word previous = null;
		for (int i = 0; i < line.words().size(); i++) {
			Word word = line.words().get(i);
			long nextStart = i + 1 < line.words().size()
					? line.words().get(i + 1).startMs()
					: line.endMs();
			long end = word.endMs() > word.startMs() ? word.endMs()
					: nextStart > word.startMs() ? nextStart : word.startMs() + 300L;
			int wordEnd = charOffset + word.text().length();

			if (positionMs < word.startMs()) {
				return previous == null
						? new WordTiming(0, 0, 0f)
						: new WordTiming(charOffset - previous.text().length(),
								charOffset, 1f);
			}
			if (positionMs < end || i == line.words().size() - 1) {
				float p = Math.max(0f, Math.min(1f,
						(positionMs - word.startMs()) / (float) Math.max(1L, end - word.startMs())));
				return new WordTiming(charOffset, wordEnd, p);
			}

			charOffset = wordEnd;
			previous = word;
		}
		return new WordTiming(charOffset, charOffset, 1f);
	}

	public static void clear() {
		activeKey = "";
		requestedKey = "";
		lines = List.of();
	}

	private static List<Line> fetch(String artist, String title) {
		String cleanTitle = clean(title);
		List<Line> exact = request("https://lrclib.net/api/get?artist_name=" + encode(artist)
				+ "&track_name=" + encode(cleanTitle));
		if (!exact.isEmpty()) {
			return exact;
		}
		List<Line> result = search("https://lrclib.net/api/search?q=" + encode(artist + " " + cleanTitle),
				artist, cleanTitle);
		return result.isEmpty()
				? search("https://lrclib.net/api/search?q=" + encode(cleanTitle), artist, cleanTitle)
				: result;
	}

	private static List<Line> request(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "PvPVisuals (Minecraft client)")
					.timeout(Duration.ofSeconds(6))
					.GET()
					.build();
			HttpResponse<String> response = CLIENT.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return List.of();
			}
			JsonElement root = JsonParser.parseString(response.body());
			if (!root.isJsonObject()) {
				return List.of();
			}
			return parseRecord(root.getAsJsonObject());
		} catch (Exception ignored) {
			return List.of();
		}
	}

	private static List<Line> search(String url, String artist, String title) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "PvPVisuals (Minecraft client)")
					.timeout(Duration.ofSeconds(6))
					.GET()
					.build();
			HttpResponse<String> response = CLIENT.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return List.of();
			}
			JsonElement root = JsonParser.parseString(response.body());
			if (!root.isJsonArray()) {
				return List.of();
			}
			JsonArray entries = root.getAsJsonArray();
			for (JsonElement element : entries) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				String foundTitle = string(object, "trackName");
				String foundArtist = string(object, "artistName");
				if (!matches(foundTitle, title)) {
					continue;
				}
				if (!artist.isBlank() && !foundArtist.isBlank()
						&& !matches(foundArtist, artist)
						&& !foundArtist.toLowerCase(java.util.Locale.ROOT)
								.contains(artist.toLowerCase(java.util.Locale.ROOT))) {
					continue;
				}
				List<Line> parsed = parseRecord(object);
				if (!parsed.isEmpty()) {
					return parsed;
				}
			}
		} catch (Exception ignored) {
			// Нет сети, подходящей записи или синхронизированного текста — виджет просто скроет строку.
		}
		return List.of();
	}

	private static List<Line> parseRecord(JsonObject object) {
		String lyricsfile = string(object, "lyricsfile");
		List<Line> wordSynced = parseLyricsfile(lyricsfile);
		if (wordSynced.stream().anyMatch(line -> !line.words().isEmpty())) {
			return wordSynced;
		}
		return parse(string(object, "syncedLyrics"));
	}

	/**
	 * Reads the documented Lyricsfile YAML subset used by LRCLIB: lines, line start/end
	 * milliseconds, and nested word text/start/end milliseconds. No YAML dependency is needed.
	 */
	private static List<Line> parseLyricsfile(String yaml) {
		if (yaml == null || yaml.isBlank()) {
			return List.of();
		}

		List<Line> result = new ArrayList<>();
		MutableLine line = null;
		MutableWord word = null;
		boolean inLines = false;
		boolean inWords = false;

		for (String raw : yaml.split("\\R")) {
			int indent = 0;
			while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
			String value = raw.substring(indent).trim();
			if (value.isEmpty() || value.startsWith("#")) continue;

			if (indent == 0) {
				if (value.equals("lines:")) {
					inLines = true;
					continue;
				}
				if (inLines) break;
			}

			if (!inLines) continue;
			if (indent == 2 && value.startsWith("- text:")) {
				finishWord(line, word);
				finishLine(result, line);
				line = new MutableLine();
				line.text = yamlScalar(value.substring("- text:".length()));
				word = null;
				inWords = false;
				continue;
			}

			if (line == null) continue;
			if (indent == 4) {
				if (value.equals("words:")) {
					inWords = true;
					continue;
				}
				if (value.startsWith("start_ms:")) {
					line.startMs = yamlLong(value.substring("start_ms:".length()), -1L);
				} else if (value.startsWith("end_ms:")) {
					line.endMs = yamlLong(value.substring("end_ms:".length()), -1L);
				}
			} else if (indent == 6 && inWords && value.startsWith("- text:")) {
				finishWord(line, word);
				word = new MutableWord();
				word.text = yamlScalar(value.substring("- text:".length()));
			} else if (indent == 8 && inWords && word != null) {
				if (value.startsWith("start_ms:")) {
					word.startMs = yamlLong(value.substring("start_ms:".length()), -1L);
				} else if (value.startsWith("end_ms:")) {
					word.endMs = yamlLong(value.substring("end_ms:".length()), -1L);
				}
			}
		}

		finishWord(line, word);
		finishLine(result, line);
		result.sort(Comparator.comparingLong(Line::startMs));
		return List.copyOf(result);
	}

	private static void finishWord(MutableLine line, MutableWord word) {
		if (line != null && word != null && word.startMs >= 0L && !word.text.isEmpty()) {
			line.words.add(new Word(word.startMs, word.endMs, word.text));
		}
	}

	private static void finishLine(List<Line> result, MutableLine line) {
		if (line == null) return;
		String text = line.text;
		if (text.isBlank() && !line.words.isEmpty()) {
			StringBuilder joined = new StringBuilder();
			for (Word word : line.words) joined.append(word.text());
			text = joined.toString();
		}
		if (text.isBlank()) return;
		long start = line.startMs >= 0L ? line.startMs
				: line.words.isEmpty() ? 0L : line.words.get(0).startMs();
		result.add(new Line(start, line.endMs, text, line.words));
	}

	private static long yamlLong(String value, long fallback) {
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String yamlScalar(String value) {
		String scalar = value == null ? "" : value.trim();
		if (scalar.startsWith("'") && scalar.endsWith("'") && scalar.length() >= 2) {
			return scalar.substring(1, scalar.length() - 1).replace("''", "'");
		}
		if (scalar.startsWith("\"") && scalar.endsWith("\"") && scalar.length() >= 2) {
			try {
				return JsonParser.parseString(scalar).getAsString();
			} catch (RuntimeException ignored) {
				return scalar.substring(1, scalar.length() - 1);
			}
		}
		return scalar;
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object == null ? null : object.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static boolean matches(String first, String second) {
		if (first == null || second == null) {
			return false;
		}
		String a = first.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
		String b = second.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
		return !a.isEmpty() && !b.isEmpty() && (a.equals(b) || a.contains(b) || b.contains(a));
	}

	private static String clean(String title) {
		String cleaned = title
				.replaceAll("(?i)\\s*[-–]\\s*(remaster(ed)?|radio edit|single version|album version)\\b.*$", "")
				.replaceAll("(?i)\\s*[\\[(][^\\])]*(remaster|feat\\.?|ft\\.?|prod\\.?|official|video|audio|lyrics)[^\\])]*[\\])]", "")
				.trim();
		return cleaned.isEmpty() ? title.trim() : cleaned;
	}

	private static List<Line> parse(String lrc) {
		if (lrc == null || lrc.isBlank()) {
			return List.of();
		}
		List<Line> parsed = new ArrayList<>();
		for (String raw : lrc.split("\\R")) {
			Matcher matcher = STAMP.matcher(raw);
			List<Long> stamps = new ArrayList<>(2);
			int end = 0;
			while (matcher.find()) {
				long minute = Long.parseLong(matcher.group(1));
				long second = Long.parseLong(matcher.group(2));
				String fractionText = matcher.group(3);
				long fraction = fractionText == null ? 0L : Long.parseLong(fractionText);
				long millis = fractionText == null ? 0L
						: fractionText.length() == 1 ? fraction * 100L
						: fractionText.length() == 2 ? fraction * 10L : fraction;
				stamps.add(minute * 60_000L + second * 1000L + millis);
				end = matcher.end();
			}
			String text = raw.substring(end).trim();
			if (!text.isEmpty()) {
				for (long stamp : stamps) {
					parsed.add(new Line(stamp, text));
				}
			}
		}
		parsed.sort(Comparator.comparingLong(Line::startMs));
		return List.copyOf(parsed);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}
}
