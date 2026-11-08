package com.pvpvisuals.gui.mainmenu;

import com.pvpvisuals.gui.echo.Gfx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom glass carousel for worlds, while delegating world operations to vanilla entries.
 */
public final class PvpWorldSelectScreen extends SelectWorldScreen {
    private final PvpMainMenuScreen menu;
    private final List<AbstractWidget> actionButtons = new ArrayList<>();
    private final List<WorldSelectionList.WorldListEntry> worldEntries = new ArrayList<>();
    private static final int ACTION_WIDTH = 108;
    private static final int ACTION_HEIGHT = 27;
    private static final int ACTION_GAP = 8;

    private WorldSelectionList worldList;
    private int worldIndex;
    private float worldPosition;

    public PvpWorldSelectScreen(PvpMainMenuScreen menu) {
        super(menu);
        this.menu = menu;
    }

    @Override
    protected void init() {
        super.init();
        actionButtons.clear();
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.getY() > height - 80) {
                actionButtons.add(widget);
            }
        }

        int count = actionButtons.size();
        if (count > 0) {
            int totalWidth = count * ACTION_WIDTH + (count - 1) * ACTION_GAP;
            int startX = (width - totalWidth) / 2;
            int rowY = height - 101;
            for (int i = 0; i < count; i++) {
                AbstractWidget widget = actionButtons.get(i);
                widget.setWidth(ACTION_WIDTH);
                widget.setHeight(ACTION_HEIGHT);
                widget.setX(startX + i * (ACTION_WIDTH + ACTION_GAP));
                widget.setY(rowY);
                widget.setAlpha(0.0f);
            }
        }

        bindWorldList();
    }

    private void bindWorldList() {
        try {
            Field field = SelectWorldScreen.class.getDeclaredField("list");
            field.setAccessible(true);
            worldList = (WorldSelectionList) field.get(this);
            if (worldList == null) return;
            worldList.setAlpha(0f);
            hideVanillaWorldList();
            worldEntries.clear();
            for (WorldSelectionList.Entry entry : worldList.children()) {
                if (entry instanceof WorldSelectionList.WorldListEntry world) {
                    worldEntries.add(world);
                }
            }
            if (!worldEntries.isEmpty()) {
                WorldSelectionList.Entry selected = worldList.getSelected();
                int selectedIndex = worldEntries.indexOf(selected);
                worldIndex = selectedIndex >= 0 ? selectedIndex : Math.min(worldIndex, worldEntries.size() - 1);
                worldList.setSelected(worldEntries.get(worldIndex));
                worldPosition = worldIndex;
            } else {
                worldIndex = 0;
                worldPosition = 0f;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            worldList = null;
            worldEntries.clear();
            System.err.println("[pvpvisuals] Не удалось подключить карусель миров: " + exception);
        }
    }

    /**
     * Remove only the vanilla list from Screen's render pass. Keep it in the
     * event children so vanilla selection and action-button state still work.
     */
    @SuppressWarnings("unchecked")
    private void hideVanillaWorldList() throws ReflectiveOperationException {
        Field renderablesField = Screen.class.getDeclaredField("renderables");
        renderablesField.setAccessible(true);
        List<Renderable> renderables = (List<Renderable>) renderablesField.get(this);
        renderables.remove(worldList);
    }

    private void refreshWorldEntries() {
        if (worldList == null) return;
        List<WorldSelectionList.WorldListEntry> latest = new ArrayList<>();
        for (WorldSelectionList.Entry entry : worldList.children()) {
            if (entry instanceof WorldSelectionList.WorldListEntry world) latest.add(world);
        }
        if (latest.size() != worldEntries.size()) {
            worldEntries.clear();
            worldEntries.addAll(latest);
            worldIndex = Math.max(0, Math.min(worldEntries.size() - 1, worldIndex));
            worldPosition = worldIndex;
            if (!worldEntries.isEmpty()) worldList.setSelected(worldEntries.get(worldIndex));
        }
    }

    private void moveWorldSelection(int delta) {
        refreshWorldEntries();
        if (worldEntries.isEmpty()) return;
        worldIndex = Math.max(0, Math.min(worldEntries.size() - 1, worldIndex + delta));
        worldList.setSelected(worldEntries.get(worldIndex));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        menu.renderWorldSelectBackground(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshWorldEntries();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderWorldCarousel(graphics, mouseX, mouseY, partialTick);
        renderMinimalActions(graphics, mouseX, mouseY);
        menu.renderWorldSelectDock(graphics, mouseX, mouseY, partialTick);
    }

    private void renderWorldCarousel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float target = worldIndex;
        worldPosition += (target - worldPosition) * 0.22f;
        if (Math.abs(target - worldPosition) < 0.001f) worldPosition = target;

        float centerX = width / 2f;
        float centerY = Math.min(height * 0.48f, height - 185f);
        Gfx.textAtSize(graphics, font, "МИРЫ", centerX - Gfx.widthAtSize(font, "МИРЫ", 10f) / 2f,
                centerY - 105f, 10f, 0xFFF0F2FA, false);
        if (worldEntries.isEmpty()) {
            String empty = "Миров пока нет";
            Gfx.glass(graphics, centerX - 112f, centerY - 58f, 224f, 116f, 12f,
                    0x85202634, 0x7610141D, 0x37FFFFFF);
            Gfx.textAtSize(graphics, font, empty,
                    centerX - Gfx.widthAtSize(font, empty, 7f) / 2f, centerY - 3f,
                    7f, 0xFFB9C3D7, false);
            return;
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < worldEntries.size(); i++) {
            if (Math.abs(i - worldPosition) <= 2.1f) order.add(i);
        }
        order.sort((a, b) -> Float.compare(
                Math.abs(b - worldPosition), Math.abs(a - worldPosition)));

        for (int index : order) {
            WorldSelectionList.WorldListEntry entry = worldEntries.get(index);
            LevelSummary summary = entry.getLevelSummary();
            float distance = index - worldPosition;
            float abs = Math.abs(distance);
            float scale = Math.max(0.70f, 1f - abs * 0.15f);
            float cardW = 150f * scale;
            float cardH = 142f * scale;
            float spacing = abs <= 1f ? abs * 132f : 132f + (abs - 1f) * 78f;
            float cardX = centerX + Math.signum(distance) * spacing - cardW / 2f;
            float cardY = centerY - cardH / 2f + abs * 5f;
            float fade = abs <= 1.4f ? 1f : Math.max(0.42f, 1f - (abs - 1.4f) * 0.42f);
            boolean selected = index == worldIndex;
            boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= cardY && mouseY <= cardY + cardH;

            Gfx.alpha = fade;
            if (selected) Gfx.glow(graphics, cardX, cardY, cardW, cardH, 11f, 0x358C9EFF, 5f);
            Gfx.glass(graphics, cardX, cardY, cardW, cardH, 11f,
                    selected ? 0xB03B4965 : 0x85202634,
                    selected ? 0x8A151E2D : 0x7410141D,
                    selected ? 0x778C9EFF : 0x35FFFFFF);

            Gfx.circle(graphics, centerX + Math.signum(distance) * spacing,
                    cardY + 43f * scale, 25f * scale,
                    selected ? 0x5A8C9EFF : 0x403F526E);
            String emblem = "✦";
            Gfx.textAtSize(graphics, font, emblem,
                    centerX + Math.signum(distance) * spacing
                            - Gfx.widthAtSize(font, emblem, 20f * scale) / 2f,
                    cardY + 35f * scale, 20f * scale, 0xFFDCE6FF, false);

            String name = entry.getLevelName();
            if (name == null || name.isBlank()) name = summary.getLevelId();
            name = trim(name, Math.round(cardW - 18f), 7f * scale);
            float nameWidth = Gfx.widthAtSize(font, name, 7f * scale);
            Gfx.textAtSize(graphics, font, name,
                    cardX + (cardW - nameWidth) / 2f, cardY + 78f * scale,
                    7f * scale, 0xFFF0F2FA, false);

            String id = summary.getLevelId();
            id = trim(id, Math.round(cardW - 20f), 4.8f * scale);
            float idWidth = Gfx.widthAtSize(font, id, 4.8f * scale);
            Gfx.textAtSize(graphics, font, id,
                    cardX + (cardW - idWidth) / 2f, cardY + 92f * scale,
                    4.8f * scale, 0xFF9CA8BE, false);

            if (selected) {
                Gfx.glass(graphics, cardX + 15f, cardY + cardH - 27f, cardW - 30f, 18f, 6f,
                        0x78475270, 0x50202A3A, 0x558C9EFF);
                String state = "ВЫБРАН";
                float stateWidth = Gfx.widthAtSize(font, state, 5f * scale);
                Gfx.textAtSize(graphics, font, state,
                        centerX + Math.signum(distance) * spacing - stateWidth / 2f,
                        cardY + cardH - 21f, 5f * scale, 0xFFE8EEFF, false);
            } else if (hovered) {
                Gfx.glass(graphics, cardX + 15f, cardY + cardH - 27f, cardW - 30f, 18f, 6f,
                        0x553B4963, 0x40151B27, 0x32FFFFFF);
            }
            Gfx.alpha = 1f;
        }

        String counter = (worldIndex + 1) + " / " + worldEntries.size();
        float counterWidth = Gfx.widthAtSize(font, counter, 5.8f);
        Gfx.textAtSize(graphics, font, counter, centerX - counterWidth / 2f,
                centerY + 91f, 5.8f, 0xFF9CA8BE, false);
        if (worldEntries.size() > 1) {
            Gfx.textAtSize(graphics, font, "‹", centerX - 190f, centerY - 8f,
                    20f, 0xFFDCE6FF, false);
            Gfx.textAtSize(graphics, font, "›", centerX + 180f, centerY - 8f,
                    20f, 0xFFDCE6FF, false);
        }
    }

    private String trim(String value, int maxWidth, float size) {
        if (Gfx.widthAtSize(font, value, size) <= maxWidth) return value;
        String result = value;
        while (result.length() > 1 && Gfx.widthAtSize(font, result + "…", size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private void renderMinimalActions(GuiGraphics graphics, int mouseX, int mouseY) {
        for (AbstractWidget widget : actionButtons) {
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();
            boolean hovered = widget.active && mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + h;
            String label = widget.getMessage().getString();
            Gfx.glass(graphics, x, y, w, h, 8f,
                    hovered ? 0xB83A4963 : 0x8830394D,
                    hovered ? 0xA51B2638 : 0x76151D2A,
                    hovered ? 0x668C9EFF : 0x32FFFFFF);
            int textWidth = Gfx.widthAtSize(font, label, 6.2f);
            Gfx.textAtSize(graphics, font, label, x + (w - textWidth) / 2f,
                    y + (h - 7f) / 2f, 6.2f,
                    hovered ? 0xFFFFFFFF : 0xFFE4EAF6, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && menu.handleDockClick(event.x(), event.y())) return true;
        if (event.button() == 0 && !worldEntries.isEmpty()) {
            float centerX = width / 2f;
            float centerY = Math.min(height * 0.48f, height - 185f);
            for (int i = 0; i < worldEntries.size(); i++) {
                float distance = i - worldPosition;
                float abs = Math.abs(distance);
                if (abs > 2.1f) continue;
                float scale = Math.max(0.70f, 1f - abs * 0.15f);
                float cardW = 150f * scale;
                float cardH = 142f * scale;
                float spacing = abs <= 1f ? abs * 132f : 132f + (abs - 1f) * 78f;
                float cardX = centerX + Math.signum(distance) * spacing - cardW / 2f;
                float cardY = centerY - cardH / 2f + abs * 5f;
                if (event.x() >= cardX && event.x() <= cardX + cardW
                        && event.y() >= cardY && event.y() <= cardY + cardH) {
                    worldIndex = i;
                    worldList.setSelected(worldEntries.get(i));
                    if (doubleClick) worldEntries.get(i).joinWorld();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!worldEntries.isEmpty()) {
            moveWorldSelection(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            moveWorldSelection(-1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            moveWorldSelection(1);
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && !worldEntries.isEmpty()) {
            worldEntries.get(worldIndex).joinWorld();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        super.removed();
        menu.setWorldSelectPage(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
