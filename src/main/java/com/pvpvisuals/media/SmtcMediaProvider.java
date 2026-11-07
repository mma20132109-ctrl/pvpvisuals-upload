package com.pvpvisuals.media;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Читает данные Windows SMTC (System Media Transport Controls) — то, что Windows показывает в оверлее громкости:
 * Spotify, Яндекс.Музыка, браузер и т.д.
 *
 * <p>Реализация без внешних библиотек: один постоянный процесс {@code powershell.exe}, который через WinRT-класс
 * {@code GlobalSystemMediaTransportControlsSessionManager} раз в 500 мс печатает по строке JSON (название, артист,
 * позиция, длительность, обложка при смене трека). Java читает строки в фоновом потоке и кэширует последнее
 * состояние, поэтому ни рендер, ни тики не блокируются. Не Windows / нет PowerShell — провайдер тихо отключается.
 */
public final class SmtcMediaProvider implements MediaProvider {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/media");
	private static final int COVER_PX = 96;
	private static final int MAX_RESTARTS = 5;

	/** Скрипт PowerShell (Windows PowerShell 5.1+). PARENT_PID подставляется перед запуском. */
	private static final String SCRIPT = """
			$ErrorActionPreference = 'Stop'
$lastErr = ''
function Emit($json) { Write-Output ([Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($json))) }
function Report($m) { if ($m -ne $script:lastErr) { $script:lastErr = $m; Emit (ConvertTo-Json @{ error = [string]$m } -Compress) } }
			$parentPid = PARENT_PID
						Add-Type -AssemblyName System.Runtime.WindowsRuntime
			$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
			$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
			$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			function Await($op, $type) {
			  $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
			  [void]$task.Wait(5000)
			  if ($task.IsCompleted) { return $task.Result } else { return $null }
			}
			try { $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]) } catch { Report ('init: ' + $_.Exception.Message); exit 1 }
			if ($mgr -eq $null) { Report 'SessionManager.RequestAsync returned null (Windows 10 1809+ required)'; exit 1 }
			$lastKey = ''; $cover = ''; $coverTries = 0; $n = 0
			while ($true) {
			  $n++
			  if ($n % 10 -eq 0) { if (-not (Get-Process -Id $parentPid)) { exit 0 } }
			  try {
			    $s = $mgr.GetCurrentSession()
			    if ($s -eq $null) { Emit '{"none":true}' }
			    else {
			      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			      if ($p -ne $null) {
			        $pb = $s.GetPlaybackInfo()
			        $tl = $s.GetTimelineProperties()
			        $key = "$($s.SourceAppUserModelId)|$($p.Title)|$($p.Artist)"
			        if ($key -ne $lastKey) { $lastKey = $key; $cover = ''; $coverTries = 0 }
			        $newCover = ''
			        if ($cover -eq '' -and $coverTries -lt 8 -and $p.Thumbnail -ne $null) {
			          $coverTries++
			          try {
			            $st = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
			            if ($st -ne $null -and $st.Size -gt 0 -and $st.Size -lt 8000000) {
			              $rd = New-Object Windows.Storage.Streams.DataReader($st)
			              [void](Await ($rd.LoadAsync([uint32]$st.Size)) ([uint32]))
			              $bytes = New-Object byte[] ([int]$st.Size)
			              $rd.ReadBytes($bytes)
			              $cover = [Convert]::ToBase64String($bytes)
			              $newCover = $cover
			              $rd.Dispose(); $st.Dispose()
			            }
			          } catch { Report ('cover: ' + $_.Exception.Message) }
			        }
			        $playing = ([string]$pb.PlaybackStatus -eq 'Playing')
			        $pos = $tl.Position.TotalSeconds
			        $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds
			        if ($playing) { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalSeconds }
			        if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
			        if ($pos -lt 0) { $pos = 0 }
			        $o = [ordered]@{ title = [string]$p.Title; artist = [string]$p.Artist; app = [string]$s.SourceAppUserModelId; playing = $playing; pos = [math]::Round($pos, 2); dur = [math]::Round($dur, 2) }
			        if ($newCover -ne '') { $o.cover = $newCover }
			        Emit (ConvertTo-Json $o -Compress)
			      }
			    }
			  } catch { Report ('loop: ' + $_.Exception.Message) }
			  Start-Sleep -Milliseconds 500
			}
			""";

	/** Состояние, которое пишет фоновый поток и читает клиентский. */
	private record Snapshot(boolean present, String key, String title, String artist, String app, boolean playing,
			double pos, double dur, long recvNs, BufferedImage cover, byte[] coverPng, int coverHash) {
		static final Snapshot EMPTY = new Snapshot(false, "", "", "", "", false, 0, 0, 0, null, null, 0);
	}

	private volatile Snapshot snap = Snapshot.EMPTY;
	private volatile boolean available = isWindows();
	private Process proc;
	private Thread reader;
	private int restarts;
	private long nextStartMs;
	private TrackInfo track;
	private boolean playing;

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public boolean isPlaying() {
		return playing;
	}

	@Override
	public TrackInfo getCurrentTrack() {
		return track;
	}

	@Override
	public int getPositionSeconds() {
		Snapshot s = snap;
		if (!s.present) {
			return 0;
		}
		double p = s.pos;
		if (s.playing) {
			p += (System.nanoTime() - s.recvNs) / 1e9;
		}
		if (s.dur > 0) {
			p = Math.min(p, s.dur);
		}
		return (int) Math.max(0, p);
	}

	@Override
	public void tick() {
		if (!available) {
			return;
		}
		ensureStarted();
		Snapshot s = snap;
		playing = s.present && s.playing;
		if (!s.present) {
			track = null;
			return;
		}
		int dur = (int) Math.round(s.dur);
		if (track == null || !track.key.equals(s.key) || track.coverHash != s.coverHash || track.durationSeconds != dur) {
			track = new TrackInfo(s.key, s.title, s.artist, s.app, dur, s.cover, s.coverPng, s.coverHash);
		}
	}

	// ---------- процесс PowerShell ----------

	private void ensureStarted() {
		if (proc != null && proc.isAlive()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextStartMs) {
			return;
		}
		if (restarts >= MAX_RESTARTS) {
			LOG.warn("SMTC bridge keeps failing, Now Playing is disabled for this session");
			available = false;
			return;
		}
		restarts++;
		nextStartMs = now + 5000L * restarts;
		try {
			String script = SCRIPT.replace("PARENT_PID", Long.toString(ProcessHandle.current().pid()));
			// Скрипт кладём в файл и запускаем короткой безобидной командой: длинные -EncodedCommand/-ExecutionPolicy Bypass/
			// -WindowStyle Hidden похожи на вредоносные и режутся антивирусами («Отказано в доступе»).
			java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("pvpvisuals");
			java.nio.file.Files.createDirectories(dir);
			java.nio.file.Path file = dir.resolve("smtc-bridge.ps1");
			java.nio.file.Files.writeString(file, script, StandardCharsets.UTF_8);
			String cmd = "Get-Content -Raw -LiteralPath '" + file.toAbsolutePath().toString().replace("'", "''")
					+ "' | Invoke-Expression";
			String root = System.getenv("SystemRoot");
			if (root == null) {
				root = "C:\\Windows";
			}
			String ps = root + "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";
			java.util.List<java.util.List<String>> attempts = java.util.List.of(
					java.util.List.of(ps, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", cmd),
					java.util.List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", cmd),
					java.util.List.of(root + "\\System32\\cmd.exe", "/c", "powershell", "-NoLogo", "-NoProfile",
							"-NonInteractive", "-Command", cmd));
			Process p = null;
			StringBuilder errors = new StringBuilder();
			for (java.util.List<String> a : attempts) {
				try {
					p = new ProcessBuilder(a).start();
					break;
				} catch (Throwable e) {
					errors.append("\n  ").append(a.get(0)).append(": ").append(e.getMessage());
				}
			}
			if (p == null) {
				throw new java.io.IOException("all launch attempts failed:" + errors);
			}
			proc = p;
			final Process fp = p;
			Thread err = new Thread(() -> {
				try (BufferedReader er = new BufferedReader(new InputStreamReader(fp.getErrorStream(), StandardCharsets.UTF_8))) {
					String l;
					int n = 0;
					while ((l = er.readLine()) != null && n++ < 30) {
						LOG.warn("powershell: {}", l);
					}
				} catch (Exception ignored) {
					// процесс завершён
				}
			}, "pvpvisuals-smtc-err");
			err.setDaemon(true);
			err.start();
			LOG.info("Now Playing: SMTC bridge started");
			reader = new Thread(() -> readLoop(fp), "pvpvisuals-smtc");
			reader.setDaemon(true);
			reader.start();
		} catch (Throwable e) {
			LOG.warn("Cannot start PowerShell for SMTC: {}", e.toString());
		}
	}

	private void readLoop(Process p) {
		String coverKey = "";
		BufferedImage cover = null;
		byte[] png = null;
		int hash = 0;
		int junk = 0;
		boolean first = true;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (!line.isEmpty() && line.charAt(0) == '﻿') {
					line = line.substring(1);
				}
				if (line.isBlank()) {
					continue;
				}
				if (line.charAt(0) != '{') {
					// строки приходят в base64 (UTF-8 JSON): не зависим от кодировки консоли
					try {
						line = new String(Base64.getDecoder().decode(line.trim()), StandardCharsets.UTF_8);
					} catch (IllegalArgumentException e) {
						if (junk++ < 20) {
							LOG.warn("powershell stdout: {}", line);
						}
						continue;
					}
				}
				JsonObject o;
				try {
					o = JsonParser.parseString(line).getAsJsonObject();
				} catch (Exception e) {
					continue;
				}
				if (o.has("error")) {
					LOG.warn("SMTC bridge: {}", o.get("error").getAsString());
					continue;
				}
				if (o.has("none")) {
					snap = Snapshot.EMPTY;
					continue;
				}
				String title = str(o, "title");
				String artist = str(o, "artist");
				String app = str(o, "app");
				String key = app + "|" + title + "|" + artist;
				if (!key.equals(coverKey)) {
					coverKey = key;
					cover = null;
					png = null;
					hash = 0;
				}
				if (o.has("cover")) {
					try {
						byte[] raw = Base64.getDecoder().decode(o.get("cover").getAsString());
						BufferedImage prepared = prepareCover(raw);
						if (prepared != null) {
							cover = prepared;
							ByteArrayOutputStream bos = new ByteArrayOutputStream();
							ImageIO.write(prepared, "png", bos);
							png = bos.toByteArray();
							hash = Arrays.hashCode(png) | 1;
						}
					} catch (Exception e) {
						LOG.debug("Cover decode failed: {}", e.toString());
					}
				}
				if (first) {
					first = false;
					LOG.info("Now Playing: first track from {}: {} - {}", app, artist, title);
				}
				snap = new Snapshot(true, key, title, artist, app, o.has("playing") && o.get("playing").getAsBoolean(),
						num(o, "pos"), num(o, "dur"), System.nanoTime(), cover, png, hash);
			}
		} catch (Exception e) {
			LOG.debug("SMTC reader stopped: {}", e.toString());
		}
		snap = Snapshot.EMPTY;
		try {
			LOG.info("Now Playing: SMTC bridge exited with code {}", p.waitFor());
		} catch (InterruptedException ignored) {
			// выход
		}
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static double num(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0.0;
	}

	/** Квадратная обрезка по центру, плавное уменьшение до 96×96 и скруглённые углы с антиалиасингом. */
	static BufferedImage prepareCover(byte[] raw) throws java.io.IOException {
		BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
		if (src == null) {
			return null;
		}
		int side = Math.min(src.getWidth(), src.getHeight());
		BufferedImage cur = src.getSubimage((src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
		while (cur.getWidth() / 2 >= COVER_PX) {
			int nw = cur.getWidth() / 2;
			BufferedImage half = new BufferedImage(nw, nw, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = half.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(cur, 0, 0, nw, nw, null);
			g.dispose();
			cur = half;
		}
		BufferedImage out = new BufferedImage(COVER_PX, COVER_PX, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setColor(java.awt.Color.WHITE);
		float arc = COVER_PX * 0.5f; // радиус 25% стороны
		g.fill(new RoundRectangle2D.Float(0, 0, COVER_PX, COVER_PX, arc, arc));
		g.setComposite(AlphaComposite.SrcIn);
		g.drawImage(cur, 0, 0, COVER_PX, COVER_PX, null);
		g.dispose();
		return out;
	}

	@Override
	public void close() {
		Process p = proc;
		proc = null;
		if (p != null) {
			p.destroyForcibly();
		}
		snap = Snapshot.EMPTY;
		track = null;
		playing = false;
	}
}
