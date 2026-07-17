package me.mrhakan.agalarhack.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.module.Module;

public class SettingsManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File configFile = new File("AgalarHack", "agalarhack-config.json");

    public Map<String, Settings> readSettings() {
        Map<String, Settings> settingsArray = new HashMap<>();
        if (configFile.exists() && configFile.isFile()) {
            try (FileReader reader = new FileReader(configFile)) {
                Map<String, Settings> loaded = gson.fromJson(reader, new TypeToken<Map<String, Settings>>(){}.getType());
                if (loaded != null) {
                    settingsArray = loaded;
                }
            } catch (IOException | JsonSyntaxException e) {
                e.printStackTrace();
            }
        }
        return settingsArray;
    }

    public void writeSettings(Map<String, Settings> settingsArray) {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter fw = new FileWriter(configFile)) {
            gson.toJson(settingsArray, fw);
            fw.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateSettings() {
        Map<String, Settings> settingsArray = new HashMap<>();
        for (Module module : Main.moduleManager.getModuleList()) {
            settingsArray.put(module.getName(), module.settings);
        }
        writeSettings(settingsArray);
    }

    public void loadSettings() {
        Map<String, Settings> settingsArray = readSettings();
        for (Module module : Main.moduleManager.getModuleList()) {
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
