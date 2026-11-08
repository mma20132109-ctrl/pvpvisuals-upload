package org.zenith.client.screens.override.main.page;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.util.Util;
import org.zenith.Animation;
import org.zenith.Easing;
import org.zenith.RenderState;
import org.zenith.Zenith;
import org.zenith.ZenithColor;
import org.zenith.base.font.Font;
import org.zenith.client.screens.override.main.MenuIcon;
import org.zenith.client.screens.override.main.MenuIcons;
import org.zenith.client.screens.override.main.MenuSurface;
import org.zenith.client.screens.override.main.MenuTheme;
import org.zenith.client.screens.override.main.component.SkinPreview;
import org.zenith.client.screens.override.main.widget.MenuButton;
import org.zenith.client.screens.override.main.widget.MenuTextField;
import org.zenith.client.screens.override.main.widget.MenuWidget;
import org.zenith.utility.api.MicrosoftAuth;
import org.zenith.utility.misc.Account;
import org.zenith.utility.misc.Accounts;
import org.zenith.utility.render.display.base.BoundsData;
import org.zenith.utility.render.display.base.MenuDrawContext;
import org.zenith.utility.render.display.base.ProxyDotRadiusData;

public final class AccountManagerPage implements MainMenuPage {
   private static final float WIDTH = MenuTheme.px(800.0F);
   private static final float CARD_WIDTH = MenuTheme.px(180.0F);
   private static final float CARD_HEIGHT = MenuTheme.px(240.0F);
   private static final float CARD_SPACING = MenuTheme.px(130.0F);
   private static final float PANEL_RADIUS = 11.0F;
   private static final float SKIN_WINDOW = MenuTheme.px(70.0F);
   private static final float SKIN_HEIGHT = MenuTheme.px(130.0F);

   private static final DateTimeFormatter ADDED_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());
   private static final List<String> NAME_PARTS = List.of(
      "Void", "Nova", "Echo", "Frost", "Ember", "Lunar", "Cyber", "Neon",
      "Shadow", "Prism", "Riot", "Drift", "Pulse", "Zen", "Flux", "Onyx",
      "Vertex", "Quartz", "Raven", "Solar"
   );

   // Bottom input controls: username field + compact expanding icon buttons
   private final MenuTextField usernameField = new MenuTextField(MenuIcons.USER, MenuTheme.t("menu.accounts.username"));
   private final ExpandingIconButton addButton = new ExpandingIconButton(MenuIcons.ADD, "Добавить", MenuButton.Style.ACCENT);
   private final ExpandingIconButton microsoftButton = new ExpandingIconButton(MenuIcons.LAUNCH, "Microsoft", MenuButton.Style.GHOST);
   private final ExpandingIconButton randomButton = new ExpandingIconButton(MenuIcons.RANDOM, "Случайный", MenuButton.Style.GHOST);
   private final ExpandingIconButton clearButton = new ExpandingIconButton(MenuIcons.CLEAR, "Очистить", MenuButton.Style.DANGER);

   // Central action buttons for the focused account
   private final MenuButton selectButton = MenuButton.accent(MenuIcons.ACCEPT, "Выбрать");
   private final MenuButton removeButton = new MenuButton(MenuIcons.REMOVE, "Удалить", MenuButton.Style.DANGER);
   private final MenuButton cancelLoginButton = MenuButton.ghost(MenuIcons.CLEAR, MenuTheme.t("menu.accounts.cancel"));

   private final Map<String, PlayerSkinWidget> skinWidgets = new HashMap<>();
   private final Map<Integer, Animation> cardHovers = new HashMap<>();
   private final Map<Integer, Long> cardClickTimes = new HashMap<>();

   // Cover flow carousel state
   private float scrollPos = 0.0F;
   private int targetIndex = 0;
   private boolean initialized = false;

   // Dragging state
   private boolean isDragging = false;
   private double dragStartX = 0.0;
   private float dragStartPos = 0.0F;

   // Hit detection bounds for cards
   private final Map<Integer, BoundsData> renderedCardBounds = new HashMap<>();
   private BoundsData carouselViewport = new BoundsData(0.0F, 0.0F, 0.0F, 0.0F);

   // Microsoft auth state
   private MicrosoftAuth.Data deviceCode;
   private BoundsData deviceCodeBounds = new BoundsData(0.0F, 0.0F, 0.0F, 0.0F);
   private boolean loginRunning;
   private volatile int loginGeneration;

   public AccountManagerPage() {
      this.addButton.onClick(this::addOffline);
      this.microsoftButton.onClick(this::startMicrosoftLogin);
      this.randomButton.onClick(() -> this.usernameField.setValue(randomName()));
      this.clearButton.onClick(this.usernameField::clear);
      this.cancelLoginButton.onClick(this::cancelLogin);
      this.usernameField.setOnEnter(this::addOffline);
      this.usernameField.setMaxLength(16);

      this.selectButton.onClick(() -> {
         List<Account> list = this.manager().method4751();
         int idx = this.getActiveIndex(list.size());
         if (idx >= 0 && idx < list.size()) {
            this.manager().select(idx);
            this.manager().method4757("Выбран аккаунт: " + list.get(idx).getName());
         }
      });

      this.removeButton.onClick(() -> {
         List<Account> list = this.manager().method4751();
         int idx = this.getActiveIndex(list.size());
         if (idx >= 0 && idx < list.size()) {
            this.manager().method4754(idx);
            int newSize = this.manager().method4751().size();
            this.targetIndex = Math.max(0, Math.min(newSize - 1, this.targetIndex));
         }
      });
   }

   private Accounts manager() {
      return Zenith.getInstance().getAccounts();
   }

   private PlayerSkinWidget skinWidget(Account account) {
      String key = account.getName() + "#" + account.getType();
      return this.skinWidgets.computeIfAbsent(key, k -> {
         String name = account.getName();
         UUID uuid = account.method5472();
         PlayerSkinWidget widget = new PlayerSkinWidget(
            Math.round(SKIN_WINDOW),
            Math.round(SKIN_HEIGHT),
            MinecraftClient.getInstance().getLoadedEntityModels(),
            () -> SkinPreview.textures(name, uuid)
         );
         widget.xRotation = 0.0F;
         widget.yRotation = 0.0F;
         return widget;
      });
   }

   private int getActiveIndex(int totalCount) {
      if (totalCount <= 0) return 0;
      return Math.max(0, Math.min(totalCount - 1, Math.round(this.scrollPos)));
   }

   @Override
   public float contentWidth() {
      return WIDTH;
   }

   @Override
   public float contentHeight() {
      return CARD_HEIGHT + MenuTheme.px(10.0F) + MenuButton.HEIGHT + MenuTheme.px(12.0F) + MenuButton.HEIGHT + MenuTheme.px(18.0F);
   }

   private void addOffline() {
      String name = this.usernameField.getValue().trim();
      if (!name.isEmpty()) {
         this.manager().method4753(Account.method5470(name));
         this.manager().method4757(MenuTheme.t("menu.accounts.added") + " " + name);
         this.usernameField.clear();
         this.usernameField.setFocused(false);
         int newCount = this.manager().method4751().size();
         this.targetIndex = newCount - 1;
      }
   }

   private static String randomName() {
      ThreadLocalRandom rand = ThreadLocalRandom.current();
      return NAME_PARTS.get(rand.nextInt(NAME_PARTS.size()))
         + NAME_PARTS.get(rand.nextInt(NAME_PARTS.size())).toLowerCase(Locale.ROOT)
         + rand.nextInt(10, 100);
   }

   private void startMicrosoftLogin() {
      if (!this.loginRunning) {
         this.loginRunning = true;
         int gen = ++this.loginGeneration;
         Accounts mgr = this.manager();
         mgr.method4757(MenuTheme.t("menu.accounts.contacting"));
         MicrosoftAuth.method3056().whenComplete((data, err) -> MinecraftClient.getInstance().execute(() -> {
            if (gen == this.loginGeneration) {
               if (err == null && data != null) {
                  this.deviceCode = data;
                  mgr.method4757(MenuTheme.t("menu.accounts.waiting"));
                  MinecraftClient.getInstance().keyboard.setClipboard(data.getText());
                  Util.getOperatingSystem().open(data.getText2());
                  this.awaitLogin(data, gen);
               } else {
                  this.loginRunning = false;
                  mgr.method4757(MenuTheme.t("menu.accounts.loginFailed") + " " + Accounts.rootMessage(err));
               }
            }
         }));
      }
   }

   private void awaitLogin(MicrosoftAuth.Data data, int gen) {
      Accounts mgr = this.manager();
      MicrosoftAuth.method3058(data, msg -> MinecraftClient.getInstance().execute(() -> {
         if (gen == this.loginGeneration) {
            mgr.method4757(msg);
         }
      }), () -> gen != this.loginGeneration).whenComplete((res, err) -> MinecraftClient.getInstance().execute(() -> {
         if (gen == this.loginGeneration) {
            this.loginRunning = false;
            this.deviceCode = null;
            if (err == null && res != null) {
               mgr.method4753(Account.method5471(res.name(), res.uuid(), res.getText2(), res.getText3()));
               mgr.method4757(MenuTheme.t("menu.accounts.added") + " " + res.name());
               int newCount = this.manager().method4751().size();
               this.targetIndex = newCount - 1;
            } else {
               mgr.method4757(MenuTheme.t("menu.accounts.loginFailed") + " " + Accounts.rootMessage(err));
            }
         }
      }));
   }

   private void cancelLogin() {
      this.loginGeneration++;
      this.deviceCode = null;
      this.loginRunning = false;
      this.manager().method4757("Microsoft login cancelled");
   }

   @Override
   public void layout(BoundsData bounds) {
      float centerX = bounds.getFloatValue() + bounds.width() / 2.0F;
      float cardTopY = bounds.getFloatValue2() + MenuTheme.px(6.0F);

      this.carouselViewport = new BoundsData(
         bounds.getFloatValue(),
         cardTopY,
         bounds.width(),
         CARD_HEIGHT + MenuTheme.px(6.0F)
      );

      // Central action buttons below cards
      float actionY = cardTopY + CARD_HEIGHT + MenuTheme.px(10.0F);
      float actionH = MenuButton.HEIGHT;
      float btnSelectW = MenuTheme.px(140.0F);
      float btnRemoveW = MenuTheme.px(100.0F);
      float btnGap = MenuTheme.px(8.0F);
      float totalActionW = btnSelectW + btnGap + btnRemoveW;
      float actionX = centerX - totalActionW / 2.0F;

      this.selectButton.setBounds(actionX, actionY, btnSelectW, actionH);
      this.removeButton.setBounds(actionX + btnSelectW + btnGap, actionY, btnRemoveW, actionH);

      // Bottom input controls row
      float controlsY = actionY + actionH + MenuTheme.px(12.0F);
      float fieldW = MenuTheme.px(200.0F);
      float gap = MenuTheme.px(6.0F);

      float totalControlsW = fieldW + MenuTheme.px(8.0F)
         + this.addButton.currentWidth() + gap
         + this.microsoftButton.currentWidth() + gap
         + this.randomButton.currentWidth() + gap
         + this.clearButton.currentWidth();
      float controlsX = centerX - totalControlsW / 2.0F;

      this.usernameField.setBounds(controlsX, controlsY, fieldW, MenuButton.HEIGHT);
      controlsX += fieldW + MenuTheme.px(8.0F);

      this.addButton.setBounds(controlsX, controlsY, this.addButton.currentWidth(), MenuButton.HEIGHT);
      controlsX += this.addButton.currentWidth() + gap;

      this.microsoftButton.setBounds(controlsX, controlsY, this.microsoftButton.currentWidth(), MenuButton.HEIGHT);
      controlsX += this.microsoftButton.currentWidth() + gap;

      this.randomButton.setBounds(controlsX, controlsY, this.randomButton.currentWidth(), MenuButton.HEIGHT);
      controlsX += this.randomButton.currentWidth() + gap;

      this.clearButton.setBounds(controlsX, controlsY, this.clearButton.currentWidth(), MenuButton.HEIGHT);

      if (this.deviceCode != null) {
         float modalW = MenuTheme.px(360.0F);
         float modalH = MenuTheme.px(160.0F);
         this.deviceCodeBounds = new BoundsData(
            bounds.getFloatValue() + (bounds.width() - modalW) / 2.0F,
            bounds.getFloatValue2() + (CARD_HEIGHT - modalH) / 2.0F,
            modalW,
            modalH
         );
         float cancelW = this.cancelLoginButton.preferredWidth();
         this.cancelLoginButton.setBounds(
            this.deviceCodeBounds.getFloatValue() + (modalW - cancelW) / 2.0F,
            this.deviceCodeBounds.getFloatValue2() + modalH - MenuTheme.px(16.0F) - MenuButton.HEIGHT,
            cancelW,
            MenuButton.HEIGHT
         );
      }
   }

   @Override
   public void render(MenuDrawContext var1, float mouseX, float mouseY, BoundsData bounds) {
      List<Account> accounts = this.manager().method4751();

      if (!this.initialized && !accounts.isEmpty()) {
         int sel = this.manager().getIntValue();
         if (sel >= 0 && sel < accounts.size()) {
            this.targetIndex = sel;
            this.scrollPos = sel;
         }
         this.initialized = true;
      }

      // Smooth carousel animation
      if (!this.isDragging) {
         this.scrollPos += (this.targetIndex - this.scrollPos) * 0.18F;
         if (Math.abs(this.targetIndex - this.scrollPos) < 0.001F) {
            this.scrollPos = this.targetIndex;
         }
      }

      float centerX = bounds.getFloatValue() + bounds.width() / 2.0F;
      float cardTopY = bounds.getFloatValue2() + MenuTheme.px(6.0F);
      float cardCenterY = cardTopY + CARD_HEIGHT / 2.0F;

      this.renderedCardBounds.clear();

      if (accounts.isEmpty()) {
         this.renderEmptyState(var1, centerX, cardCenterY);
      } else {
         this.renderCardStack(var1, mouseX, mouseY, accounts, centerX, cardCenterY);

         // Render central action buttons for the focused account
         int activeIdx = this.getActiveIndex(accounts.size());
         boolean isCurSelected = (activeIdx == this.manager().getIntValue());

         this.selectButton.setLabel(isCurSelected ? "Выбран" : "Выбрать");
         this.selectButton.setIcon(isCurSelected ? MenuIcons.ACCEPT : MenuIcons.USER);
         this.selectButton.setStyle(isCurSelected ? MenuButton.Style.ACCENT : MenuButton.Style.GHOST);

         this.selectButton.render(var1, mouseX, mouseY);
         this.removeButton.render(var1, mouseX, mouseY);
      }

      // Dynamically reposition and render expanding bottom controls
      float actionY = cardTopY + CARD_HEIGHT + MenuTheme.px(10.0F);
      float controlsY = actionY + MenuButton.HEIGHT + MenuTheme.px(12.0F);
      float fieldW = MenuTheme.px(200.0F);
      float gap = MenuTheme.px(6.0F);

      float totalControlsW = fieldW + MenuTheme.px(8.0F)
         + this.addButton.currentWidth() + gap
         + this.microsoftButton.currentWidth() + gap
         + this.randomButton.currentWidth() + gap
         + this.clearButton.currentWidth();
      float controlsX = centerX - totalControlsW / 2.0F;

      this.usernameField.setBounds(controlsX, controlsY, fieldW, MenuButton.HEIGHT);
      controlsX += fieldW + MenuTheme.px(8.0F);

      this.addButton.setBounds(controlsX, controlsY, this.addButton.currentWidth(), MenuButton.HEIGHT);
      controlsX += this.addButton.currentWidth() + gap;

      this.microsoftButton.setBounds(controlsX, controlsY, this.microsoftButton.currentWidth(), MenuButton.HEIGHT);
      controlsX += this.microsoftButton.currentWidth() + gap;

      this.randomButton.setBounds(controlsX, controlsY, this.randomButton.currentWidth(), MenuButton.HEIGHT);
      controlsX += this.randomButton.currentWidth() + gap;

      this.clearButton.setBounds(controlsX, controlsY, this.clearButton.currentWidth(), MenuButton.HEIGHT);

      this.usernameField.render(var1, mouseX, mouseY);
      this.addButton.render(var1, mouseX, mouseY);
      this.microsoftButton.render(var1, mouseX, mouseY);
      this.randomButton.render(var1, mouseX, mouseY);
      this.clearButton.render(var1, mouseX, mouseY);

      this.renderStatus(var1, bounds);

      if (this.deviceCode != null) {
         this.renderDeviceCode(var1, mouseX, mouseY);
      }
   }

   private void renderEmptyState(MenuDrawContext var1, float cx, float cy) {
      float w = MenuTheme.px(240.0F);
      float h = MenuTheme.px(150.0F);
      float x = cx - w / 2.0F;
      float y = cy - h / 2.0F;
      ProxyDotRadiusData radius = ProxyDotRadiusData.method8069(PANEL_RADIUS);

      MenuSurface.blur(var1, x, y, w, h, radius, 1.0F);
      var1.drawRoundedRect(x, y, w, h, radius, MenuTheme.hudBackground());
      var1.drawRoundedBorder(x, y, w, h, 1.0F, radius, MenuTheme.borderSubtle());

      Font titleFont = MenuTheme.medium(12.0F);
      Font subFont = MenuTheme.regular(9.5F);
      String title = "Нет добавленных аккаунтов";
      String sub = "Введите ник снизу и нажмите 'Добавить'";

      var1.drawText(titleFont, title, cx - titleFont.width(title) / 2.0F, cy - 12.0F, MenuTheme.textSecondary());
      var1.drawText(subFont, sub, cx - subFont.width(sub) / 2.0F, cy + 8.0F, MenuTheme.textTertiary());
   }

   private void renderCardStack(MenuDrawContext var1, float mouseX, float mouseY, List<Account> accounts, float centerX, float cardCenterY) {
      // Include nearby and depth cards so the stack is clearly visible
      List<Integer> drawOrder = new ArrayList<>();
      for (int i = 0; i < accounts.size(); i++) {
         float dist = (float) i - this.scrollPos;
         if (Math.abs(dist) <= 2.8F) {
            drawOrder.add(i);
         }
      }

      // Sort: farthest cards drawn first (background), closest cards drawn last (foreground)
      drawOrder.sort((a, b) -> Float.compare(
         Math.abs(b - this.scrollPos),
         Math.abs(a - this.scrollPos)
      ));

      // Determine hovered card from front to back
      int hoveredIndex = -1;
      for (int k = drawOrder.size() - 1; k >= 0; k--) {
         int idx = drawOrder.get(k);
         float dist = (float) idx - this.scrollPos;
         float absDist = Math.abs(dist);
         float scale = Math.max(0.68F, 1.0F - 0.16F * absDist);
         float cardW = CARD_WIDTH * scale;
         float cardH = CARD_HEIGHT * scale;

         float spacingOffset = (absDist <= 1.0F)
            ? absDist * CARD_SPACING
            : CARD_SPACING + (absDist - 1.0F) * (CARD_SPACING * 0.70F);
         float cx = centerX + Math.signum(dist) * spacingOffset;
         float cy = cardCenterY + absDist * 3.5F;
         float cardX = cx - cardW / 2.0F;
         float cardY = cy - cardH / 2.0F;

         if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
            hoveredIndex = idx;
            break;
         }
      }

      int selectedIdx = this.manager().getIntValue();

      for (int idx : drawOrder) {
         Account acc = accounts.get(idx);
         float dist = (float) idx - this.scrollPos;
         float absDist = Math.abs(dist);

         // Cards smoothly fade out between distance 1.5 and 2.6
         float fade = (absDist <= 1.5F) ? 1.0F : Math.clamp(1.0F - (absDist - 1.5F) / 1.1F, 0.0F, 1.0F);
         if (fade <= 0.01F) continue;

         float scale = Math.max(0.68F, 1.0F - 0.16F * absDist);
         float cardW = CARD_WIDTH * scale;
         float cardH = CARD_HEIGHT * scale;

         float spacingOffset = (absDist <= 1.0F)
            ? absDist * CARD_SPACING
            : CARD_SPACING + (absDist - 1.0F) * (CARD_SPACING * 0.70F);
         float cx = centerX + Math.signum(dist) * spacingOffset;
         float cy = cardCenterY + absDist * 3.5F;
         float cardX = cx - cardW / 2.0F;
         float cardY = cy - cardH / 2.0F;

         BoundsData cardBounds = new BoundsData(cardX, cardY, cardW, cardH);
         this.renderedCardBounds.put(idx, cardBounds);

         boolean isCardHovered = (idx == hoveredIndex);
         Animation hAnim = this.cardHovers.computeIfAbsent(idx, k -> new Animation(180L, 0.0F, Easing.INSTANCE12));
         hAnim.method2807(isCardHovered);
         float hVal = hAnim.getValue();

         boolean isSelected = (idx == selectedIdx);

         this.drawSingleCard(var1, acc, idx, cardX, cardY, cardW, cardH, scale, isSelected, hVal, fade, absDist);
      }
   }

   private void drawSingleCard(
      MenuDrawContext var1,
      Account account,
      int index,
      float x,
      float y,
      float w,
      float h,
      float scale,
      boolean isSelected,
      float hoverVal,
      float fade,
      float absDist
   ) {
      // Tactile click compression animation when card is clicked
      long clickElapsed = System.currentTimeMillis() - this.cardClickTimes.getOrDefault(index, 0L);
      float clickScale = 1.0F;
      if (clickElapsed < 160L) {
         float t = (float)clickElapsed / 160.0F;
         clickScale = 1.0F - (float)Math.sin(t * Math.PI) * 0.04F;
      }

      boolean hasClickScale = clickScale < 0.999F;
      if (hasClickScale) {
         float ccx = x + w / 2.0F;
         float ccy = y + h / 2.0F;
         var1.getMatrices().pushMatrix();
         var1.getMatrices().translate(ccx, ccy);
         var1.getMatrices().scale(clickScale, clickScale);
         var1.getMatrices().translate(-ccx, -ccy);
      }

      ProxyDotRadiusData radius = ProxyDotRadiusData.method8069(PANEL_RADIUS * scale);

      // Smooth darkening for distant cards: center card is crisp glass, cards further back smoothly deepen into darker shades
      float dim = Math.clamp(absDist / 2.0F, 0.0F, 0.85F);
      ZenithColor baseBg = MenuTheme.hudBackground();
      ZenithColor darkGlass = new ZenithColor(6, 8, 12, 245);
      ZenithColor blendedBg = baseBg.method7295(darkGlass, dim);

      ZenithColor cardFill;
      if (isSelected) {
         // Only front selected card has accent tint; distant selected cards darken into dark glass
         float accTint = 0.12F * (1.0F - dim);
         cardFill = blendedBg.method7295(MenuTheme.accent(), accTint).method7294(fade);
      } else {
         cardFill = blendedBg.method7294(fade);
      }

      // Frosted glass background blur
      MenuSurface.blur(var1, x, y, w, h, radius, fade * (1.0F - 0.50F * dim));

      if (isSelected) {
         // Glow only on or very near the front card; distant cards never emit bright halos
         if (dim < 0.35F) {
            float glowAlpha = 0.40F * (1.0F - dim / 0.35F) * fade;
            MenuSurface.glow(var1, x, y, w, h, PANEL_RADIUS * scale, MenuTheme.accent(), glowAlpha);
         }
         var1.drawRoundedRect(x, y, w, h, radius, cardFill);
         // Accent border dims down into dark slate as it gets farther away
         ZenithColor accBorder = MenuTheme.accent().method7295(new ZenithColor(20, 24, 32), dim * 0.75F);
         var1.drawRoundedBorder(x, y, w, h, 1.2F, radius, accBorder.method7294(fade * (1.0F - dim * 0.45F)));
      } else {
         if (hoverVal > 0.01F && dim < 0.35F) {
            float glowAlpha = 0.22F * hoverVal * (1.0F - dim / 0.35F) * fade;
            MenuSurface.glow(var1, x, y, w, h, PANEL_RADIUS * scale, MenuTheme.accent(), glowAlpha);
         }
         var1.drawRoundedRect(x, y, w, h, radius, cardFill);
         ZenithColor border = (hoverVal > 0.01F)
            ? MenuTheme.accent().method7294(hoverVal * fade * (1.0F - dim * 0.50F))
            : MenuTheme.borderSubtle().method7295(new ZenithColor(180, 190, 215, 12), dim).method7294((0.50F - 0.35F * dim) * fade);
         var1.drawRoundedBorder(x, y, w, h, 1.0F, radius, border);
      }

      // Skin dimensions proportionally scaled so it fits comfortably inside the card
      PlayerSkinWidget widget = this.skinWidget(account);
      float curSkinW = SKIN_WINDOW * scale;
      float curSkinH = SKIN_HEIGHT * scale;
      float skinX = x + (w - curSkinW) / 2.0F;
      float skinY = y + MenuTheme.px(8.0F) * scale;

      widget.setPosition(Math.round(skinX), Math.round(skinY));
      widget.setDimensions(Math.round(curSkinW), Math.round(curSkinH));
      widget.xRotation = 0.0F;
      widget.yRotation = 0.0F;

      int lookX = Math.round(skinX + curSkinW / 2.0F);
      int lookY = Math.round(skinY + curSkinH * 0.35F);

      // Smooth skin scale on hover (+15%)
      float skinHoverScale = 1.0F + 0.15F * hoverVal;
      float skinCenterX = skinX + curSkinW / 2.0F;
      float skinCenterY = skinY + curSkinH / 2.0F;

      // Scissor strictly to card rectangle to prevent any model protrusion
      var1.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));

      var1.getMatrices().pushMatrix();
      var1.getMatrices().translate(skinCenterX, skinCenterY);
      var1.getMatrices().scale(skinHoverScale, skinHoverScale);
      var1.getMatrices().translate(-skinCenterX, -skinCenterY);

      // Apply shader color dimming to the skin model so distant skins smoothly darken and fade with their card
      float skinBrightness = Math.clamp(1.0F - absDist * 0.35F, 0.22F, 1.0F);
      float skinAlpha = fade;
      RenderState.method1883(skinBrightness, skinBrightness, skinBrightness, skinAlpha);

      widget.render(var1, lookX, lookY, 0.0F);

      RenderState.method1884();

      var1.getMatrices().popMatrix();

      var1.disableScissor();

      // Account Name with smooth dimming in depth
      float textDim = Math.clamp(1.0F - absDist * 0.35F, 0.25F, 1.0F);
      Font nameFont = MenuTheme.medium(10.5F * scale);
      String name = account.getName();
      float nameW = nameFont.width(name);
      float nameX = x + (w - nameW) / 2.0F;
      float nameY = y + h - MenuTheme.px(36.0F) * scale;
      ZenithColor nameColor;
      if (isSelected && dim < 0.35F) {
         nameColor = MenuTheme.accent().method7294(fade * textDim);
      } else {
         nameColor = ZenithColor.INSTANCE.method7294(fade * textDim);
      }
      var1.drawText(nameFont, name, nameX, nameY, nameColor);

      // Subtitle (Offline / Microsoft) with smooth dimming in depth
      String type = (account.getType() == Account.Type.MICROSOFT) ? "Microsoft" : "Оффлайн";
      Font subFont = MenuTheme.regular(8.5F * scale);
      float subW = subFont.width(type);
      float subX = x + (w - subW) / 2.0F;
      float subY = nameY + MenuTheme.px(14.0F) * scale;
      var1.drawText(subFont, type, subX, subY, MenuTheme.textSecondary().method7294(fade * textDim * 0.85F));

      if (hasClickScale) {
         var1.getMatrices().popMatrix();
      }
   }

   private void renderStatus(MenuDrawContext var1, BoundsData bounds) {
      String status = this.manager().getStatus();
      if (status != null && !status.isEmpty()) {
         Font font = MenuTheme.regular(9.5F);
         float textY = bounds.getFloatValue2() + this.contentHeight() - 6.0F;
         var1.drawText(
            font,
            status,
            bounds.getFloatValue() + (bounds.width() - font.width(status)) / 2.0F,
            textY,
            MenuTheme.textTertiary()
         );
      }
   }

   private void renderDeviceCode(MenuDrawContext var1, float mouseX, float mouseY) {
      BoundsData bounds = this.deviceCodeBounds;
      ProxyDotRadiusData radius = ProxyDotRadiusData.method8069(PANEL_RADIUS);
      var1.drawRoundedRect(
         bounds.getFloatValue(), bounds.getFloatValue2(), bounds.width(), bounds.height(), radius, MenuTheme.surface().method7295(ZenithColor.INSTANCE2, 0.85F)
      );
      var1.drawRoundedBorder(bounds.getFloatValue(), bounds.getFloatValue2(), bounds.width(), bounds.height(), -0.1F, radius, MenuTheme.borderSubtle());
      Font semibold14 = MenuTheme.semibold(14.0F);
      Font semibold20 = MenuTheme.semibold(20.0F);
      Font regular10 = MenuTheme.regular(10.0F);
      String title = "Sign in to Microsoft";
      var1.drawText(semibold14, title, bounds.getFloatValue() + (bounds.width() - semibold14.width(title)) / 2.0F, bounds.getFloatValue2() + MenuTheme.px(16.0F), MenuTheme.text());
      String code = this.deviceCode.getText();
      var1.drawText(semibold20, code, bounds.getFloatValue() + (bounds.width() - semibold20.width(code)) / 2.0F, bounds.getFloatValue2() + MenuTheme.px(46.0F), MenuTheme.accent());
      String sub = "Code copied - enter it at " + this.deviceCode.getText2();
      var1.drawText(regular10, sub, bounds.getFloatValue() + (bounds.width() - regular10.width(sub)) / 2.0F, bounds.getFloatValue2() + MenuTheme.px(82.0F), MenuTheme.textSecondary());
      this.cancelLoginButton.render(var1, mouseX, mouseY);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.deviceCode != null) {
         if (this.cancelLoginButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
         }
         if (this.deviceCodeBounds.contains(mouseX, mouseY)) {
            MinecraftClient.getInstance().keyboard.setClipboard(this.deviceCode.getText());
            return true;
         }
         return true;
      }

      if (this.usernameField.mouseClicked(mouseX, mouseY, button)) {
         return true;
      }

      if (this.addButton.mouseClicked(mouseX, mouseY, button)
         || this.microsoftButton.mouseClicked(mouseX, mouseY, button)
         || this.randomButton.mouseClicked(mouseX, mouseY, button)
         || this.clearButton.mouseClicked(mouseX, mouseY, button)) {
         return true;
      }

      List<Account> accounts = this.manager().method4751();
      if (!accounts.isEmpty()) {
         if (this.selectButton.mouseClicked(mouseX, mouseY, button)
            || this.removeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
         }
      }

      if (button == 0 && this.carouselViewport.contains(mouseX, mouseY)) {
         List<Integer> keys = new ArrayList<>(this.renderedCardBounds.keySet());
         keys.sort((a, b) -> Float.compare(Math.abs(a - this.scrollPos), Math.abs(b - this.scrollPos)));

         for (int idx : keys) {
            BoundsData b = this.renderedCardBounds.get(idx);
            if (b != null && b.contains(mouseX, mouseY)) {
               this.cardClickTimes.put(idx, System.currentTimeMillis());
               int active = this.getActiveIndex(accounts.size());
               if (idx == active) {
                  this.manager().select(idx);
                  this.manager().method4757("Выбран аккаунт: " + accounts.get(idx).getName());
               } else {
                  this.targetIndex = idx;
               }
               return true;
            }
         }

         this.isDragging = true;
         this.dragStartX = mouseX;
         this.dragStartPos = this.scrollPos;
         return true;
      }

      return false;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (this.isDragging && button == 0) {
         List<Account> accounts = this.manager().method4751();
         if (accounts.size() > 1) {
            float dx = (float) (mouseX - this.dragStartX);
            this.scrollPos = this.dragStartPos - dx / CARD_SPACING;
            this.scrollPos = Math.max(-0.4F, Math.min(accounts.size() - 0.6F, this.scrollPos));
            this.targetIndex = Math.max(0, Math.min(accounts.size() - 1, Math.round(this.scrollPos)));
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.isDragging && button == 0) {
         this.isDragging = false;
         List<Account> accounts = this.manager().method4751();
         if (!accounts.isEmpty()) {
            this.targetIndex = Math.max(0, Math.min(accounts.size() - 1, Math.round(this.scrollPos)));
         }
         return true;
      }
      return false;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      List<Account> accounts = this.manager().method4751();
      if (accounts.isEmpty()) {
         return false;
      }

      if (amount < 0.0) {
         this.targetIndex = Math.min(accounts.size() - 1, this.targetIndex + 1);
      } else if (amount > 0.0) {
         this.targetIndex = Math.max(0, this.targetIndex - 1);
      }
      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.usernameField.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      return this.usernameField.charTyped(chr, modifiers);
   }

   /**
    * Compact icon-only button that smoothly expands in width on hover to show its text label.
    */
   public static class ExpandingIconButton extends MenuWidget {
      private final MenuIcon icon;
      private final String label;
      private final MenuButton.Style style;
      private final Animation hoverAnim = new Animation(180L, 0.0F, Easing.INSTANCE12);
      private Runnable action = () -> {};
      private long lastClickTime = 0L;

      public ExpandingIconButton(MenuIcon icon, String label, MenuButton.Style style) {
         this.icon = icon;
         this.label = label;
         this.style = style;
      }

      public ExpandingIconButton onClick(Runnable action) {
         this.action = action;
         return this;
      }

      public float currentWidth() {
         float h = this.hoverAnim.getValue();
         float baseW = this.height;
         if (this.label == null || this.label.isEmpty()) return baseW;
         Font font = MenuTheme.medium(11.0F);
         float extraW = font.width(this.label) + MenuTheme.px(18.0F);
         return baseW + extraW * h;
      }

      @Override
      public void draw(MenuDrawContext var1, float mouseX, float mouseY) {
         long elapsed = System.currentTimeMillis() - this.lastClickTime;
         float clickScale = 1.0F;
         if (elapsed < 160L) {
            float t = (float)elapsed / 160.0F;
            clickScale = 1.0F - (float)Math.sin(t * Math.PI) * 0.08F;
         }

         float w = this.currentWidth();
         boolean hasClickScale = clickScale < 0.999F;
         if (hasClickScale) {
            float cx = this.x + w / 2.0F;
            float cy = this.y + this.height / 2.0F;
            var1.getMatrices().pushMatrix();
            var1.getMatrices().translate(cx, cy);
            var1.getMatrices().scale(clickScale, clickScale);
            var1.getMatrices().translate(-cx, -cy);
         }

         boolean isHov = this.enabled && this.contains(mouseX, mouseY);
         this.hoverAnim.method2807(isHov);
         float h = this.hoverAnim.getValue();

         ProxyDotRadiusData radius = ProxyDotRadiusData.method8069(11.0F);

         ZenithColor bg;
         ZenithColor textCol;
         switch (this.style) {
            case ACCENT:
               bg = MenuTheme.accent().method7303(h * 0.12F);
               textCol = MenuTheme.onAccent();
               break;
            case DANGER:
               bg = MenuTheme.danger().method7303(h * 0.12F);
               textCol = MenuTheme.onAccent();
               break;
            default:
               bg = MenuTheme.hudBackground().method7295(MenuTheme.track(), h);
               textCol = MenuTheme.text();
               break;
         }

         MenuSurface.blur(var1, this.x, this.y, w, this.height, radius, 1.0F);
         var1.drawRoundedRect(this.x, this.y, w, this.height, radius, bg);
         if (h > 0.01F) {
            ZenithColor border = (this.style == MenuButton.Style.DANGER) ? MenuTheme.danger() : MenuTheme.accent();
            var1.drawRoundedBorder(this.x, this.y, w, this.height, 1.0F, radius, border.method7294(h));
         }

         Font iconFont = this.icon.font(11.5F);
         float iconW = this.icon.width(11.5F);
         float iconH = iconFont.height();
         float iconX = this.x + (this.height - iconW) / 2.0F;
         float iconY = this.y + (this.height - iconH) / 2.0F;

         // Smooth icon enlargement on hover (+20%)
         if (h > 0.01F) {
            float iconScale = 1.0F + 0.20F * h;
            float icx = iconX + iconW / 2.0F;
            float icy = iconY + iconH / 2.0F;
            var1.getMatrices().pushMatrix();
            var1.getMatrices().translate(icx, icy);
            var1.getMatrices().scale(iconScale, iconScale);
            var1.getMatrices().translate(-icx, -icy);
            var1.drawText(iconFont, this.icon.glyph(), iconX, iconY, textCol);
            var1.getMatrices().popMatrix();
         } else {
            var1.drawText(iconFont, this.icon.glyph(), iconX, iconY, textCol);
         }

         // Smooth text appearance when expanding
         if (h > 0.1F && this.label != null && !this.label.isEmpty()) {
            Font font = MenuTheme.medium(11.0F);
            float textX = this.x + this.height + MenuTheme.px(2.0F);
            float textY = this.y + (this.height - font.height()) / 2.0F;
            var1.enableScissor(this.x + 2.0F, this.y, this.x + w - 2.0F, this.y + this.height);
            var1.drawText(font, this.label, textX, textY, textCol.method7294(h));
            var1.disableScissor();
         }

         if (hasClickScale) {
            var1.getMatrices().popMatrix();
         }
      }

      @Override
      public boolean mouseClicked(double mouseX, double mouseY, int button) {
         if (this.visible && this.enabled && button == 0 && this.contains(mouseX, mouseY)) {
            this.lastClickTime = System.currentTimeMillis();
            this.action.run();
            return true;
         }
         return false;
      }

      @Override
      public boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.x && mouseX <= this.x + this.currentWidth() && mouseY >= this.y && mouseY <= this.y + this.height;
      }
   }
}
