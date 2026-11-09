package com.pvpvisuals.gui.echo;

import com.pvpvisuals.gui.echo.EchoIcons.Glyph;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleEntry;
import com.pvpvisuals.gui.rival.RoundedRender;
import com.pvpvisuals.keybind.KeybindManager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Компактная карточка модуля (высота {@link #H}): название и описание в одну строку слева, справа — плашка бинда
 * (если назначен), шеврон настроек и тумблер. Включённый модуль подсвечивается акцентной заливкой и обводкой.
 * Позиция x/y — в координатах содержимого (без прокрутки).
 */
public final class ModuleCard {
	public enum Zone { NONE, TOGGLE, BIND, MORE, BODY }

	public static final int H = 32;
	private static final int PAD = 10;
	private static final int R = 9;

	public final ModuleDef def;
	public final ModuleEntry root;
	public final boolean bindable;
	private final Glyph glyph;
	private final String desc;
	private final String descLower;

	public int x;
	public int y;
	public int w;

	/** Название клавиши (уже в верхнем регистре) или null, если бинда нет. Заполняет экран. */
	public String bindText;

	private final ToggleSwitch sw = new ToggleSwitch();
	private float hov;
	private float on;
	private boolean hovered;

	private String cacheKey = "";
	private Component titleC = Component.empty();
	private String descShown = "";
	private int chipW;

	public ModuleCard(ModuleDef def) {
		this.def = def;
		this.root = new ModuleEntry(def, 0);
		this.bindable = def.isToggle() && KeybindManager.isBindable(def.name);
		this.glyph = ModuleInfo.glyph(def.name, def.category);
		this.desc = ModuleInfo.desc(def.name);
		this.descLower = desc.toLowerCase(java.util.Locale.ROOT);
		this.on = def.isToggle() && def.enabled() ? 1f : 0f;
	}

	public String description() {
		return desc;
	}

	public Glyph glyph() {
		return glyph;
	}

	public boolean hasSettings() {
		return !def.children.isEmpty();
	}

	private boolean opensSettings() {
		return hasSettings() || def.isGroup();
	}

	public boolean matches(String q) {
		return q.isEmpty() || root.subtreeMatches(q) || descLower.contains(q);
	}

	// ---------- геометрия правой части ----------

	private int rightButtonW() {
		return def.isToggle() ? ToggleSwitch.W : 34;
	}

	private int switchX() {
		return x + w - PAD - rightButtonW();
	}

	private int chevronX() {
		return switchX() - 16;
	}

	private int chipX() {
		return (opensSettings() ? chevronX() - 4 : switchX() - 4) - chipW;
	}

	private boolean showChip() {
		return bindable && (bindText != null || def.name.equals(KeybindManager.listeningModule()));
	}

	/** Пересчитывает усечение названия и описания (только при смене ширины/бинда/текста). */
	public void refreshText(Font font) {
		String chip = showChip() ? (bindText == null ? "…" : bindText) : "";
		String key = w + "|" + chip + "|" + def.title();
		if (key.equals(cacheKey)) {
			return;
		}
		cacheKey = key;
		chipW = chip.isEmpty() ? 0 : Math.min(60, Gfx.width(font, chip) + 10);
		int reserve = PAD + rightButtonW() + (opensSettings() ? 18 : 6) + (chipW > 0 ? chipW + 6 : 0);
		int avail = w - PAD - reserve;
		titleC = Component.literal(trim(font, def.title(), avail, true)).withStyle(ChatFormatting.BOLD);
		descShown = trim(font, desc, avail, false);
	}

	private static String trim(Font font, String s, int maxW, boolean bold) {
		Component c = bold ? Component.literal(s).withStyle(ChatFormatting.BOLD) : Component.literal(s);
		if (Gfx.width(font, c) <= maxW) {
			return s;
		}
		String shown = s;
		while (shown.length() > 1) {
			shown = shown.substring(0, shown.length() - 1);
			Component t = bold ? Component.literal(shown + "…").withStyle(ChatFormatting.BOLD) : Component.literal(shown + "…");
			if (Gfx.width(font, t) <= maxW) {
				break;
			}
		}
		return shown + "…";
	}

	// ---------- состояние ----------

	public void setHover(boolean h) {
		this.hovered = h;
	}

	public void update(float dtMs) {
		float k = Math.min(1f, dtMs / 80f);
		hov += ((hovered ? 1f : 0f) - hov) * k;
		float target = def.isToggle() ? (def.enabled() ? 1f : 0f) : 0f;
		on += (target - on) * Math.min(1f, dtMs / 90f);
		sw.update(def.enabled(), dtMs);
	}

	// ---------- зоны для кликов (sy — экранная координата верха карточки) ----------

	public Zone zoneAt(double mx, double my, int sy) {
		if (!Gfx.hit(mx, my, x, sy, w, H)) {
			return Zone.NONE;
		}
		if (showChip() && Gfx.hit(mx, my, chipX(), sy + (H - 12) / 2, chipW, 12)) {
			return Zone.BIND;
		}
		if (opensSettings() && Gfx.hit(mx, my, chevronX() - 3, sy + 4, 16, H - 8)) {
			return Zone.MORE;
		}
		if (def.isToggle() && ToggleSwitch.hit(mx, my, switchX(), sy + (H - ToggleSwitch.H) / 2, ToggleSwitch.W,
				ToggleSwitch.H)) {
			return Zone.TOGGLE;
		}
		return Zone.BODY;
	}

	// ---------- отрисовка ----------

	public void render(GuiGraphics g, Font font, int sy) {
		refreshText(font);
		int base = RoundedRender.lerp(EchoTheme.CARD, EchoTheme.CARD_HOVER, hov);
		int border = RoundedRender.lerp(EchoTheme.CARD_BORDER, EchoTheme.CARD_BORDER_HOT, hov * 0.75f);
		if (on > 0.02f) {
			border = RoundedRender.lerp(border, RoundedRender.scaleAlpha(EchoTheme.NEON, 0.88f), on);
		}
		LiquidGlassRenderer.surface(g, x, sy, w, H, R, base, border);
		if (on > 0.02f) {
			// акцентная подсветка включённого модуля: заливка сверху вниз
			Gfx.rrect(g, x, sy, w, H, R, RoundedRender.scaleAlpha(EchoTheme.NEON, 0.16f * on),
					RoundedRender.scaleAlpha(EchoTheme.NEON, 0.03f * on), 0, 0f);
		}

		int nameC = RoundedRender.lerp(EchoTheme.MUTED, EchoTheme.TEXT, Math.max(on, Math.max(hov * 0.6f, def.isToggle() ? 0f : 0.8f)));
		Gfx.text(g, font, titleC, x + PAD, sy + 6, nameC, false);
		Gfx.text(g, font, descShown, x + PAD, sy + 18, RoundedRender.lerp(EchoTheme.DIM, EchoTheme.MUTED, Math.max(on, hov * 0.5f)));

		if (showChip()) {
			boolean listening = def.name.equals(KeybindManager.listeningModule());
			String label = listening ? ((Util.getMillis() / 400) % 2 == 0 ? "…" : "") : bindText;
			int cx = chipX();
			LiquidGlassRenderer.surface(g, cx, sy + (H - 12) / 2f, chipW, 12, 4f, EchoTheme.PILL,
					listening ? EchoTheme.NEON : EchoTheme.PILL_BORDER);
			String shown = label;
			while (shown.length() > 1 && Gfx.width(font, shown) > chipW - 6) {
				shown = shown.substring(0, shown.length() - 1);
			}
			Gfx.text(g, font, shown, cx + (chipW - Gfx.width(font, shown)) / 2, sy + (H - 8) / 2, EchoTheme.MUTED);
		}

		if (opensSettings()) {
			int cc = RoundedRender.lerp(EchoTheme.DIM, EchoTheme.ACCENT_TEXT, Math.max(hov, on * 0.6f));
			EchoIcons.chevron(g, chevronX() + 2, sy + (H - 8) / 2 + 1, 1, cc);
		}

		if (def.isToggle()) {
			sw.render(g, switchX(), sy + (H - ToggleSwitch.H) / 2, ToggleSwitch.W, ToggleSwitch.H);
		} else {
			String label = def.isGroup() ? "Open" : "Run";
			int bw = rightButtonW();
			int bx = switchX();
			LiquidGlassRenderer.surface(g, bx, sy + (H - 14) / 2f, bw, 14, 5f, EchoTheme.TAB_TOP,
					EchoTheme.ACCENT_TEXT);
			Gfx.text(g, font, label, bx + (bw - Gfx.width(font, label)) / 2, sy + (H - 8) / 2, EchoTheme.TEXT);
		}
	}
}
