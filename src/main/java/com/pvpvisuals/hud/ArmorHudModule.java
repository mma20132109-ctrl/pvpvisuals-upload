package com.pvpvisuals.hud;

import com.pvpvisuals.config.PvpConfig;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.hud.inventory.SlotRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Armor HUD в геометрии Svintus: четыре слота брони, разделитель и
 * опциональные предметы в руках. Позиция, масштаб и альфа общие с остальными HUD.
 */
public final class ArmorHudModule extends AbstractHudModule {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int CELL = 18;
    private static final int ITEM = 14;
    private static final int GAP = 3;
    private static final int PAD = 4;
    private static final int SEPARATOR_GAP = 4;

    private final ItemStack[] stacks = new ItemStack[6];

    @Override public String id() { return "armor_hud"; }
    @Override public String label() { return "Armor HUD"; }
    @Override public int defaultX() { return 240; }
    @Override public int defaultY() { return 190; }
    @Override public boolean enabled() { return PvpConfig.get().armorHud; }

    private boolean hands() {
        return PvpConfig.get().armorHudHands;
    }

    private boolean percentages() {
        return PvpConfig.get().armorHudPercent;
    }

    private int slotCount() {
        return 4 + (hands() ? 2 : 0);
    }

    private void collect() {
        Player player = Minecraft.getInstance().player;
        for (int i = 0; i < ARMOR.length; i++) {
            stacks[i] = player == null ? ItemStack.EMPTY : player.getItemBySlot(ARMOR[i]);
        }
        stacks[4] = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        stacks[5] = player == null ? ItemStack.EMPTY : player.getOffhandItem();
    }

    @Override
    protected int contentWidth() {
        int count = slotCount();
        return PAD * 2 + count * CELL + (count - 1) * GAP
                + (hands() ? SEPARATOR_GAP : 0);
    }

    @Override
    protected int contentHeight() {
        return PAD * 2 + CELL + (percentages() ? 7 : 0);
    }

    @Override
    protected void drawContent(GuiGraphics g, boolean preview) {
        collect();
        boolean any = false;
        for (int i = 0; i < slotCount(); i++) {
            if (!stacks[i].isEmpty()) {
                any = true;
                break;
            }
        }
        if (!preview && !any) {
            return;
        }

        int width = contentWidth();
        int height = contentHeight();
        HudStyle.panel(g, 0, 0, width, height, 7, HudAlpha.current);

        int x = PAD;
        int y = PAD;
        for (int i = 0; i < slotCount(); i++) {
            if (i == ARMOR.length && hands()) {
                HudStyle.separator(g, x + 1.5f, y + 3f, CELL - 6f, HudAlpha.current);
                x += SEPARATOR_GAP;
            }
            drawSlot(g, Minecraft.getInstance().font, stacks[i], x, y);
            x += CELL + GAP;
        }
    }

    private void drawSlot(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        int percent = stack.isDamageableItem()
                ? Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue())
                        / Math.max(1f, stack.getMaxDamage()))
                : -1;
        int tint = percent < 0 ? 0x10FFFFFF
                : percent <= 20 ? 0x35FF4040 : 0x14FFFFFF;

        Gfx.rrect(g, x, y, CELL, CELL, 4f,
                HudAlpha.mul(tint), HudAlpha.mul(tint), 0, 0f);

        if (stack.isEmpty()) {
            Gfx.circle(g, x + CELL / 2f, y + CELL / 2f, 1.2f,
                    HudAlpha.mul(0x35FFFFFF));
        } else {
            g.pose().pushMatrix();
            g.pose().translate(x + (CELL - ITEM) / 2f, y + (CELL - ITEM) / 2f - 0.5f);
            g.pose().scale(ITEM / 16f, ITEM / 16f);
            g.renderItem(stack, 0, 0);
            g.pose().popMatrix();

            if (percent >= 0 && stack.getDamageValue() > 0) {
                float ratio = Math.max(0f, Math.min(1f, percent / 100f));
                int barX = x + 3;
                int barY = y + CELL - 3;
                int barW = CELL - 6;
                Gfx.rrect(g, barX, barY, barW, 1.5f, 0.75f,
                        HudAlpha.mul(0x50000000), HudAlpha.mul(0x50000000), 0, 0f);
                int fill = SlotRenderer.durabilityColor(ratio);
                Gfx.rrect(g, barX, barY, Math.max(1f, barW * ratio), 1.5f, 0.75f,
                        HudAlpha.mul(fill), HudAlpha.mul(fill), 0, 0f);
            }
        }

        if (percentages()) {
            String label = percent >= 0 ? percent + "%"
                    : stack.getCount() > 1 ? "x" + stack.getCount() : "";
            if (!label.isEmpty()) {
                int textWidth = Gfx.widthAtSize(font, label, 5f);
                int color = percent < 0
                        ? HudAlpha.mul(HudStyle.MUTED)
                        : HudAlpha.mul(percent <= 20 ? 0xFFFF6868 : HudStyle.TEXT);
                Gfx.textAtSize(g, font, label,
                        x + (CELL - textWidth) / 2f,
                        y + CELL + 1f, 5f, color, false);
            }
        }
    }
}
