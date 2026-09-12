package me.mrhakan.agalarhack.services;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sequencing rules a long plan depends on, run against tasks that only count ticks.
 *
 * <p>That is the point of keeping the runner free of Minecraft: a five-minute grind is tested in a
 * millisecond, and the cases that matter - work already done, a step that can never finish, a step
 * that throws - are arranged directly instead of being waited for in a world.
 */
class TaskRunnerTest {

    /** A task that becomes satisfied after a set number of ticks. */
    private static final class Counting implements TaskRunner.Task {
        private final String name;
        private final int ticksNeeded;
        private final int budget;
        int ticked;
        int cancelled;
        int satisfiedCalls;

        Counting(String name, int ticksNeeded) { this(name, ticksNeeded, TaskRunner.DEFAULT_BUDGET_TICKS); }

        Counting(String name, int ticksNeeded, int budget) {
            this.name = name;
            this.ticksNeeded = ticksNeeded;
            this.budget = budget;
        }

        @Override public String name() { return name; }
        @Override public boolean satisfied() { satisfiedCalls++; return ticked >= ticksNeeded; }
        @Override public boolean tick() { ticked++; return true; }
        @Override public void cancel() { cancelled++; }
        @Override public int budgetTicks() { return budget; }
    }

    private static void tick(TaskRunner runner, int times) {
        for (int i = 0; i < times; i++) runner.tick();
    }

    @Test void anEmptyPlanIsAlreadyDone() {
        var runner = new TaskRunner();
        runner.start(List.of());
        assertEquals(TaskRunner.State.DONE, runner.state());
        assertNull(runner.failure());
    }

    @Test void tasksRunInOrderAndOnlyOneAtATime() {
        var first = new Counting("first", 3);
        var second = new Counting("second", 2);
        var runner = new TaskRunner();
        runner.start(List.of(first, second));

        runner.tick();
        assertEquals(1, first.ticked);
        assertEquals(0, second.ticked, "the second task started before the first finished");
        assertEquals("first", runner.currentTask());

        tick(runner, 2);
        assertEquals(3, first.ticked);
        assertEquals("second", runner.currentTask());
        assertEquals(1, runner.completed());

        tick(runner, 2);
        assertEquals(TaskRunner.State.DONE, runner.state());
        assertEquals(2, runner.completed());
        assertEquals(2, runner.total());
    }

    /** Resuming a plan must not redo finished work; this is what makes a grind restartable. */
    @Test void workAlreadyDoneIsSkippedWithoutTicking() {
        var done = new Counting("already done", 0);
        var real = new Counting("real work", 1);
        var runner = new TaskRunner();
        runner.start(List.of(done, real));

        runner.tick();
        assertEquals(0, done.ticked, "a satisfied task was started anyway");
        assertEquals(1, real.ticked);
    }

    @Test void aWholePlanOfFinishedWorkEndsWithoutATickEach() {
        var runner = new TaskRunner();
        var a = new Counting("a", 0);
        var b = new Counting("b", 0);
        runner.start(List.of(a, b));
        runner.tick();
        assertEquals(TaskRunner.State.DONE, runner.state());
        assertEquals(0, a.ticked + b.ticked);
    }

    @Test void aTaskThatGivesUpFailsThePlanByName() {
        var runner = new TaskRunner();
        runner.start(List.of(new TaskRunner.Task() {
            @Override public String name() { return "find diamonds"; }
            @Override public boolean satisfied() { return false; }
            @Override public boolean tick() { return false; }
        }));
        runner.tick();
        assertEquals(TaskRunner.State.FAILED, runner.state());
        assertTrue(runner.failure().startsWith("find diamonds"), runner.failure());
        assertTrue(runner.failure().contains("gave up"), runner.failure());
    }

    /** Without this a task that can never finish grinds forever and the plan never reports anything. */
    @Test void aTaskThatNeverFinishesRunsOutOfItsBudget() {
        var stuck = new Counting("walk to the village", Integer.MAX_VALUE, 5);
        var runner = new TaskRunner();
        runner.start(List.of(stuck));
        tick(runner, 20);
        assertEquals(TaskRunner.State.FAILED, runner.state());
        assertTrue(runner.failure().contains("ran out of time"), runner.failure());
        assertEquals(5, stuck.ticked, "the runner kept ticking a task it had already given up on");
        assertEquals(1, stuck.cancelled, "a failed task was not told to put its state back");
    }

    @Test void theBudgetIsPerTaskRatherThanPerPlan() {
        var first = new Counting("first", 4, 5);
        var second = new Counting("second", 4, 5);
        var runner = new TaskRunner();
        runner.start(List.of(first, second));
        tick(runner, 8);
        assertEquals(TaskRunner.State.DONE, runner.state(),
                "the second task inherited the first task's spent budget");
    }

    @Test void aThrowingTaskFailsThePlanInsteadOfEscaping() {
        var runner = new TaskRunner();
        runner.start(List.of(new TaskRunner.Task() {
            @Override public String name() { return "mine stone"; }
            @Override public boolean satisfied() { return false; }
            @Override public boolean tick() { throw new IllegalStateException("no pickaxe"); }
        }));
        assertDoesNotThrow(runner::tick);
        assertEquals(TaskRunner.State.FAILED, runner.state());
        assertTrue(runner.failure().contains("mine stone"), runner.failure());
        assertTrue(runner.failure().contains("no pickaxe"), runner.failure());
    }

    @Test void aTaskThatThrowsWhileReportingProgressAlsoFails() {
        var runner = new TaskRunner();
        runner.start(List.of(new TaskRunner.Task() {
            @Override public String name() { return "count logs"; }
            @Override public boolean satisfied() { throw new IllegalStateException("no player"); }
            @Override public boolean tick() { return true; }
        }));
        assertDoesNotThrow(runner::tick);
        assertEquals(TaskRunner.State.FAILED, runner.state());
        assertTrue(runner.failure().contains("count logs"), runner.failure());
    }

    @Test void cancellingStopsThePlanAndTellsOnlyTheCurrentTask() {
        var first = new Counting("first", 10);
        var second = new Counting("second", 1);
        var runner = new TaskRunner();
        runner.start(List.of(first, second));
        runner.tick();
        runner.cancel();

        assertEquals(TaskRunner.State.IDLE, runner.state());
        assertEquals(1, first.cancelled);
        assertEquals(0, second.cancelled, "a task that never ran was told to cancel");
        assertNull(runner.currentTask());

        runner.tick();
        assertEquals(1, first.ticked, "a cancelled plan kept running");
    }

    /** A task that throws on the way out must not replace the reason the plan stopped. */
    @Test void aTaskThatThrowsWhileCancellingDoesNotHideTheFailure() {
        var runner = new TaskRunner();
        runner.start(List.of(new TaskRunner.Task() {
            @Override public String name() { return "smelt iron"; }
            @Override public boolean satisfied() { return false; }
            @Override public boolean tick() { return false; }
            @Override public void cancel() { throw new IllegalStateException("furnace gone"); }
        }));
        assertDoesNotThrow(runner::tick);
        assertEquals(TaskRunner.State.FAILED, runner.state());
        assertTrue(runner.failure().contains("smelt iron"), runner.failure());
        assertFalse(runner.failure().contains("furnace gone"), runner.failure());
    }

    @Test void startingAgainCancelsWhatWasRunning() {
        var first = new Counting("first", 10);
        var runner = new TaskRunner();
        runner.start(List.of(first));
        runner.tick();
        runner.start(List.of(new Counting("replacement", 1)));
        assertEquals(1, first.cancelled);
        assertEquals("replacement", runner.currentTask());
    }

    @Test void aFinishedPlanStaysFinished() {
        var runner = new TaskRunner();
        var only = new Counting("only", 1);
        runner.start(List.of(only));
        tick(runner, 5);
        assertEquals(TaskRunner.State.DONE, runner.state());
        assertEquals(1, only.ticked, "the runner kept ticking a finished plan");
    }

    @Test void progressIsReportableWhileItRuns() {
        var runner = new TaskRunner();
        runner.start(List.of(new Counting("chop wood", 3), new Counting("craft table", 1)));
        runner.tick();
        assertEquals("chop wood", runner.currentTask());
        assertEquals(0, runner.completed());
        assertEquals(2, runner.total());
        assertEquals(1, runner.spentOnCurrent());
        assertTrue(runner.running());
    }

    @Test void aNullTaskIsRefusedRatherThanStored() {
        var runner = new TaskRunner();
        var withNull = new java.util.ArrayList<TaskRunner.Task>();
        withNull.add(new Counting("fine", 1));
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> runner.start(withNull));
    }
}
