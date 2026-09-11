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
        /**
         * Optional, so a profile written before descriptions existed still loads, and one written
         * now still loads in a client that does not know about them.
         */
        public me.mrhakan.agalarhack.config.ProfileMetadata meta;
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-profiles");
    private final Path bindingsPath = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-server-profiles.json");
    private final Map<String, String> serverBindings = new LinkedHashMap<>();
    private final Map<String, String> dimensionBindings = new LinkedHashMap<>();
    private final Path dimensionBindingsPath =
            FabricLoader.getInstance().getConfigDir().resolve("agalarhack-dimension-profiles.json");
    private String activeProfile = "";
    private String observedServer = "";
    private String observedDimension = "";

    public void loadBindings() {
        loadBindingFile(bindingsPath, serverBindings, true);
        loadBindingFile(dimensionBindingsPath, dimensionBindings, false);
    }

    /**
     * @param normaliseKeys server addresses need normalising; dimension ids are already canonical
     *                      and lower-casing an id would be wrong for a namespaced key
     */
    private void loadBindingFile(Path path, Map<String, String> into, boolean normaliseKeys) {
        into.clear();
        if (!Files.isRegularFile(path)) {
            saveBindingFile(path, into);
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, String> loaded = gson.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            if (loaded != null) {
                loaded.forEach((key, profile) -> {
                    if (key != null && profile != null && isValidName(profile)) {
                        into.put(normaliseKeys ? normalizeServer(key) : key, profile);
                    }
                });
            }
        } catch (Exception failure) {
            AgalarHackClient.LOGGER.warn("Failed to load profile bindings from {}", path.getFileName(), failure);
        }
    }

    /**
     * Auto-switches on a new server, and then on a new dimension.
     *
     * <p>Dimension is checked second and so wins where both apply, because it is the more specific
     * of the two: binding a profile to the nether is a statement about the nether on every server,
     * and it would be useless if the server binding reapplied over it on arrival.
     */
    public void tick(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        if (mc.getCurrentServer() != null) {
            String server = normalizeServer(mc.getCurrentServer().ip);
            if (!server.isBlank() && !server.equals(observedServer)) {
                observedServer = server;
                autoLoad(serverBindings.get(server), "server " + server);
            }
        }
        String dimension = mc.level.dimension().identifier().toString();
        if (!dimension.equals(observedDimension)) {
            observedDimension = dimension;
            autoLoad(dimensionBindings.get(dimension), "dimension " + dimension);
        }
    }

    /** Never throws at the caller: a bad binding must not break joining a world. */
    private void autoLoad(String profile, String because) {
        if (profile == null || !exists(profile)) {
            return;
        }
        try {
            load(profile);
            AgalarHackClient.LOGGER.info("Auto-loaded profile '{}' for {}", profile, because);
        } catch (RuntimeException failure) {
            AgalarHackClient.LOGGER.warn("Could not auto-load profile '{}' for {}", profile, because, failure);
        }
    }

    public void onDisconnect() {
        observedServer = "";
        observedDimension = "";
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
        me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.NotificationService.class)
                .publish(me.mrhakan.agalarhack.services.NotificationService.Type.SUCCESS, "Profile loaded: " + name);
    }

    /**
     * Applies only the parts of a profile the selection names.
     *
     * <p>Everything outside the selection is left exactly as it is, including which modules are on,
     * so this never becomes a whole-profile load with extra steps.
     *
     * <p>The active profile name is deliberately not changed: after a partial load the live config
     * matches no stored profile, and claiming otherwise would make the next {@code save} overwrite a
     * profile the player never fully loaded.
     *
     * @return a human-readable summary of what was applied
     */
    public String loadPartial(String name, me.mrhakan.agalarhack.config.ProfileSelection selection) {
        ProfileData data = readProfile(name);
        if (selection == null || selection.isEverything()) {
            load(name);
            return "everything";
        }
        List<String> parts = new java.util.ArrayList<>();
        List<String> modules = AgalarHackClient.SETTINGS_MANAGER.applyPartialSettings(data.modules, selection);
        if (!modules.isEmpty()) {
            parts.add(modules.size() + (modules.size() == 1 ? " module" : " modules"));
        }
        if (selection.includesTargetPolicy() && data.targetPolicy != null) {
            AgalarHackClient.TARGET_POLICY.applySnapshot(data.targetPolicy);
            parts.add("target policy");
        }
        if (selection.includesHud() && data.hud != null) {
            AgalarHackClient.HUD_LAYOUT.applySnapshot(data.hud);
            parts.add("HUD layout");
        }
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }

    /**
     * Compares two stored profiles, or a stored profile against the live configuration.
     *
     * @param right the profile to compare to, or null for the current live settings
     */
    public List<me.mrhakan.agalarhack.config.ProfileDiff.Change> diff(String left, String right) {
        Map<String, Map<String, Object>> from = moduleValues(readProfile(left).modules);
        Map<String, Map<String, Object>> to = right == null
                ? moduleValues(AgalarHackClient.SETTINGS_MANAGER.captureSettings())
                : moduleValues(readProfile(right).modules);
        return me.mrhakan.agalarhack.config.ProfileDiff.compare(from, to);
    }

    /** Unwraps the raw value maps the diff works on; a Settings with no values contributes an empty map. */
    private static Map<String, Map<String, Object>> moduleValues(Map<String, Settings> modules) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        if (modules == null) return values;
        modules.forEach((module, settings) ->
                values.put(module, settings == null || settings.settings == null ? Map.of() : settings.settings));
        return values;
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
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Could not list profiles: " + e.getMessage());
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

    public void bindCurrentDimension(Minecraft mc, String profile) {
        validateName(profile);
        if (!exists(profile)) {
            throw new IllegalArgumentException("Profile does not exist: " + profile);
        }
        String dimension = currentDimension(mc);
        if (dimension == null) {
            throw new IllegalArgumentException("Join a world first.");
        }
        dimensionBindings.put(dimension, profile);
        saveBindingFile(dimensionBindingsPath, dimensionBindings);
    }

    public boolean unbindCurrentDimension(Minecraft mc) {
        String dimension = currentDimension(mc);
        if (dimension == null || dimensionBindings.remove(dimension) == null) {
            return false;
        }
        saveBindingFile(dimensionBindingsPath, dimensionBindings);
        return true;
    }

    public String getDimensionProfile(Minecraft mc) {
        String dimension = currentDimension(mc);
        return dimension == null ? null : dimensionBindings.get(dimension);
    }

    public Map<String, String> getDimensionBindings() {
        return Map.copyOf(dimensionBindings);
    }

    private static String currentDimension(Minecraft mc) {
        return mc == null || mc.level == null ? null : mc.level.dimension().identifier().toString();
    }

    /** Metadata for one profile, or null when it has none; never throws for a missing profile. */
    public me.mrhakan.agalarhack.config.ProfileMetadata metadata(String name) {
        if (!exists(name)) return null;
        try {
            var meta = readProfile(name).meta;
            return meta == null ? null : meta.sanitised();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Sets the description and tags, leaving the settings alone.
     *
     * <p>Reads and rewrites the whole profile rather than patching the file, so a description can
     * never be written into a profile that would not have loaded.
     */
    public void describe(String name, String description, String tags) {
        ProfileData data = readProfile(name);
        long now = System.currentTimeMillis();
        var existing = data.meta;
        var updated = me.mrhakan.agalarhack.config.ProfileMetadata.of(description, tags, now);
        // Keep the original creation time; only the edit is new.
        if (existing != null && existing.createdAt > 0) updated.createdAt = existing.createdAt;
        data.meta = updated;
        writeProfile(name, data, true);
    }

    /** @return matching profile names in the same order {@link #list()} gives them */
    public List<String> search(String query) {
        List<String> matches = new ArrayList<>();
        for (String name : list()) {
            if (me.mrhakan.agalarhack.config.ProfileMetadata.matches(name, metadata(name), query)) {
                matches.add(name);
            }
        }
        return List.copyOf(matches);
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
        // A hand-edited or older file can hold anything; bring it back inside the documented bounds
        // rather than letting an unbounded description reach a chat line.
        if (data.meta != null) {
            data.meta = data.meta.sanitised();
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

    private void saveBindingFile(Path path, Map<String, String> bindings) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(bindings, writer);
            }
        } catch (IOException failure) {
            AgalarHackClient.LOGGER.warn("Could not save profile bindings to {}", path.getFileName(), failure);
        }
    }

    private void saveBindings() {
        saveBindingFile(bindingsPath, serverBindings);
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
