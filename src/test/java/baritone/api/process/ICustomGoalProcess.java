package baritone.api.process;

import baritone.api.pathing.goals.Goal;

/**
 * Stub of the real interface.
 *
 * <p>{@code setGoalAndPath} is a {@code default} method on the interface in Baritone itself, which
 * is the detail this stub exists to reproduce: it is not found on the implementing class, so the
 * bridge has to look at the interfaces.
 */
public interface ICustomGoalProcess {
    void setGoal(Goal goal);

    default void setGoalAndPath(Goal goal) { setGoal(goal); }
}
