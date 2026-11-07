package com.pvpvisuals.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.rival.ModuleDef;
import com.pvpvisuals.gui.rival.ModuleRegistry;
import com.pvpvisuals.gui.rival.RoundedRender;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** ArrayList в стиле Svintus: width-sort, slide/fade и привязка к любому углу. */
public final class ModuleListHudModule extends AbstractHudModule {
    private static final float SIZE = 6.4f;
    private static final float LINE = 13f;
    private static final float GAP = 2f;

    private final Map<String, Float> progress = new HashMap<>();
    private List<ModuleDef> visible = List.of();
    private float heightCache = LINE;
    private long lastUpdateNs;

    @Override public String id() { return "module_list"; }
    @Override public String label() { return "Module List"; }
    @Override public int defaultX() { return 6; }
    @Override public int defaultY() { return 48; }
    @Override public boolean enabled() { return PvpConfig.get().moduleListHud; }

    private int lineWidth(ModuleDef module) {
        return Gfx.widthAtSize(Minecraft.getInstance().font, module.name, SIZE) + 15;
    }

    private void update() {
        long now = System.nanoTime();
        float dt = lastUpdateNs == 0L ? 1f / 60f
                : Math.max(0f, Math.min(0.05f, (now - lastUpdateNs) / 1_000_000_000f));
        lastUpdateNs = now;

        List<ModuleDef> list = new ArrayList<>();
        for (ModuleDef module : ModuleRegistry.build()) {
            if (!module.isToggle()) {
                continue;
            }
            float current = progress.getOrDefault(module.name, module.enabled() ? 1f : 0f);
            float target = module.enabled() ? 1f : 0f;
            current += (target - current) * (1f - (float) Math.exp(-dt * 12f));
            if (Math.abs(target - current) < 0.003f) {
                current = target;
            }
            progress.put(module.name, current);
            if (current > 0.01f) {
                list.add(module);
            }
        }

        list.sort(Comparator.comparingInt(this::lineWidth).reversed());
        visible = list;

        float total = 0f;
        for (ModuleDef module : visible) {
            total += (LINE + GAP) * Gfx.ease(progress.getOrDefault(module.name, 0f));
        }
        heightCache = Math.max(LINE, total - GAP);
    }

    @Override
    protected int contentWidth() {
        update();
        return Math.max(60, visible.stream().mapToInt(this::lineWidth).max().orElse(60));
    }

    @Override
    protected int contentHeight() {
        update();
        return Math.round(heightCache);
    }

    @Override
    protected void drawContent(GuiGraphics g, boolean preview) {
        update();
        var font = Minecraft.getInstance().font;

        if (visible.isEmpty()) {
            if (preview) {
                HudStyle.panel(g, 0, 0, 60, Math.round(LINE), 4, HudAlpha.current);
                Gfx.textAtSize(g, font, "Нет модулей", 7f, 3.2f, SIZE,
                        HudAlpha.mul(HudStyle.MUTED), false);
            }
            return;
        }

        HudAnchor anchor = anchor();
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
        int maxWidth = Math.max(60, visible.stream().mapToInt(this::lineWidth).max().orElse(60));
        float y = bottom ? heightCache : 0f;

        for (int i = 0; i < visible.size(); i++) {
            ModuleDef module = visible.get(i);
            float raw = progress.getOrDefault(module.name, 0f);
            float appear = Gfx.ease(raw);
            int width = lineWidth(module);
            float x = right
                    ? maxWidth - width + (1f - appear) * (width + 10f)
                    : -(1f - appear) * (width + 10f);
            float rowY = bottom ? y - LINE * appear : y;

            float gradient = visible.size() <= 1 ? 0f : i / (float) (visible.size() - 1);
            int accent = RoundedRender.lerp(EchoTheme.ACCENT_A, EchoTheme.ACCENT_B, gradient);
            float alpha = HudAlpha.current * appear;

            HudStyle.panel(g, Math.round(x), Math.round(rowY), width, Math.round(LINE), 4, alpha);
            int barX = right ? Math.round(x + width - 4f) : Math.round(x + 2.5f);
            if (PvpConfig.get().moduleListGlow) {
                Gfx.glow(g, barX, rowY + 3f, 1.5f, LINE - 6f, 0.75f,
                        HudStyle.opacity(accent, alpha * 0.65f), 4f);
            }
            Gfx.rrect(g, barX, rowY + 3f, 1.5f, LINE - 6f, 0.75f,
                    HudStyle.opacity(accent, alpha), HudStyle.opacity(accent, alpha), 0, 0f);

            Gfx.textAtSize(g, font, module.name, x + (right ? 6f : 8f), rowY + 3.1f, SIZE,
                    HudStyle.opacity(HudStyle.TEXT, alpha), false);

            float step = (LINE + GAP) * appear;
            y += bottom ? -step : step;
        }
    }
}
