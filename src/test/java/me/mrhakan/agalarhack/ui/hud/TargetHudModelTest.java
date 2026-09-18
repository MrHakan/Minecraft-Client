package me.mrhakan.agalarhack.ui.hud;

import me.mrhakan.agalarhack.ui.hud.TargetHudModel.Layout;
import me.mrhakan.agalarhack.ui.hud.TargetHudModel.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TargetHudModelTest {
    private static Target target(float health, float absorption, int ping, boolean player, boolean friend) {
        return new Target("Steve", health, 20f, absorption, 12.5, 8, ping, player, friend, false);
    }

    @Test void layoutParsingDefaultsToCompact() {
        assertEquals(Layout.COMPACT, Layout.parse(null));
        assertEquals(Layout.COMPACT, Layout.parse("nonsense"));
        assertEquals(Layout.MINIMAL, Layout.parse("MINIMAL"));
        assertEquals(Layout.DETAILED, Layout.parse("detailed"));
    }

    @Test void minimalShowsOnlyTheName() {
        var lines = TargetHudModel.lines(Layout.MINIMAL, target(20, 0, 30, true, false), true, true, true);
        assertEquals(1, lines.size());
        assertEquals("Steve", lines.get(0));
        assertFalse(TargetHudModel.showsIcons(Layout.MINIMAL));
    }

    @Test void compactStopsBeforeArmourAndPing() {
        var lines = TargetHudModel.lines(Layout.COMPACT, target(20, 0, 30, true, false), true, true, true);
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).startsWith("HP"));
        assertTrue(lines.get(2).startsWith("Distance"));
        assertTrue(TargetHudModel.showsIcons(Layout.COMPACT));
    }

    @Test void detailedAddsArmourAndPingForPlayersOnly() {
        var player = TargetHudModel.lines(Layout.DETAILED, target(20, 0, 30, true, false), true, true, true);
        assertTrue(player.stream().anyMatch(line -> line.startsWith("Armor")));
        assertTrue(player.stream().anyMatch(line -> line.equals("Ping 30 ms")));

        var mob = TargetHudModel.lines(Layout.DETAILED, target(20, 0, 30, false, false), true, true, true);
        assertFalse(mob.stream().anyMatch(line -> line.startsWith("Armor")));
        assertFalse(mob.stream().anyMatch(line -> line.startsWith("Ping")));
    }

    @Test void unknownPingIsOmittedRatherThanShownAsMinusOne() {
        var lines = TargetHudModel.lines(Layout.DETAILED, target(20, 0, -1, true, false), true, true, true);
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Ping")));
    }

    @Test void absorptionIsCalledOutRatherThanHiddenInTheTotal() {
        var lines = TargetHudModel.lines(Layout.COMPACT, target(20, 8, -1, true, false), true, false, false);
        assertEquals("HP 28.0 / 20.0 (+8.0)", lines.get(1));
        var plain = TargetHudModel.lines(Layout.COMPACT, target(20, 0, -1, true, false), true, false, false);
        assertEquals("HP 20.0 / 20.0", plain.get(1));
    }

    @Test void friendsAreMarkedOnEveryLayout() {
        for (Layout layout : Layout.values()) {
            var lines = TargetHudModel.lines(layout, target(20, 0, -1, true, true), true, true, true);
            assertTrue(lines.get(0).startsWith("★ "), layout + " should mark friends");
        }
    }

    @Test void togglesRemoveTheirOwnLinesOnly() {
        var lines = TargetHudModel.lines(Layout.DETAILED, target(20, 0, -1, true, false), false, false, false);
        assertEquals(1, lines.size(), "only the name survives when every toggle is off");
    }

    @Test void anAbsentTargetProducesNoLines() {
        assertTrue(TargetHudModel.lines(Layout.DETAILED, null, true, true, true).isEmpty());
        assertEquals(0, TargetHudModel.healthFraction(null));
    }

    @Test void absorptionFillsTheSameBarInsteadOfOverflowing() {
        assertEquals(1.0, TargetHudModel.healthFraction(target(20, 20, -1, true, false)), 1e-9);
        assertEquals(0.5, TargetHudModel.healthFraction(target(10, 0, -1, true, false)), 1e-9);
        assertEquals(0.0, TargetHudModel.healthFraction(target(-5, 0, -1, true, false)), 1e-9);
    }

    @Test void aZeroMaxHealthDoesNotDivideByZero() {
        var broken = new Target("X", 5, 0, 0, 1, 0, -1, false, false, false);
        assertEquals(0, TargetHudModel.healthFraction(broken));
    }

    @Test void easingApproachesTheTargetAndThenSnaps() {
        double displayed = TargetHudModel.easeToward(1.0, 0.0, 0.5, true);
        assertEquals(0.5, displayed, 1e-9);
        for (int i = 0; i < 50; i++) displayed = TargetHudModel.easeToward(displayed, 0.0, 0.5, true);
        assertEquals(0.0, displayed, 1e-12, "the bar must settle exactly, not creep");
    }

    @Test void reducedMotionReportsTheTrueValueImmediately() {
        assertEquals(0.0, TargetHudModel.easeToward(1.0, 0.0, 0.5, false));
        assertEquals(0.25, TargetHudModel.easeToward(1.0, 0.25, 0.1, false));
    }

    @Test void easingClampsItsInputsInsteadOfPropagatingThem() {
        assertEquals(0.0, TargetHudModel.easeToward(Double.NaN, 0.0, 0.5, true));
        assertEquals(1.0, TargetHudModel.easeToward(0.0, 5.0, 1.0, true));
        assertEquals(0.0, TargetHudModel.easeToward(0.5, Double.NaN, 1.0, true));
        assertEquals(0.5, TargetHudModel.easeToward(0.5, 0.5, Double.NaN, true), 1e-9);
    }

    @Test void barColourFollowsTheThresholdsAndHurtWins() {
        assertEquals(0xFF55DD55, TargetHudModel.barColor(0.9, false));
        assertEquals(0xFFFFCC44, TargetHudModel.barColor(0.5, false));
        assertEquals(0xFFFF5555, TargetHudModel.barColor(0.1, false));
        assertEquals(0xFFFF8888, TargetHudModel.barColor(0.9, true));
    }
}
