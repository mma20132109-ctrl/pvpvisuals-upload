package com.pvpvisuals.gui;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.pvpvisuals.account.OfflineAccountManager;
import com.pvpvisuals.gui.echo.EchoTheme;
import com.pvpvisuals.gui.echo.Gfx;
import com.pvpvisuals.gui.echo.LiquidGlassRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Liquid Glass offline-profile manager. No Microsoft credentials are requested or stored. */
public final class AltManagerScreen extends Screen {
    private final Screen parent;
    private final OfflineAccountManager manager = OfflineAccountManager.get();

    private String input = "";
    private boolean inputFocused;
    private float scroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int fieldX;
    private int fieldY;
    private int fieldW;
    private int addX;
    private int randomX;
    private int actionY;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int backY;
    private static final int ROW_H = 31;
    private static final int ROW_GAP = 5;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String current = manager.currentName();
        if (OfflineAccountManager.isValidName(current)) manager.add(current);
        layoutPanel();
    }

    private void layoutPanel() {
        panelW = Math.min(500, this.width - 18);
        panelH = Math.min(430, this.height - 18);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        fieldX = panelX + 22;
        fieldY = panelY + 100;
        fieldW = panelW - 44;
        actionY = fieldY + 36;
        addX = panelX + 22;
        randomX = panelX + panelW - 148;
        listX = panelX + 22;
        listY = actionY + 43;
        listW = panelW - 44;
        listH = Math.max(48, panelY + panelH - 58 - listY);
        backY = panelY + panelH - 42;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        EchoTheme.sync();
        layoutPanel();
        g.fillGradient(0, 0, this.width, this.height, 0xFF0A1925, 0xFF050B13);
        Gfx.circle(g, this.width * 0.84f, this.height * 0.18f, Math.max(34f, this.width * 0.1f),
                0x1858D8F3);
        LiquidGlassRenderer.window(g, panelX, panelY, panelW, panelH, 16f);

        Gfx.textScaled(g, this.font, "Alt Manager", panelX + 22, panelY + 22, 1.55f, EchoTheme.TEXT, false);
        Gfx.text(g, this.font, "Управление локальными профилями", panelX + 22, panelY + 49,
                EchoTheme.MUTED);
        Gfx.text(g, this.font, "Текущий ник: " + manager.currentName(), panelX + 22, panelY + 72,
                EchoTheme.NEON);

        boolean fieldHover = Gfx.hit(mouseX, mouseY, fieldX, fieldY, fieldW, 28);
        LiquidGlassRenderer.surface(g, fieldX, fieldY, fieldW, 28, 8f,
                inputFocused ? EchoTheme.FIELD_FOCUS : EchoTheme.FIELD,
                inputFocused ? EchoTheme.NEON : EchoTheme.FIELD_BORDER);
        String fieldText = input.isEmpty() ? "Введите ник (3–16 символов)" : input;
        Gfx.text(g, this.font, fieldText + (inputFocused ? "_" : ""), fieldX + 11, fieldY + 10,
                input.isEmpty() ? EchoTheme.DIM : EchoTheme.TEXT);

        drawAction(g, mouseX, mouseY, addX, actionY, 116, "Добавить");
        drawAction(g, mouseX, mouseY, randomX, actionY, 126, "Случайный ник");

        Gfx.text(g, this.font, "СОХРАНЁННЫЕ ПРОФИЛИ", listX + 2, listY - 13, EchoTheme.MUTED);
        Gfx.rrect(g, listX, listY, listW, listH, 10f, 0x301A3442, 0x30101F2B, 0x448AC7D8, 0.7f);

        List<String> accounts = manager.getAccounts();
        int step = ROW_H + ROW_GAP;
        int maxScroll = Math.max(0, accounts.size() * step - ROW_GAP - listH);
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        String active = manager.currentName();
        for (int i = 0; i < accounts.size(); i++) {
            int rowY = listY + 6 + i * step - Math.round(scroll);
            if (rowY < listY + 4 || rowY + ROW_H > listY + listH - 3) continue;
            String name = accounts.get(i);
            boolean selected = name.equalsIgnoreCase(active);
            boolean hovered = Gfx.hit(mouseX, mouseY, listX + 6, rowY, listW - 12, ROW_H);
            int fill = selected ? 0xA52B6175 : hovered ? EchoTheme.CARD_HOVER : 0x64243D4B;
            int border = selected ? EchoTheme.NEON : (hovered ? EchoTheme.CARD_BORDER_HOT : 0x447B9AA8);
            LiquidGlassRenderer.surface(g, listX + 6, rowY, listW - 12, ROW_H, 7f, fill, border);
            Gfx.circle(g, listX + 20, rowY + ROW_H / 2, selected ? 4 : 3, selected ? EchoTheme.NEON : EchoTheme.DIM);
            Gfx.text(g, this.font, name, listX + 32, rowY + 11, EchoTheme.TEXT);
            if (selected) {
                Gfx.text(g, this.font, "АКТИВЕН", listX + listW - 85, rowY + 11, EchoTheme.NEON);
            } else {
                Gfx.text(g, this.font, "ВЫБРАТЬ", listX + listW - 91, rowY + 11, EchoTheme.MUTED);
            }
            Gfx.text(g, this.font, "×", listX + listW - 25, rowY + 8, 0xFFFF8995);
        }
        if (accounts.isEmpty()) {
            Gfx.text(g, this.font, "Список пуст. Добавь ник или создай случайный.",
                    listX + 12, listY + 18, EchoTheme.DIM);
        }

        drawAction(g, mouseX, mouseY, panelX + 22, backY, 112, "← Назад");
        Gfx.text(g, this.font, "Только оффлайн-профили • Microsoft-вход не выполняется",
                panelX + 146, backY + 10, EchoTheme.DIM);
    }

    private void drawAction(GuiGraphics g, int mx, int my, int x, int y, int w, String label) {
        boolean hovered = Gfx.hit(mx, my, x, y, w, 28);
        LiquidGlassRenderer.surface(g, x, y, w, 28, 8f,
                hovered ? EchoTheme.CARD_HOVER : EchoTheme.CARD,
                hovered ? EchoTheme.CARD_BORDER_HOT : EchoTheme.CARD_BORDER);
        Gfx.text(g, this.font, label, x + 12, y + 10, hovered ? EchoTheme.TEXT : EchoTheme.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        layoutPanel();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mx = (int) event.x();
        int my = (int) event.y();

        if (Gfx.hit(mx, my, fieldX, fieldY, fieldW, 28)) {
            inputFocused = true;
            return true;
        }
        inputFocused = false;

        if (Gfx.hit(mx, my, addX, actionY, 116, 28)) {
            if (manager.add(input)) input = "";
            return true;
        }
        if (Gfx.hit(mx, my, randomX, actionY, 126, 28)) {
            String name = manager.randomName(input);
            if (manager.add(name)) input = "";
            return true;
        }
        if (Gfx.hit(mx, my, panelX + 22, backY, 112, 28)) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        List<String> accounts = manager.getAccounts();
        int step = ROW_H + ROW_GAP;
        int maxScroll = Math.max(0, accounts.size() * step - ROW_GAP - listH);
        for (int i = 0; i < accounts.size(); i++) {
            int rowY = listY + 6 + i * step - Math.round(scroll);
            if (rowY < listY + 4 || rowY + ROW_H > listY + listH - 3) continue;
            if (!Gfx.hit(mx, my, listX + 6, rowY, listW - 12, ROW_H)) continue;
            String name = accounts.get(i);
            if (mx >= listX + listW - 34) {
                manager.remove(name);
            } else {
                manager.switchTo(name);
                Minecraft.getInstance().setScreen(parent);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        layoutPanel();
        if (Gfx.hit(mouseX, mouseY, listX, listY, listW, listH)) {
            scroll -= (float) (scrollY * 28.0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (inputFocused) {
                inputFocused = false;
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
            return true;
        }
        if (inputFocused && key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        if (inputFocused && key == GLFW.GLFW_KEY_ENTER) {
            if (manager.add(input)) input = "";
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!inputFocused) return super.charTyped(event);
        int cp = event.codepoint();
        if (cp >= 32 && cp <= 126 && input.length() < 16) {
            char value = (char) cp;
            if (Character.isLetterOrDigit(value) || value == '_') input += value;
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}