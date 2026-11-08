package org.zenith.client.screens.override.main;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.zenith.Animation;
import org.zenith.Easing;
import org.zenith.MatrixUtils;
import org.zenith.MinecraftAccess;
import org.zenith.Zenith;
import org.zenith.ZenithColor;
import org.zenith.base.font.Font;
import org.zenith.base.font.SvgIcon;
import org.zenith.client.screens.override.main.page.AccountManagerPage;
import org.zenith.client.screens.override.main.page.MainMenuPage;
import org.zenith.utility.render.RenderShaders;
import org.zenith.utility.render.display.base.BoundsData;
import org.zenith.utility.render.display.base.MenuDrawContext;
import org.zenith.utility.render.display.base.ProxyDotRadiusData;

public class MainMenuScreen extends Screen implements MinecraftAccess {
   public static final int HOME_TAB = -1;
   public static final int ACCOUNTS_TAB = 0;
   private static final Identifier SKY_BG = Zenith.method1501("menu/sky.png");
   private static final Identifier SADNES_BG = Zenith.method1501("menu/sadnes.png");

   // 8 Backgrounds (Sadnes Landscape + Sky Landscape + 6 Procedural GPU Shaders)
   public static final String[] SHADER_NAMES = new String[]{
      "Sadnes пейзаж",
      "Небесный пейзаж",
      "Космическая туманность",
      "Полярное сияние",
      "Киберпанк неон",
      "Закатный мираж",
      "Тёмный обсидиан",
      "Цвета темы"
   };

   private static final ZenithColor[] NEON_PALETTE = new ZenithColor[]{
      new ZenithColor(255, 45, 135),
      new ZenithColor(0, 235, 255),
      new ZenithColor(135, 30, 245),
      new ZenithColor(25, 10, 55)
   };
   private static final ZenithColor[] SUNSET_PALETTE = new ZenithColor[]{
      new ZenithColor(255, 160, 40),
      new ZenithColor(235, 60, 50),
      new ZenithColor(120, 25, 95),
      new ZenithColor(28, 14, 40)
   };
   private static final ZenithColor[] OBSIDIAN_PALETTE = new ZenithColor[]{
      new ZenithColor(22, 28, 42),
      new ZenithColor(14, 18, 26),
      new ZenithColor(38, 46, 68),
      new ZenithColor(10, 12, 16)
   };

   private int currentShaderIndex = 0;
   private long shaderToastStartTime = 0L;
   private float currentParallaxX = 0.0F;
   private float currentParallaxY = 0.0F;

   private final MainMenuBlurRenderer backdrop = new MainMenuBlurRenderer();
   private final List<MainMenuPage> pages = new ArrayList<>();
   private final List<DockItem> dockItems = new ArrayList<>();
   private final List<SidebarItem> sidebarItems = new ArrayList<>();

   private final Animation pageTransition = new Animation(260L, 1.0F, Easing.INSTANCE7);
   private final Animation openTransition = new Animation(380L, 0.0F, Easing.INSTANCE7);

   private int activeIndex = HOME_TAB;
   private float fit = 1.0F;

   public MainMenuScreen() {
      super(Text.literal("Zenith"));

      // Account Manager subpage
      this.pages.add(new AccountManagerPage()); // 0

      // 4 Main Navigation Buttons
      this.dockItems.add(new DockItem("Одиночная", SvgIcon.SINGLEPLAYER, -1, false, false, () -> {
         mc.setScreen(new SelectWorldScreen(this));
      }));
      this.dockItems.add(new DockItem("Сетевая игра", SvgIcon.MULTIPLAYER, -1, false, false, () -> {
         mc.setScreen(new MultiplayerScreen(this));
      }));
      this.dockItems.add(new DockItem("Аккаунты", SvgIcon.ACCOUNTS, ACCOUNTS_TAB, false, false, () -> {
         this.togglePage(ACCOUNTS_TAB);
      }));
      this.dockItems.add(new DockItem("Моды", SvgIcon.CONFIGS, -1, false, false, () -> {
         if (Zenith.getInstance().getNLMenuScreen() != null) {
            mc.setScreen(Zenith.getInstance().getNLMenuScreen());
         }
      }));

      // 2 Utility Action Buttons (Settings & Exit)
      this.dockItems.add(new DockItem(null, SvgIcon.SETTINGS, -1, true, false, () -> {
         mc.setScreen(new OptionsScreen(this, mc.options));
      }));
      this.dockItems.add(new DockItem(null, SvgIcon.EXIT, -1, true, true, () -> mc.scheduleStop()));

      // Left Floating Sidebar (Shader & Theme Switcher)
      this.sidebarItems.add(new SidebarItem(SvgIcon.VISUALS, () -> {
         this.currentShaderIndex = (this.currentShaderIndex + 1) % SHADER_NAMES.length;
         this.shaderToastStartTime = System.currentTimeMillis();
      }));
   }

   public void togglePage(int index) {
      if (this.activeIndex == index) {
         this.select(HOME_TAB);
      } else {
         this.select(index);
      }
   }

   public void select(int index) {
      if (index != this.activeIndex) {
         if (this.activeIndex >= 0 && this.activeIndex < this.pages.size()) {
            this.pages.get(this.activeIndex).onHide();
         }
         this.activeIndex = index;
         if (this.activeIndex >= 0 && this.activeIndex < this.pages.size()) {
            this.pages.get(this.activeIndex).onShow();
         }
         this.pageTransition.method2809(0.0F);
      }
   }

   private MainMenuPage page() {
      if (this.activeIndex >= 0 && this.activeIndex < this.pages.size()) {
         return this.pages.get(this.activeIndex);
      }
      return null;
   }

   @Override
   public void init() {
      this.openTransition.method2809(0.0F);
      this.pageTransition.method2809(0.0F);
      super.init();
   }

   @Override
   public void onDisplayed() {
      super.onDisplayed();
      this.openTransition.method2809(0.0F);
      this.pageTransition.method2809(0.0F);
      MainMenuPage cur = this.page();
      if (cur != null) {
         cur.onShow();
      }
   }

   @Override
   public void tick() {
      MainMenuPage cur = this.page();
      if (cur != null) {
         cur.tick();
      }
      super.tick();
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      MenuDrawContext var5 = MenuDrawContext.method8080(context, mouseX, mouseY, deltaTicks);
      Window win = mc.getWindow();
      float screenW = win.getScaledWidth();
      float screenH = win.getScaledHeight();

      // Smooth mouse parallax interpolation
      this.updateParallax((float)mouseX, (float)mouseY, screenW, screenH, deltaTicks);

      // 1. Procedural Animated GPU Shader Layer (Full-screen native GPU shader, crisp at any aspect ratio)
      this.renderBackgroundShader(var5, screenW, screenH);

      // 2. Realtime Frosted Glass Capture
      this.backdrop.capture(var5, MenuTheme.px(24.0F));
      MenuSurface.useBackgroundBlur(this.backdrop);

      this.openTransition.method2808(1.0F);
      this.pageTransition.method2808(1.0F);

      // 4. Center content (Brand or Active Subpage)
      MainMenuPage curPage = this.page();
      if (curPage != null) {
         curPage.update();
         float footerH = curPage.footerHeight();
         float dockH = 35.0F;
         float topSpace = 24.0F;
         float bottomSpace = dockH + 26.0F;
         float availH = screenH - topSpace - bottomSpace;

         float totalPageH = curPage.contentHeight() + (footerH > 0.0F ? 14.0F + footerH : 0.0F);
         float pageW = Math.max(curPage.contentWidth(), curPage.footerWidth());

         float fitH = Math.min(1.0F, availH / Math.max(1.0F, totalPageH));
         float fitW = Math.min(1.0F, (screenW - 20.0F) / Math.max(1.0F, pageW));
         this.fit = Math.max(0.65F, Math.min(fitH, fitW));

         float scaledScreenW = screenW / this.fit;
         float scaledAvailH = availH / this.fit;
         float scaledTopSpace = topSpace / this.fit;

         float pageX = MenuTheme.snap((scaledScreenW - curPage.contentWidth()) / 2.0F);
         float pageY = MenuTheme.snap(scaledTopSpace + Math.max(0.0F, (scaledAvailH - totalPageH) / 2.0F));

         var5.getMatrices().pushMatrix();
         var5.getMatrices().scale(this.fit, this.fit);

         float transY = (1.0F - this.pageTransition.getValue()) * 18.0F;
         var5.getMatrices().pushMatrix();
         var5.getMatrices().translate(0.0F, transY);

         float scaledMouseX = mouseX / this.fit;
         float scaledMouseY = mouseY / this.fit;

         BoundsData contentBounds = new BoundsData(pageX, pageY, curPage.contentWidth(), curPage.contentHeight());
         curPage.layout(contentBounds);
         curPage.render(var5, scaledMouseX, scaledMouseY, contentBounds);

         if (footerH > 0.0F) {
            BoundsData footerBounds = new BoundsData(
               MenuTheme.snap((scaledScreenW - curPage.footerWidth()) / 2.0F),
               pageY + curPage.contentHeight() + 14.0F,
               curPage.footerWidth(),
               footerH
            );
            curPage.renderFooter(var5, scaledMouseX, scaledMouseY, footerBounds);
         }

         var5.getMatrices().popMatrix();
         var5.getMatrices().popMatrix();
      } else {
         this.fit = 1.0F;
         this.renderCenterBrand(var5, screenW, screenH);
      }

      // 5. Chrome UI (Top Badge, Left Sidebar, Bottom Dock reduced by 30%)
      this.renderUserBadge(var5, screenW);
      this.renderSidebar(var5, (float)mouseX, (float)mouseY, screenW, screenH);
      this.renderDock(var5, (float)mouseX, (float)mouseY, screenW, screenH);
   }

   private void renderCenterBrand(MenuDrawContext var5, float screenW, float screenH) {
      float brandY = screenH * 0.35F;

      // Title "Zenith" and Subtitle "Welcome back" strictly centered on screen
      Font titleFont = MenuTheme.semibold(40.0F);
      String title = "Zenith";
      Font subFont = MenuTheme.regular(13.0F);
      String subtitle = "Welcome back";

      float titleW = titleFont.width(title);
      float titleX = (screenW - titleW) / 2.0F;

      float subW = subFont.width(subtitle);
      float subX = (screenW - subW) / 2.0F;

      // Draw Title "Zenith"
      var5.drawText(titleFont, title, titleX, brandY, ZenithColor.INSTANCE);

      // Draw Subtitle "Welcome back"
      var5.drawText(subFont, subtitle, subX, brandY + titleFont.height() + 8.0F, new ZenithColor(190, 195, 215, 185));
   }

   private void updateParallax(float mouseX, float mouseY, float screenW, float screenH, float deltaTicks) {
      float targetX = 0.0F;
      float targetY = 0.0F;
      if (screenW > 0.0F && screenH > 0.0F) {
         targetX = Math.clamp((mouseX - screenW / 2.0F) / (screenW / 2.0F), -1.0F, 1.0F);
         targetY = Math.clamp((mouseY - screenH / 2.0F) / (screenH / 2.0F), -1.0F, 1.0F);
      }
      float speed = Math.clamp(0.08F * Math.max(1.0F, deltaTicks), 0.04F, 0.25F);
      this.currentParallaxX += (targetX - this.currentParallaxX) * speed;
      this.currentParallaxY += (targetY - this.currentParallaxY) * speed;
   }

   private void renderBackgroundShader(MenuDrawContext var5, float screenW, float screenH) {
      float timeSec = (float)(System.currentTimeMillis() % 3600000L) / 1000.0F;

      if (this.currentShaderIndex == 0) {
         // Sadnes Landscape from Sadnes client
         this.renderCoverImage(var5, SADNES_BG, screenW, screenH);
      } else if (this.currentShaderIndex == 1) {
         // Sky Landscape from Download/sky.png
         this.renderCoverImage(var5, SKY_BG, screenW, screenH);
      } else {
         float maxOffset = 16.0F;
         float pX = -this.currentParallaxX * maxOffset;
         float pY = -this.currentParallaxY * maxOffset;
         float scale = 1.06F;

         var5.getMatrices().pushMatrix();
         var5.getMatrices().translate(screenW / 2.0F + pX, screenH / 2.0F + pY);
         var5.getMatrices().scale(scale, scale);
         var5.getMatrices().translate(-screenW / 2.0F, -screenH / 2.0F);

         Matrix4f mat = MatrixUtils.method664(var5.getMatrices());

         switch (this.currentShaderIndex) {
            case 2: // Cosmic Nebula & Stars
               RenderShaders.method5261(mat, screenW, screenH,
                  new ZenithColor(140, 110, 245), new ZenithColor(55, 120, 240), 1.15F);
               break;
            case 3: // Aurora Borealis
               RenderShaders.method5261(mat, screenW, screenH,
                  new ZenithColor(35, 235, 165), new ZenithColor(15, 145, 250), 1.35F);
               break;
            case 4: // Cyberpunk Neon Fluid
               RenderShaders.method5262(mat, screenW, screenH, NEON_PALETTE, timeSec, 1.25F, 1.0F);
               break;
            case 5: // Sunset Mirage Twilight
               RenderShaders.method5262(mat, screenW, screenH, SUNSET_PALETTE, timeSec, 0.95F, 1.0F);
               break;
            case 6: // Obsidian Minimalist
               RenderShaders.method5262(mat, screenW, screenH, OBSIDIAN_PALETTE, timeSec, 0.65F, 1.0F);
               break;
            case 7: // Theme Adaptive
            default:
               ZenithColor stylePrimary = Zenith.getInstance().getStyleManager().getCurrentStyle().getPrimaryColor().getZenithColor();
               ZenithColor styleSecondary = Zenith.getInstance().getStyleManager().getCurrentStyle().getSecondaryPrimaryColor().getZenithColor();
               RenderShaders.method5261(mat, screenW, screenH, stylePrimary, styleSecondary, 1.20F);
               break;
         }

         var5.getMatrices().popMatrix();
      }

      // Atmospheric vignette overlay for contrast
      var5.drawRect(0.0F, 0.0F, screenW, screenH, new ZenithColor(0, 0, 0, 30));
   }

   private void renderCoverImage(MenuDrawContext var5, Identifier texture, float screenW, float screenH) {
      float maxOffset = 22.0F;
      float extra = maxOffset * 2.0F + 12.0F;
      float targetW = screenW + extra;
      float targetH = screenH + extra;
      float imgAspect = 1920.0F / 1080.0F;
      float screenAspect = targetW / targetH;
      float drawW;
      float drawH;
      if (screenAspect > imgAspect) {
         drawW = targetW;
         drawH = targetW / imgAspect;
      } else {
         drawH = targetH;
         drawW = targetH * imgAspect;
      }
      float offsetX = -this.currentParallaxX * maxOffset;
      float offsetY = -this.currentParallaxY * maxOffset;
      float drawX = (screenW - drawW) / 2.0F + offsetX;
      float drawY = (screenH - drawH) / 2.0F + offsetY;
      var5.drawTexture(texture, drawX, drawY, drawW, drawH, ZenithColor.INSTANCE);
   }

   private void renderDock(MenuDrawContext var5, float mouseX, float mouseY, float screenW, float screenH) {
      // Dock dimensions reduced by 30%
      float dockH = 36.0F;
      float dockRadius = 11.0F;
      ProxyDotRadiusData radiusData = ProxyDotRadiusData.method8069(dockRadius);

      float btnW = 46.0F;
      float btnH = 28.0F;
      float actW = 26.0F;
      float gap = 2.0F;
      float sepGap = 6.0F;
      float padX = 7.0F;

      float totalDockW = padX * 2.0F + (btnW * 4.0F) + (gap * 3.0F) + (sepGap * 2.0F) + 1.0F + (actW * 2.0F) + gap;
      float dockX = (screenW - totalDockW) / 2.0F;
      float dockY = screenH - dockH - 22.0F; // Moved dock slightly down

      // Match reference glass material
      ZenithColor dockFill = MenuTheme.glassBackground();

      // Frosted Glass Blur & Rounded Container (uses project Kawase blur, matching reference)
      MenuSurface.blur(var5, dockX, dockY, totalDockW, dockH, radiusData, 1.0F);
      var5.drawRoundedRect(dockX, dockY, totalDockW, dockH, radiusData, dockFill);

      float curX = dockX + padX;
      float curY = dockY + (dockH - btnH) / 2.0F;

      // 4 Main Navigation Buttons
      for (int i = 0; i < 4; i++) {
         DockItem item = this.dockItems.get(i);
         item.x = curX;
         item.y = curY;
         item.width = btnW;
         item.height = btnH;

         boolean isHovered = item.contains(mouseX, mouseY);
         item.hover.method2807(isHovered);

         boolean isActive = (item.linkedTabIndex >= 0 && this.activeIndex == item.linkedTabIndex);
         float hVal = item.hover.getValue();

         // Section icon scales up on hover
         float iconScale = 1.0F + 0.28F * hVal;
         Font iconFont = SvgIcon.font(11.0F);
         float iW = iconFont.width(item.icon.glyph());
         float iH = iconFont.height();
         float iX = item.x + (item.width - iW) / 2.0F;
         float iY = item.y + 3.5F;

         ZenithColor iconColor = new ZenithColor(185, 190, 205, 195).method7295(ZenithColor.INSTANCE, isActive ? 1.0F : hVal);

         var5.getMatrices().pushMatrix();
         float iconCenterX = iX + iW / 2.0F;
         float iconCenterY = iY + iH / 2.0F;
         var5.getMatrices().translate(iconCenterX, iconCenterY);
         var5.getMatrices().scale(iconScale, iconScale);
         var5.getMatrices().translate(-iconCenterX, -iconCenterY);
         var5.drawText(iconFont, item.icon.glyph(), iX, iY, iconColor);
         var5.getMatrices().popMatrix();

         // Section text rendered cleanly without typewriter or glow wave
         Font textFont = MenuTheme.medium(7.0F);
         float totalW = textFont.width(item.label);
         float curCharX = item.x + (item.width - totalW) / 2.0F;
         float textY = item.y + 18.5F;

         ZenithColor baseTextColor = new ZenithColor(175, 180, 200, 185).method7295(ZenithColor.INSTANCE, isActive ? 1.0F : hVal);
         var5.drawText(textFont, item.label, curCharX, textY, baseTextColor);

         curX += btnW + gap;
      }

      // Vertical separator line
      curX += sepGap - gap;
      float sepY = dockY + (dockH - 14.0F) / 2.0F;
      var5.drawRect(curX, sepY, 1.0F, 14.0F, new ZenithColor(255, 255, 255, 25));
      curX += 1.0F + sepGap;

      // 2 Action Buttons (Settings & Exit)
      for (int i = 4; i < 6; i++) {
         DockItem item = this.dockItems.get(i);
         item.x = curX;
         item.y = curY;
         item.width = actW;
         item.height = btnH;

         boolean isHovered = item.contains(mouseX, mouseY);
         item.hover.method2807(isHovered);

         float hVal = item.hover.getValue();

         // Action icon scales up on hover
         float iconScale = 1.0F + 0.25F * hVal;
         ZenithColor iconColor;
         if (item.isExit) {
            iconColor = new ZenithColor(185, 190, 205, 195).method7295(new ZenithColor(255, 95, 95), hVal);
         } else {
            iconColor = new ZenithColor(185, 190, 205, 195).method7295(ZenithColor.INSTANCE, hVal);
         }

         Font iconFont = SvgIcon.font(11.5F);
         float iW = iconFont.width(item.icon.glyph());
         float iH = iconFont.height();
         float iX = item.x + (item.width - iW) / 2.0F;
         float iY = item.y + (item.height - iH) / 2.0F;

         var5.getMatrices().pushMatrix();
         float iconCenterX = iX + iW / 2.0F;
         float iconCenterY = iY + iH / 2.0F;
         var5.getMatrices().translate(iconCenterX, iconCenterY);
         var5.getMatrices().scale(iconScale, iconScale);
         var5.getMatrices().translate(-iconCenterX, -iconCenterY);
         var5.drawText(iconFont, item.icon.glyph(), iX, iY, iconColor);
         var5.getMatrices().popMatrix();

         curX += actW + gap;
      }
   }

   private void renderSidebar(MenuDrawContext var5, float mouseX, float mouseY, float screenW, float screenH) {
      float sideW = 26.0F;
      float btnSize = 22.0F;
      float gap = 3.0F;
      float pad = 3.0F;
      float sideH = pad * 2.0F + (btnSize * this.sidebarItems.size()) + gap * (this.sidebarItems.size() - 1);
      float sideX = 16.0F;
      float sideY = (screenH - sideH) / 2.0F;
      ProxyDotRadiusData sideRadius = ProxyDotRadiusData.method8069(11.0F);

      ZenithColor sideFill = MenuTheme.glassBackground();
      ZenithColor sideBorder = MenuTheme.glassBorder();
      float borderWidth = MenuTheme.glassBorderWidth();

      MenuSurface.blur(var5, sideX, sideY, sideW, sideH, sideRadius, 1.0F);
      var5.drawRoundedRect(sideX, sideY, sideW, sideH, sideRadius, sideFill);

      float curY = sideY + pad;
      for (SidebarItem item : this.sidebarItems) {
         item.x = sideX + (sideW - btnSize) / 2.0F;
         item.y = curY;
         item.width = btnSize;
         item.height = btnSize;
         item.hover.method2807(item.contains(mouseX, mouseY));

         float hVal = item.hover.getValue();

         ZenithColor iconColor = new ZenithColor(185, 190, 205, 190).method7295(ZenithColor.INSTANCE, hVal);
         Font iconFont = SvgIcon.font(11.0F);
         float iW = iconFont.width(item.icon.glyph());
         float iH = iconFont.height();
         float iX = item.x + (item.width - iW) / 2.0F;
         float iY = item.y + (item.height - iH) / 2.0F;

         // Sidebar icon scales up on hover from center
         float iconScale = 1.0F + 0.28F * hVal;
         var5.getMatrices().pushMatrix();
         float iconCenterX = iX + iW / 2.0F;
         float iconCenterY = iY + iH / 2.0F;
         var5.getMatrices().translate(iconCenterX, iconCenterY);
         var5.getMatrices().scale(iconScale, iconScale);
         var5.getMatrices().translate(-iconCenterX, -iconCenterY);
         var5.drawText(iconFont, item.icon.glyph(), iX, iY, iconColor);
         var5.getMatrices().popMatrix();

         curY += btnSize + gap;
      }

      // Floating toast indicating active shader name when user cycles through them
      long toastElapsed = System.currentTimeMillis() - this.shaderToastStartTime;
      if (toastElapsed < 2400L) {
         float toastAlpha = 1.0F;
         if (toastElapsed > 1800L) {
            toastAlpha = 1.0F - (float)(toastElapsed - 1800L) / 600.0F;
         }
         Font toastFont = MenuTheme.medium(9.0F);
         String toastText = "Шейдер: " + SHADER_NAMES[this.currentShaderIndex];
         float tW = toastFont.width(toastText) + 20.0F;
         float tH = 22.0F;
         float tX = sideX + sideW + 10.0F;
         float tY = sideY + (sideH - tH) / 2.0F;
         ProxyDotRadiusData tRadius = ProxyDotRadiusData.method8069(6.0F);
         var5.drawRoundedRect(tX, tY, tW, tH, tRadius, sideFill.method7294(toastAlpha));
         var5.drawText(toastFont, toastText, tX + 10.0F, tY + (tH - toastFont.height()) / 2.0F, ZenithColor.INSTANCE.method7294(toastAlpha));
      }
   }

   private void renderUserBadge(MenuDrawContext var5, float screenW) {
      String username = (this.mc != null && this.mc.getSession() != null && !this.mc.getSession().getUsername().isEmpty())
            ? this.mc.getSession().getUsername()
            : Zenith.getInstance().getEntry().getUsername();
      if (username == null || username.isEmpty()) {
         username = "Zenith User";
      }

      Font userFont = MenuTheme.medium(14.0F);
      Font miniIcon = SvgIcon.font(14.0F);
      float badgeH = 28.0F;
      float iconW = miniIcon.width(SvgIcon.PROFILE.glyph());
      float padX = 8.0F;
      float gap = 4.0F;
      float badgeW = padX + iconW + gap + userFont.width(username) + padX;
      float badgeX = screenW - badgeW - 16.0F;
      float badgeY = 16.0F;

      ProxyDotRadiusData badgeRadius = ProxyDotRadiusData.method8069(10.5F);

      ZenithColor badgeFill = MenuTheme.glassBackground();

      MenuSurface.blur(var5, badgeX, badgeY, badgeW, badgeH, badgeRadius, 1.0F);
      var5.drawRoundedRect(badgeX, badgeY, badgeW, badgeH, badgeRadius, badgeFill);

      float iconY = badgeY + (badgeH - miniIcon.height()) / 2.0F;
      var5.drawText(miniIcon, SvgIcon.PROFILE.glyph(), badgeX + padX, iconY, MenuTheme.accent());

      float textY = badgeY + (badgeH - userFont.height()) / 2.0F;
      var5.drawText(userFont, username, badgeX + padX + iconW + gap, textY, ZenithColor.INSTANCE);
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      return this.mouseClicked(click.x(), click.y(), click.button());
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      // 1. Check Bottom Dock Clicks
      for (DockItem item : this.dockItems) {
         if (item.contains(mouseX, mouseY)) {
            item.action.run();
            return true;
         }
      }

      // 2. Check Left Sidebar Clicks
      for (SidebarItem item : this.sidebarItems) {
         if (item.contains(mouseX, mouseY)) {
            item.action.run();
            return true;
         }
      }

      // 3. Delegate to Active Subpage if open
      MainMenuPage cur = this.page();
      if (cur != null) {
         double scaledX = mouseX / this.fit;
         double scaledY = mouseY / this.fit;
         if (cur.mouseClicked(scaledX, scaledY, button)) {
            return true;
         }
      }

      return super.mouseClicked(new Click(mouseX, mouseY, new MouseInput(button, 0)), false);
   }

   @Override
   public boolean mouseReleased(Click click) {
      return this.mouseReleased(click.x(), click.y(), click.button());
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      MainMenuPage cur = this.page();
      if (cur != null) {
         return cur.mouseReleased(mouseX / this.fit, mouseY / this.fit, button)
            ? true
            : super.mouseReleased(new Click(mouseX, mouseY, new MouseInput(button, 0)));
      }
      return super.mouseReleased(new Click(mouseX, mouseY, new MouseInput(button, 0)));
   }

   @Override
   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      return this.mouseDragged(click.x(), click.y(), click.button(), offsetX, offsetY);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
      MainMenuPage cur = this.page();
      if (cur != null) {
         return cur.mouseDragged(mouseX / this.fit, mouseY / this.fit, button)
            ? true
            : super.mouseDragged(new Click(mouseX, mouseY, new MouseInput(button, 0)), offsetX, offsetY);
      }
      return super.mouseDragged(new Click(mouseX, mouseY, new MouseInput(button, 0)), offsetX, offsetY);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      MainMenuPage cur = this.page();
      if (cur != null) {
         return cur.mouseScrolled(mouseX / this.fit, mouseY / this.fit, verticalAmount)
            ? true
            : super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      if (input.key() == 256) { // ESC key
         if (this.activeIndex != HOME_TAB) {
            this.select(HOME_TAB);
            return true;
         }
      }
      return this.keyPressed(input.key(), input.scancode(), input.modifiers());
   }

   public boolean keyPressed(int key, int scancode, int modifiers) {
      if (key == 256 && this.activeIndex != HOME_TAB) {
         this.select(HOME_TAB);
         return true;
      }
      MainMenuPage cur = this.page();
      if (cur != null && cur.keyPressed(key, scancode, modifiers)) {
         return true;
      }
      return super.keyPressed(new KeyInput(key, scancode, modifiers));
   }

   @Override
   public boolean charTyped(CharInput input) {
      return this.charTyped((char)input.codepoint(), input.modifiers());
   }

   public boolean charTyped(char codePoint, int modifiers) {
      MainMenuPage cur = this.page();
      if (cur != null && cur.charTyped(codePoint, modifiers)) {
         return true;
      }
      return super.charTyped(new CharInput(codePoint, modifiers));
   }

   @Override
   public void removed() {
      MainMenuPage cur = this.page();
      if (cur != null) {
         cur.onHide();
      }
      MenuSurface.clearBackgroundBlur();
      MenuSurface.setMenuSource(null);
      this.backdrop.close();
      super.removed();
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   @Override
   public void renderInGameBackground(DrawContext context) {
   }

   private static class DockItem {
      final String label;
      final SvgIcon icon;
      final int linkedTabIndex;
      final boolean isAction;
      final boolean isExit;
      final Runnable action;
      final Animation hover = new Animation(160L, Easing.INSTANCE12);

      boolean wasHovered = false;
      long hoverStartTime = 0L;

      float x, y, width, height;

      DockItem(String label, SvgIcon icon, int linkedTabIndex, boolean isAction, boolean isExit, Runnable action) {
         this.label = label;
         this.icon = icon;
         this.linkedTabIndex = linkedTabIndex;
         this.isAction = isAction;
         this.isExit = isExit;
         this.action = action;
      }

      boolean contains(double mx, double my) {
         return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
      }
   }

   private static class SidebarItem {
      final SvgIcon icon;
      final Runnable action;
      final Animation hover = new Animation(160L, Easing.INSTANCE12);
      float x, y, width, height;

      SidebarItem(SvgIcon icon, Runnable action) {
         this.icon = icon;
         this.action = action;
      }

      boolean contains(double mx, double my) {
         return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
      }
   }
}
