package com.pvpvisuals.render.splash;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;
import com.pvpvisuals.PvpVisualsClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Текстуры для экрана загрузки. Во время первой загрузки ресурсов менеджер ресурсов и шрифты Minecraft ещё не
 * готовы, поэтому логотип читается прямо из jar мода, а текст рисуется через AWT (шрифт Inter из jar) в текстуру.
 * Обе текстуры заранее делаются ровно в физический размер на экране и рисуются 1:1 — без «лесенки».
 */
final class SplashAssets {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/splash");

	record Tex(Identifier id, int wPx, int hPx) { }


	private static final Map<String, Tex> TEXT = new HashMap<>();
	private static Font baseFont;
	private static boolean fontFailed;
	private static int counter;

	private SplashAssets() { }

	private static InputStream openAsset(String path) throws java.io.IOException {
		Path p = FabricLoader.getInstance().getModContainer(PvpVisualsClient.MOD_ID)
				.flatMap(c -> c.findPath(path)).orElse(null);
		if (p == null) {
			throw new java.io.FileNotFoundException(path);
		}
		return Files.newInputStream(p);
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

	private static Identifier upload(BufferedImage img, String name) throws java.io.IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", bos);
		NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
		DynamicTexture tex = new DynamicTexture(() -> "pvpvisuals_" + name, ni);
		tex.upload();
		Identifier id = Identifier.fromNamespaceAndPath(PvpVisualsClient.MOD_ID, "splash/" + name + "_" + (counter++));
		Minecraft.getInstance().getTextureManager().register(id, tex);
		return id;
	}

	private static void release(Identifier id) {
		try {
			Minecraft.getInstance().getTextureManager().release(id);
		} catch (Throwable ignored) {
			// не критично
		}
	}


	/** Текст (белый, со сглаживанием) высотой шрифта sizePx физических пикселей; null, если шрифт недоступен. */
	static Tex text(String text, int sizePx) {
		if (fontFailed || text == null || text.isEmpty()) {
			return null;
		}
		String key = sizePx + "|" + text;
		Tex t = TEXT.get(key);
		if (t != null) {
			return t;
		}
		try {
			if (baseFont == null) {
				try (InputStream in = openAsset("assets/" + PvpVisualsClient.MOD_ID + "/font/inter.ttf")) {
					baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
				}
			}
			Font f = baseFont.deriveFont(Font.PLAIN, (float) sizePx);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			var pg = probe.createGraphics();
			pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			FontMetrics fm = pg.getFontMetrics(f);
			int w = fm.stringWidth(text) + 4;
			int h = fm.getHeight() + 2;
			int ascent = fm.getAscent();
			pg.dispose();
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			var g2 = img.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setFont(f);
			g2.setColor(java.awt.Color.WHITE);
			g2.drawString(text, 2, ascent + 1);
			g2.dispose();
			if (TEXT.size() > 24) {
				for (Tex old : TEXT.values()) {
					release(old.id());
				}
				TEXT.clear();
			}
			t = new Tex(upload(img, "text"), w, h);
			TEXT.put(key, t);
			return t;
		} catch (Throwable e) {
			fontFailed = true;
			LOG.warn("Splash text not available: {}", e.toString());
			return null;
		}
	}
}
