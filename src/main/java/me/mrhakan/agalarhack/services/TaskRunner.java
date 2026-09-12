package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs an ordered plan of tasks, one at a time, and gives up in a way somebody can read.
 *
 * <p>This is the backbone of long automation - "get sixteen logs, then a crafting table, then a
 * stone pickaxe" - and the reason it exists as its own class is that almost everything which makes
 * such automation trustworthy is sequencing logic rather than game logic: skipping work already
 * done, stopping at the first thing that cannot be finished, never running forever, and saying which
 * step failed rather than "it stopped".
 *
 * <p>It holds no Minecraft types. A {@link Task} closes over whatever it needs, so the whole runner
 * is unit tested against tasks that count ticks, which is the only way to test a five-minute
 * sequence in a millisecond.
 *
 * <p>Three rules worth stating because they are what make it safe to point at a player's world:
 * <ul>
 *   <li>a task that is already satisfied is never started, so a resumed plan does not redo work and
 *       a plan run twice is not twice the work;</li>
 *   <li>every task has a tick budget, so a task that can never finish - a block that is not there,
 *       a path that cannot be walked - ends the plan instead of grinding forever;</li>
 *   <li>a task that throws fails its plan rather than escaping into the tick loop, the same error
 *       boundary the modules use.</li>
 * </ul>
 */
public final class TaskRunner {

    /** One step of a plan. Implementations close over the world; the runner never sees it. */
    public interface Task {
        /** Shown to the player and named in failures, so make it specific. */
        String name();

        /**
         * Whether this step's goal already holds.
         *
         * <p>Checked before the task is ever started and again after every tick, which is what lets
         * a plan be resumed, and what lets a step complete because of something else - an item
         * picked up, a block mined by a mob - rather than only because this task did it.
         */
        boolean satisfied();

        /**
         * One tick of work.
         *
         * @return false to give up, which fails the plan. Returning true forever is what the budget
         *         is for.
         */
        boolean tick();

        /** Called when the plan stops for any reason while this task is the current one. */
        default void cancel() { }

        /** How long this task may run before the plan gives up on it. */
        default int budgetTicks() { return DEFAULT_BUDGET_TICKS; }
    }

    /** Five minutes at twenty ticks a second: long enough to walk somewhere, short enough to notice. */
    public static final int DEFAULT_BUDGET_TICKS = 20 * 60 * 5;

    public enum State {
        /** Nothing loaded, or the plan was cancelled. */
        IDLE,
        RUNNING,
        /** Every task is satisfied. */
        DONE,
        /** A task gave up, ran out of budget or threw; {@link #failure()} says which and why. */
        FAILED
    }

    private final List<Task> plan = new ArrayList<>();
    private State state = State.IDLE;
    private int index;
    private int spent;
    private String failure;

    /** Loads a plan and starts it. Replaces anything already running, cancelling it first. */
    public void start(List<Task> tasks) {
        cancel();
        if (tasks == null || tasks.isEmpty()) {
            state = State.DONE;
            return;
        }
        for (Task task : tasks) plan.add(Objects.requireNonNull(task, "a plan cannot hold a null task"));
        state = State.RUNNING;
        index = 0;
        spent = 0;
        failure = null;
    }

    /**
     * Advances the plan by at most one tick of one task.
     *
     * <p>Satisfied tasks are skipped in the same call, so a plan whose work is already done finishes
     * without the player waiting a tick per step for nothing.
     */
    public void tick() {
        if (state != State.RUNNING) return;
        if (!skipSatisfied()) return;

        Task current = plan.get(index);
        boolean keepGoing;
        try {
            keepGoing = current.tick();
        } catch (RuntimeException failed) {
            fail(current, "threw " + failed);
            return;
        }
        spent++;

        boolean done;
        try {
            done = current.satisfied();
        } catch (RuntimeException failed) {
            fail(current, "threw while reporting whether it was finished: " + failed);
            return;
        }
        if (done) {
            advance();
            skipSatisfied();
            return;
        }
        if (!keepGoing) {
            fail(current, "gave up");
            return;
        }
        if (spent >= Math.max(1, current.budgetTicks())) {
            fail(current, "ran out of time after " + spent + " ticks");
        }
    }

    /**
     * Walks past tasks whose goal already holds.
     *
     * @return false when the plan finished or failed doing so, meaning the caller must stop
     */
    private boolean skipSatisfied() {
        while (index < plan.size()) {
            Task task = plan.get(index);
            boolean done;
            try {
                done = task.satisfied();
            } catch (RuntimeException failed) {
                fail(task, "threw while reporting whether it was finished: " + failed);
                return false;
            }
            if (!done) return true;
            advance();
        }
        state = State.DONE;
        return false;
    }

    private void advance() {
        index++;
        spent = 0;
        if (index >= plan.size()) state = State.DONE;
    }

    private void fail(Task task, String because) {
        failure = task.name() + " " + because;
        safeCancel(task);
        state = State.FAILED;
    }

    /** Stops the plan where it is. The current task is told, so it can put its own state back. */
    public void cancel() {
        if (state == State.RUNNING && index < plan.size()) safeCancel(plan.get(index));
        plan.clear();
        state = State.IDLE;
        index = 0;
        spent = 0;
        failure = null;
    }

    /** A task that throws on the way out must not hide why the plan stopped. */
    private void safeCancel(Task task) {
        try {
            task.cancel();
        } catch (RuntimeException ignored) {
            // Nothing useful to do: the plan is already ending, and the reason it is ending is
            // more interesting than a failure to tidy up.
        }
    }

    public State state() { return state; }

    public boolean running() { return state == State.RUNNING; }

    /** Why the plan stopped, naming the task, or null unless it failed. */
    public String failure() { return failure; }

    /** The task being worked on, or null when nothing is running. */
    public String currentTask() {
        return state == State.RUNNING && index < plan.size() ? plan.get(index).name() : null;
    }

    /** How many tasks are finished, for a progress line. */
    public int completed() { return Math.min(index, plan.size()); }

    public int total() { return plan.size(); }

    /** Ticks spent on the current task, so a HUD can show something moving. */
    public int spentOnCurrent() { return spent; }
}
