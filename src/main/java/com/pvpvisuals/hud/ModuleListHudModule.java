package com.pvpvisuals.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Анимированный список включённых модулей, отсортированный по ширине как в Svintus. */
public final class ModuleListHudModule extends AbstractHudModule {
 private final Map<String,Float> progress=new HashMap<>();
 private List<ModuleDef> visible=List.of();
 private static final float SIZE=6.4f, LINE=13f, GAP=2f;
 @Override public String id(){return "module_list";}
 @Override public String label(){return "Module List";}
 @Override public int defaultX(){return 6;}
 @Override public int defaultY(){return 48;}
 @Override public boolean enabled(){return PvpConfig.get().moduleListHud;}
 private int lineWidth(ModuleDef d){return Gfx.widthAtSize(Minecraft.getInstance().font,d.name,SIZE)+15;}
 private void update(){
  List<ModuleDef> list=new ArrayList<>(); long now=System.nanoTime();
  float dt=Math.min(.05f,Math.max(0,(now-last)/1_000_000_000f)); last=now;
  for(ModuleDef d:ModuleRegistry.build()){
   if(!d.isToggle())continue;
   float p=progress.getOrDefault(d.name,d.enabled()?1f:0f);
   float target=d.enabled()?1f:0f;
   p+=(target-p)*(1f-(float)Math.exp(-dt*12f));
   if(Math.abs(target-p)<.003f)p=target;
   progress.put(d.name,p); if(p>.01f)list.add(d);
  }
  list.sort(Comparator.comparingInt(this::lineWidth).reversed()); visible=list;
 }
 private long last=System.nanoTime();
 @Override protected int contentWidth(){update();return Math.max(60,visible.stream().mapToInt(this::lineWidth).max().orElse(60));}
 @Override protected int contentHeight(){update();float h=0;for(ModuleDef d:visible)h+=(LINE+GAP)*progress.getOrDefault(d.name,0f);return Math.max(13,Math.round(h-GAP));}
 @Override protected void drawContent(GuiGraphics g,boolean preview){
  update();var f=Minecraft.getInstance().font;boolean right=false;float y=0;int max=contentWidth();
  for(int i=0;i<visible.size();i++){
   ModuleDef d=visible.get(i);float a=progress.getOrDefault(d.name,0f);
   int w=lineWidth(d);float x=right?max-w:0;int accent=HudStyle.ACCENT;
   int alpha=Math.round(a*HudAlpha.current*255);
   HudStyle.panel(g,(int)x,Math.round(y),w,13,4,alpha/255f);
   if(PvpConfig.get().moduleListGlow)Gfx.glow(g,(int)x+2,Math.round(y)+3,2,7,1,HudAlpha.mul((alpha<<24)|(accent&0xffffff)),3);
   Gfx.fill(g,(int)x+2,Math.round(y)+3,1,7,1,HudAlpha.mul((alpha<<24)|(accent&0xffffff)));
   Gfx.textAtSize(g,f,d.name,x+8,y+3.2f,SIZE,HudAlpha.mul((alpha<<24)|(HudStyle.TEXT&0xffffff)),false);
   y+=(LINE+GAP)*a;
  }
 }
}
