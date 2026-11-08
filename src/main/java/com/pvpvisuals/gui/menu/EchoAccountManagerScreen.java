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
    private static final int MAX_PROFILES = 100;
    private static final int MAX_VISIBLE_PROFILES = 6;

    private final Screen parent;
    private final List<String> accounts = new ArrayList<>();
    private String preferred = "";
    private EditBox nameField;
    private int page;
    private int pageSize = MAX_VISIBLE_PROFILES;
    private String status = "";
    private boolean statusError;

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
                setStatus("Could not read saved profiles", true);
            }
        }

        preferred = properties.getProperty("preferred", "");
        int count;
        try {
            count = Math.max(0, Math.min(MAX_PROFILES, Integer.parseInt(properties.getProperty("count", "0"))));
        } catch (NumberFormatException ignored) {
            count = 0;
        }

        for (int i = 0; i < count; i++) {
            String name = properties.getProperty("account." + i, "").trim();
            if (validName(name) && accounts.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                accounts.add(name);
            }
        }
        if (accounts.stream().noneMatch(name -> name.equalsIgnoreCase(preferred))) preferred = "";
    }

    private boolean save() {
        Properties properties = new Properties();
        properties.setProperty("count", Integer.toString(accounts.size()));
        properties.setProperty("preferred", preferred);
        for (int i = 0; i < accounts.size(); i++) {
            properties.setProperty("account." + i, accounts.get(i));
        }

        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                properties.store(out, "PvPVisuals offline profiles");
            }
            return true;
        } catch (IOException e) {
            LOG.error("Could not save offline profiles", e);
            setStatus("Could not save profiles; check the game config folder", true);
            return false;
        }
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    private void setStatus(String message, boolean error) {
        status = message;
        statusError = error;
    }

    private void addProfile() {
        String name = nameField.getValue().trim();
        if (!validName(name)) {
            setStatus("Use 3-16 characters: A-Z, 0-9 or _", true);
            return;
        }
        if (accounts.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
            setStatus("That profile is already saved", true);
            return;
        }
        if (accounts.size() >= MAX_PROFILES) {
            setStatus("Profile limit reached (" + MAX_PROFILES + ")", true);
            return;
        }

        accounts.add(name);
        preferred = name;
        page = (accounts.size() - 1) / Math.max(1, pageSize);
        if (save()) setStatus("Saved " + name + " as the preferred profile", false);
        init();
    }

    private void removeProfile(String name) {
        accounts.removeIf(existing -> existing.equalsIgnoreCase(name));
        if (preferred.equalsIgnoreCase(name)) preferred = accounts.isEmpty() ? "" : accounts.get(0);
        page = Math.min(page, Math.max(0, pageCount() - 1));
        if (save()) setStatus("Removed " + name, false);
        init();
    }

    private void choosePreferred(String name) {
        preferred = name;
        if (save()) setStatus(name + " is now the preferred saved profile", false);
        init();
    }

    private int pageCount() {
        return Math.max(1, (accounts.size() + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(pageCount() - 1, page + direction));
        init();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, Math.max(220, this.width - 32));
        int left = (this.width - panelWidth) / 2;
        int top = Math.min(120, Math.max(54, this.height / 5));
        int listTop = top + 42;
        int footerTop = this.height - 92;
        pageSize = Math.max(1, Math.min(MAX_VISIBLE_PROFILES, (footerTop - listTop - 24) / 27));
        page = Math.max(0, Math.min(page, pageCount() - 1));

        nameField = new EditBox(this.font, left, top, panelWidth - 94, 20, Component.literal("Username"));
        nameField.setMaxLength(16);
        nameField.setHint(Component.literal("Minecraft username"));
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addProfile())
            .bounds(left + panelWidth - 86, top, 86, 20).build());

        int start = page * pageSize;
        int end = Math.min(accounts.size(), start + pageSize);
        int rowY = listTop;
        for (int i = start; i < end; i++) {
            String account = accounts.get(i);
            boolean isPreferred = account.equalsIgnoreCase(preferred);
            String label = (isPreferred ? "★  " : "   ") + account;
            addRenderableWidget(Button.builder(Component.literal(label), b -> choosePreferred(account))
                .bounds(left, rowY, panelWidth - 92, 22).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> removeProfile(account))
                .bounds(left + panelWidth - 84, rowY, 36, 22).build());
            rowY += 27;
        }

        int pagerY = rowY + 3;
        addRenderableWidget(Button.builder(Component.literal("Prev"), b -> changePage(-1))
            .bounds(left, pagerY, 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next"), b -> changePage(1))
            .bounds(left + panelWidth - 54, pagerY, 54, 20).build());
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
        } else {
            graphics.drawCenteredString(this.font, "Page " + (page + 1) + " / " + pageCount()
                + "  •  " + accounts.size() + " / " + MAX_PROFILES + " profiles", this.width / 2,
                Math.min(this.height - 96, this.height / 5 + 42 + pageSize * 27 + 4), 0xFF9990AA);
        }

        graphics.drawCenteredString(this.font, "Saved choice only — it does not change your Minecraft login.",
            this.width / 2, this.height - 75, 0xFF9990AA);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height - 62,
                statusError ? 0xFFFF8585 : 0xFF9FE0B4);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
