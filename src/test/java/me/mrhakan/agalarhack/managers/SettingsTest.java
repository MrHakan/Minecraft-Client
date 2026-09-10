package me.mrhakan.agalarhack.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void numberSettingsRejectInvalidCommandValues() {
        Settings settings = new Settings();
        settings.addNumberSetting("range", 4.0, 1.0, 6.0, "Attack range");

        assertEquals(5.5, settings.parseSettingValue("range", "5.5"));
        assertThrows(IllegalArgumentException.class, () -> settings.parseSettingValue("range", "0.99"));
        assertThrows(IllegalArgumentException.class, () -> settings.parseSettingValue("range", "6.01"));
        assertThrows(IllegalArgumentException.class, () -> settings.parseSettingValue("range", "NaN"));
        assertThrows(IllegalArgumentException.class, () -> settings.parseSettingValue("range", "Infinity"));
    }

    @Test
    void loadedNumbersAreClampedInsteadOfAppliedUnchecked() {
        Settings settings = new Settings();
        settings.addNumberSetting("speed", 0.1, 0.02, 1.0, "Flight speed");

        settings.setSetting("speed", 500.0);
        settings.sanitizeLoadedValues();
        assertEquals(1.0, settings.getSetting("speed"));

        settings.setSetting("speed", -500.0);
        settings.sanitizeLoadedValues();
        assertEquals(0.02, settings.getSetting("speed"));

        settings.setSetting("speed", Double.NaN);
        settings.sanitizeLoadedValues();
        assertEquals(0.1, settings.getSetting("speed"));
    }

    @Test
    void booleanAliasesAreStrictButFriendly() {
        Settings settings = new Settings();
        settings.addBooleanSetting("friends", true, "Friend filter");

        assertEquals(true, settings.parseSettingValue("friends", "ON"));
        assertEquals(false, settings.parseSettingValue("friends", "off"));
        assertThrows(IllegalArgumentException.class, () -> settings.parseSettingValue("friends", "maybe"));
    }

    @Test
    void choicesAreCaseInsensitiveAndCanonicalized() {
        Settings settings = new Settings();
        settings.addChoiceSetting("priority", "closest", "Target priority",
                "closest", "lowest_health", "highest_health");

        assertEquals("lowest_health", settings.parseSettingValue("priority", "LOWEST_HEALTH"));
        assertThrows(IllegalArgumentException.class, () -> settings.parseSettingValue("priority", "random"));

        settings.setSetting("priority", "HIGHEST_HEALTH");
        settings.sanitizeLoadedValues();
        assertEquals("highest_health", settings.getSetting("priority"));
    }
}
