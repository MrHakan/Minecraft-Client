package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import me.mrhakan.agalarhack.AgalarHackClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Named client snapshots with optional automatic server-address binding. */
public class ProfileManager {
    public static final class ProfileData {
        public Map<String, Settings> modules = new LinkedHashMap<>();
        public Settings targetPolicy;
        public Map<String, HudLayoutManager.WidgetState> hud = new LinkedHashMap<>();
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-profiles");
    private final Path bindingsPath = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-server-profiles.json");
    private final Map<String, String> serverBindings = new LinkedHashMap<>();
    private String activeProfile = "";
    private String observedServer = "";

    public void loadBindings() {
        serverBindings.clear();
        if (!Files.isRegularFile(bindingsPath)) {
            saveBindings();
            return;
        }
        try (Reader reader = Files.newBufferedReader(bindingsPath, StandardCharsets.UTF_8)) {
            Map<String, String> loaded = gson.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            if (loaded != null) {
                loaded.forEach((server, profile) -> {
                    if (server != null && profile != null && isValidName(profile)) {
                        serverBindings.put(normalizeServer(server), profile);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[Agalar Hack] Failed to load server profile bindings: " + e.getMessage());
        }
    }

    public void tick(Minecraft mc) {
        if (mc.level == null || mc.getCurrentServer() == null) {
            return;
        }
        String server = normalizeServer(mc.getCurrentServer().ip);
        if (server.isBlank() || server.equals(observedServer)) {
            return;
        }
        observedServer = server;
        String profile = serverBindings.get(server);
        if (profile != null && exists(profile)) {
            try {
                load(profile);
                System.out.println("[Agalar Hack] Auto-loaded profile '" + profile + "' for " + server);
            } catch (RuntimeException e) {
                System.err.println("[Agalar Hack] Could not auto-load profile '" + profile + "': " + e.getMessage());
            }
        }
    }

    public void onDisconnect() {
        observedServer = "";
    }

    public void save(String name) {
        validateName(name);
        ProfileData data = new ProfileData();
        data.modules = AgalarHackClient.SETTINGS_MANAGER.captureSettings();
        data.targetPolicy = deepCopy(AgalarHackClient.TARGET_POLICY.getSettings(), Settings.class);
        data.hud = AgalarHackClient.HUD_LAYOUT.snapshot();
        writeProfile(name, data, true);
        activeProfile = name;
    }

    public void load(String name) {
        ProfileData data = readProfile(name);
        AgalarHackClient.SETTINGS_MANAGER.applySettings(data.modules);
        AgalarHackClient.TARGET_POLICY.applySnapshot(data.targetPolicy);
        AgalarHackClient.HUD_LAYOUT.applySnapshot(data.hud);
        activeProfile = name;
    }

    public boolean delete(String name) {
        validateName(name);
        try {
            boolean deleted = Files.deleteIfExists(profilePath(name));
            if (deleted) {
                serverBindings.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(name));
                saveBindings();
                if (activeProfile.equalsIgnoreCase(name)) {
                    activeProfile = "";
                }
            }
            return deleted;
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete profile: " + e.getMessage(), e);
        }
    }

    /** Returns validated canonical profile JSON suitable for clipboard export. */
    public String exportJson(String name) {
        return gson.toJson(readProfile(name));
    }

    /** Imports a complete snapshot from JSON under a local profile name. */
    public void importJson(String name, String json) {
        validateName(name);
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Clipboard/profile JSON is empty.");
        }
        final ProfileData data;
        try {
            data = gson.fromJson(json, ProfileData.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid profile JSON: " + e.getMessage());
        }
        validateProfileData(data);
        sanitizeImportedData(data);
        writeProfile(name, data, true);
    }

    public void duplicate(String source, String target) {
        validateName(source);
        validateName(target);
        if (exists(target)) {
            throw new IllegalArgumentException("Target profile already exists: " + target);
        }
        writeProfile(target, readProfile(source), false);
    }

    public void rename(String source, String target) {
        validateName(source);
        validateName(target);
        if (!exists(source)) {
            throw new IllegalArgumentException("Profile does not exist: " + source);
        }
        if (exists(target)) {
            throw new IllegalArgumentException("Target profile already exists: " + target);
        }
        try {
            Files.createDirectories(dir);
            Files.move(profilePath(source), profilePath(target), StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            try {
                Files.move(profilePath(source), profilePath(target));
            } catch (IOException e) {
                throw new IllegalStateException("Could not rename profile: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not rename profile: " + e.getMessage(), e);
        }

        serverBindings.replaceAll((server, profile) -> profile.equalsIgnoreCase(source) ? target : profile);
        saveBindings();
        if (activeProfile.equalsIgnoreCase(source)) {
            activeProfile = target;
        }
    }

    public List<String> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .sorted(Comparator.naturalOrder())
                    .forEach(names::add);
        } catch (IOException e) {
            System.err.println("[Agalar Hack] Could not list profiles: " + e.getMessage());
        }
        return names;
    }

    public boolean exists(String name) {
        return isValidName(name) && Files.isRegularFile(profilePath(name));
    }

    public void bindCurrentServer(Minecraft mc, String profile) {
        validateName(profile);
        if (!exists(profile)) {
            throw new IllegalArgumentException("Profile does not exist: " + profile);
        }
        String server = currentServer(mc);
        serverBindings.put(server, profile);
        saveBindings();
    }

    public boolean unbindCurrentServer(Minecraft mc) {
        String server = currentServer(mc);
        boolean removed = serverBindings.remove(server) != null;
        if (removed) {
            saveBindings();
        }
        return removed;
    }

    public String getBoundProfile(Minecraft mc) {
        if (mc == null || mc.getCurrentServer() == null) {
            return null;
        }
        return serverBindings.get(normalizeServer(mc.getCurrentServer().ip));
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public Map<String, String> getServerBindings() {
        return Map.copyOf(serverBindings);
    }

    private ProfileData readProfile(String name) {
        validateName(name);
        Path path = profilePath(name);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Profile does not exist: " + name);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ProfileData data = gson.fromJson(reader, ProfileData.class);
            validateProfileData(data);
            return data;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Profile JSON is invalid: " + name);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load profile: " + e.getMessage(), e);
        }
    }

    private void writeProfile(String name, ProfileData data, boolean overwrite) {
        validateName(name);
        validateProfileData(data);
        if (!overwrite && exists(name)) {
            throw new IllegalArgumentException("Profile already exists: " + name);
        }
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(profilePath(name), StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save profile: " + e.getMessage(), e);
        }
    }

    private void validateProfileData(ProfileData data) {
        if (data == null) {
            throw new IllegalArgumentException("Profile is empty or invalid.");
        }
        if (data.modules == null) {
            data.modules = new LinkedHashMap<>();
        }
        if (data.hud == null) {
            data.hud = new LinkedHashMap<>();
        }
    }

    private void sanitizeImportedData(ProfileData data) {
        data.modules.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (Settings settings : data.modules.values()) {
            if (settings.settings == null) {
                settings.settings = new LinkedHashMap<>();
            }
        }
        data.hud.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        if (data.targetPolicy != null && data.targetPolicy.settings == null) {
            data.targetPolicy.settings = new LinkedHashMap<>();
        }
    }

    private String currentServer(Minecraft mc) {
        if (mc == null || mc.getCurrentServer() == null || mc.getCurrentServer().ip == null) {
            throw new IllegalStateException("You are not connected to a multiplayer server.");
        }
        return normalizeServer(mc.getCurrentServer().ip);
    }

    private void saveBindings() {
        try {
            Files.createDirectories(bindingsPath.getParent());
            try (Writer writer = Files.newBufferedWriter(bindingsPath, StandardCharsets.UTF_8)) {
                gson.toJson(serverBindings, writer);
            }
        } catch (IOException e) {
            System.err.println("[Agalar Hack] Failed to save server profile bindings: " + e.getMessage());
        }
    }

    private Path profilePath(String name) {
        return dir.resolve(name + ".json");
    }

    private void validateName(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Profile name must be 1-32 characters: letters, digits, dot, dash or underscore.");
        }
    }

    private boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9._-]{1,32}");
    }

    private String normalizeServer(String server) {
        return server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
    }

    private <T> T deepCopy(T value, Class<T> type) {
        return gson.fromJson(gson.toJson(value), type);
    }
}
