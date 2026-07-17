package me.mrhakan.agalarhack.managers;

import java.util.HashMap;
import java.util.Map;

public class Settings {
    public Map<String, Object> settings = new HashMap<>();

    public Object addSetting(String settingName, Object defaultValue) {
        return settings.putIfAbsent(settingName, defaultValue);
    }

    public void setSetting(String settingName, Object newValue) {
        settings.put(settingName, newValue);
    }

    public Object getSetting(String settingName) {
        return settings.get(settingName);
    }

    public String getKeyIgnoreCase(String settingName) {
        for (String key : settings.keySet()) {
            if (key.equalsIgnoreCase(settingName)) {
                return key;
            }
        }
        return null;
    }
}
