package me.mrhakan.agalarhack.services;

import com.google.gson.JsonParser;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks GitHub's latest stable release once per client launch without blocking the game thread. */
public final class GitHubUpdateChecker {
    private static final URI LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/MrHakan/Minecraft-Client/releases/latest");
    private static final Pattern FULL_COMMIT = Pattern.compile("(?i)\\+([0-9a-f]{40})(?:\\b|$)");
    private static final Pattern VERSION = Pattern.compile("(?i)\\bv?([0-9]+\\.[0-9]+\\.[0-9]+)\\b");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final NotificationService notifications;
    private final AtomicBoolean checkStarted = new AtomicBoolean();
    private EventBus events;
    private EventBus.Subscription pendingWorldSubscription;
    private String pendingMessage;

    public GitHubUpdateChecker(NotificationService notifications) {
        this.notifications = Objects.requireNonNull(notifications);
    }

    public void register(EventBus events) {
        this.events = Objects.requireNonNull(events);
        ClientLifecycleEvents.CLIENT_STARTED.register(this::checkOnce);
    }

    private void checkOnce(Minecraft client) {
        if (!checkStarted.compareAndSet(false, true) || shouldSkipCheck()) return;

        String installedCommit = installedCommit();
        if (!isFullCommit(installedCommit)) {
            AgalarHackClient.LOGGER.debug("GitHub update check skipped: build commit metadata is unavailable");
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "AgalarHack/" + AgalarHackClient.VERSION)
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("GitHub releases API returned HTTP " + response.statusCode());
                    }
                    return parseLatestRelease(response.body());
                })
                .thenAccept(release -> {
                    if (!isUpdateAvailable(installedCommit, release)) return;
                    String message = notificationMessage(release);
                    client.execute(() -> queueNotification(client, message));
                })
                .exceptionally(failure -> {
                    AgalarHackClient.LOGGER.debug("GitHub release check failed", failure);
                    return null;
                });
    }

    private void queueNotification(Minecraft client, String message) {
        if (client.player != null) {
            notifications.publish(NotificationService.Type.INFO, message);
            return;
        }
        pendingMessage = message;
        pendingWorldSubscription = events.subscribe(ClientEvents.WorldChanged.class,
                "github-update-notification", 10, event -> {
                    if (!event.ready() || client.player == null || pendingMessage == null) return;
                    String readyMessage = pendingMessage;
                    pendingMessage = null;
                    EventBus.Subscription subscription = pendingWorldSubscription;
                    pendingWorldSubscription = null;
                    if (subscription != null) subscription.close();
                    notifications.publish(NotificationService.Type.INFO, readyMessage);
                });
    }

    private static boolean shouldSkipCheck() {
        return FabricLoader.getInstance().isDevelopmentEnvironment()
                || Boolean.getBoolean("agalarhack.disableUpdateCheck");
    }

    private static String installedCommit() {
        return FabricLoader.getInstance().getModContainer(AgalarHackClient.MOD_ID)
                .map(container -> {
                    var value = container.getMetadata().getCustomValue("agalarhack:commit");
                    return value == null ? "" : value.getAsString();
                })
                .orElse("");
    }

    static Release parseLatestRelease(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            var json = JsonParser.parseString(responseBody);
            if (!json.isJsonObject()) return null;
            var nameValue = json.getAsJsonObject().get("name");
            if (nameValue == null || !nameValue.isJsonPrimitive() || !nameValue.getAsJsonPrimitive().isString()) {
                return null;
            }
            String name = nameValue.getAsString();
            Matcher commitMatcher = FULL_COMMIT.matcher(name);
            if (!commitMatcher.find()) return null;
            Matcher versionMatcher = VERSION.matcher(name);
            String version = versionMatcher.find() ? versionMatcher.group(1) : "new build";
            return new Release(version, commitMatcher.group(1).toLowerCase(Locale.ROOT));
        } catch (RuntimeException malformedJson) {
            return null;
        }
    }

    private static String notificationMessage(Release release) {
        return "GitHub update available: " + release.version() + " (" + release.commit().substring(0, 7) + ")";
    }

    private static boolean isFullCommit(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{40}");
    }

    static boolean isUpdateAvailable(String installedCommit, Release latest) {
        return latest != null && isFullCommit(installedCommit)
                && !installedCommit.equalsIgnoreCase(latest.commit());
    }

    record Release(String version, String commit) { }
}
