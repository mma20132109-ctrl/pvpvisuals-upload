package com.pvpvisuals.gui.mainmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Microsoft Device Code -> Xbox Live -> XSTS -> Minecraft Services authentication.
 * This mirrors the public-client flow from the supplied Zenith source package.
 */
public final class PvpMicrosoftAuth {
    // Public OAuth application ID used by the source package; this is not a client secret.
    private static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String SCOPE = "XboxLive.signin XboxLive.offline_access";
    private static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "pvpvisuals-microsoft-auth");
        thread.setDaemon(true);
        return thread;
    });

    private PvpMicrosoftAuth() { }

    public static CompletableFuture<DeviceCode> requestDeviceCode() {
        String form = "client_id=" + encode(CLIENT_ID) + "&scope=" + encode(SCOPE);
        return postForm(DEVICE_CODE_URL, form).thenApply(json -> new DeviceCode(
                required(json, "user_code"),
                json.has("verification_uri") ? json.get("verification_uri").getAsString() : "https://microsoft.com/devicelogin",
                required(json, "device_code"),
                json.has("interval") ? json.get("interval").getAsInt() : 5,
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 900
        ));
    }

    public static CompletableFuture<AuthenticatedProfile> pollDeviceCode(
            DeviceCode deviceCode, Consumer<String> status, BooleanSupplier cancelled) {
        return CompletableFuture.supplyAsync(() -> {
            long deadline = System.currentTimeMillis() + deviceCode.expiresInSeconds() * 1000L;
            long interval = Math.max(1, deviceCode.intervalSeconds()) * 1000L;
            while (System.currentTimeMillis() < deadline) {
                checkCancelled(cancelled);
                sleep(interval);
                checkCancelled(cancelled);
                JsonObject response = pollToken(deviceCode.deviceCode());
                if (response.has("access_token")) {
                    String accessToken = required(response, "access_token");
                    String refreshToken = response.has("refresh_token")
                            ? response.get("refresh_token").getAsString() : "";
                    status.accept("Вход в Xbox Live...");
                    return authenticateXbox(accessToken, refreshToken, status, cancelled);
                }
                String error = response.has("error") ? response.get("error").getAsString() : "";
                switch (error) {
                    case "authorization_pending" -> { }
                    case "slow_down" -> interval += 5_000L;
                    case "authorization_declined" -> throw new IllegalStateException("Вход отменён в Microsoft");
                    case "expired_token" -> throw new IllegalStateException("Код Microsoft истёк");
                    case "authorization_failed" -> throw new IllegalStateException(errorDescription(response, error));
                    case "" -> throw new IllegalStateException("Не удалось получить ответ от Microsoft");
                    default -> throw new IllegalStateException(errorDescription(response, error));
                }
            }
            throw new IllegalStateException("Код Microsoft истёк");
        }, EXECUTOR);
    }

    public static CompletableFuture<AuthenticatedProfile> refresh(
            String refreshToken, Consumer<String> status, BooleanSupplier cancelled) {
        return CompletableFuture.supplyAsync(() -> {
            checkCancelled(cancelled);
            String form = "client_id=" + encode(CLIENT_ID)
                    + "&refresh_token=" + encode(refreshToken)
                    + "&grant_type=refresh_token&scope=" + encode(SCOPE);
            JsonObject response = sendForm(TOKEN_URL, form);
            String accessToken = required(response, "access_token");
            String nextRefresh = response.has("refresh_token")
                    ? response.get("refresh_token").getAsString() : refreshToken;
            status.accept("Вход в Xbox Live...");
            return authenticateXbox(accessToken, nextRefresh, status, cancelled);
        }, EXECUTOR);
    }

    private static AuthenticatedProfile authenticateXbox(
            String microsoftAccessToken, String refreshToken,
            Consumer<String> status, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        JsonObject xboxProperties = new JsonObject();
        xboxProperties.addProperty("AuthMethod", "RPS");
        xboxProperties.addProperty("SiteName", "user.auth.xboxlive.com");
        xboxProperties.addProperty("RpsTicket", "d=" + microsoftAccessToken);
        JsonObject xboxBody = new JsonObject();
        xboxBody.add("Properties", xboxProperties);
        xboxBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xboxBody.addProperty("TokenType", "JWT");
        JsonObject xboxResponse = sendJson("https://user.auth.xboxlive.com/user/authenticate", xboxBody);
        String xboxToken = required(xboxResponse, "Token");
        String userHash = xboxResponse.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        checkCancelled(cancelled);
        status.accept("Получение XSTS-токена...");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xboxToken);
        JsonObject xstsProperties = new JsonObject();
        xstsProperties.add("UserTokens", userTokens);
        xstsProperties.addProperty("SandboxId", "RETAIL");
        JsonObject xstsBody = new JsonObject();
        xstsBody.add("Properties", xstsProperties);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xstsResponse = sendJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody);
        String xstsToken = required(xstsResponse, "Token");

        checkCancelled(cancelled);
        status.accept("Авторизация Minecraft...");
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject minecraftLogin = sendJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox", loginBody);
        String minecraftToken = required(minecraftLogin, "access_token");

        checkCancelled(cancelled);
        status.accept("Загрузка профиля Minecraft...");
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", minecraftToken);
        String name = required(profile, "name");
        String rawId = required(profile, "id");
        UUID uuid = parseUuid(rawId);
        return new AuthenticatedProfile(name, uuid, minecraftToken, refreshToken);
    }

    private static JsonObject pollToken(String deviceCode) {
        String form = "grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code")
                + "&client_id=" + encode(CLIENT_ID)
                + "&device_code=" + encode(deviceCode);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "PvPVisuals/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        // OAuth device-code polling deliberately returns HTTP 400 for authorization_pending/slow_down.
        return send(request, TOKEN_URL, false, true);
    }

    private static CompletableFuture<JsonObject> postForm(String url, String form) {
        return CompletableFuture.supplyAsync(() -> sendForm(url, form), EXECUTOR);
    }

    private static JsonObject sendForm(String url, String form) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", "PvPVisuals/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return send(request, url, false, false);
    }

    private static JsonObject sendJson(String url, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "PvPVisuals/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return send(request, url, false, false);
    }

    private static JsonObject getJson(String url, String bearer) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/json")
                .header("User-Agent", "PvPVisuals/1.0")
                .GET().build();
        return send(request, url, true, false);
    }

    private static JsonObject send(HttpRequest request, String endpoint, boolean profileRequest, boolean allowOAuthErrorBody) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() / 100 != 2 && !allowOAuthErrorBody) {
                if (profileRequest && response.statusCode() == 404) {
                    throw new IllegalStateException("Этот Microsoft-аккаунт не владеет Minecraft Java Edition");
                }
                throw new IllegalStateException("HTTP " + response.statusCode() + " (" + endpoint + "): "
                        + errorDescription(body, "ошибка авторизации"));
            }
            return body;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Вход прерван");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Сетевая ошибка при обращении к " + endpoint, error);
        } catch (RuntimeException error) {
            throw error;
        }
    }

    private static String errorDescription(JsonObject json, String fallback) {
        if (json.has("error_description")) return json.get("error_description").getAsString();
        if (json.has("error")) return json.get("error").getAsString();
        if (json.has("XErr")) return "Xbox error " + json.get("XErr").getAsString();
        return fallback;
    }

    private static String required(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("Ответ авторизации не содержит поле " + key);
        }
        return json.get(key).getAsString();
    }

    private static UUID parseUuid(String value) {
        if (value.length() == 32) {
            value = value.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                    "$1-$2-$3-$4-$5");
        }
        return UUID.fromString(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Вход прерван");
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Вход отменён");
        }
    }

    public record DeviceCode(String userCode, String verificationUri, String deviceCode,
                             int intervalSeconds, int expiresInSeconds) { }

    public record AuthenticatedProfile(String name, UUID uuid, String minecraftAccessToken,
                                       String refreshToken) { }
}
