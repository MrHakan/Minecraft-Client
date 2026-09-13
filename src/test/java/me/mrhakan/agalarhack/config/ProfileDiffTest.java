package me.mrhakan.agalarhack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileDiffTest {

    private static Map<String, Map<String, Object>> profile(String module, Object... pairs) {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) settings.put((String) pairs[index], pairs[index + 1]);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put(module, settings);
        return result;
    }

    @Test
    void identicalProfilesProduceNoChanges() {
        var left = profile("KillAura", "range", 4.0, "enabled", true);
        var right = profile("KillAura", "range", 4.0, "enabled", true);
        assertEquals(List.of(), ProfileDiff.compare(left, right));
        assertEquals("identical", ProfileDiff.summarise(List.of()));
    }

    @Test
    void aJsonRoundTripIsNotAChange() {
        // Gson turns every number into a Double; a live setting holds whatever the module assigned.
        var live = profile("KillAura", "range", 3, "delay", 10L);
        var stored = profile("KillAura", "range", 3.0, "delay", 10.0);
        assertEquals(List.of(), ProfileDiff.compare(live, stored),
                "otherwise every diff is buried under type noise");
    }

    @Test
    void reportsAChangedValue() {
        var changes = ProfileDiff.compare(profile("KillAura", "range", 3.0), profile("KillAura", "range", 5.0));
        assertEquals(1, changes.size());
        var change = changes.get(0);
        assertEquals(ProfileDiff.Kind.CHANGED, change.kind());
        assertEquals("KillAura", change.module());
        assertEquals("range", change.setting());
        assertEquals(List.of("KillAura.range: 3 -> 5"), ProfileDiff.format(changes, 10));
    }

    @Test
    void reportsModulesPresentOnOnlyOneSide() {
        var left = profile("KillAura", "range", 3.0);
        var right = profile("AutoWalk", "sprint", true);
        var changes = ProfileDiff.compare(left, right);
        assertEquals(List.of("+ AutoWalk", "- KillAura"), ProfileDiff.format(changes, 10),
                "a missing module is one line, not one line per setting it happens to have");
    }

    @Test
    void reportsSettingsPresentOnOnlyOneSide() {
        var changes = ProfileDiff.compare(profile("KillAura", "range", 3.0),
                profile("KillAura", "range", 3.0, "wallRange", 2.0));
        assertEquals(List.of("+ KillAura.wallRange = 2"), ProfileDiff.format(changes, 10));

        var removed = ProfileDiff.compare(profile("KillAura", "range", 3.0, "wallRange", 2.0),
                profile("KillAura", "range", 3.0));
        assertEquals(List.of("- KillAura.wallRange (was 2)"), ProfileDiff.format(removed, 10));
    }

    @Test
    void orderingIsStableRegardlessOfMapOrder() {
        Map<String, Map<String, Object>> left = new LinkedHashMap<>();
        left.put("Zebra", new LinkedHashMap<>(Map.of("a", 1.0)));
        left.put("Alpha", new LinkedHashMap<>(Map.of("a", 1.0)));
        Map<String, Map<String, Object>> right = new LinkedHashMap<>();
        right.put("Alpha", new LinkedHashMap<>(Map.of("a", 2.0)));
        right.put("Zebra", new LinkedHashMap<>(Map.of("a", 2.0)));
        assertEquals(List.of("Alpha.a: 1 -> 2", "Zebra.a: 1 -> 2"),
                ProfileDiff.format(ProfileDiff.compare(left, right), 10));
    }

    @Test
    void nullSnapshotsAreTreatedAsEmpty() {
        assertEquals(List.of("- KillAura"),
                ProfileDiff.format(ProfileDiff.compare(profile("KillAura", "range", 3.0), null), 10));
        assertEquals(List.of("+ KillAura"),
                ProfileDiff.format(ProfileDiff.compare(null, profile("KillAura", "range", 3.0)), 10));
        assertEquals(List.of(), ProfileDiff.compare(null, null));
    }

    @Test
    void truncationSaysHowMuchWasHidden() {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (int index = 0; index < 10; index++) { before.put("s" + index, 0.0); after.put("s" + index, 1.0); }
        Map<String, Map<String, Object>> left = Map.of("M", before);
        Map<String, Map<String, Object>> right = Map.of("M", after);
        List<String> lines = ProfileDiff.format(ProfileDiff.compare(left, right), 3);
        assertEquals(4, lines.size());
        assertEquals("... and 7 more", lines.get(3));
    }

    @Test
    void formatAlwaysShowsAtLeastOneLine() {
        var changes = ProfileDiff.compare(profile("M", "a", 1.0), profile("M", "a", 2.0));
        assertEquals(1, ProfileDiff.format(changes, 0).size(), "a cap of zero must not silently show nothing");
    }

    @Test
    void describeDropsThePointlessDecimal() {
        assertEquals("3", ProfileDiff.describe(3.0));
        assertEquals("3.5", ProfileDiff.describe(3.5));
        assertEquals("true", ProfileDiff.describe(true));
        assertEquals("Enabled", ProfileDiff.describe("Enabled"));
        assertEquals("-", ProfileDiff.describe(null));
    }

    @Test
    void stringValuesCompareExactly() {
        assertFalse(ProfileDiff.sameValue("Enabled", "enabled"),
                "a choice the player picked is not ours to case-normalise");
        assertTrue(ProfileDiff.sameValue("Enabled", "Enabled"));
    }

    @Test
    void twoNotANumbersAreNotAChange() {
        assertTrue(ProfileDiff.sameValue(Double.NaN, Double.NaN));
        assertFalse(ProfileDiff.sameValue(Double.NaN, 1.0));
    }

    @Test
    void summariseCountsModulesAndSettingsSeparately() {
        var left = profile("KillAura", "range", 3.0);
        Map<String, Map<String, Object>> right = new LinkedHashMap<>();
        right.put("KillAura", new LinkedHashMap<>(Map.of("range", 5.0)));
        right.put("AutoWalk", new LinkedHashMap<>(Map.of("sprint", true)));
        assertEquals("1 module, 1 setting", ProfileDiff.summarise(ProfileDiff.compare(left, right)));
    }
}
