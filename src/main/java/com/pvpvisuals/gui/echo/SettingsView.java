package com.pvpvisuals.gui.echo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleEntry;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.keybind.KeybindManager;
import com.pvpvisuals.gui.svintus.Anim;

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
	private static final int ROW_H = 24;
	private static final int SLIDER_H = 31;
	private static final int ROW_GAP = 4;
	private static final int PILL_H = 14;

	private final ModuleCard card;
	private final boolean inline;
	private final ScrollArea scroll = new ScrollArea();
	private final List<ModuleEntry> rows = new ArrayList<>();
	private final Map<ModuleEntry, ToggleSwitch> switches = new IdentityHashMap<>();
	private final Map<ModuleEntry, Anim> sliderFills = new IdentityHashMap<>();
	private final Map<ModuleEntry, Anim> rowHovers = new IdentityHashMap<>();
	private final ToggleSwitch headSwitch = new ToggleSwitch();

	private int x;
	private int y;
	private int w;
	private int h;
	private ModuleEntry dragging;
	/** Строка с текстовым полем, которая сейчас редактируется (или null). */
	private ModuleEntry editing;
	private ModuleEntry dropdown;
	private final StringBuilder buf = new StringBuilder();
	private String editOriginal = "";
	private int pillW = 34;
	private float anim;

	public SettingsView(ModuleCard card) {
		this(card, false);
	}

	public SettingsView(ModuleCard card, boolean inline) {
		this.card = card;
		this.inline = inline;
}

	public ModuleCard card() {
		return card;
	}

	public void layoutInline(int x, int y, int w, int h) {
		this.x = x; this.y = y; this.w = w; this.h = h;
		scroll.setBounds(x, y, w, h);
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

		// Шапка отдельного экрана; встроенный режим показывает только строки настроек.
		if (!inline) {
		boolean backHover = allowHover && Gfx.hit(mx, my, x, y + 6, 22, 22);
		Gfx.fill(g, x, y + 6, 22, 22, 6, backHover ? EchoTheme.CARD_HOVER : EchoTheme.CARD);
		Gfx.ring(g, x, y + 6, 22, 22, 6, 1, backHover ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER);
		EchoIcons.chevron(g, x + 8, y + 10, 0, EchoTheme.TEXT);

		Component title = Component.literal(card.def.title()).withStyle(ChatFormatting.BOLD);
		g.pose().pushMatrix();
		g.pose().translate(x + 30, y + 9);
		g.pose().scale(1f, 1f);
		Gfx.textAtSize(g, font, title, 0, 0, 7.4f, EchoTheme.TEXT, true);
		g.pose().popMatrix();
		String d = card.description();
		if (!d.isEmpty()) {
			Gfx.textAtSize(g, font, d, x + 30, y + 22, 5.8f, EchoTheme.MUTED, false);
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
			pillW = Math.max(34, Gfx.widthAtSize(font, label, 5.4f) + 12);
			int px = right - pillW;
			int py = y + 6 + 4;
			Gfx.fill(g, px, py, pillW, PILL_H, 5, EchoTheme.PILL);
			Gfx.ring(g, px, py, pillW, PILL_H, 5, 1, listening ? EchoTheme.NEON : EchoTheme.PILL_BORDER);
			Gfx.textAtSize(g, font, label, px + (pillW - Gfx.widthAtSize(font, label, 5.4f)) / 2f, py + 4, 5.4f,
					listening ? EchoTheme.TEXT : (bindText != null ? EchoTheme.ACCENT_TEXT : EchoTheme.DIM), false);
			if (bindText != null && !listening) {
				boolean hv = allowHover && Gfx.hit(mx, my, px - 18, py, 14, PILL_H);
				Gfx.fill(g, px - 18, py, 14, PILL_H, 5, hv ? EchoTheme.CARD_HOVER : EchoTheme.PILL);
				Gfx.ring(g, px - 18, py, 14, PILL_H, 5, 1, hv ? EchoTheme.CARD_BORDER_HOT : EchoTheme.PILL_BORDER);
				EchoIcons.close(g, px - 15, py + 3, hv ? EchoTheme.TEXT : EchoTheme.MUTED);
			}
		}
		Gfx.rect(g, x, y + HEAD_H, x + w, y + HEAD_H + 1, EchoTheme.DIVIDER);
		}

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
		renderDropdown(g, font, mx, my, allowHover);
	}

	private void renderRow(GuiGraphics g, Font font, ModuleEntry e, int ry, int rh, boolean hover, float dtMs,
			float fade) {
		ModuleDef d = e.def;
		int rw = rowW();
		Anim hoverAnim = rowHovers.computeIfAbsent(e, key -> new Anim(0f, 14f));
		float hoverProgress = hoverAnim.to(hover).get();
		boolean rowHovered = hoverProgress > 0.02f;
		float prev = Gfx.alpha;
		Gfx.alpha = prev * fade;

		if (d.isText()) {
			renderTextRow(g, font, e, ry, rh, rw, rowHovered);
			Gfx.alpha = prev;
			return;
		}
		Gfx.fill(g, x, ry, rw, rh, 7, RoundedRender.lerp(EchoTheme.CARD, EchoTheme.CARD_HOVER, hoverProgress));
		Gfx.ring(g, x, ry, rw, rh, 7, 1, RoundedRender.lerp(EchoTheme.CARD_BORDER, EchoTheme.CARD_BORDER_HOT, hoverProgress));

		int tx = x + 10 + e.depth * 8 - 8;
		boolean lit = d.enabled() || d.isGroup() || d.isSlider();
		int textC = lit ? EchoTheme.TEXT : EchoTheme.MUTED;

		if (d.isSlider()) {
			Gfx.textAtSize(g, font, d.title(), tx, ry + 5, 6.6f, EchoTheme.TEXT, false);
			String v = d.valueText();
			Gfx.textAtSize(g, font, v, x + rw - 10 - Gfx.widthAtSize(font, v, 6.2f), ry + 5, 6.2f, EchoTheme.ACCENT_TEXT, false);
			int bx = x + 10;
			int bw = rw - 20;
			Gfx.fill(g, bx, ry + 19, bw, 4, 2, EchoTheme.CARD_BORDER);
			Anim fill = sliderFills.computeIfAbsent(e, key -> new Anim((float) d.ratio(), 18f));
			float ratio = fill.to((float) d.ratio()).get();
			int fw = Math.max(0, Math.round(bw * ratio));
			if (fw > 0) {
				Gfx.fill(g, bx, ry + 19, fw, 4, 2, EchoTheme.ACCENT_A, EchoTheme.ACCENT_B);
			}
			float knobX = bx + (bw - 1) * ratio;
			Gfx.glow(g, knobX - 3f, ry + 18f, 6f, 6f, 3f,
					com.pvpvisuals.gui.rival.RoundedRender.scaleAlpha(EchoTheme.NEON, 0.24f), 2f);
			Gfx.circle(g, knobX, ry + 21, 3.2f, EchoTheme.KNOB);
		} else {
			Gfx.textAtSize(g, font, d.title(), tx, ry + (rh - 7) / 2, 6.6f, textC, false);
			int right = x + rw - 8;
			if (d.isChoice()) {
				String value = d.choiceValue();
				int pw = 132;
				int px = right - pw;
				Gfx.fill(g, px, ry + 4, pw, rh - 8, 5, EchoTheme.PILL);
				Gfx.ring(g, px, ry + 4, pw, rh - 8, 5, 1, dropdown == e ? EchoTheme.ACCENT_TEXT : EchoTheme.PILL_BORDER);
				Gfx.textAtSize(g, font, value, px + 7, ry + (rh - 7) / 2, 6.2f, EchoTheme.TEXT, false);
				EchoIcons.chevron(g, right - 11, ry + (rh - 8) / 2 + 1, dropdown == e ? 3 : 1, EchoTheme.MUTED);
			} else if (e.hasKids()) {
				EchoIcons.chevron(g, right - 7, ry + (rh - 4) / 2 - 1, 2, e.expanded ? EchoTheme.ACCENT_TEXT : EchoTheme.DIM);
				right -= 16;
			}
			if (d.isToggle()) {
				ToggleSwitch sw = switches.computeIfAbsent(e, k -> new ToggleSwitch());
				sw.update(d.enabled(), dtMs);
				sw.render(g, right - 20, ry + (rh - 10) / 2, 20, 10);
			} else if (!d.isGroup() && !d.isChoice()) {
				EchoIcons.chevron(g, right - 4, ry + (rh - 8) / 2, 1, hover ? EchoTheme.ACCENT_TEXT : EchoTheme.DIM);
			}
		}
		Gfx.alpha = prev;
	}


	private int dropdownRowY() {
		if (dropdown == null) return y;
		int ry = scroll.y - scroll.offset();
		for (ModuleEntry e : rows) {
			if (e == dropdown) return ry;
			ry += rowHeight(e) + ROW_GAP;
		}
		return y;
	}

	private void renderDropdown(GuiGraphics g, Font font, int mx, int my, boolean allowHover) {
		if (dropdown == null || !dropdown.def.isChoice()) return;
		List<String> values = dropdown.def.choices();
		int rowY = dropdownRowY();
		int itemH = 14;
		int boxW = 132;
		int boxX = x + rowW() - 8 - boxW;
		int boxH = values.size() * itemH + 4;
		int boxY = rowY + ROW_H + 3;
		if (boxY + boxH > scroll.y + scroll.h) boxY = rowY - boxH - 3;
		boxY = Math.max(y + HEAD_H + 6, boxY);
		Gfx.fill(g, boxX, boxY, boxW, boxH, 7, 0xF01B1E28);
		Gfx.ring(g, boxX, boxY, boxW, boxH, 7, 1, EchoTheme.CARD_BORDER_HOT);
		for (int i = 0; i < values.size(); i++) {
			String value = values.get(i);
			int iy = boxY + 2 + i * itemH;
			boolean selected = value.equals(dropdown.def.choiceValue());
			boolean hover = allowHover && Gfx.hit(mx, my, boxX + 2, iy, boxW - 4, itemH);
			if (selected || hover) Gfx.fill(g, boxX + 2, iy, boxW - 4, itemH, 4,
					selected ? com.pvpvisuals.gui.rival.RoundedRender.scaleAlpha(EchoTheme.NEON, 0.16f) : 0x18FFFFFF);
			if (selected) Gfx.circle(g, boxX + 9, iy + itemH / 2f, 1.6f, EchoTheme.ACCENT_TEXT);
			Gfx.textAtSize(g, font, value, boxX + 16, iy + 3, 6.2f, selected ? EchoTheme.ACCENT_TEXT : EchoTheme.TEXT, false);
		}
	}

	private void renderTextRow(GuiGraphics g, Font font, ModuleEntry e, int ry, int rh, int rw, boolean hover) {
		boolean edit = e == editing;
		Gfx.fill(g, x, ry, rw, rh, 7, hover || edit ? EchoTheme.CARD_HOVER : EchoTheme.CARD);
		Gfx.ring(g, x, ry, rw, rh, 7, 1, edit || hover ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER);
		Gfx.textAtSize(g, font, e.def.title(), x + 10 + e.depth * 8 - 8, ry + (rh - 7) / 2, 6.6f, EchoTheme.TEXT, false);
		int fx = x + (int) (rw * 0.36f);
		int fw = rw - (fx - x) - 6;
		Gfx.fill(g, fx, ry + 3, fw, rh - 6, 5, EchoTheme.CARD);
		Gfx.ring(g, fx, ry + 3, fw, rh - 6, 5, 1, edit ? EchoTheme.ACCENT_TEXT : EchoTheme.CARD_BORDER);
		String s = edit ? buf.toString() : e.def.text();
		boolean caret = edit && (System.currentTimeMillis() / 500) % 2 == 0;
		String shown = caret ? s + "_" : s;
		// длинный текст показываем с конца, чтобы был виден ввод
		while (shown.length() > 1 && Gfx.widthAtSize(font, shown, 6.2f) > fw - 10) {
			shown = shown.substring(1);
		}
		if (s.isEmpty() && !edit) {
			Gfx.textAtSize(g, font, "пусто", fx + 5, ry + (rh - 7) / 2, 6.2f, EchoTheme.DIM, false);
		} else {
			Gfx.textAtSize(g, font, shown, fx + 5, ry + (rh - 7) / 2, 6.2f, EchoTheme.TEXT, false);
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
			commit();
		}
		if (dropdown != null) {
			List<String> values = dropdown.def.choices();
			int rowY = dropdownRowY();
			int itemH = 14;
			int boxW = 132;
			int boxX = x + rowW() - 8 - boxW;
			int boxH = values.size() * itemH + 4;
			int boxY = rowY + ROW_H + 3;
			if (boxY + boxH > scroll.y + scroll.h) boxY = rowY - boxH - 3;
			boxY = Math.max(y + HEAD_H + 6, boxY);
			if (Gfx.hit(mx, my, boxX, boxY, boxW, boxH)) {
				int index = (int) ((my - boxY - 2) / itemH);
				if (index >= 0 && index < values.size()) dropdown.def.setChoice(values.get(index));
				dropdown = null;
				com.pvpvisuals.config.PvpConfig.get().save();
				return Result.CHANGED;
			}
			dropdown = null;
		}
		if (!inline && Gfx.hit(mx, my, x, y + 6, 22, 22)) {
			return Result.BACK;
		}
		// шапка: тумблер и бинд
		if (!inline && my >= y && my < y + HEAD_H) {
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
				if (d.isChoice()) {
					if (button == 1) {
						List<String> values = d.choices();
						if (!values.isEmpty()) {
							int current = values.indexOf(d.choiceValue());
							d.setChoice(values.get((current + 1) % values.size()));
							com.pvpvisuals.config.PvpConfig.get().save();
							return Result.CHANGED;
						}
					}
					dropdown = dropdown == e ? null : e;
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
