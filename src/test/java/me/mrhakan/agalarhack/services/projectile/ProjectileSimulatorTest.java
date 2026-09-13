package me.mrhakan.agalarhack.services.projectile;

import me.mrhakan.agalarhack.services.projectile.ProjectilePhysics.Family;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileSimulatorTest {
    private static ProjectileSimulator.State launch(ProjectilePhysics physics) {
        return ProjectileSimulator.launch(0, 64, 0, 0, 0, physics, 1.0, 0, 0, 0);
    }

    @Test void yawZeroAndPitchZeroFiresAlongPositiveZ() {
        var state = launch(ProjectilePhysics.of(Family.CROSSBOW, 0));
        assertEquals(0.0, state.vx(), 1e-9);
        assertEquals(0.0, state.vy(), 1e-9);
        assertEquals(3.15, state.vz(), 1e-9);
    }

    @Test void launchSpeedScalesWithPower() {
        var physics = ProjectilePhysics.of(Family.CROSSBOW, 0);
        var half = ProjectileSimulator.launch(0, 0, 0, 0, 0, physics, 0.5, 0, 0, 0);
        assertEquals(3.15 * 0.5, half.vz(), 1e-9);
    }

    @Test void potionsAreThrownAboveTheAimPoint() {
        // The -20 degree pitch offset is what makes a potion arc instead of firing flat.
        var potion = launch(ProjectilePhysics.of(Family.SPLASH_POTION, 0));
        assertTrue(potion.vy() > 0, "expected an upward component, got " + potion.vy());
        var arrow = launch(ProjectilePhysics.of(Family.CROSSBOW, 0));
        assertEquals(0.0, arrow.vy(), 1e-9);
    }

    @Test void inheritedMotionIsAddedToTheLaunchVelocity() {
        var physics = ProjectilePhysics.of(Family.CROSSBOW, 0);
        var state = ProjectileSimulator.launch(0, 0, 0, 0, 0, physics, 1.0, 0.4, -0.1, 0.2);
        assertEquals(0.4, state.vx(), 1e-9);
        assertEquals(-0.1, state.vy(), 1e-9);
        assertEquals(3.15 + 0.2, state.vz(), 1e-9);
    }

    @Test void arrowsMoveFirstAndThenLoseSpeed() {
        var physics = ProjectilePhysics.of(Family.CROSSBOW, 0);
        var start = launch(physics);
        var next = ProjectileSimulator.advance(start, physics);
        // The first step covers the full launch velocity; drag and gravity apply afterwards.
        assertEquals(3.15, next.z(), 1e-9);
        assertEquals(64.0, next.y(), 1e-9);
        assertEquals(3.15 * 0.99, next.vz(), 1e-9);
        assertEquals(-0.05, next.vy(), 1e-9);
    }

    @Test void throwablesLoseSpeedBeforeTheyMove() {
        var physics = ProjectilePhysics.of(Family.THROWN, 0);
        var start = launch(physics);
        var next = ProjectileSimulator.advance(start, physics);
        // Gravity then drag are applied first, so the very first step is already slowed.
        assertEquals(1.5 * 0.99, next.z(), 1e-9);
        assertEquals(64.0 + (-0.03 * 0.99), next.y(), 1e-9);
    }

    @Test void theTwoIntegrationOrdersGenuinelyDiffer() {
        var arrowLike = new ProjectilePhysics(1.0, 0.05, 0.99, 0, false);
        var thrownLike = new ProjectilePhysics(1.0, 0.05, 0.99, 0, true);
        var a = ProjectileSimulator.advance(launch(arrowLike), arrowLike);
        var b = ProjectileSimulator.advance(launch(thrownLike), thrownLike);
        assertNotEquals(a.y(), b.y(), "order of drag and gravity must change the path");
    }

    @Test void familiesCarryTheirVanillaConstants() {
        assertEquals(0.05, ProjectilePhysics.of(Family.CROSSBOW, 0).gravity(), 1e-9);
        assertEquals(0.05, ProjectilePhysics.of(Family.TRIDENT, 0).gravity(), 1e-9);
        assertEquals(0.03, ProjectilePhysics.of(Family.THROWN, 0).gravity(), 1e-9);
        assertEquals(0.05, ProjectilePhysics.of(Family.SPLASH_POTION, 0).gravity(), 1e-9);
        assertEquals(0.07, ProjectilePhysics.of(Family.XP_BOTTLE, 0).gravity(), 1e-9);
        assertEquals(2.5, ProjectilePhysics.of(Family.TRIDENT, 0).speed(), 1e-9);
        assertFalse(ProjectilePhysics.of(Family.CROSSBOW, 0).gravityBeforeDrag());
        assertTrue(ProjectilePhysics.of(Family.THROWN, 0).gravityBeforeDrag());
    }

    @Test void bowSpeedComesFromTheCaller() {
        assertEquals(3.0, ProjectilePhysics.of(Family.BOW, 3.0).speed(), 1e-9);
        assertEquals(1.2, ProjectilePhysics.of(Family.BOW, 1.2).speed(), 1e-9);
    }

    @Test void nonFiniteOrOutOfRangeValuesFallBackInsteadOfPropagating() {
        var physics = new ProjectilePhysics(Double.NaN, Double.NaN, 5.0, Double.POSITIVE_INFINITY, false);
        assertTrue(Double.isFinite(physics.speed()));
        assertTrue(Double.isFinite(physics.gravity()));
        assertEquals(1.0, physics.drag(), 1e-9);
        assertEquals(0.0, physics.pitchOffset(), 1e-9);
        assertEquals(0.0, new ProjectilePhysics(1, -5, 0.99, 0, false).gravity(), 1e-9);
    }

    @Test void aFallingProjectileNeverGainsHeight() {
        var physics = ProjectilePhysics.of(Family.THROWN, 0);
        var state = ProjectileSimulator.launch(0, 64, 0, 0, 0, physics, 1.0, 0, 0, 0);
        double previous = state.y();
        for (int tick = 0; tick < 200; tick++) {
            state = ProjectileSimulator.advance(state, physics);
            assertTrue(state.y() <= previous + 1e-9, "height increased at tick " + tick);
            previous = state.y();
        }
    }

    @Test void closestApproachFindsTheNearestPointAndItsTick() {
        var physics = ProjectilePhysics.of(Family.CROSSBOW, 0);
        var state = ProjectileSimulator.launch(0, 64, 0, 0, 0, physics, 1.0, 0, 0, 0);
        var approach = ProjectileSimulator.closestApproach(state, physics, 0, 64, 30, 100);
        assertTrue(approach.ticks() > 0, "the target is ahead, not at the start");
        assertTrue(approach.distance() < 3.0, "should pass close to a point on its path");
    }

    @Test void closestApproachReportsTickZeroWhenAlreadyClosest() {
        var physics = ProjectilePhysics.of(Family.CROSSBOW, 0);
        var state = ProjectileSimulator.launch(0, 64, 0, 0, 0, physics, 1.0, 0, 0, 0);
        var approach = ProjectileSimulator.closestApproach(state, physics, 0, 64, -50, 100);
        assertEquals(0, approach.ticks(), "a projectile moving away is closest right now");
    }

    @Test void closestApproachStepCountIsBounded() {
        var physics = ProjectilePhysics.of(Family.CROSSBOW, 0);
        var state = ProjectileSimulator.launch(0, 64, 0, 0, 0, physics, 1.0, 0, 0, 0);
        assertDoesNotThrow(() -> ProjectileSimulator.closestApproach(state, physics, 0, 0, 0, Integer.MAX_VALUE));
        assertDoesNotThrow(() -> ProjectileSimulator.closestApproach(state, physics, 0, 0, 0, -10));
    }

    @Test void horizontalSpeedIgnoresVerticalMotion() {
        var state = new ProjectileSimulator.State(0, 0, 0, 3, -100, 4);
        assertEquals(5.0, ProjectileSimulator.horizontalSpeed(state), 1e-9);
    }
}
