package com.pvpvisuals.gui.menu;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pvpvisuals.PvpVisualsClient;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.render.splash.EchoSplash;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;

/**
 * Главное меню Echo Visual. Наследует TitleScreen, чтобы остальной код (и другие моды) по-прежнему видели «главное
 * меню» (instanceof TitleScreen), но init/render/ввод полностью переопределены: ванильных виджетов, панорамы и
 * логотипа Mojang здесь нет. Подмена происходит в {@link com.pvpvisuals.mixin.MinecraftScreenMixin}.
 */
public class EchoMainMenuScreen extends TitleScreen {
	private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/menu");
	private static boolean failed;
	private static boolean logged;

	private final List<EchoButton> main = new ArrayList<>();
	private EchoButton btnExit;
	private EchoButton btnLang;
	private EchoButton btnVisuals;
	private EchoButton pressed;
	private EchoButton pending;
	private long pendingAt;

	private long startMs = -1;
	private long lastMs;

	public EchoMainMenuScreen() {
		super();
	}

	/** Можно ли подменять ванильное меню (настройка включена и наше меню не падало). */
	public static boolean enabled() {
		return !failed && PvpConfig.get().menu.customMainMenu;
	}

	@Override
	protected void init() {
		// super.init() не вызываем: он создал бы ванильные кнопки и Realms
		main.clear();
		Minecraft mc = Minecraft.getInstance();
		main.add(new EchoButton("Singleplayer", EchoButton.Icon.SINGLEPLAYER,
				() -> mc.setScreen(new SelectWorldScreen(this))));
		main.add(new EchoButton("Multiplayer", EchoButton.Icon.MULTIPLAYER,
				() -> mc.setScreen(new JoinMultiplayerScreen(this))));
		main.add(new EchoButton("Accounts", EchoButton.Icon.ACCOUNTS,
				() -> mc.setScreen(new EchoAccountManagerScreen(this))));
		main.add(new EchoButton("Settings", EchoButton.Icon.SETTINGS,
				() -> mc.setScreen(new OptionsScreen(this, mc.options))));
		btnExit = new EchoButton("Exit", EchoButton.Icon.EXIT, mc::stop);
		btnLang = new EchoButton("Language", EchoButton.Icon.GLOBE,
				() -> mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager())));
		btnVisuals = new EchoButton("Background", EchoButton.Icon.VISUALS, this::cycleBackground);
		layout();
	}

	private int logoSize() {
		return Math.max(36, Math.min(100, Math.round(this.height * 0.19f)));
	}

	private float logoCy() {
		return this.height * 0.27f;
	}

	private void layout() {
		// Centered navigation dock with responsive width for smaller GUI scales.
		int bh = 36;
		int gap = this.width < 720 ? 5 : 9;
		int available = Math.max(220, this.width - 52);
		int bw = Math.max(76, Math.min(156,
				(available - (main.size() - 1) * gap) / Math.max(1, main.size())));
		int total = main.size() * bw + (main.size() - 1) * gap;
		int left = (this.width - total) / 2;
		int top = Math.max(10, this.height - 68);
		for (int i = 0; i < main.size(); i++) {
			main.get(i).setBounds(left + i * (bw + gap), top, bw, bh);
		}

		int utility = 26;
		int utilityY = this.height - utility - 12;
		btnVisuals.setBounds(12, utilityY, utility, utility);
		btnLang.setBounds(this.width - utility * 2 - 18, utilityY, utility, utility);
		btnExit.setBounds(this.width - utility - 12, utilityY, utility, utility);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// ванильную панораму/размытие не рисуем
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float prevAlpha = Gfx.alpha;
		try {
			draw(g, mouseX, mouseY);
		} catch (Throwable e) {
			failed = true;
			LOG.error("Echo main menu failed and was disabled, switching to the vanilla title screen", e);
			Minecraft.getInstance().setScreen(new TitleScreen());
		} finally {
			Gfx.alpha = prevAlpha;
		}
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	private void draw(GuiGraphics g, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		long now = Util.getMillis();
		// анимация входа стартует, когда экран загрузки уже ушёл (под ним меню не «проигрывается» зря)
		if (mc.getOverlay() != null) {
			startMs = -1;
		} else if (startMs < 0) {
			startMs = now;
			lastMs = now;
		}
		float dt = Math.min(0.1f, Math.max(0f, (now - (lastMs == 0 ? now : lastMs)) / 1000f));
		lastMs = now;
		float el = startMs < 0 ? 0f : (now - startMs); // мс с начала анимации
		if (!logged) {
			logged = true;
			LOG.info("Echo main menu is drawing");
		}
		EchoTheme.sync();
		PvpConfig cfg = PvpConfig.get();
		float t = now / 1000f;

		Gfx.alpha = 1f;
		EchoSplash.drawBackground(g, this.width, this.height, t, cfg.splash.splashBackground);
		if (cfg.menu.showMenuParticles) {
			EchoSplash.drawParticles(g, this.width / 2f, logoCy(), logoSize(), t);
		}

		// логотип: fade-in + выезд сверху (700 мс, ease-out-cubic)
		float lp = Gfx.ease(el / 700f);
		Gfx.alpha = lp;
		float slide = -28f * (1f - lp);
		EchoSplash.drawLogo(g, this.width / 2f, logoCy() + slide, logoSize(), t, 1f);
		Gfx.alpha = 1f;

		// Compact active-player card, inspired by the source menu's account header.
		String playerName = mc.getUser().getName();
		String playerLabel = mc.getUser().getAccessToken().isBlank() ? "OFFLINE PROFILE" : "PLAYER";
		int nameWidth = this.font.width(playerName);
		int labelWidth = this.font.width(playerLabel);
		float profileW = Math.max(112f, nameWidth + labelWidth + 38f);
		float profileX = this.width - profileW - 14f;
		float profileY = 14f;
		float profileAlpha = Gfx.ease(el / 550f);
		Gfx.alpha = profileAlpha;
		Gfx.glass(g, profileX, profileY, profileW, 34f, 10f,
				0xA91B1E2B, 0x9410121C, 0x55FFFFFF);
		Gfx.circle(g, profileX + 17f, profileY + 17f, 5f, EchoTheme.shift(0xFF9B5CFF));
		Gfx.text(g, this.font, playerLabel, Math.round(profileX + 29f), Math.round(profileY + 6f), 0xFF9B8ABF);
		Gfx.text(g, this.font, playerName, Math.round(profileX + 29f), Math.round(profileY + 18f), 0xFFFFFFFF);
		Gfx.alpha = 1f;

		// Frosted-glass dock backing, matching the source menu's bottom navigation.
		float dockAlpha = Gfx.ease((el - 280f) / 450f);
		if (dockAlpha > 0.003f && !main.isEmpty()) {
			EchoButton first = main.get(0);
			EchoButton last = main.get(main.size() - 1);
			Gfx.alpha = dockAlpha;
			Gfx.glass(g, first.x - 12f, first.y - 8f,
					last.x + last.w - first.x + 24f, first.h + 16f, 15f,
					0xC91C222E, 0xB90B101B, 0x66FFFFFF);
			Gfx.rrect(g, first.x + 4f, first.y - 5f,
					last.x + last.w - first.x - 8f, 1f, 1f,
					0x30FFFFFF, 0x00FFFFFF, 0, 0f);
			Gfx.alpha = 1f;
		}
		// Dock buttons appear sequentially with a short upward motion.
		for (int i = 0; i < main.size(); i++) {
			EchoButton b = main.get(i);
			float p = Gfx.ease((el - 280f - i * 130f) / 450f);
			update(b, mx, my, dt, p);
			b.render(g, this.font, p, 14f * (1f - p));
		}
		float pc = Gfx.ease((el - 280f - main.size() * 130f) / 450f);
		update(btnExit, mx, my, dt, pc);
		update(btnLang, mx, my, dt, pc);
		update(btnVisuals, mx, my, dt, pc);
		btnExit.render(g, this.font, pc, 0f);
		btnLang.render(g, this.font, pc, 0f);
		btnVisuals.render(g, this.font, pc, 0f);

		if (cfg.menu.showVersionText) {
			String v = FabricLoader.getInstance().getModContainer(PvpVisualsClient.MOD_ID)
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
			String text = v.isEmpty() ? "ECHO VISUAL" : "ECHO VISUAL  /  " + v;
			Gfx.alpha = pc;
			Gfx.text(g, this.font, text, 46, this.height - 17, 0xFF8E98AE);
			Gfx.alpha = 1f;
		}
	}

	private void cycleBackground() {
		PvpConfig cfg = PvpConfig.get();
		String current = cfg.splash.splashBackground;
		cfg.splash.splashBackground = "GRADIENT".equalsIgnoreCase(current) ? "SHADER"
				: "SHADER".equalsIgnoreCase(current) ? "DARK" : "GRADIENT";
		cfg.markDirtyQuiet();
		cfg.save();
	}

	private void update(EchoButton b, int mx, int my, float dt, float appear) {
		b.update(dt, appear > 0.6f && b.contains(mx, my));
	}

	@Override
	public void tick() {
		// super.tick() не нужен (только Realms-уведомления); здесь выполняем отложенное действие кнопки,
		// чтобы «проседание» при нажатии успело отрисоваться
		if (pending != null && Util.getMillis() - pendingAt >= 110) {
			EchoButton b = pending;
			pending = null;
			b.action.run();
		}
	}

	private List<EchoButton> all() {
		List<EchoButton> l = new ArrayList<>(main);
		l.add(btnLang);
		l.add(btnVisuals);
		l.add(btnExit);
		return l;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && pending == null) {
			for (EchoButton b : all()) {
				if (b != null && b.contains(event.x(), event.y())) {
					b.down = true;
					pressed = b;
					return true;
				}
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		EchoButton b = pressed;
		pressed = null;
		if (b != null) {
			b.down = false;
			if (event.button() == 0 && b.contains(event.x(), event.y())) {
				pending = b;
				pendingAt = Util.getMillis();
			}
		}
		return true;
	}
}
