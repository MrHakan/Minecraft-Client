package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.fabricmc.loader.api.FabricLoader;

public class SettingsManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("agalarhack.json");

    public Map<String, Settings> readSettings() {
        Map<String, Settings> settingsArray = new LinkedHashMap<>();
        if (!Files.isRegularFile(configPath)) {
            return settingsArray;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Map<String, Settings> loaded = gson.fromJson(reader, new TypeToken<Map<String, Settings>>(){}.getType());
            if (loaded != null) {
                settingsArray = loaded;
            }
        } catch (JsonSyntaxException e) {
            System.err.println("[Agalar Hack] Config JSON is malformed: " + e.getMessage());
            backupBrokenConfig();
        } catch (IOException e) {
            System.err.println("[Agalar Hack] Failed to read config: " + e.getMessage());
        }
        return settingsArray;
    }

    public void writeSettings(Map<String, Settings> settingsArray) {
        Path parent = configPath.getParent();
        if (parent == null) {
            return;
        }

        Path tempFile = null;
        try {
            Files.createDirectories(parent);
            tempFile = Files.createTempFile(parent, "agalarhack-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                gson.toJson(settingsArray, writer);
            }
            replaceConfig(tempFile);
            tempFile = null;
        } catch (IOException e) {
            System.err.println("[Agalar Hack] Failed to write config: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void replaceConfig(Path tempFile) throws IOException {
        try {
            Files.move(tempFile, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tempFile, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void backupBrokenConfig() {
        if (!Files.isRegularFile(configPath)) {
            return;
        }
        Path backup = configPath.resolveSibling("agalarhack.json.broken-" + System.currentTimeMillis());
        try {
            Files.move(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[Agalar Hack] Broken config backed up to " + backup.getFileName());
        } catch (IOException backupError) {
            System.err.println("[Agalar Hack] Could not back up broken config: " + backupError.getMessage());
        }
    }

    public Map<String, Settings> captureSettings() {
        Map<String, Settings> settingsArray = new LinkedHashMap<>();
        for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
            Settings copy = gson.fromJson(gson.toJson(module.settings), Settings.class);
            settingsArray.put(module.getName(), copy);
        }
        return settingsArray;
    }

    public void updateSettings() {
        writeSettings(captureSettings());
    }

    public void loadSettings() {
        applyValues(readSettings(), false);
        updateSettings();
    }

    /** Applies a profile snapshot and synchronizes live module enabled states in one batch. */
    public void applySettings(Map<String, Settings> snapshot) {
        applyValues(snapshot == null ? Map.of() : snapshot, true);
        updateSettings();
    }

    private void applyValues(Map<String, Settings> values, boolean syncEnabledState) {
        for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
            module.registerSettings();
            Settings saved = values.get(module.getName());
            if (saved != null && saved.settings != null) {
                module.settings.settings.putAll(saved.settings);
            }
            module.settings.sanitizeLoadedValues();
        }

        if (!syncEnabledState) {
            return;
        }

        for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
            boolean desired = Boolean.TRUE.equals(module.settings.getSetting("enabled"));
            if (module.isToggled() == desired) {
                continue;
            }
            try {
                module.setToggled(desired, false);
            } catch (RuntimeException e) {
                System.err.println("[Agalar Hack] Failed to apply profile state for " + module.getName());
                e.printStackTrace();
                module.settings.setSetting("enabled", false);
            }
        }
    }
}
