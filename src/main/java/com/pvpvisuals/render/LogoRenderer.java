package com.pvpvisuals.render;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.pvpvisuals.PvpVisualsClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Рисует логотип echo_visual_{128,512,2048}.png из textures/gui/logo/.
 * Файл ищется в нескольких namespace (на случай опечатки в папке assets/...). Если ни одной версии нет,
 * ничего не рисуется (иначе Minecraft показал бы фиолетово-чёрную «missing texture»).
 */
public final class LogoRenderer {
	public static final int[] SIZES = {128, 512, 2048};
	/** Основной namespace и запасные (папки assets/<ns>/...). */
	private static final String[] NAMESPACES = {PvpVisualsClient.MOD_ID, "pvpvisual", "echovisual"};
	private static final long RECHECK_NANOS = 5_000_000_000L;

	private static final Identifier[] RESOLVED = new Identifier[SIZES.length];
	private static final long[] NEXT_CHECK = new long[SIZES.length];
	private static final int[] TEX_W = new int[SIZES.length];
	private static final int[] TEX_H = new int[SIZES.length];
	private static boolean warned;

	private LogoRenderer() { }

	private static int index(int size) {
		return size <= SIZES[0] ? 0 : size <= SIZES[1] ? 1 : 2;
	}

	/** Текстура версии i или null, если файла нет ни в одном namespace. Проверка кэшируется на 5 секунд. */
	private static Identifier resolve(int i) {
		long now = System.nanoTime();
		if (now < NEXT_CHECK[i]) {
			return RESOLVED[i];
		}
		NEXT_CHECK[i] = now + RECHECK_NANOS;
		Identifier found = null;
		var rm = Minecraft.getInstance().getResourceManager();
		for (String ns : NAMESPACES) {
			Identifier id = Identifier.fromNamespaceAndPath(ns, "textures/gui/logo/echo_visual_" + SIZES[i] + ".png");
			if (rm.getResource(id).isPresent()) {
				found = id;
				break;
			}
		}
		if (found == null && !warned) {
			warned = true;
			System.err.println("[pvpvisuals] Логотип не найден: ожидается assets/" + PvpVisualsClient.MOD_ID
					+ "/textures/gui/logo/echo_visual_" + SIZES[i] + ".png");
		}
		if (found != null && !found.equals(RESOLVED[i])) {
			TEX_W[i] = 0; // другой файл — размеры прочитать заново
		}
		RESOLVED[i] = found;
		return found;
	}

	/** Ближайшая существующая версия к желаемой (сначала она, затем остальные), либо -1. */
	private static int pick(int size) {
		int want = index(size);
		if (resolve(want) != null) {
			return want;
		}
		for (int d = 1; d < SIZES.length; d++) {
			for (int i : new int[] {want + d, want - d}) {
				if (i >= 0 && i < SIZES.length && resolve(i) != null) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Есть ли хотя бы одна версия логотипа. */
	public static boolean hasLogo() {
		return pick(SIZES[0]) >= 0;
	}

	/** Identifier найденной версии (128 / 512 / 2048) или null. */
	public static Identifier getLogoIdentifier(int size) {
		int i = pick(size);
		return i < 0 ? null : RESOLVED[i];
	}

	private static void ensureDims(int i) {
		if (TEX_W[i] != 0) {
			return;
		}
		int w = SIZES[i];
		int h = SIZES[i];
		try {
			var res = Minecraft.getInstance().getResourceManager().getResource(RESOLVED[i]);
			if (res.isPresent()) {
				try (InputStream in = res.get().open()) {
					var img = ImageIO.read(in);
					if (img != null) {
						w = img.getWidth();
						h = img.getHeight();
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[pvpvisuals] Не удалось прочитать размер логотипа " + RESOLVED[i] + ": " + e);
		}
		TEX_W[i] = w;
		TEX_H[i] = h;
	}

	/** Отношение ширины к высоте реального файла (1, если логотипа нет). */
	public static float aspect(int size) {
		int i = pick(size);
		if (i < 0) {
			return 1f;
		}
		ensureDims(i);
		return TEX_W[i] / (float) TEX_H[i];
	}

	/** Версия для Auto: порог по высоте на экране в пикселях (<=64 -> 128, <=256 -> 512, иначе 2048). */
	public static int autoSize(float targetHeightGui) {
		double px = targetHeightGui * Minecraft.getInstance().getWindow().getGuiScale();
		return px <= 64 ? 128 : px <= 256 ? 512 : 2048;
	}

	public static int resolve(LogoSizeMode mode, float targetHeightGui) {
		return mode == LogoSizeMode.AUTO ? autoSize(targetHeightGui) : mode.size;
	}

	/** Ширина логотипа при заданной высоте; 0, если логотипа нет. */
	public static int widthFor(LogoSizeMode mode, int targetHeight, float uiScale) {
		int size = resolve(mode, targetHeight * uiScale);
		if (pick(size) < 0) {
			return 0;
		}
		return Math.round(targetHeight * aspect(size));
	}

	/** Рисует текстуру нужного размера в прямоугольник; alpha умножается на альфу текстуры. */
	public static boolean renderLogo(GuiGraphics g, int size, int x, int y, int width, int height, float alpha) {
		int i = pick(size);
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		if (i < 0 || a == 0) {
			return false;
		}
		ensureDims(i);
		RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
		g.blit(pipeline, RESOLVED[i], x, y, 0f, 0f, width, height, TEX_W[i], TEX_H[i], TEX_W[i], TEX_H[i],
				(a << 24) | 0xFFFFFF);
		return true;
	}

	/** @return ширина нарисованного логотипа или 0, если логотипа нет */
	public static int renderLogoAuto(GuiGraphics g, LogoSizeMode mode, int x, int y, int targetHeight,
			float uiScale, float alpha) {
		int size = resolve(mode, targetHeight * uiScale);
		if (pick(size) < 0) {
			return 0;
		}
		int w = Math.round(targetHeight * aspect(size));
		return renderLogo(g, size, x, y, w, targetHeight, alpha) ? w : 0;
	}

	// ---------- «чёткий» логотип: заранее уменьшенный до точного размера в физических пикселях ----------

	private static long crispKey = -1;
	private static Identifier crispId;
	private static int crispCounter;

	/** Ширина логотипа (в единицах GUI) при высоте targetH для crisp-режима; 0, если логотипа нет. */
	public static int crispWidth(int targetH) {
		int i = pick(SIZES[2]);
		return i < 0 ? 0 : Math.round(targetH * aspect(SIZES[i]));
	}

	/**
	 * Рисует логотип из самой большой версии, предварительно уменьшенной (ступенчато вдвое + бикубика) ровно до
	 * размера на экране в физических пикселях и рисуемой 1:1. Это убирает «лесенку» и мерцание, которые даёт
	 * прямое сжатие большой текстуры без mip-уровней. Текстура создаётся один раз на размер.
	 *
	 * @return ширина в единицах GUI или 0
	 */
	public static int renderCrisp(GuiGraphics g, int x, int y, int targetH, float alpha) {
		int i = pick(SIZES[2]);
		if (i < 0) {
			return 0;
		}
		ensureDims(i);
		int s = (int) Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
		int wGui = Math.round(targetH * aspect(SIZES[i]));
		int wPx = wGui * s;
		int hPx = targetH * s;
		Identifier id = crispTexture(i, wPx, hPx);
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		if (id == null) {
			return renderLogo(g, SIZES[i], x, y, wGui, targetH, alpha) ? wGui : 0;
		}
		if (a > 0) {
			g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, wGui, targetH, wPx, hPx, wPx, hPx,
					(a << 24) | 0xFFFFFF);
		}
		return wGui;
	}

	private static Identifier crispTexture(int i, int wPx, int hPx) {
		long key = ((long) i << 40) | ((long) wPx << 20) | hPx;
		if (key == crispKey) {
			return crispId;
		}
		crispKey = key;
		Minecraft mc = Minecraft.getInstance();
		Identifier old = crispId;
		crispId = null;
		try {
			BufferedImage cur;
			try (InputStream in = mc.getResourceManager().getResource(RESOLVED[i]).orElseThrow().open()) {
				cur = ImageIO.read(in);
			}
			if (cur == null) {
				return null;
			}
			while (cur.getWidth() > wPx * 2 && cur.getHeight() > hPx * 2) {
				cur = resample(cur, cur.getWidth() / 2, cur.getHeight() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			}
			cur = resample(cur, wPx, hPx, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageIO.write(cur, "png", bos);
			NativeImage img = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
			DynamicTexture tex = new DynamicTexture(() -> "pvpvisuals_logo_crisp", img);
			tex.upload();
			Identifier id = Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "logo_crisp/" + (crispCounter++));
			mc.getTextureManager().register(id, tex);
			crispId = id;
		} catch (Throwable e) {
			System.err.println("[pvpvisuals] Crisp-логотип не создан, используется обычный: " + e);
		}
		if (old != null) {
			try {
				mc.getTextureManager().release(old);
			} catch (Throwable ignored) {
				// не критично
			}
		}
		return crispId;
	}

	private static BufferedImage resample(BufferedImage src, int w, int h, Object hint) {
		BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
		var g2 = out.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
		g2.dispose();
		return out;
	}
}
