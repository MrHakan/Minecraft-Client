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

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.fabricmc.loader.api.FabricLoader;

public class SettingsManager {
    private boolean writable = true;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("agalarhack.json");

    public Map<String, Settings> readSettings() {
        writable = true;
        Map<String, Settings> settingsArray = new LinkedHashMap<>();
        if (!Files.isRegularFile(configPath)) {
            return settingsArray;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            if (Files.size(configPath) > 4 * 1024 * 1024) throw new IOException("Config exceeds 4 MiB limit");
            Map<String, Settings> loaded = ConfigCodec.decode(com.google.gson.JsonParser.parseReader(reader), gson);
            if (loaded != null) {
                settingsArray = loaded;
            }
        } catch (ConfigCodec.UnsupportedVersionException e) {
            writable = false;
            AgalarHackClient.LOGGER.error("Cannot load config", e);
            notifyRecovery(e.getMessage());
        } catch (com.google.gson.JsonParseException | IllegalArgumentException e) {
            AgalarHackClient.LOGGER.error("Malformed config", e);
            writable = backupBrokenConfig();
            notifyRecovery(writable ? "Config recovered; damaged file was backed up" : "Config recovery failed; original file retained");
        } catch (IOException e) {
            writable = false;
            AgalarHackClient.LOGGER.error("Cannot read config; writes are disabled to preserve it", e);
            notifyRecovery("Config could not be read; original file retained");
        }
        return settingsArray;
    }

    public void writeSettings(Map<String, Settings> settingsArray) {
        if (!writable) return;
        Path parent = configPath.getParent();
        if (parent == null) {
            return;
        }

        Path tempFile = null;
        try {
            Files.createDirectories(parent);
            tempFile = Files.createTempFile(parent, "agalarhack-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                gson.toJson(ConfigCodec.encode(settingsArray, gson), writer);
            }
            replaceConfig(tempFile);
            tempFile = null;
        } catch (IOException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to write config: " + e.getMessage());
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

    private boolean backupBrokenConfig() {
        if (!Files.isRegularFile(configPath)) return true;
        Path backup = configPath.resolveSibling("agalarhack.json.broken-" + System.currentTimeMillis());
        try {
            Files.move(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            AgalarHackClient.LOGGER.warn("Broken config backed up to {}", backup.getFileName());
            return true;
        } catch (IOException backupError) {
            AgalarHackClient.LOGGER.error("Could not back up broken config", backupError);
            return false;
        }
    }

    private void notifyRecovery(String message) {
        me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.NotificationService.class)
                .ifPresent(service -> service.publish(me.mrhakan.agalarhack.services.NotificationService.Type.WARNING, message));
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
        if (syncEnabledState) {
            for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
                if (module.isToggled()) module.setToggled(false, false);
            }
        }
        for (Module module : AgalarHackClient.moduleManager.getModuleList()) {
            if (syncEnabledState) {
                // A named profile is a complete snapshot: modules/settings not present
                // in an older profile should use current-version defaults, not leak values
                // from whichever profile happened to be active before it.
                module.setSettings(new Settings());
            }
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
                me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to apply profile state for " + module.getName());
                me.mrhakan.agalarhack.AgalarHackClient.LOGGER.error("Operation failed", e);
                module.settings.setSetting("enabled", false);
            }
        }
    }
}
