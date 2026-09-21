package baritone.api;

import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.process.IMineProcess;

/**
 * Stub of Baritone's entry point, recording what the bridge asks it to do.
 *
 * <p>It lives in the real package with the real method names and the real shapes — a static
 * {@code getProvider()}, a provider with {@code getPrimaryBaritone()}, an instance with
 * {@code getCustomGoalProcess()} and {@code getPathingBehavior()}, and {@code setGoalAndPath} as a
 * default interface method. That is the whole point: the bridge reaches Baritone by name through
 * reflection, so the only way to test that it names things correctly is to put something correctly
 * named in front of it.
 *
 * <p>Test source only. It is never on the mod's own classpath, so the shipped client sees the real
 * Baritone or nothing at all.
 */
public final class BaritoneAPI {
    public static Goal lastGoal;
    public static int cancels;
    public static int mineCancels;
    public static int lastMineQuantity;
    public static String[] lastMineBlocks;
    public static boolean pathing;
    public static boolean mining;

    private BaritoneAPI() { }

    public static void reset() {
        lastGoal = null;
        cancels = 0;
        mineCancels = 0;
        lastMineQuantity = 0;
        lastMineBlocks = null;
        pathing = false;
        mining = false;
    }

    public static Provider getProvider() { return new Provider(); }

    public static final class Provider {
        public Instance getPrimaryBaritone() { return new Instance(); }
    }

    public static final class Instance {
        public ICustomGoalProcess getCustomGoalProcess() {
            return goal -> lastGoal = goal;
        }

        public IMineProcess getMineProcess() {
            return new IMineProcess() {
                @Override public void mineByName(int quantity, String... blocks) {
                    lastMineQuantity = quantity;
                    lastMineBlocks = blocks.clone();
                    mining = true;
                }

                @Override public void cancel() {
                    mineCancels++;
                    mining = false;
                }

                @Override public boolean isActive() { return mining; }
            };
        }

        public IPathingBehavior getPathingBehavior() {
            return new IPathingBehavior() {
                @Override public boolean cancelEverything() { cancels++; return true; }

                @Override public boolean isPathing() { return pathing; }
            };
        }
    }
}
