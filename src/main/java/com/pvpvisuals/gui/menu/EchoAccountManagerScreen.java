package com.pvpvisuals.gui.menu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Локальный менеджер сохранённых офлайн-профилей.
 * Он не подменяет текущую Minecraft-сессию: для этого нужна отдельная безопасная интеграция с auth/session API.
 */
public final class EchoAccountManagerScreen extends Screen {
    private static final Logger LOG = LoggerFactory.getLogger("pvpvisuals/accounts");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pvpvisuals-accounts.properties");

    private final Screen parent;
    private final List<String> accounts = new ArrayList<>();
    private String preferred = "";
    private EditBox nameField;

    public EchoAccountManagerScreen(Screen parent) {
        super(Component.literal("PvPVisuals Accounts"));
        this.parent = parent;
        load();
    }

    private void load() {
        accounts.clear();
        Properties properties = new Properties();
        if (Files.isRegularFile(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                properties.load(in);
            } catch (IOException e) {
                LOG.warn("Could not load saved offline profiles", e);
            }
        }
        preferred = properties.getProperty("preferred", "");
        int count;
        try {
            count = Math.max(0, Math.min(100, Integer.parseInt(properties.getProperty("count", "0"))));
        } catch (NumberFormatException ignored) {
            count = 0;
        }
        for (int i = 0; i < count; i++) {
            String name = properties.getProperty("account." + i, "").trim();
            if (validName(name) && accounts.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                accounts.add(name);
            }
        }
        if (!accounts.contains(preferred)) preferred = "";
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty("count", Integer.toString(accounts.size()));
        properties.setProperty("preferred", preferred);
        for (int i = 0; i < accounts.size(); i++) properties.setProperty("account." + i, accounts.get(i));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                properties.store(out, "PvPVisuals offline profiles");
            }
        } catch (IOException e) {
            LOG.error("Could not save offline profiles", e);
        }
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    private void addProfile() {
        String name = nameField.getValue().trim();
        if (!validName(name) || accounts.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) return;
        accounts.add(name);
        preferred = name;
        save();
        init();
    }

    private void removeProfile(String name) {
        accounts.removeIf(existing -> existing.equalsIgnoreCase(name));
        if (preferred.equalsIgnoreCase(name)) preferred = accounts.isEmpty() ? "" : accounts.get(0);
        save();
        init();
    }

    private void choosePreferred(String name) {
        preferred = name;
        save();
        init();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, this.width - 32);
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(54, this.height / 5);
        nameField = new EditBox(this.font, left, top, panelWidth - 94, 20, Component.literal("Username"));
        nameField.setMaxLength(16);
        nameField.setHint(Component.literal("Minecraft username"));
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addProfile())
            .bounds(left + panelWidth - 86, top, 86, 20).build());

        int rowY = top + 42;
        for (String account : List.copyOf(accounts)) {
            String label = (account.equalsIgnoreCase(preferred) ? "✓  " : "   ") + account;
            addRenderableWidget(Button.builder(Component.literal(label), b -> choosePreferred(account))
                .bounds(left, rowY, panelWidth - 92, 22).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> removeProfile(account))
                .bounds(left + panelWidth - 84, rowY, 36, 22).build());
            rowY += 27;
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 50, this.height - 42, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, "ACCOUNT MANAGER", this.width / 2, 30, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, "Saved offline profiles", this.width / 2, 46, 0xFFB9A5E8);
        if (accounts.isEmpty()) {
            graphics.drawCenteredString(this.font, "No saved profiles yet", this.width / 2, Math.max(140, this.height / 2), 0xFF9990AA);
        }
        graphics.drawCenteredString(this.font, "Preferred profile is only a saved choice; it does not change your login.", this.width / 2,
            this.height - 62, 0xFF9990AA);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
