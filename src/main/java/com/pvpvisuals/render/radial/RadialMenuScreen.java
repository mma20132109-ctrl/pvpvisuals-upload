package com.pvpvisuals.render.radial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.config.RadialMenuSettings;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.smooth.SmoothRenderer;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Радиальное меню быстрых сообщений. Пока клавиша зажата — экран открыт; курсор (его разблокирует и ставит в центр
 * сама игра при открытии экрана) выбирает сектор по углу; отпускание клавиши или клик отправляет фразу в чат.
 * Esc закрывает меню без отправки.
 */
public class RadialMenuScreen extends Screen {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/radial");
	private static final int SEGMENTS = 14; // точек на дугу сектора
	private static final float GAP = (float) Math.toRadians(1.6); // зазор между секторами (с каждой стороны)

	private final KeyMapping key;
	private final long openedAt = Util.getMillis();
	private boolean done;
	private int selected = -1;
	private float hoverAnim; // 0..1: подсветка выбранного сектора плавно разгорается

	public RadialMenuScreen(KeyMapping key) {
		super(Component.literal("Radial Menu"));
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// ванильное размытие не нужно: затемнение рисуем сами в render()
	}

	@Override
	public void tick() {
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.getConnection() == null) {
			this.onClose(); // отключились от сервера
		}
	}

	// ---------- ввод ----------

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (key != null && key.matches(event)) {
			finish();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (key != null && key.matchesMouse(event)) { // клавиша вызова назначена на кнопку мыши
			finish();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) { // клик тоже подтверждает выбор
			finish();
		}
		return true;
	}

	/** Закрывает меню и отправляет фразу выбранного сектора (один раз). */
	private void finish() {
		if (done) {
			return;
		}
		done = true;
		RadialMenuSettings s = PvpConfig.get().radial;
		String text = selected >= 0 && selected < s.phrases.length ? s.phrases[selected] : null;
		this.onClose();
		send(text);
	}

	private static void send(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener conn = mc.getConnection(); // null, если не подключены к серверу
		if (conn == null) {
			return;
		}
		try {
			String t = text.trim();
			if (t.startsWith("/")) {
				conn.sendCommand(t.substring(1));
			} else {
				conn.sendChat(t);
			}
		} catch (Throwable e) {
			LOG.error("Radial Menu: failed to send '{}'", text, e);
		}
	}

	// ---------- отрисовка ----------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float prevAlpha = Gfx.alpha;
		try {
			draw(g, mouseX, mouseY);
		} catch (Throwable e) {
			LOG.error("Radial Menu failed to render, closing", e);
			this.onClose();
		} finally {
			Gfx.alpha = prevAlpha;
		}
	}

	private static int argb(int rgb, float alpha) {
		return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | (rgb & 0xFFFFFF);
	}

	private void draw(GuiGraphics g, int mouseX, int mouseY) {
		RadialMenuSettings s = PvpConfig.get().radial;
		int w = this.width;
		int h = this.height;
		float cx = w / 2f;
		float cy = h / 2f;
		EchoTheme.sync();

		// анимация открытия: 160 мс, ease-out
		float open = Gfx.ease((Util.getMillis() - openedAt) / 160f);
		Gfx.alpha = open;

		// затемнение экрана
		g.fill(0, 0, w, h, argb(0x07040D, 0.62f * open));

		float radius = Math.min(s.menuRadius, Math.min(w, h) * 0.46f) * (0.88f + 0.12f * open);
		float dead = Math.min(s.deadZoneRadius, radius * 0.5f);
		int n = RadialMenuSettings.SECTORS;
		selected = RadialSector.pick(mouseX - cx, mouseY - cy, dead, n);

		// плавное разгорание подсветки при смене сектора
		hoverAnim += ((selected >= 0 ? 1f : 0f) - hoverAnim) * 0.25f;

		int bg = argb(s.backgroundColor, s.backgroundAlpha * open);
		int hl = argb(s.highlightColor, s.highlightAlpha * open);
		int edge = argb(EchoTheme.shift(0xFF6D3FD0), open);
		int edgeHot = argb(EchoTheme.shift(0xFFC9A6FF), open);
		float step = (float) (Math.PI * 2.0 / n);

		// мягкое свечение под выбранным сектором (толстая полупрозрачная дуга по краю)
		if (selected >= 0) {
			float mid = (selected - 0.5f) * step; // начало сектора выбранного
			arc(g, cx, cy, radius + 3f, mid + GAP, mid + step - GAP, 5f, argb(s.highlightColor, 0.35f * open * hoverAnim));
		}

		for (int i = 0; i < n; i++) {
			float a0 = (i - 0.5f) * step + GAP;
			float a1 = (i + 0.5f) * step - GAP;
			boolean hot = i == selected;
			float r = hot ? radius + 6f * hoverAnim : radius;
			wedge(g, cx, cy, r, a0, a1, hot ? hl : bg);
			arc(g, cx, cy, r, a0, a1, hot ? 2f : 1f, hot ? edgeHot : edge);
		}

		// мёртвая зона в центре
		Gfx.circle(g, cx, cy, dead, argb(0x0B0616, 0.96f * open));
		Gfx.ringCircle(g, cx, cy, dead, 1.5f, edge);
		Gfx.circle(g, cx, cy, 2.5f, selected >= 0 ? edgeHot : edge);

		// подписи секторов: по центру сектора, на ~62% радиуса
		for (int i = 0; i < n; i++) {
			RadialSector sec = new RadialSector(i, (i - 0.5f) * step, (i + 0.5f) * step, s.phrases[i]);
			boolean hot = i == selected;
			float tr = dead + (radius - dead) * 0.55f + (hot ? 4f * hoverAnim : 0f);
			float tx = cx + (float) Math.sin(sec.midRad()) * tr;
			float ty = cy - (float) Math.cos(sec.midRad()) * tr;
			String label = sec.hasText() ? sec.text() : "-";
			float scale = hot ? 1.0f + 0.2f * hoverAnim : 1.0f;
			int color = sec.hasText() ? argb(s.textColor, open * (hot ? 1f : 0.85f)) : argb(0x9A8DB8, 0.6f * open);
			float tw = Gfx.width(this.font, label) * scale;
			Gfx.textScaled(g, this.font, label, tx - tw / 2f, ty - 4f * scale, scale, color, false);
		}
	}

	/** Сектор-«пирог» из центра (выпуклый, 72° < 180°); центр потом закрывается кругом мёртвой зоны. */
	private static void wedge(GuiGraphics g, float cx, float cy, float r, float a0, float a1, int color) {
		float[] pts = new float[(SEGMENTS + 2) * 2];
		pts[0] = cx;
		pts[1] = cy;
		for (int i = 0; i <= SEGMENTS; i++) {
			float a = a0 + (a1 - a0) * i / SEGMENTS;
			pts[2 + i * 2] = cx + (float) Math.sin(a) * r;
			pts[3 + i * 2] = cy - (float) Math.cos(a) * r;
		}
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothPolygon(g, pts, color);
		} else {
			// запасной вариант без сглаживания: веер из толстых линий от центра
			for (int i = 0; i <= SEGMENTS * 2; i++) {
				float a = a0 + (a1 - a0) * i / (SEGMENTS * 2);
				Gfx.line(g, cx, cy, cx + (float) Math.sin(a) * r, cy - (float) Math.cos(a) * r, 3f, color);
			}
		}
	}

	/** Дуга толщиной t по окружности радиуса r (ломаная со скруглёнными стыками). */
	private static void arc(GuiGraphics g, float cx, float cy, float r, float a0, float a1, float t, int color) {
		float[] pts = new float[(SEGMENTS + 1) * 2];
		for (int i = 0; i <= SEGMENTS; i++) {
			float a = a0 + (a1 - a0) * i / SEGMENTS;
			pts[i * 2] = cx + (float) Math.sin(a) * r;
			pts[i * 2 + 1] = cy - (float) Math.cos(a) * r;
		}
		if (SmoothRenderer.isOn()) {
			SmoothRenderer.drawSmoothPolyline(g, pts, t, color);
		}
	}
}
