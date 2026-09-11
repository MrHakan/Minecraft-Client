package me.mrhakan.agalarhack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfileSelectionTest {
    private static final Set<String> MODULES = Set.of("KillAura", "AutoWalk", "Fullbright");
    private static final Set<String> CATEGORIES = Set.of("Combat", "Movement", "Render");

    @Test
    void blankSpecKeepsTheOldWholeProfileBehaviour() {
        for (String spec : new String[] { null, "", "   ", "all" }) {
            ProfileSelection selection = ProfileSelection.parse(spec);
            assertTrue(selection.isEverything(), "spec was " + spec);
            assertTrue(selection.includesHud());
            assertTrue(selection.includesTargetPolicy());
            assertTrue(selection.includesModule("KillAura", "Combat"));
        }
    }

    @Test
    void namedModuleSelectsOnlyThatModule() {
        ProfileSelection selection = ProfileSelection.parse("killaura");
        assertTrue(selection.includesModule("KillAura", "Combat"));
        assertFalse(selection.includesModule("AutoWalk", "Movement"));
        assertFalse(selection.includesHud());
        assertFalse(selection.includesTargetPolicy());
    }

    @Test
    void categorySelectsEveryModuleInIt() {
        ProfileSelection selection = ProfileSelection.parse("movement");
        assertTrue(selection.includesModule("AutoWalk", "Movement"));
        assertTrue(selection.includesModule("Anything", "movement"));
        assertFalse(selection.includesModule("KillAura", "Combat"));
    }

    @Test
    void tokensAreCaseInsensitiveAndSeparatorTolerant() {
        for (String spec : new String[] { "KillAura,hud", "killaura hud", "KILLAURA, HUD", " killaura   hud " }) {
            ProfileSelection selection = ProfileSelection.parse(spec);
            assertTrue(selection.includesModule("KillAura", "Combat"), spec);
            assertTrue(selection.includesHud(), spec);
            assertFalse(selection.includesTargetPolicy(), spec);
        }
    }

    @Test
    void modulesTokenTakesEveryModuleButNotTheHud() {
        ProfileSelection selection = ProfileSelection.parse("modules");
        assertTrue(selection.includesModule("KillAura", "Combat"));
        assertTrue(selection.includesModule("AutoWalk", "Movement"));
        assertFalse(selection.includesHud());
        assertFalse(selection.includesTargetPolicy());
    }

    @Test
    void allAnywhereInTheSpecWins() {
        ProfileSelection selection = ProfileSelection.parse("killaura, all");
        assertTrue(selection.isEverything());
        assertTrue(selection.includesHud());
    }

    @Test
    void namesTheTypoRatherThanApplyingNothing() {
        ProfileSelection selection = ProfileSelection.parse("killuara, hud");
        assertEquals(List.of("killuara"), selection.unknown(MODULES, CATEGORIES));
        assertEquals(List.of(), ProfileSelection.parse("killaura, combat, hud, targets, modules, all")
                .unknown(MODULES, CATEGORIES));
    }

    @Test
    void reportsASelectionThatWouldChangeNothing() {
        assertTrue(ProfileSelection.parse("nonsense").isEmpty(MODULES, CATEGORIES));
        assertFalse(ProfileSelection.parse("hud").isEmpty(MODULES, CATEGORIES));
        assertFalse(ProfileSelection.parse("killaura").isEmpty(MODULES, CATEGORIES));
        assertFalse(ProfileSelection.parse("combat").isEmpty(MODULES, CATEGORIES));
        assertFalse(ProfileSelection.everything().isEmpty(MODULES, CATEGORIES));
    }

    @Test
    void hudAndTargetsAreIndependentOfModules() {
        ProfileSelection hud = ProfileSelection.parse("hud");
        assertTrue(hud.includesHud());
        assertFalse(hud.includesTargetPolicy());
        assertFalse(hud.includesModule("KillAura", "Combat"));

        ProfileSelection targets = ProfileSelection.parse("targets");
        assertTrue(targets.includesTargetPolicy());
        assertFalse(targets.includesHud());
        assertFalse(targets.includesModule("KillAura", "Combat"));
    }

    @Test
    void nullModuleOrCategoryNeverMatches() {
        ProfileSelection selection = ProfileSelection.parse("killaura");
        assertFalse(selection.includesModule(null, null));
    }
}
