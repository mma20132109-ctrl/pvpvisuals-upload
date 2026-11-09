package com.pvpvisuals.gui.echo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleEntry;
import com.pvpvisuals.keybind.KeybindManager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Настройки одного модуля (подменю): шапка с кнопкой «назад», тумблером и биндом, ниже — прокручиваемый список строк
 * (переключатели, ползунки, выпадающие группы, кнопки) в стиле карточек.
 */
public final class SettingsView {
	public enum Result { NONE, CONSUMED, CHANGED, BACK }

	private static final int HEAD_H = 34;
	private static final int ROW_H = 22;
	private static final int SLIDER_H = 30;
	private static final int ROW_GAP = 4;
	private static final int PILL_H = 14;

	private final ModuleCard card;
	private final ScrollArea scroll = new ScrollArea();
	private final List<ModuleEntry> rows = new ArrayList<>();
	private final Map<ModuleEntry, ToggleSwitch> switches = new IdentityHashMap<>();
	private final ToggleSwitch headSwitch = new ToggleSwitch();

	private int x;
	private int y;
	private int w;
	private int h;
	private ModuleEntry dragging;
	/** Строка с текстовым полем, которая сейчас редактируется (или null). */
	private ModuleEntry editing;
	private final StringBuilder buf = new StringBuilder();
	private String editOriginal = "";
	private int pillW = 34;
	private float anim;

	public SettingsView(ModuleCard card) {
		this.card = card;
	}

	public ModuleCard card() {
		return card;
	}

	public void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		scroll.setBounds(x, y + HEAD_H + 6, w, h - HEAD_H - 6);
	}

	private void collect() {
		rows.clear();
		for (ModuleEntry k : card.root.kids) {
			flatten(k);
		}
	}

	private void flatten(ModuleEntry e) {
		if (!e.def.isVisible()) {
			return;
		}
		rows.add(e);
		if (e.expanded) {
			for (ModuleEntry k : e.kids) {
				flatten(k);
			}
		}
	}

	private static int rowHeight(ModuleEntry e) {
		return e.def.isSlider() ? SLIDER_H : ROW_H;
	}

	private int contentHeight() {
		int total = 0;
		for (ModuleEntry e : rows) {
			total += rowHeight(e) + ROW_GAP;
		}
		return Math.max(0, total - ROW_GAP);
	}

	private int rowW() {
		return w - 8;
	}

	public boolean contains(double mx, double my) {
		return Gfx.hit(mx, my, x, y, w, h);
	}

	public void scrollBy(float px) {
		scroll.scrollBy(px);
	}

	// ---------- отрисовка ----------

	public void render(GuiGraphics g, Font font, int mx, int my, float dtMs, boolean allowHover, String bindText) {
		collect();
		scroll.setContentHeight(contentHeight());
		scroll.update(dtMs);
		anim = Math.min(1f, anim + dtMs / 180f);

		// шапка
		boolean backHover = allowHover && Gfx.hit(mx, my, x, y + 6, 22, 22);
		LiquidGlassRenderer.surface(g, x, y + 6, 22, 22, 6,
				backHover ? EchoTheme.CARD_HOVER : EchoTheme.CARD,
				backHover ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER);
		EchoIcons.chevron(g, x + 8, y + 10, 0, EchoTheme.TEXT);

		Component title = Component.literal(card.def.title()).withStyle(ChatFormatting.BOLD);
		g.pose().pushMatrix();
		g.pose().translate(x + 30, y + 9);
		g.pose().scale(1.2f, 1.2f);
		Gfx.text(g, font, title, 0, 0, EchoTheme.TEXT, true);
		g.pose().popMatrix();
		String d = card.description();
		if (!d.isEmpty()) {
			Gfx.text(g, font, d, x + 30, y + 22, EchoTheme.MUTED);
		}

		int right = x + w - 8;
		if (card.def.isToggle()) {
			headSwitch.update(card.def.enabled(), dtMs);
			right -= ToggleSwitch.W;
			headSwitch.render(g, right, y + 5 + (PILL_H + 4 - ToggleSwitch.H) / 2 + 1, ToggleSwitch.W, ToggleSwitch.H);
			right -= 8;
		}
		if (card.bindable) {
			boolean listening = card.def.name.equals(KeybindManager.listeningModule());
			String label = listening ? "PRESS KEY" : bindText != null ? bindText : "NONE";
			pillW = Math.max(34, Gfx.width(font, label) + 12);
			int px = right - pillW;
			int py = y + 6 + 4;
			LiquidGlassRenderer.surface(g, px, py, pillW, PILL_H, 5, EchoTheme.PILL,
					listening ? EchoTheme.NEON : EchoTheme.PILL_BORDER);
			Gfx.text(g, font, label, px + (pillW - Gfx.width(font, label)) / 2, py + 3,
					listening ? EchoTheme.TEXT : (bindText != null ? EchoTheme.ACCENT_TEXT : EchoTheme.DIM));
			if (bindText != null && !listening) {
				boolean hv = allowHover && Gfx.hit(mx, my, px - 18, py, 14, PILL_H);
				LiquidGlassRenderer.surface(g, px - 18, py, 14, PILL_H, 5,
						hv ? EchoTheme.CARD_HOVER : EchoTheme.PILL,
						hv ? EchoTheme.CARD_BORDER_HOT : EchoTheme.PILL_BORDER);
				EchoIcons.close(g, px - 15, py + 3, hv ? EchoTheme.TEXT : EchoTheme.MUTED);
			}
		}
		Gfx.rect(g, x, y + HEAD_H, x + w, y + HEAD_H + 1, EchoTheme.DIVIDER);

		// строки
		float fade = Gfx.ease(anim);
		scroll.begin(g);
		int off = scroll.offset();
		int ry = scroll.y - off;
		boolean inside = allowHover && scroll.contains(mx, my);
		for (ModuleEntry e : rows) {
			int rh = rowHeight(e);
			if (ry + rh >= scroll.y - 2 && ry <= scroll.y + scroll.h + 2) {
				renderRow(g, font, e, ry, rh, inside && Gfx.hit(mx, my, x, ry, rowW(), rh), dtMs, fade);
			}
			ry += rh + ROW_GAP;
		}
		scroll.end(g);
		scroll.renderBar(g);
	}

	private void renderRow(GuiGraphics g, Font font, ModuleEntry e, int ry, int rh, boolean hover, float dtMs,
			float fade) {
		ModuleDef d = e.def;
		int rw = rowW();
		float prev = Gfx.alpha;
		Gfx.alpha = prev * fade;

		if (d.isText()) {
			renderTextRow(g, font, e, ry, rh, rw, hover);
			Gfx.alpha = prev;
			return;
		}
		LiquidGlassRenderer.surface(g, x, ry, rw, rh, 7, hover ? EchoTheme.CARD_HOVER : EchoTheme.CARD,
			hover ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER);

		int tx = x + 10 + e.depth * 8 - 8;
		boolean lit = d.enabled() || d.isGroup() || d.isSlider();
		int textC = lit ? EchoTheme.TEXT : EchoTheme.MUTED;

		if (d.isSlider()) {
			Gfx.text(g, font, d.title(), tx, ry + 5, EchoTheme.TEXT);
			String v = d.valueText();
			Gfx.text(g, font, v, x + rw - 10 - Gfx.width(font, v), ry + 5, EchoTheme.ACCENT_TEXT);
			int bx = x + 10;
			int bw = rw - 20;
			Gfx.fill(g, bx, ry + 19, bw, 4, 2, EchoTheme.CARD_BORDER);
			float ratio = (float) d.ratio();
			int fw = Math.max(4, Math.round(bw * ratio));
			Gfx.fill(g, bx, ry + 19, fw, 4, 2, EchoTheme.ACCENT_A, EchoTheme.ACCENT_B);
			Gfx.circle(g, bx + Math.round((bw - 1) * ratio), ry + 21, 4, EchoTheme.KNOB);
		} else {
			Gfx.text(g, font, d.title(), tx, ry + (rh - 8) / 2, textC);
			int right = x + rw - 8;
			if (e.hasKids()) {
				EchoIcons.chevron(g, right - 7, ry + (rh - 4) / 2 - 1, 2, e.expanded ? EchoTheme.ACCENT_TEXT : EchoTheme.DIM);
				right -= 16;
			}
			if (d.isToggle()) {
				ToggleSwitch sw = switches.computeIfAbsent(e, k -> new ToggleSwitch());
				sw.update(d.enabled(), dtMs);
				sw.render(g, right - 20, ry + (rh - 10) / 2, 20, 10);
			} else if (!d.isGroup()) {
				EchoIcons.chevron(g, right - 4, ry + (rh - 8) / 2, 1, hover ? EchoTheme.ACCENT_TEXT : EchoTheme.DIM);
			}
		}
		Gfx.alpha = prev;
	}

	private void renderTextRow(GuiGraphics g, Font font, ModuleEntry e, int ry, int rh, int rw, boolean hover) {
		boolean edit = e == editing;
		LiquidGlassRenderer.surface(g, x, ry, rw, rh, 7, hover || edit ? EchoTheme.CARD_HOVER : EchoTheme.CARD,
			edit || hover ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER);
		Gfx.text(g, font, e.def.title(), x + 10 + e.depth * 8 - 8, ry + (rh - 8) / 2, EchoTheme.TEXT);
		int fx = x + (int) (rw * 0.36f);
		int fw = rw - (fx - x) - 6;
		LiquidGlassRenderer.surface(g, fx, ry + 3, fw, rh - 6, 5, EchoTheme.CARD,
			edit ? EchoTheme.ACCENT_TEXT : EchoTheme.CARD_BORDER);
		String s = edit ? buf.toString() : e.def.text();
		boolean caret = edit && (System.currentTimeMillis() / 500) % 2 == 0;
		String shown = caret ? s + "_" : s;
		// длинный текст показываем с конца, чтобы был виден ввод
		while (shown.length() > 1 && Gfx.width(font, shown) > fw - 10) {
			shown = shown.substring(1);
		}
		if (s.isEmpty() && !edit) {
			Gfx.text(g, font, "empty", fx + 5, ry + (rh - 8) / 2, EchoTheme.DIM);
		} else {
			Gfx.text(g, font, shown, fx + 5, ry + (rh - 8) / 2, EchoTheme.TEXT);
		}
	}

	// ---------- редактирование текстовых строк ----------

	public boolean isEditing() {
		return editing != null;
	}

	private void beginEdit(ModuleEntry e) {
		editing = e;
		editOriginal = e.def.text();
		buf.setLength(0);
		buf.append(editOriginal);
	}

	private void commit() {
		if (editing != null) {
			editing = null;
			com.pvpvisuals.config.PvpConfig.get().save();
		}
	}

	/** Символ с клавиатуры. @return true, если поглощён */
	public boolean editChar(int codepoint) {
		if (editing == null) {
			return false;
		}
		if (codepoint >= 32 && codepoint != 127 && buf.length() < 64) {
			buf.appendCodePoint(codepoint);
			editing.def.setText(buf.toString());
		}
		return true;
	}

	/** Клавиша во время редактирования: Backspace, Enter (применить), Esc (отменить). */
	public boolean editKey(int key) {
		if (editing == null) {
			return false;
		}
		if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
			if (buf.length() > 0) {
				buf.deleteCharAt(buf.length() - 1);
				editing.def.setText(buf.toString());
			}
		} else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
			commit();
		} else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			editing.def.setText(editOriginal);
			commit();
		}
		return true;
	}

	// ---------- ввод ----------

	/** @param bindText текущий текст бинда (нужен для плашки в шапке; сам клик по плашке запускает ожидание клавиши) */
	public Result click(double mx, double my, int button) {
		if (editing != null) {
			commit(); // клик куда угодно применяет ввод (если клик по другому полю, оно откроется ниже)
		}
		if (Gfx.hit(mx, my, x, y + 6, 22, 22)) {
			return Result.BACK;
		}
		// шапка: тумблер и бинд
		if (my >= y && my < y + HEAD_H) {
			int right = x + w - 8;
			if (card.def.isToggle()) {
				right -= ToggleSwitch.W;
				if (ToggleSwitch.hit(mx, my, right, y + 5 + (PILL_H + 4 - ToggleSwitch.H) / 2 + 1, ToggleSwitch.W, ToggleSwitch.H)) {
					return card.def.activate() ? Result.CHANGED : Result.CONSUMED;
				}
				right -= 8;
			}
			if (card.bindable && card.bindText != null && Gfx.hit(mx, my, right - pillW - 18, y + 10, 14, PILL_H)) {
				KeybindManager.clear(card.def.name); // кнопка «×» рядом с плашкой бинда
				return Result.CONSUMED;
			}
			if (card.bindable && Gfx.hit(mx, my, right - pillW, y + 10, pillW, PILL_H)) {
				if (button == 1) {
					KeybindManager.clear(card.def.name);
				} else {
					KeybindManager.startListening(card.def.name);
				}
				return Result.CONSUMED;
			}
			return Result.CONSUMED;
		}
		if (!scroll.contains(mx, my)) {
			return Result.NONE;
		}
		collect();
		int ry = scroll.y - scroll.offset();
		int rw = rowW();
		for (ModuleEntry e : rows) {
			int rh = rowHeight(e);
			if (Gfx.hit(mx, my, x, ry, rw, rh)) {
				ModuleDef d = e.def;
				if (d.isText()) {
					beginEdit(e);
					return Result.CONSUMED;
				}
				if (d.isSlider()) {
					dragging = e;
					dragTo(mx);
					return Result.CHANGED;
				}
				if (e.hasKids() && (d.isGroup() || mx >= x + rw - 28)) {
					e.expanded = !e.expanded;
					return Result.CONSUMED;
				}
				return d.activate() ? Result.CHANGED : Result.CONSUMED;
			}
			ry += rh + ROW_GAP;
		}
		return Result.CONSUMED;
	}

	public boolean isDragging() {
		return dragging != null;
	}

	public void dragTo(double mx) {
		if (dragging != null) {
			dragging.def.setRatio((mx - (x + 10)) / (double) (rowW() - 20));
		}
	}

	public void endDrag() {
		dragging = null;
	}
}
