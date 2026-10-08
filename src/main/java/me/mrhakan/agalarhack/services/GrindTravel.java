package me.mrhakan.agalarhack.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * AutoGrind's Baritone travel: one movement goal at a time, the mining process it owns, stall
 * detection, and the pause message a task shows when travel cannot continue.
 * Moved out of {@link GrindExecutor} in 2.0.05; it reaches the executor's shared state through
 * {@code g}.
 */
final class GrindTravel {
    private final GrindExecutor g;
    boolean ownsMining;
    private BlockPos movementGoal;
    private BlockPos movementTarget;
    private boolean approachingUnloaded;
    private String travelProblem;
    private final GrindTravelProgress travelProgress = new GrindTravelProgress();
    private final GrindBaritoneTransition baritoneTransition = new GrindBaritoneTransition();

    GrindTravel(GrindExecutor g) {
        this.g = g;
    }

    boolean moveTo(BlockPos target) {
        travelProblem = null;
        if (!g.useBaritone || !g.baritone.available()) return false;
        g.rotations.release(GrindExecutor.OWNER); g.inventory.release(GrindExecutor.OWNER);
        if (movementGoal != null && movementGoal.equals(target)) {
            if (g.client.player.blockPosition().equals(target)) { cancelMovement(); return true; }
            return continueTravel();
        }
        cancelMovement();
        if (awaitCancellation()) return true;
        if (g.baritone.mining() || g.baritone.pathing() || g.baritone.goalActive()) return false;
        if (g.baritone.pathTo(target.getX(), target.getY(), target.getZ()) != BaritoneBridge.Result.STARTED) return false;
        movementGoal = target; movementTarget = target; return true;
    }

    boolean moveNear(BlockPos target) {
        travelProblem = null;
        if (!g.useBaritone || !g.baritone.available()) return false;
        g.rotations.release(GrindExecutor.OWNER); g.inventory.release(GrindExecutor.OWNER);
        if (movementGoal != null) {
            if (!target.equals(movementTarget) || approachingUnloaded && g.loaded(target)) cancelMovement();
            else return continueTravel();
        }
        if (awaitCancellation()) return true;
        if (g.baritone.mining() || g.baritone.pathing() || g.baritone.goalActive()) return false;
        // Distant base chunks have no collision data yet. Approach their column, then resolve
        // a visible standing cell once the target loads instead of refusing the return journey.
        if (!g.loaded(target)) {
            if (g.baritone.pathTo(target.getX(), target.getZ()) != BaritoneBridge.Result.STARTED) return false;
            movementGoal = target; movementTarget = target; approachingUnloaded = true;
            return true;
        }
        // GoalBlock is a supported API; choose a clear standing position beside the interaction.
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (int dy = -5; dy <= 1; dy++) for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos feet = target.relative(side).offset(0, dy, 0);
            if (!g.loaded(feet)) continue;
            if (g.client.player.position().distanceToSqr(Vec3.atBottomCenterOf(feet)) < 0.25) continue;
            if (!g.client.level.getBlockState(feet).getCollisionShape(g.client.level, feet).isEmpty()
                    || !g.client.level.getBlockState(feet.above()).getCollisionShape(g.client.level, feet.above()).isEmpty()
                    || g.client.level.getBlockState(feet.below()).getCollisionShape(g.client.level, feet.below()).isEmpty()
                    || !g.client.level.getFluidState(feet).isEmpty()) continue;
            double d = g.client.player.position().distanceToSqr(Vec3.atBottomCenterOf(feet));
            if (d < distance && Vec3.atBottomCenterOf(feet).add(0, 1.62, 0).distanceToSqr(Vec3.atCenterOf(target)) < 19) {
                best = feet; distance = d;
            }
        }
        if (best == null) return false;
        if (g.baritone.pathTo(best.getX(), best.getY(), best.getZ()) != BaritoneBridge.Result.STARTED) return false;
        movementGoal = best; movementTarget = target;
        return true;
    }

    private boolean continueTravel() {
        if (!g.baritone.ownsGoal()) travelProblem = "Baritone travel goal was replaced; finish the other task, then .grind resume.";
        else if (!g.baritone.pathing() && !g.baritone.goalActive()) travelProblem = "Baritone stopped before access was reached; check the route, then .grind resume.";
        else if (travelProgress.stalled(g.client.player.getX(), g.client.player.getY(), g.client.player.getZ()))
            travelProblem = "Baritone made no travel progress for 60 seconds; clear the route, then .grind resume.";
        if (travelProblem == null) return true;
        cancelMovement();
        return false;
    }

    String movementProblem(String fallback) { return travelProblem == null ? fallback : travelProblem; }

    void cancelMovement() {
        if (movementGoal != null) {
            boolean owned = g.baritone.ownsGoal();
            g.baritone.cancelGoal();
            if (owned) baritoneTransition.cancelled();
        }
        movementGoal = null; movementTarget = null; approachingUnloaded = false; travelProgress.reset();
    }

    boolean awaitCancellation() {
        return baritoneTransition.waiting(g.baritone.pathing(), g.baritone.mining(), g.baritone.goalActive());
    }

    void cancelOwnedMining() {
        if (!ownsMining) return;
        g.baritone.cancelMining(); ownsMining = false; baritoneTransition.cancelled();
    }

    void cancelAutomation() {
        cancelOwnedMining();
        cancelMovement();
    }
}
