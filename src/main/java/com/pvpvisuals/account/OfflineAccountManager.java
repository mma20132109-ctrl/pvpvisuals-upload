package com.pvpvisuals.account;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import com.pvpvisuals.mixin.MinecraftUserAccessor;

/**
 * Local offline-profile list. This deliberately does not store Microsoft passwords,
 * access tokens, or authentication data.
 */
public final class OfflineAccountManager {
    private static final OfflineAccountManager INSTANCE = new OfflineAccountManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private final Path file = FabricLoader.getInstance().getConfigDir()
            .resolve("pvpvisuals").resolve("alts.json");
    private final List<String> accounts = new ArrayList<>();

    private OfflineAccountManager() {
        load();
    }

    public static OfflineAccountManager get() {
        return INSTANCE;
    }

    public synchronized List<String> getAccounts() {
        return List.copyOf(accounts);
    }

    public String currentName() {
        User user = Minecraft.getInstance().getUser();
        return user == null ? "Player" : user.getName();
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public synchronized boolean add(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!isValidName(name) || contains(name)) {
            return false;
        }
        accounts.add(name);
        save();
        return true;
    }

    public synchronized void remove(String name) {
        if (name != null && accounts.removeIf(existing -> existing.equalsIgnoreCase(name))) {
            save();
        }
    }

    public synchronized String randomName(String base) {
        StringBuilder prefix = new StringBuilder();
        if (base != null) {
            for (char c : base.trim().toCharArray()) {
                if (Character.isLetterOrDigit(c) || c == '_') {
                    prefix.append(c);
                }
                if (prefix.length() == 8) break;
            }
        }
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder candidate = new StringBuilder(prefix);
            int suffixLength = Math.max(4, Math.min(8, 16 - candidate.length()));
            int targetLength = Math.min(16, Math.max(8, candidate.length() + suffixLength));
            while (candidate.length() < targetLength) {
                candidate.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String value = candidate.toString();
            if (isValidName(value) && !contains(value)) {
                return value;
            }
        }
        return "Player" + RANDOM.nextInt(1_000_000);
    }

    public void switchTo(String name) {
        if (!isValidName(name)) return;
        UUID offlineId = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        User offlineUser = new User(name, offlineId, "0",
                java.util.Optional.empty(), java.util.Optional.empty());
        ((MinecraftUserAccessor) (Object) Minecraft.getInstance()).pvpvisuals$setUser(offlineUser);
    }

    private boolean contains(String name) {
        return accounts.stream().anyMatch(existing -> existing.equalsIgnoreCase(name));
    }

    private synchronized void load() {
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray stored = root.has("accounts") && root.get("accounts").isJsonArray()
                    ? root.getAsJsonArray("accounts") : new JsonArray();
            for (JsonElement element : stored) {
                if (!element.isJsonPrimitive()) continue;
                String name = element.getAsString().trim();
                if (isValidName(name) && !contains(name)) accounts.add(name);
            }
        } catch (Exception exception) {
            System.err.println("[pvpvisuals] Could not read offline accounts: " + exception.getMessage());
        }
    }

    private synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray stored = new JsonArray();
        for (String account : accounts) stored.add(account);
        root.add("accounts", stored);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException exception) {
            System.err.println("[pvpvisuals] Could not save offline accounts: " + exception.getMessage());
        }
    }
}