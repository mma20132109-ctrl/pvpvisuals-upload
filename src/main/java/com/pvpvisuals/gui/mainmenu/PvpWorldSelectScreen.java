package com.pvpvisuals.gui.mainmenu;

import com.mojang.blaze3d.platform.NativeImage;
import com.pvpvisuals.gui.echo.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Singleplayer screen ported from the reference menu's SingleplayerMenuPage.
 *
 * Vanilla still owns world discovery, save validation, confirmation dialogs and
 * world launch. This screen only replaces the presentation with the reference
 * compact world rows, play/delete actions and create-world button.
 */
public final class PvpWorldSelectScreen extends SelectWorldScreen {
    private static final float CONTENT_WIDTH = 431f;
    private static final float MAX_LIST_HEIGHT = 342f;
    private static final float ROW_HEIGHT = 62f;
    private static final float ROW_GAP = 8f;
    private static final float ROW_RADIUS = 12f;
    private static final float ICON_SIZE = 30f;
    private static final float SCROLL_RAIL_WIDTH = 2f;
    private static final DateTimeFormatter LAST_PLAYED =
            DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());

    private final PvpMainMenuScreen menu;
    private final List<WorldSelectionList.WorldListEntry> worldEntries = new ArrayList<>();
    private final Map<String, Identifier> worldIcons = new HashMap<>();
    private final Set<String> iconLoadAttempted = new HashSet<>();

    private WorldSelectionList worldList;
    private int selectedIndex;
    private float scrollOffset;
    private float scrollTarget;
    private float entrance;

    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;
    private float viewportX;
    private float viewportY;
    private float viewportWidth;
    private float viewportHeight;
    private float createX;
    private float createY;

    public PvpWorldSelectScreen(PvpMainMenuScreen menu) {
        super(menu);
        this.menu = menu;
    }

    @Override
    protected void init() {
        super.init();
        updateLayout();
        bindWorldList();
    }

    private void updateLayout() {
        panelWidth = Math.min(CONTENT_WIDTH, Math.max(240f, width - 40f));

        // Keep the title, glass list and create action inside one centered
        // safe area. The previous layout reserved space for menu chrome that
        // is no longer drawn on this screen, pushing the controls apart.
        float titleHeight = 20f;
        float titleGap = 10f;
        float createGap = 14f;
        float createHeight = 34f;
        float verticalMargin = 38f;
        float maxPanelHeight = Math.max(112f,
                height - verticalMargin * 2f - titleHeight - titleGap - createGap - createHeight);
        panelHeight = Math.min(MAX_LIST_HEIGHT, maxPanelHeight);

        float groupHeight = titleHeight + titleGap + panelHeight + createGap + createHeight;
        float groupTop = Math.max(8f, (height - groupHeight) * 0.5f);
        panelX = (width - panelWidth) * 0.5f;
        panelY = groupTop + titleHeight + titleGap;

        viewportX = panelX + 10f;
        viewportY = panelY + 10f;
        viewportWidth = Math.max(120f, panelWidth - 34f);
        viewportHeight = Math.max(84f, panelHeight - 20f);

        createX = (width - 160f) * 0.5f;
        createY = panelY + panelHeight + createGap;
    }

    private void bindWorldList() {
        try {
            WorldSelectionList foundList = null;

            // SelectWorldScreen stores this list as a private field on 1.21.11.
            // Keep the compatibility checks for other supported patch mappings.
            for (var child : children()) {
                if (child instanceof WorldSelectionList found) {
                    foundList = found;
                    break;
                }
            }
            if (foundList == null) {
                for (Class<?> type = getClass(); type != null && foundList == null; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (!WorldSelectionList.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        Object value = field.get(this);
                        if (value instanceof WorldSelectionList found) {
                            foundList = found;
                            break;
                        }
                    }
                }
            }
            if (foundList == null) {
                for (Class<?> type = getClass(); type != null && foundList == null; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (!List.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        Object value = field.get(this);
                        if (value instanceof List<?> list) {
                            for (Object element : list) {
                                if (element instanceof WorldSelectionList found) {
                                    foundList = found;
                                    break;
                                }
                            }
                        }
                        if (foundList != null) break;
                    }
                }
            }

            if (foundList == null) {
                worldList = null;
                worldEntries.clear();
                return;
            }

            worldList = foundList;
            hideVanillaWorldList();
            refreshWorldEntries();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            worldList = null;
            worldEntries.clear();
            System.err.println("[pvpvisuals] Не удалось подключить список миров: " + exception);
        }
    }

    /**
     * Hide vanilla rows and buttons from the visible/input passes. The list
     * itself is still polled below under an empty scissor so vanilla's async
     * world-summary load can finish and the original WorldListEntry actions
     * remain available to the custom rows.
     */
    private void hideVanillaWorldList() {
        if (worldList != null) {
            worldList.visible = false;
            worldList.active = false;
            worldList.setAlpha(0f);
        }
        for (var child : children()) {
            if (child instanceof AbstractWidget widget) {
                widget.visible = false;
                widget.active = false;
                widget.setAlpha(0f);
            }
        }
    }

    private void renderWorldListForLoading(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (worldList == null) return;
        graphics.enableScissor(0, 0, 0, 0);
        try {
            worldList.renderWidget(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.disableScissor();
        }
    }

    private void refreshWorldEntries() {
        if (worldList == null) bindWorldList();
        if (worldList == null) return;

        List<WorldSelectionList.WorldListEntry> latest = new ArrayList<>();
        for (WorldSelectionList.Entry entry : worldList.children()) {
            if (entry instanceof WorldSelectionList.WorldListEntry world
                    && !world.getLevelSummary().isDisabled()) {
                latest.add(world);
            }
        }

        if (!latest.equals(worldEntries)) {
            WorldSelectionList.Entry current = worldList.getSelected();
            worldEntries.clear();
            worldEntries.addAll(latest);
            selectedIndex = Math.max(0, Math.min(worldEntries.size() - 1, selectedIndex));
            int selected = worldEntries.indexOf(current);
            if (selected >= 0) selectedIndex = selected;
            scrollTarget = Math.min(scrollTarget, maxScroll());
            scrollOffset = Math.min(scrollOffset, maxScroll());
            if (!worldEntries.isEmpty()) worldList.setSelected(worldEntries.get(selectedIndex));
        }
    }

    private float contentHeight() {
        return worldEntries.isEmpty() ? 0f : worldEntries.size() * ROW_HEIGHT + (worldEntries.size() - 1) * ROW_GAP;
    }

    private float maxScroll() {
        return Math.max(0f, contentHeight() - viewportHeight);
    }

    private void selectWorld(int index) {
        if (worldEntries.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(worldEntries.size() - 1, index));
        worldList.setSelected(worldEntries.get(selectedIndex));
        ensureSelectedVisible();
    }

    private void ensureSelectedVisible() {
        float top = selectedIndex * (ROW_HEIGHT + ROW_GAP);
        float bottom = top + ROW_HEIGHT;
        if (top < scrollTarget) scrollTarget = top;
        else if (bottom > scrollTarget + viewportHeight) scrollTarget = bottom - viewportHeight;
        scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget));
    }

    private void playWorld(WorldSelectionList.WorldListEntry entry) {
        LevelSummary summary = entry.getLevelSummary();
        if (summary.isLocked() || summary.requiresManualConversion() || summary.isDisabled()) return;
        entry.joinWorld();
    }

    private void deleteWorld(WorldSelectionList.WorldListEntry entry) {
        // Vanilla opens its own delete-confirmation flow and performs the safe
        // session-lock deletion. The next refresh picks up the updated list.
        entry.deleteWorld();
    }

    private void createWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        CreateWorldScreen.openFresh(minecraft, () -> minecraft.setScreen(this));
    }

    private Identifier worldIcon(LevelSummary summary) {
        String levelId = summary.getLevelId();
        Identifier cached = worldIcons.get(levelId);
        if (cached != null) return cached;
        if (!iconLoadAttempted.add(levelId)) return null;

        Path iconPath = summary.getIcon();
        if (iconPath == null || !Files.isRegularFile(iconPath)) return null;

        try (InputStream stream = Files.newInputStream(iconPath)) {
            NativeImage image = NativeImage.read(stream);
            DynamicTexture texture = new DynamicTexture(() -> "pvpvisuals_world_" + levelId, image);
            texture.upload();
            String textureName = UUID.nameUUIDFromBytes(levelId.getBytes(StandardCharsets.UTF_8)).toString();
            Identifier identifier = Identifier.fromNamespaceAndPath("pvpvisuals", "world_icons/" + textureName);
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
            worldIcons.put(levelId, identifier);
            return identifier;
        } catch (Exception exception) {
            System.err.println("[pvpvisuals] Не удалось загрузить иконку мира " + levelId + ": " + exception);
            return null;
        }
    }

    private String worldDetails(LevelSummary summary) {
        String mode = summary.getGameMode().getLongDisplayName().getString();
        String lastPlayed = summary.getLastPlayed() <= 0L
                ? "—"
                : LAST_PLAYED.format(Instant.ofEpochMilli(summary.getLastPlayed()));
        if (summary.isHardcore()) mode = "Хардкор";
        if (summary.isLocked()) return "Мир занят другим процессом";
        if (summary.requiresManualConversion()) return "Требуется обновление мира";
        return mode + "  •  " + lastPlayed;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        menu.renderWorldSelectBackground(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLayout();
        if (worldList == null) bindWorldList();
        renderWorldListForLoading(graphics, mouseX, mouseY, partialTick);
        refreshWorldEntries();

        entrance = Math.min(1f, entrance + 0.045f);
        scrollOffset += (scrollTarget - scrollOffset) * 0.24f;

        // This page owns its complete presentation. Calling SelectWorldScreen/Screen.render
        // also paints vanilla header/footer chrome, which leaked above and below our glass UI.
        // Render only the custom backdrop and custom world cards instead.
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderWorldList(graphics, mouseX, mouseY);
        renderCreateButton(graphics, mouseX, mouseY);
        // This is a dedicated singleplayer page, not the main menu underneath:
        // do not draw the main menu's top badge, side pill or bottom navigation.
        Gfx.alpha = 1f;
    }

    private void renderWorldList(GuiGraphics graphics, int mouseX, int mouseY) {
        float titleSize = 9.5f;
        String title = "ОДИНОЧНАЯ ИГРА";
        float titleWidth = Gfx.widthAtSize(font, title, titleSize);
        Gfx.textAtSize(graphics, font, title, (width - titleWidth) * 0.5f,
                panelY - 26f, titleSize, 0xFFF0F2FA, false);

        Gfx.glass(graphics, panelX, panelY, panelWidth, panelHeight, 13f,
                0x82394359, 0x7020293B, 0x55FFFFFF);

        if (worldEntries.isEmpty()) {
            String empty = "Миров пока нет";
            String hint = "Создай новый мир, чтобы начать игру";
            float ew = Gfx.widthAtSize(font, empty, 7.6f);
            float hw = Gfx.widthAtSize(font, hint, 5.6f);
            Gfx.textAtSize(graphics, font, empty,
                    panelX + (panelWidth - ew) * 0.5f,
                    viewportY + viewportHeight * 0.43f, 7.6f, 0xFFE8ECF6, false);
            Gfx.textAtSize(graphics, font, hint,
                    panelX + (panelWidth - hw) * 0.5f,
                    viewportY + viewportHeight * 0.43f + 17f, 5.6f, 0xFF9CA8BE, false);
            return;
        }

        graphics.enableScissor(
                Math.round(viewportX), Math.round(viewportY),
                Math.round(viewportX + viewportWidth), Math.round(viewportY + viewportHeight));
        try {
            for (int index = 0; index < worldEntries.size(); index++) {
                float rowY = viewportY + index * (ROW_HEIGHT + ROW_GAP) - scrollOffset;
                if (rowY + ROW_HEIGHT < viewportY || rowY > viewportY + viewportHeight) continue;

                WorldSelectionList.WorldListEntry entry = worldEntries.get(index);
                LevelSummary summary = entry.getLevelSummary();
                boolean selected = index == selectedIndex;
                boolean hovered = mouseX >= viewportX && mouseX <= viewportX + viewportWidth
                        && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
                boolean unavailable = summary.isLocked() || summary.requiresManualConversion();

                Gfx.alpha = entrance;
                if (selected) Gfx.glow(graphics, viewportX, rowY, viewportWidth, ROW_HEIGHT,
                        ROW_RADIUS, 0x408C9EFF, 4f);
                Gfx.glass(graphics, viewportX, rowY, viewportWidth, ROW_HEIGHT, ROW_RADIUS,
                        selected ? 0xB343536F : (hovered ? 0x9A38475D : 0x86303D51),
                        selected ? 0x921D293B : 0x7817202D,
                        selected ? 0x778C9EFF : 0x30FFFFFF);

                float iconX = viewportX + 15f;
                float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5f;
                Gfx.glass(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE, 7f,
                        selected ? 0x8A536B91 : 0x68404D65,
                        0x55303A4D, selected ? 0x668C9EFF : 0x28FFFFFF);

                Identifier icon = worldIcon(summary);
                if (icon != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                            Math.round(iconX + 1f), Math.round(iconY + 1f),
                            0f, 0f, 28, 28, 64, 64, 64, 64, 0xFFFFFFFF);
                } else {
                    String emblem = "✦";
                    float emblemWidth = Gfx.widthAtSize(font, emblem, 13f);
                    Gfx.textAtSize(graphics, font, emblem,
                            iconX + (ICON_SIZE - emblemWidth) * 0.5f,
                            iconY + 7f, 13f, 0xFFDCE6FF, false);
                }

                float textX = viewportX + 58f;
                String name = summary.getLevelName();
                if (name == null || name.isBlank()) name = summary.getLevelId();
                name = trim(name, Math.round(viewportWidth - 150f), 7.4f);
                Gfx.textAtSize(graphics, font, name, textX, rowY + 16f,
                        7.4f, 0xFFF0F2FA, false);

                String details = trim(worldDetails(summary), Math.round(viewportWidth - 150f), 5.4f);
                Gfx.textAtSize(graphics, font, details, textX, rowY + 36f,
                        5.4f, unavailable ? 0xFFFFC68A : 0xFFA9B4C8, false);

                float deleteX = viewportX + viewportWidth - 55f;
                float playX = viewportX + viewportWidth - 27f;
                float actionY = rowY + (ROW_HEIGHT - 16f) * 0.5f;
                boolean deleteHover = Gfx.hit(mouseX, mouseY, Math.round(deleteX - 3f),
                        Math.round(actionY - 4f), 22, 24);
                boolean playHover = Gfx.hit(mouseX, mouseY, Math.round(playX - 3f),
                        Math.round(actionY - 4f), 22, 24);

                Gfx.circle(graphics, deleteX + 5f, actionY + 7f, 9f,
                        deleteHover ? 0x553D2732 : 0x30242C3A);
                Gfx.textAtSize(graphics, font, "×", deleteX, actionY + 1f,
                        11f, deleteHover ? 0xFFFFB6BA : 0xFF9BA6BA, false);

                Gfx.circle(graphics, playX + 5f, actionY + 7f, 9f,
                        playHover ? 0x668C9EFF : 0x3D536C8E);
                String playGlyph = unavailable ? "!" : "▶";
                float playGlyphWidth = Gfx.widthAtSize(font, playGlyph, 6.8f);
                Gfx.textAtSize(graphics, font, playGlyph,
                        playX + 5f - playGlyphWidth * 0.5f, actionY + 4f,
                        6.8f, unavailable ? 0xFFFFC68A : 0xFFE5EDFF, false);
                Gfx.alpha = 1f;
            }
        } finally {
            graphics.disableScissor();
        }

        float totalHeight = contentHeight();
        if (totalHeight > viewportHeight) {
            float railX = panelX + panelWidth - 12f;
            Gfx.fill(graphics, Math.round(railX), Math.round(viewportY),
                    Math.round(SCROLL_RAIL_WIDTH), Math.round(viewportHeight), 1, 0x553C4659);
            float thumbHeight = Math.max(22f, viewportHeight * viewportHeight / totalHeight);
            float thumbTravel = viewportHeight - thumbHeight;
            float progress = maxScroll() <= 0f ? 0f : scrollOffset / maxScroll();
            Gfx.fill(graphics, Math.round(railX), Math.round(viewportY + thumbTravel * progress),
                    Math.round(SCROLL_RAIL_WIDTH), Math.round(thumbHeight), 1, 0xB7AFC4E8);
        }

        String counter = (worldEntries.size() == 1 ? "1 мир" : worldEntries.size() + " миров");
        float counterWidth = Gfx.widthAtSize(font, counter, 5.4f);
        Gfx.textAtSize(graphics, font, counter,
                panelX + panelWidth - counterWidth - 15f, panelY + panelHeight - 5f,
                5.4f, 0xFF8E9AAF, false);
    }

    private void renderCreateButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = Gfx.hit(mouseX, mouseY, Math.round(createX), Math.round(createY), 160, 34);
        Gfx.glass(graphics, createX, createY, 160f, 34f, 10f,
                hovered ? 0xB24C6389 : 0x88374762,
                hovered ? 0x8731425F : 0x6A1B2638,
                hovered ? 0x888C9EFF : 0x45FFFFFF);
        String label = "＋  Создать мир";
        float labelWidth = Gfx.widthAtSize(font, label, 6.8f);
        Gfx.textAtSize(graphics, font, label,
                createX + (160f - labelWidth) * 0.5f, createY + 12f,
                6.8f, 0xFFF1F4FC, false);
    }

    private String trim(String value, int maxWidth, float size) {
        if (Gfx.widthAtSize(font, value, size) <= maxWidth) return value;
        String result = value;
        while (result.length() > 1 && Gfx.widthAtSize(font, result + "…", size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX <= viewportX + viewportWidth
                && mouseY >= viewportY && mouseY <= viewportY + viewportHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0
                && Gfx.hit(event.x(), event.y(), Math.round(createX), Math.round(createY), 160, 34)) {
            createWorld();
            return true;
        }

        if (event.button() == 0 && insideViewport(event.x(), event.y())) {
            float relativeY = (float) event.y() - viewportY + scrollOffset;
            int index = (int) (relativeY / (ROW_HEIGHT + ROW_GAP));
            if (index >= 0 && index < worldEntries.size()) {
                float rowTop = viewportY + index * (ROW_HEIGHT + ROW_GAP) - scrollOffset;
                if (event.y() <= rowTop + ROW_HEIGHT) {
                    selectWorld(index);
                    WorldSelectionList.WorldListEntry entry = worldEntries.get(index);
                    float deleteX = viewportX + viewportWidth - 55f;
                    float playX = viewportX + viewportWidth - 27f;
                    float actionY = rowTop + (ROW_HEIGHT - 16f) * 0.5f;
                    if (Gfx.hit(event.x(), event.y(), Math.round(deleteX - 3f),
                            Math.round(actionY - 4f), 22, 24)) {
                        deleteWorld(entry);
                    } else if (Gfx.hit(event.x(), event.y(), Math.round(playX - 3f),
                            Math.round(actionY - 4f), 22, 24)) {
                        playWorld(entry);
                    } else {
                        // The reference page launches a world when its row is pressed.
                        playWorld(entry);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideViewport(mouseX, mouseY)) {
            scrollTarget = Math.max(0f, Math.min(maxScroll(), scrollTarget - (float) scrollY * (ROW_HEIGHT + ROW_GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_LEFT) {
            selectWorld(selectedIndex - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN || event.key() == GLFW.GLFW_KEY_RIGHT) {
            selectWorld(selectedIndex + 1);
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && !worldEntries.isEmpty()) {
            playWorld(worldEntries.get(selectedIndex));
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE && !worldEntries.isEmpty()) {
            deleteWorld(worldEntries.get(selectedIndex));
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
