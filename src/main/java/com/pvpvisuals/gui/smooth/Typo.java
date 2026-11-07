package com.pvpvisuals.gui.smooth;

import com.pvpvisuals.config.PvpConfig;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Шрифт Inter для меню. Ванильный шрифт — пиксельная bitmap-текстура; TrueType-провайдер растеризует Inter
 * с oversample 8, поэтому текст остаётся гладким при любом масштабе GUI.
 *
 * <p>Шрифт подключается через стиль компонента: {@code assets/pvpvisuals/font/inter.json} (провайдер "ttf",
 * файл {@code inter.ttf} рядом). Выключается в конфиге: {@code gui.smoothFont = false}.
 *
 * <p>ПРОВЕРИТЬ при компиляции: в 1.21.11 {@code Style#withFont} принимает {@code FontDescription}
 * ({@code new FontDescription.Resource(Identifier)}). Если у вас другая сигнатура, менять нужно только строку STYLE.
 */
public final class Typo {
	private Typo() { }

	private static final Identifier FONT_ID = Identifier.fromNamespaceAndPath("pvpvisuals", "inter");
	private static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT_ID));

	/** null — ещё не проверяли; true — шрифт не загрузился (все глифы одинаковой ширины), используем ванильный. */
	private static Boolean broken;

	public static boolean on() {
		return PvpConfig.get().gui.smoothFont && !Boolean.TRUE.equals(broken);
	}

	/**
	 * Самопроверка: если шрифт Inter не загрузился, Minecraft рисует «квадраты» одинаковой ширины для любого символа.
	 * Сравниваем ширину «i» и «W»: у настоящего шрифта они разные. При поломке автоматически включается ванильный шрифт.
	 */
	private static void verify(Font font) {
		if (broken != null) {
			return;
		}
		try {
			int wi = font.width(Component.literal("i").withStyle(STYLE));
			int ww = font.width(Component.literal("W").withStyle(STYLE));
			broken = wi == ww;
		} catch (Throwable t) {
			broken = true;
		}
		if (broken) {
			org.slf4j.LoggerFactory.getLogger("pvpvisuals").warn("Inter font did not load (check assets/pvpvisuals/font/inter.json); using vanilla font");
		}
	}

	public static void check(Font font) {
		verify(font);
	}

	/** Строка со шрифтом Inter (или обычная строка, если шрифт выключен). */
	public static Component of(String s) {
		MutableComponent c = Component.literal(s);
		return on() ? c.withStyle(STYLE) : c;
	}

	/** Оборачивает готовый компонент (стиль шрифта наследуется детьми, жирный и цвета сохраняются). */
	public static Component of(Component c) {
		return on() ? Component.empty().withStyle(STYLE).append(c) : c;
	}

	public static int width(Font font, String s) {
		verify(font);
		return font.width(of(s));
	}

	public static int width(Font font, Component c) {
		verify(font);
		return font.width(of(c));
	}
}
