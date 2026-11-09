package com.pvpvisuals.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.rival.TopBar;
import com.pvpvisuals.render.LogoRenderer;
import com.pvpvisuals.render.LogoSizeMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Watermark с логотипом, ником игрока, временем, FPS и пингом. */
public final class WatermarkModule extends AbstractHudModule {
 private static final int HEIGHT = 22;
 private static final int LOGO_HEIGHT = 14;
 private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

 @Override public String id() { return "watermark"; }
 @Override public String label() { return "Watermark"; }
 @Override public int defaultX() { return 6; }
 @Override public int defaultY() { return 6; }
 @Override public boolean enabled() { return PvpConfig.get().watermark; }

 private boolean logo() {
  return PvpConfig.get().logo.showInWatermark && LogoRenderer.hasLogo();
 }

 private LogoSizeMode mode() {
  return LogoSizeMode.byName(PvpConfig.get().logo.sizeWatermark);
 }

 private String playerName() {
  Minecraft mc = Minecraft.getInstance();
  return mc.player == null ? "Player" : mc.player.getName().getString();
 }

 private int ping() {
  Minecraft mc = Minecraft.getInstance();
  if (mc.getConnection() == null || mc.player == null) return 0;
  var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
  return info == null ? 0 : info.getLatency();
 }

 @Override
 protected int contentWidth() {
  Minecraft mc = Minecraft.getInstance();
  var font = mc.font;
  int brandWidth = logo() ? LogoRenderer.widthFor(mode(), LOGO_HEIGHT, 1f) : font.width(TopBar.NAME);
  int nameWidth = font.width(playerName());
  int timeWidth = font.width("88:88");
  int fpsWidth = font.width(mc.getFps() + " fps");
  int pingWidth = font.width(ping() + " ms");
  return 8 + brandWidth + 10 + nameWidth + 12 + timeWidth + 12 + fpsWidth + 12 + pingWidth + 8;
 }

 @Override
 protected int contentHeight() { return HEIGHT; }

 @Override
 protected void drawContent(GuiGraphics g, boolean preview) {
  Minecraft mc = Minecraft.getInstance();
  var font = mc.font;
  int w = contentWidth();
  int cy = HEIGHT / 2;
  int textY = (HEIGHT - 8) / 2;
  int accent = HudAlpha.mul(HudStyle.ACCENT);
  HudStyle.panel(g, 0, 0, w, HEIGHT, HudStyle.RADIUS, HudAlpha.current);

  int x = 8;
  if (logo()) {
   int lw = LogoRenderer.widthFor(mode(), LOGO_HEIGHT, 1f);
   LogoRenderer.renderLogoAuto(g, mode(), x, (HEIGHT - LOGO_HEIGHT) / 2, LOGO_HEIGHT, 1f, HudAlpha.current);
   x += lw;
  } else {
   String brand = TopBar.NAME;
   g.drawString(font, brand, x, textY, HudAlpha.mul(HudStyle.TEXT), false);
   x += font.width(brand);
  }

  x += 10;
  String nick = playerName();
  g.drawString(font, nick, x, textY, HudAlpha.mul(HudStyle.TEXT), false);
  x += font.width(nick) + 6;
  HudStyle.separator(g, x, 5f, 12f, HudAlpha.current);
  x += 7;

  HudIcons.clock(g, x + 3.5f, cy, 3.2f, accent);
  String time = LocalTime.now().format(TIME_FORMAT);
  x += 10;
  g.drawString(font, time, x, textY, HudAlpha.mul(HudStyle.TEXT), false);
  x += font.width("88:88") + 7;
  HudStyle.separator(g, x, 5f, 12f, HudAlpha.current);
  x += 8;

  HudIcons.activity(g, x + 3.5f, cy, 3.2f, accent);
  String fps = mc.getFps() + " fps";
  x += 10;
  g.drawString(font, fps, x, textY, HudAlpha.mul(HudStyle.TEXT), false);
  x += font.width(fps) + 7;
  HudStyle.separator(g, x, 5f, 12f, HudAlpha.current);
  x += 8;

  HudIcons.signal(g, x + 3.5f, cy, 3.2f, accent);
  String latency = ping() + " ms";
  x += 10;
  g.drawString(font, latency, x, textY, HudAlpha.mul(HudStyle.TEXT), false);
 }
}
