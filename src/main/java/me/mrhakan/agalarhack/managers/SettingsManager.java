package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
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
        Map<String, Settings> settingsArray = new HashMap<>();
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
            // An I/O failure does not necessarily mean the file is corrupt, so
            // keep the original in place and avoid moving it out of the way.
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

    public void updateSettings() {
        Map<String, Settings> settingsArray = new HashMap<>();
        for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
            settingsArray.put(module.getName(), module.settings);
        }
        writeSettings(settingsArray);
    }

    public void loadSettings() {
        Map<String, Settings> settingsArray = readSettings();
        for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
            // Register defaults first so new settings always exist, then
            // overlay whatever was saved so old configs keep their values.
            module.registerSettings();
            Settings saved = settingsArray.get(module.getName());
            if (saved != null && saved.settings != null) {
                module.settings.settings.putAll(saved.settings);
            }
        }
        updateSettings();
    }
}
