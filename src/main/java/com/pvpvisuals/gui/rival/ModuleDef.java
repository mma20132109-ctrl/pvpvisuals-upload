package com.pvpvisuals.gui.rival;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import com.pvpvisuals.keybind.KeybindManager;

/**
 * Описание строки меню. Виды:
 * toggle — on/off; action — кнопка; cycle — кнопка с динамической подписью;
 * group — строка-«выпадашка», которая только раскрывает дочерние строки;
 * slider — ползунок min..max.
 */
public final class ModuleDef {
	public final String name;
	public final Category category;
	public final List<ModuleDef> children = new ArrayList<>();

	private final BooleanSupplier getter;
	private final Consumer<Boolean> setter;
	private final Runnable action;
	private boolean notifyOnToggle = true;
	private Supplier<String> label;
	private BooleanSupplier visible;
	private Supplier<String> textGet;
	private Consumer<String> textSet;
	private List<String> choices;
	private Supplier<String> choiceGet;
	private Consumer<String> choiceSet;

	private DoubleSupplier sliderGet;
	private DoubleConsumer sliderSet;
	private double min;
	private double max;
	private int decimals;

	private ModuleDef(String name, Category category, BooleanSupplier getter, Consumer<Boolean> setter, Runnable action) {
		this.name = name;
		this.category = category;
		this.getter = getter;
		this.setter = setter;
		this.action = action;
	}

	public static ModuleDef toggle(String name, Category category, BooleanSupplier getter, Consumer<Boolean> setter) {
		return new ModuleDef(name, category, getter, setter, null);
	}

	public static ModuleDef action(String name, Category category, Runnable action) {
		return new ModuleDef(name, category, null, null, action);
	}

	/** Кнопка с динамической подписью; после клика конфиг сохраняется. */
	public static ModuleDef cycle(Category category, Supplier<String> label, Runnable action) {
		ModuleDef d = new ModuleDef(label.get(), category, null, null, action);
		d.label = label;
		return d;
	}

	/** Раскрывающаяся строка (выпадающий список): сама ничего не делает, клик раскрывает детей. */
	public static ModuleDef group(Category category, Supplier<String> label) {
		ModuleDef d = new ModuleDef(label.get(), category, null, null, null);
		d.label = label;
		return d;
	}

	public static ModuleDef slider(String name, Category category, double min, double max, int decimals,
			DoubleSupplier get, DoubleConsumer set) {
		ModuleDef d = new ModuleDef(name, category, null, null, null);
		d.sliderGet = get;
		d.sliderSet = set;
		d.min = min;
		d.max = max;
		d.decimals = decimals;
		return d;
	}

	/** Строка показывается в подменю только при выполнении условия (например, настройки конкретного стиля). */
	public ModuleDef visibleWhen(BooleanSupplier v) {
		this.visible = v;
		return this;
	}

	public boolean isVisible() {
		return visible == null || visible.getAsBoolean();
	}

	/** Выбор значения из раскрывающегося списка в стиле Svintus. */
	public static ModuleDef choice(String name, Category category, Supplier<String> get, List<String> values, Consumer<String> set) {
		ModuleDef d = new ModuleDef(name, category, null, null, null);
		d.choiceGet = get;
		d.choiceSet = set;
		d.choices = List.copyOf(values);
		return d;
	}

	public boolean isChoice() { return choices != null && !choices.isEmpty(); }
	public List<String> choices() { return choices == null ? List.of() : choices; }
	public String choiceValue() { return choiceGet == null ? "" : choiceGet.get(); }
	public void setChoice(String value) { if (choiceSet != null && choices != null && choices.contains(value)) choiceSet.accept(value); }

	/** Строка с однострочным текстовым полем (ввод обрабатывает SettingsView). */
	public static ModuleDef text(String name, Category category, Supplier<String> get, Consumer<String> set) {
		ModuleDef d = new ModuleDef(name, category, null, null, null);
		d.textGet = get;
		d.textSet = set;
		return d;
	}

	public boolean isText() {
		return textGet != null;
	}

	public String text() {
		return textGet.get();
	}

	public void setText(String s) {
		textSet.accept(s);
	}

	public ModuleDef child(ModuleDef c) {
		c.notifyOnToggle = false;
		children.add(c);
		return this;
	}

	public String title() {
		return label != null ? label.get() : name;
	}

	public boolean isToggle() {
		return getter != null;
	}

	public boolean isSlider() {
		return sliderGet != null;
	}

	public boolean isGroup() {
		return getter == null && action == null && sliderGet == null && textGet == null && !isChoice();
	}

	public boolean enabled() {
		return getter != null && getter.getAsBoolean();
	}

	/** Для переключателей: приводит состояние к want (через activate, чтобы сработали побочные действия). */
	public void setEnabled(boolean want) {
		if (getter != null && getter.getAsBoolean() != want) {
			activate();
		}
	}

	public double ratio() {
		double v = sliderGet.getAsDouble();
		return Math.max(0, Math.min(1, (v - min) / (max - min)));
	}

	public void setRatio(double r) {
		r = Math.max(0, Math.min(1, r));
		double v = min + r * (max - min);
		double p = Math.pow(10, decimals);
		sliderSet.accept(Math.round(v * p) / p);
	}

	public String valueText() {
		return String.format(java.util.Locale.ROOT, "%." + decimals + "f", sliderGet.getAsDouble());
	}

	/** @return true, если состояние изменилось и конфиг нужно сохранить. */
	public boolean activate() {
		if (setter != null) {
			boolean enabled = !getter.getAsBoolean();
			setter.accept(enabled);
			if (notifyOnToggle && category != Category.CLIENT) {
				KeybindManager.notifyModuleToggle(name, enabled);
			}
			return true;
		}
		if (action != null) {
			action.run();
			return label != null;
		}
		return false;
	}
}
