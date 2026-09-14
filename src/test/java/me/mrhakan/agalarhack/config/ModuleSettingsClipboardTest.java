package me.mrhakan.agalarhack.config;

import me.mrhakan.agalarhack.managers.Settings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleSettingsClipboardTest {
    private Settings settings() {
        var settings = new Settings();
        settings.addBooleanSetting("enabled", false, "");
        settings.addNumberSetting("keyModifiers", 0, 0, 15, "");
        settings.addNumberSetting("scanBudget", 100, 1, 1000, "");
        return settings;
    }
    @Test void canonicalNamesRoundTripWithoutCopyingProtectedState() {
        var settings = settings();
        var parsed = ModuleSettingsClipboard.parse("{\"module\":\"Test\",\"settings\":{\"SCANBUDGET\":250}}", "Test", settings);
        parsed.forEach(settings::setSetting);
        assertEquals(250.0, settings.getSetting("scanBudget"));
        assertNull(settings.getSetting("SCANBUDGET"));
        var copy = ModuleSettingsClipboard.parse(ModuleSettingsClipboard.encode("Test", settings), "Test", settings);
        assertEquals(parsed, copy);
    }
    @Test void rejectsProtectedAliasesDuplicateAliasesAndInvalidPartialChanges() {
        var settings = settings();
        for (String values : new String[]{"\"ENABLED\":true", "\"KEYMODIFIERS\":8",
                "\"scanBudget\":10,\"SCANBUDGET\":20", "\"scanBudget\":10,\"unknown\":1",
                "\"scanBudget\":[]", "\"scanBudget\":1001", "\"scanBudget\":\"NaN\""}) {
            assertThrows(IllegalArgumentException.class, () -> ModuleSettingsClipboard.parse(
                    "{\"module\":\"Test\",\"settings\":{" + values + "}}", "Test", settings));
            assertEquals(100.0, settings.getSetting("scanBudget"));
        }
    }
}
