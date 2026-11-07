package com.pvpvisuals.gui.rival;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.pvpvisuals.config.ConfigManager;
import com.pvpvisuals.config.ExportEntry;
import com.pvpvisuals.gui.echo.EchoTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;

/**
 * Кнопки Import / Export / Reset в футере меню и модальные окна к ним.
 * Рисуется вручную в стиле остального GUI (rounded rect, цвета темы).
 */
public final class ConfigDialog {
	public enum Mode { NONE, IMPORT, EXPORT, RESET }

	/** Что изменилось после клика: при CONFIG_CHANGED экран должен пересобрать меню. */
	public enum Result { IGNORED, CONSUMED, CONFIG_CHANGED }

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
	private static final int BTN_H = 16;
	private static final int PW = 200;
	private static final int ROWS = 6;
	private static final int ROW_H = 14;

	private static final String[] FOOTER = {"Import", "Export", "Reset"};
	private static final int FOOTER_W = 46;
	private static final int FOOTER_GAP = 4;

	private Mode mode = Mode.NONE;
	private final StringBuilder name = new StringBuilder();
	private List<ExportEntry> exports = List.of();
	private int scroll;

	public boolean active() {
		return mode != Mode.NONE;
	}

	// ---------- футер ----------

	private static int footerX(int screenW, int i) {
		int total = FOOTER.length * FOOTER_W + (FOOTER.length - 1) * FOOTER_GAP;
		return screenW - 12 - total + i * (FOOTER_W + FOOTER_GAP);
	}

	private static int footerY(int screenH) {
		return screenH - BTN_H - 12;
	}

	public void renderFooter(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
		for (int i = 0; i < FOOTER.length; i++) {
			button(g, font, footerX(sw, i), footerY(sh), FOOTER_W, FOOTER[i], mx, my, !active());
		}
	}

	/** @return true, если клик попал в кнопку футера (соответствующее окно открыто) */
	public boolean clickFooter(double mx, double my, int sw, int sh) {
		for (int i = 0; i < FOOTER.length; i++) {
			if (hit(mx, my, footerX(sw, i), footerY(sh), FOOTER_W, BTN_H)) {
				open(Mode.values()[i + 1]);
				return true;
			}
		}
		return false;
	}

	public void open(Mode m) {
		mode = m;
		scroll = 0;
		if (m == Mode.IMPORT) {
			exports = ConfigManager.getInstance().listExports();
		} else if (m == Mode.EXPORT) {
			name.setLength(0);
			name.append(LocalDateTime.now().format(STAMP));
		}
	}

	// ---------- геометрия окна ----------

	private int ph() {
		return switch (mode) {
			case IMPORT -> 26 + ROWS * ROW_H + 10 + BTN_H + 10;
			case EXPORT -> 96;
			default -> 72;
		};
	}

	private static int px(int sw) {
		return (sw - PW) / 2;
	}

	private int py(int sh) {
		return (sh - ph()) / 2;
	}

	private int btnY(int sh) {
		return py(sh) + ph() - BTN_H - 10;
	}

	// ---------- отрисовка ----------

	public void render(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
		if (!active()) {
			return;
		}
		g.fill(0, 0, sw, sh, 0x90000000);
		int x = px(sw);
		int y = py(sh);
		int h = ph();
		com.pvpvisuals.gui.echo.Gfx.alpha = 1f;
		com.pvpvisuals.gui.echo.Gfx.glow(g, x, y, PW, h, 12, EchoTheme.NEON, 6);
		com.pvpvisuals.gui.echo.Gfx.fill(g, x, y, PW, h, 12, EchoTheme.WIN_TOP, EchoTheme.WIN_BOTTOM);
		com.pvpvisuals.gui.echo.Gfx.ring(g, x, y, PW, h, 12, 1, EchoTheme.NEON_LINE);

		String title = switch (mode) {
			case IMPORT -> "Import config";
			case EXPORT -> "Export config";
			default -> "Reset to default";
		};
		g.drawString(font, title, x + (PW - font.width(title)) / 2, y + 9, EchoTheme.TEXT, true);

		int by = btnY(sh);
		switch (mode) {
			case IMPORT -> {
				int top = y + 26;
				RoundedRender.fill(g, x + 10, top, PW - 20, ROWS * ROW_H, 4, 0x40000000);
				g.enableScissor(x + 10, top, x + PW - 10, top + ROWS * ROW_H);
				if (exports.isEmpty()) {
					String s = "No exports yet";
					g.drawString(font, s, x + (PW - font.width(s)) / 2, top + 6, EchoTheme.MUTED, false);
				}
				for (int i = 0; i < ROWS && i + scroll < exports.size(); i++) {
					ExportEntry e = exports.get(i + scroll);
					int ry = top + i * ROW_H;
					if (hit(mx, my, x + 10, ry, PW - 20, ROW_H)) {
						RoundedRender.fill(g, x + 11, ry, PW - 22, ROW_H - 1, 3, 0x38FFFFFF);
					}
					String when = e.when();
					int maxName = PW - 20 - font.width(when) - 22;
					String shown = e.name();
					while (shown.length() > 1 && font.width(shown) > maxName) {
						shown = shown.substring(0, shown.length() - 1);
					}
					g.drawString(font, shown, x + 16, ry + 3, EchoTheme.TEXT, false);
					g.drawString(font, when, x + PW - 16 - font.width(when), ry + 3, EchoTheme.MUTED, false);
				}
				g.disableScissor();
				button(g, font, x + 10, by, 110, "From Clipboard", mx, my, true);
				button(g, font, x + PW - 10 - 60, by, 60, "Cancel", mx, my, true);
			}
			case EXPORT -> {
				g.drawString(font, "File name", x + 10, y + 26, EchoTheme.MUTED, false);
				int fy = y + 38;
				RoundedRender.fill(g, x + 10, fy, PW - 20, 16, 5, EchoTheme.FIELD_FOCUS);
				boolean caret = (Util.getMillis() / 500) % 2 == 0;
				g.drawString(font, name + (caret ? "_" : ""), x + 16, fy + 4, 0xFFFFFFFF, false);
				int bw = (PW - 20 - 12) / 3;
				button(g, font, x + 10, by, bw, "Export", mx, my, true);
				button(g, font, x + 10 + bw + 6, by, bw, "Clipboard", mx, my, true);
				button(g, font, x + 10 + 2 * (bw + 6), by, bw, "Cancel", mx, my, true);
			}
			default -> {
				String s = "Reset all settings?";
				g.drawString(font, s, x + (PW - font.width(s)) / 2, y + 28, EchoTheme.TEXT, false);
				button(g, font, x + 10, by, 85, "Yes", mx, my, true);
				button(g, font, x + PW - 10 - 85, by, 85, "No", mx, my, true);
			}
		}
	}

	private static void button(GuiGraphics g, Font font, int x, int y, int w, String label, int mx, int my, boolean enabled) {
		boolean hover = enabled && hit(mx, my, x, y, w, BTN_H);
		int a = hover ? RoundedRender.lerp(EchoTheme.TAB_TOP, 0xFFFFFFFF, 0.18f) : EchoTheme.TAB_TOP;
		int b = hover ? RoundedRender.lerp(EchoTheme.TAB_BOTTOM, 0xFFFFFFFF, 0.18f) : EchoTheme.TAB_BOTTOM;
		com.pvpvisuals.gui.echo.Gfx.fill(g, x, y, w, BTN_H, 7, a, b);
		com.pvpvisuals.gui.echo.Gfx.ring(g, x, y, w, BTN_H, 7, 1, EchoTheme.ACCENT_B);
		g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, 0xFFFFFFFF, false);
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---------- ввод ----------

	public Result click(double mx, double my, int sw, int sh) {
		if (!active()) {
			return Result.IGNORED;
		}
		int x = px(sw);
		int y = py(sh);
		int by = btnY(sh);
		ConfigManager cm = ConfigManager.getInstance();
		switch (mode) {
			case IMPORT -> {
				int top = y + 26;
				if (hit(mx, my, x + 10, top, PW - 20, ROWS * ROW_H)) {
					int idx = (int) ((my - top) / ROW_H) + scroll;
					if (idx >= 0 && idx < exports.size()) {
						return finish(cm.importFromFile(exports.get(idx).name()));
					}
				} else if (hit(mx, my, x + 10, by, 110, BTN_H)) {
					return finish(cm.pasteFromClipboard());
				} else if (hit(mx, my, x + PW - 70, by, 60, BTN_H)) {
					mode = Mode.NONE;
				}
			}
			case EXPORT -> {
				int bw = (PW - 20 - 12) / 3;
				if (hit(mx, my, x + 10, by, bw, BTN_H)) {
					doExport();
				} else if (hit(mx, my, x + 10 + bw + 6, by, bw, BTN_H)) {
					cm.copyToClipboard();
					mode = Mode.NONE;
				} else if (hit(mx, my, x + 10 + 2 * (bw + 6), by, bw, BTN_H)) {
					mode = Mode.NONE;
				}
			}
			default -> {
				if (hit(mx, my, x + 10, by, 85, BTN_H)) {
					cm.resetToDefault();
					mode = Mode.NONE;
					return Result.CONFIG_CHANGED;
				} else if (hit(mx, my, x + PW - 95, by, 85, BTN_H)) {
					mode = Mode.NONE;
				}
			}
		}
		return Result.CONSUMED;
	}

	private Result finish(boolean ok) {
		if (ok) {
			mode = Mode.NONE;
			return Result.CONFIG_CHANGED;
		}
		return Result.CONSUMED; // ошибка: окно остаётся, toast уже показан
	}

	private void doExport() {
		if (ConfigManager.getInstance().exportToFile(name.toString())) {
			mode = Mode.NONE;
		}
	}

	public boolean scroll(double scrollY) {
		if (mode == Mode.IMPORT) {
			int max = Math.max(0, exports.size() - ROWS);
			scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
		}
		return active();
	}

	public boolean key(int key) {
		if (!active()) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			mode = Mode.NONE;
		} else if (mode == Mode.EXPORT) {
			if (key == GLFW.GLFW_KEY_BACKSPACE && name.length() > 0) {
				name.deleteCharAt(name.length() - 1);
			} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
				doExport();
			}
		}
		return true;
	}

	public boolean charTyped(int codepoint) {
		if (mode == Mode.EXPORT && name.length() < 48 && codepoint < 128
				&& (Character.isLetterOrDigit(codepoint) || codepoint == '_' || codepoint == '-' || codepoint == ' ' || codepoint == '.')) {
			name.append((char) codepoint);
		}
		return active();
	}
}
