package org.zenith.client.screens.override.main.widget;

import org.zenith.ZenithColor;
import org.zenith.base.font.Font;
import org.zenith.client.screens.override.main.MenuIcon;
import org.zenith.client.screens.override.main.MenuSkin;
import org.zenith.client.screens.override.main.MenuSurface;
import org.zenith.client.screens.override.main.MenuTheme;
import org.zenith.utility.render.display.base.MenuDrawContext;
import org.zenith.utility.render.display.base.ProxyDotRadiusData;

public class MenuButton extends MenuWidget {
   public static final float HEIGHT = MenuSkin.HEADER_HEIGHT;
   public static final float RADIUS = 11.0F;
   public static final float PADDING = MenuTheme.px(20.0F);
   public static final float GAP = MenuTheme.px(8.0F);
   public static final float ICON_SIZE = 12.0F;
   public static final float LABEL_SIZE = 12.0F;
   private MenuIcon icon;
   private String label;
   private MenuButton.Style style;
   private Runnable action = () -> {};
   private long lastClickTime = 0L;

   public MenuButton(MenuIcon var1, String var2, MenuButton.Style var3) {
      this.icon = var1;
      this.label = var2;
      this.style = var3;
   }

   public static MenuButton accent(MenuIcon var0, String var1) {
      return new MenuButton(var0, var1, MenuButton.Style.ACCENT);
   }

   public static MenuButton ghost(MenuIcon var0, String var1) {
      return new MenuButton(var0, var1, MenuButton.Style.GHOST);
   }

   public static MenuButton iconOnly(MenuIcon var0, MenuButton.Style var1) {
      return new MenuButton(var0, null, var1);
   }

   public MenuButton onClick(Runnable var1) {
      this.action = var1;
      return this;
   }

   public MenuButton setLabel(String var1) {
      this.label = var1;
      return this;
   }

   public MenuButton setIcon(MenuIcon var1) {
      this.icon = var1;
      return this;
   }

   public MenuButton setStyle(MenuButton.Style var1) {
      this.style = var1;
      return this;
   }

   public MenuButton.Style getStyle() {
      return this.style;
   }

   public float preferredWidth() {
      if (this.label != null && !this.label.isEmpty()) {
         Font var1 = MenuTheme.medium(12.0F);
         float var2 = var1.width(this.label);
         if (this.icon != null) {
            var2 += this.icon.width(12.0F) + GAP;
         }

         return PADDING * 2.0F + var2;
      } else {
         return HEIGHT;
      }
   }

   @Override
   public void draw(MenuDrawContext var1, float var2, float var3) {
      long elapsed = System.currentTimeMillis() - this.lastClickTime;
      float clickScale = 1.0F;
      if (elapsed < 160L) {
         float t = (float)elapsed / 160.0F;
         clickScale = 1.0F - (float)Math.sin(t * Math.PI) * 0.08F;
      }

      boolean hasClickScale = clickScale < 0.999F;
      if (hasClickScale) {
         float cx = this.x + this.width / 2.0F;
         float cy = this.y + this.height / 2.0F;
         var1.getMatrices().pushMatrix();
         var1.getMatrices().translate(cx, cy);
         var1.getMatrices().scale(clickScale, clickScale);
         var1.getMatrices().translate(-cx, -cy);
      }

      float var4 = this.enabled ? this.hover() : 0.0F;
      ProxyDotRadiusData var5 = ProxyDotRadiusData.method8069(RADIUS);
      ZenithColor var6;
      ZenithColor var7;
      ZenithColor var8;
      switch (this.style) {
         case ACCENT:
            var6 = MenuTheme.accent().method7303(var4 * 0.12F);
            var7 = MenuTheme.onAccent();
            var8 = MenuTheme.onAccent();
            break;
         case DANGER:
            var6 = MenuTheme.danger().method7303(var4 * 0.12F);
            var7 = MenuTheme.onAccent();
            var8 = MenuTheme.onAccent();
            break;
         default:
            var6 = MenuTheme.hudBackground().method7295(MenuTheme.track(), var4);
            var7 = MenuTheme.textTertiary().method7295(MenuTheme.accent(), var4);
            var8 = MenuTheme.textSecondary().method7295(MenuTheme.text(), var4);
      }

      if (!this.enabled) {
         var6 = var6.method7294(0.5F);
         var7 = var7.method7294(0.5F);
         var8 = var8.method7294(0.5F);
      }

      MenuSurface.blur(var1, this.x, this.y, this.width, this.height, var5, 1.0F);
      if (this.style == MenuButton.Style.GHOST) {
         var1.drawRoundedRect(this.x, this.y, this.width, this.height, var5, var6);
         if (var4 > 0.01F) {
            var1.drawRoundedBorder(this.x, this.y, this.width, this.height, 1.0F, var5, MenuTheme.accent().method7294(var4));
         }
      } else {
         var1.drawRoundedRect(this.x, this.y, this.width, this.height, var5, var6);
         if (var4 > 0.01F) {
            var1.drawRoundedBorder(this.x, this.y, this.width, this.height, 1.0F, var5, var6.method7303(0.25F));
         }
      }

      Font var9 = MenuTheme.medium(12.0F);
      boolean var10 = this.label != null && !this.label.isEmpty();
      float var11 = this.icon == null ? 0.0F : this.icon.width(12.0F);
      float var12 = var10 ? var9.width(this.label) : 0.0F;
      float var13 = var11 + var12 + (this.icon != null && var10 ? GAP : 0.0F);
      float var14 = this.x + (this.width - var13) / 2.0F;
      if (this.icon != null) {
         Font var15 = this.icon.font(12.0F);
         float iconW = var15.width(this.icon.glyph());
         float iconH = var15.height();
         float iconY = this.y + (this.height - iconH) / 2.0F;
         if (var4 > 0.005F) {
            float iconScale = 1.0F + 0.22F * var4;
            float iconCenterX = var14 + iconW / 2.0F;
            float iconCenterY = iconY + iconH / 2.0F;
            var1.getMatrices().pushMatrix();
            var1.getMatrices().translate(iconCenterX, iconCenterY);
            var1.getMatrices().scale(iconScale, iconScale);
            var1.getMatrices().translate(-iconCenterX, -iconCenterY);
            var1.drawText(var15, this.icon.glyph(), var14, iconY, var7);
            var1.getMatrices().popMatrix();
         } else {
            var1.drawText(var15, this.icon.glyph(), var14, iconY, var7);
         }
         var14 += var11 + (var10 ? GAP : 0.0F);
      }

      if (var10) {
         var1.drawText(var9, this.label, var14, this.y + (this.height - var9.height()) / 2.0F, var8);
      }

      if (hasClickScale) {
         var1.getMatrices().popMatrix();
      }
   }

   @Override
   public boolean mouseClicked(double var1, double var3, int var5) {
      if (this.visible && this.enabled && var5 == 0 && this.contains(var1, var3)) {
         this.lastClickTime = System.currentTimeMillis();
         this.action.run();
         return true;
      } else {
         return false;
      }
   }

   public static enum Style {
      ACCENT,
      GHOST,
      DANGER;
   }
}
