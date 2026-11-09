package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;

/** Координаты в компактном формате Svintus. */
public final class CoordinatesHudModule extends AbstractHudModule {
 private String coords="0, 0, 0", direction="N", other="";
 @Override public String id(){return "coordinates";}
 @Override public String label(){return "Coordinates";}
 @Override public int defaultX(){return 6;}
 @Override public int defaultY(){return 28;}
 @Override public boolean enabled(){return PvpConfig.get().coordinatesHud;}
 private void update(){
  var mc=Minecraft.getInstance(); var p=mc.player;
  if(p==null){coords="0, 0, 0";direction="N";other="";return;}
  int x=(int)Math.floor(p.getX()), y=(int)Math.floor(p.getY()), z=(int)Math.floor(p.getZ());
  coords=x+", "+y+", "+z;
  direction=PvpConfig.get().coordinatesDirection?p.getDirection().getName().substring(0,1).toUpperCase(java.util.Locale.ROOT):"";
  boolean nether=mc.level!=null && mc.level.dimension()==Level.NETHER;
  other=PvpConfig.get().coordinatesNether?(nether?(x*8)+", "+(z*8):Math.floorDiv(x, 8)+", "+Math.floorDiv(z, 8)):"";
 }
 @Override protected int contentWidth(){
  update(); var f=Minecraft.getInstance().font;
  int w=(int)Math.ceil(7+Gfx.widthAtSize(f,"XYZ",6.2f)+5+Gfx.widthAtSize(f,coords,6.2f)+7);
  if(!direction.isEmpty())w+=22+Gfx.widthAtSize(f,direction,6.2f);
  if(!other.isEmpty())w+=10+Gfx.widthAtSize(f,other,5.6f);
  return w;
 }
 @Override protected int contentHeight(){return 15;}
 @Override protected void drawContent(GuiGraphics g,boolean preview){
  update(); var f=Minecraft.getInstance().font;
  int w=contentWidth(); HudStyle.panel(g,0,0,w,15,5,HudAlpha.current);
  int x=7; Gfx.textAtSize(g,f,"XYZ",x,4.2f,6.2f,HudAlpha.mul(HudStyle.ACCENT),false); x+=Gfx.widthAtSize(f,"XYZ",6.2f)+5;
  Gfx.textAtSize(g,f,coords,x,4.2f,6.2f,HudAlpha.mul(HudStyle.TEXT),false); x+=Gfx.widthAtSize(f,coords,6.2f);
  if(!other.isEmpty()){x+=5;Gfx.fill(g,x,4,1,7,0,HudAlpha.mul(0x30FFFFFF));x+=5;Gfx.textAtSize(g,f,other,x,4.5f,5.6f,HudAlpha.mul(HudStyle.MUTED),false);x+=Gfx.widthAtSize(f,other,5.6f);}
  if(!direction.isEmpty()){x+=6;HudStyle.separator(g,x,4f,7f,HudAlpha.current);x+=6;HudIcons.compass(g,x+3f,7.5f,3.1f,HudAlpha.mul(HudStyle.ACCENT));x+=8;Gfx.textAtSize(g,f,direction,x,4.2f,6.2f,HudAlpha.mul(HudStyle.ACCENT),false);}
 }
}
