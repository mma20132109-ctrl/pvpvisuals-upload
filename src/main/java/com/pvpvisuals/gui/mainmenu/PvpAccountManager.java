package com.pvpvisuals.gui.mainmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pvpvisuals.mixin.MinecraftUserAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/** Persistent local profile list used by the Zenith-style account page. */
public final class PvpAccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("pvpvisuals").resolve("accounts.json");
    private static final java.lang.reflect.Type LIST_TYPE = new TypeToken<List<Profile>>() { }.getType();
    private static final List<Profile> PROFILES = new ArrayList<>();

    private PvpAccountManager() { }

    public static synchronized void load() {
        PROFILES.clear();
        if (Files.isRegularFile(FILE)) {
            try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                List<Profile> loaded = GSON.fromJson(reader, LIST_TYPE);
                if (loaded != null) {
                    for (Profile profile : loaded) {
                        if (valid(profile)) PROFILES.add(profile);
                    }
                }
            } catch (Exception error) {
                System.err.println("[pvpvisuals] Не удалось загрузить список аккаунтов: " + error);
            }
        }
        User current = Minecraft.getInstance().getUser();
        boolean present = PROFILES.stream().anyMatch(p -> p.uuid.equals(current.getProfileId().toString()));
        if (!present) {
            PROFILES.add(new Profile(current.getName(), current.getProfileId().toString(),
                    current.getAccessToken()));
            save();
        }
    }

    public static synchronized int activeIndex() {
        String active = Minecraft.getInstance().getUser().getProfileId().toString();
        for (int i = 0; i < PROFILES.size(); i++) {
            if (active.equals(PROFILES.get(i).uuid)) return i;
        }
        return 0;
    }

    public static synchronized List<Profile> profiles() {
        return List.copyOf(PROFILES);
    }

    public static synchronized boolean addOffline(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) return false;
        if (PROFILES.stream().anyMatch(p -> p.name.equalsIgnoreCase(name))) return false;
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        PROFILES.add(new Profile(name, uuid.toString(), ""));
        save();
        return true;
    }

    public static synchronized boolean select(int index) {
        if (index < 0 || index >= PROFILES.size()) return false;
        Profile profile = PROFILES.get(index);
        try {
            User user = new User(profile.name, UUID.fromString(profile.uuid),
                    profile.accessToken == null ? "" : profile.accessToken,
                    Optional.empty(), Optional.empty());
            ((MinecraftUserAccessor) (Object) Minecraft.getInstance()).pvpvisuals$setUser(user);
            return true;
        } catch (RuntimeException error) {
            System.err.println("[pvpvisuals] Не удалось переключить аккаунт: " + error);
            return false;
        }
    }

    public static synchronized void remove(int index) {
        if (index >= 0 && index < PROFILES.size()) {
            PROFILES.remove(index);
            save();
        }
    }

    public static boolean isActive(Profile profile) {
        try {
            return profile.uuid.equals(Minecraft.getInstance().getUser().getProfileId().toString());
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean valid(Profile profile) {
        if (profile == null || profile.name == null || profile.uuid == null) return false;
        try {
            UUID.fromString(profile.uuid);
            return profile.name.matches("[A-Za-z0-9_]{3,16}");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(PROFILES), StandardCharsets.UTF_8);
        } catch (IOException error) {
            System.err.println("[pvpvisuals] Не удалось сохранить список аккаунтов: " + error);
        }
    }

    public static final class Profile {
        public String name;
        public String uuid;
        public String accessToken;

        public Profile() { }

        public Profile(String name, String uuid, String accessToken) {
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
        }
    }
}
