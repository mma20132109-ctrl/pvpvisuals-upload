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
import java.util.function.Consumer;
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
            Profile profile = new Profile(current.getName(), current.getProfileId().toString(),
                    current.getAccessToken());
            profile.type = "CURRENT";
            PROFILES.add(profile);
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
        Profile profile = new Profile(name, uuid.toString(), "");
        profile.type = "OFFLINE";
        PROFILES.add(profile);
        save();
        return true;
    }

    public static synchronized boolean addMicrosoft(PvpMicrosoftAuth.AuthenticatedProfile authenticated) {
        if (authenticated == null || authenticated.name() == null || authenticated.uuid() == null) return false;
        for (Profile existing : PROFILES) {
            if (authenticated.uuid().toString().equals(existing.uuid)) {
                existing.name = authenticated.name();
                existing.accessToken = authenticated.minecraftAccessToken();
                existing.refreshToken = authenticated.refreshToken();
                existing.type = "MICROSOFT";
                save();
                return true;
            }
        }
        Profile profile = new Profile(authenticated.name(), authenticated.uuid().toString(),
                authenticated.minecraftAccessToken());
        profile.refreshToken = authenticated.refreshToken();
        profile.type = "MICROSOFT";
        PROFILES.add(profile);
        save();
        return true;
    }

    public static synchronized boolean select(int index, Consumer<String> status) {
        if (index < 0 || index >= PROFILES.size()) return false;
        Profile profile = PROFILES.get(index);
        if ("MICROSOFT".equals(profile.type) && profile.refreshToken != null && !profile.refreshToken.isBlank()) {
            status.accept("Обновление Microsoft-аккаунта...");
            PvpMicrosoftAuth.refresh(profile.refreshToken,
                    message -> Minecraft.getInstance().execute(() -> status.accept(message)),
                    () -> false).whenComplete((authenticated, error) -> Minecraft.getInstance().execute(() -> {
                        if (error != null || authenticated == null) {
                            status.accept("Не удалось обновить аккаунт: " + PvpAccountManager.rootMessage(error));
                            return;
                        }
                        synchronized (PvpAccountManager.class) {
                            profile.name = authenticated.name();
                            profile.uuid = authenticated.uuid().toString();
                            profile.accessToken = authenticated.minecraftAccessToken();
                            profile.refreshToken = authenticated.refreshToken();
                            applyUser(profile);
                            save();
                        }
                        status.accept("Выбран аккаунт: " + authenticated.name());
                    }));
            return true;
        }
        try {
            applyUser(profile);
            status.accept("Выбран аккаунт: " + profile.name);
            return true;
        } catch (RuntimeException error) {
            status.accept("Не удалось переключить аккаунт: " + rootMessage(error));
            return false;
        }
    }

    public static synchronized boolean select(int index) {
        return select(index, ignored -> { });
    }

    private static void applyUser(Profile profile) {
        User user = new User(profile.name, UUID.fromString(profile.uuid),
                profile.accessToken == null ? "" : profile.accessToken,
                Optional.empty(), Optional.empty());
        ((MinecraftUserAccessor) (Object) Minecraft.getInstance()).pvpvisuals$setUser(user);
    }

    public static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause != null && cause.getCause() != null) cause = cause.getCause();
        if (cause == null) return "неизвестная ошибка";
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
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
            if (profile.type == null || profile.type.isBlank()) profile.type = "OFFLINE";
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
        public String refreshToken;
        public String type;

        public Profile() { }

        public Profile(String name, String uuid, String accessToken) {
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.type = "OFFLINE";
        }
    }
}
