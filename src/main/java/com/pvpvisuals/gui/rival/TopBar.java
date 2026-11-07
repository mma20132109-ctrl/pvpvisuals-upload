package com.pvpvisuals.gui.rival;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.hud.HudIcons;
import com.pvpvisuals.media.NowPlayingWidgetRenderer;
import com.pvpvisuals.render.LogoRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Island HUD с динамической высотой. Нижняя строка всегда видна: [время] — логотип — [FPS]. Когда играет музыка,
 * остров плавно растёт вверх на одну строку: название — таймкод — автор. Высота = базовая + ROW_H * progress,
 * где progress интерполируется каждый кадр (ease-out-cubic, 350 мс). Ширина подстраивается под самую широкую строку.
 */
public final class TopBar {
	/** Название в плашке (если логотипа нет); замените на своё. */
	public static final String NAME = "PvPVisuals";

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
	private static final int BASE_H = 22;
	private static final int TOP = 8;
	private static final int LOGO_H = 16;
	private static final int CORNER = 10;
	private static final int WING = 18;
	private static final int ICON_W = 11; // иконка 7 px + зазор

	private final NowPlayingWidgetRenderer nowPlaying = new NowPlayingWidgetRenderer();

	public void render(GuiGraphics g, Font font, int screenW) {
		EchoTheme.sync();
		nowPlaying.update(font);

		Minecraft mc = Minecraft.getInstance();
		String time = LocalTime.now().format(FMT);
		String fps = mc.getFps() + " fps";
		String ping = latency(mc) + " ms";
		// фиксированная ширина по самым широким значениям, чтобы остров не «дышал» при смене FPS, пинга и времени
		int tW = Gfx.width(font, "88:88");
		int fW = Gfx.width(font, "888 fps");
		int pW = Gfx.width(font, "888 ms");
		int leftW = ICON_W + tW;
		int rightW = ICON_W + fW + 9 + ICON_W + pW;
		int side = Math.max(leftW, rightW) + 8;

		// центральный элемент: логотип (если есть картинка) или точка + название
		int logoW = LogoRenderer.crispWidth(LOGO_H);
		int nameW = Gfx.width(font, NAME);
		int centerW = logoW > 0 ? logoW : 8 + nameW;

		int infoW = 4 + side + 8 + centerW + 8 + side + 4;
		float p = nowPlaying.progress();
		int rowW = nowPlaying.active() ? nowPlaying.rowWidth() : 0;
		int rowH = Math.round(NowPlayingWidgetRenderer.ROW_H * p);
		// верхняя часть всегда шире нижней минимум на WING с каждой стороны, иначе уступа не видно
		int fullTopW = Math.max(infoW + 2 * WING, rowW);
		float t = Math.max(0f, rowH - CORNER);
		float wing = (fullTopW - infoW) / 2f * Math.min(1f, t / (NowPlayingWidgetRenderer.ROW_H - CORNER));
		float topW = infoW + 2f * wing;
		float cxf = screenW / 2f;
		int mainW = Math.round(topW);
		int x = Math.round(cxf - topW / 2f);

		// стеклянный остров как у ватермарки Svintus: тёмная заливка, тонкая светлая обводка, мягкое акцентное свечение
		Gfx.island(g, cxf, TOP, topW, rowH, infoW, BASE_H, CORNER, Math.min(8f, t), 6f, EchoTheme.HUD_TOP,
				EchoTheme.HUD_BOTTOM, 0x30FFFFFF, 1f, EchoTheme.NEON, 2f);

		// верхняя строка — музыка (раскладка по полной ширине, видимая часть — по текущей)
		if (rowH > 0) {
			int fx = Math.round(cxf - fullTopW / 2f);
			nowPlaying.renderRow(g, font, fx, TOP, fullTopW, rowH, x, x + mainW);
		}

		// нижняя строка — всегда
		int y = TOP + rowH;
		int chipH = BASE_H - 4;
		int chipY = y + 2;
		int textY = y + (BASE_H - 8) / 2;
		int cx = Math.round(cxf - infoW / 2f) + 4;

		int cy = y + BASE_H / 2;
		int accent = EchoTheme.NEON;

		// слева: часы
		int lx = cx + (side - leftW) / 2;
		HudIcons.clock(g, lx + 3.5f, cy, 3.5f, accent);
		Gfx.text(g, font, time, lx + ICON_W, textY, EchoTheme.TEXT);

		// по центру: логотип (с акцентным свечением) или точка + название
		int mid = Math.round(cxf);
		if (logoW > 0) {
			Gfx.glow(g, mid - logoW / 2f, y + (BASE_H - LOGO_H) / 2f, logoW, LOGO_H, 4f, RoundedRender.scaleAlpha(accent, 0.45f), 3f);
			LogoRenderer.renderCrisp(g, mid - logoW / 2, y + (BASE_H - LOGO_H) / 2, LOGO_H, 1f);
		} else {
			int start = mid - centerW / 2;
			RoundedRender.circle(g, start + 3, cy, 3, EchoTheme.ACCENT_B);
			Gfx.text(g, font, NAME, start + 8, textY, EchoTheme.TEXT);
		}

		// тонкие разделители между секциями
		int divL = cx + side + 4;
		int divR = Math.round(cxf + infoW / 2f) - 4 - side - 4;
		Gfx.rect(g, divL, y + 6, divL + 1, y + BASE_H - 6, 0x1EFFFFFF);
		Gfx.rect(g, divR, y + 6, divR + 1, y + BASE_H - 6, 0x1EFFFFFF);

		// справа: FPS и пинг
		int rx = Math.round(cxf + infoW / 2f) - 4 - side + (side - rightW) / 2;
		HudIcons.activity(g, rx + 3.5f, cy, 3.5f, accent);
		Gfx.text(g, font, fps, rx + ICON_W, textY, EchoTheme.TEXT);
		int px = rx + ICON_W + fW + 9;
		Gfx.rect(g, px - 5, y + 6, px - 4, y + BASE_H - 6, 0x1EFFFFFF);
		HudIcons.signal(g, px + 3.5f, cy, 3.5f, accent);
		Gfx.text(g, font, ping, px + ICON_W, textY, EchoTheme.TEXT);
	}

	/** Пинг до сервера в мс (0 в одиночной игре или если записи игрока ещё нет). */
	private static int latency(Minecraft mc) {
		if (mc.getConnection() == null || mc.player == null) {
			return 0;
		}
		var info = mc.getConnection().getPlayerInfo(mc.player.getUUID()); // ПРОВЕРИТЬ: ClientPacketListener#getPlayerInfo(UUID)
		return info == null ? 0 : info.getLatency();
	}
}
