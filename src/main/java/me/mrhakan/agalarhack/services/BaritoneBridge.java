package me.mrhakan.agalarhack.services;

import java.lang.reflect.Method;

/**
 * Calls Baritone's Java API when Baritone is installed, and says so plainly when it is not.
 *
 * <p>Two rules shape all of this. The first: <strong>nothing here ever sends chat.</strong> The
 * obvious way to drive Baritone is to send {@code #goto 100 64 -200} as a chat message, and the
 * obvious way that goes wrong is a player without Baritone — or with its prefix changed — announcing
 * their base coordinates to the whole server. A method call cannot leak; a chat message can only be
 * checked for, never taken back.
 *
 * <p>The second: Baritone is optional and is not a compile dependency, so every call goes through
 * reflection against the documented {@code baritone.api} surface. That means a Baritone whose API
 * has moved produces {@link Result#FAILED} and a single log line rather than a crash, and a client
 * with no Baritone at all produces {@link Result#ABSENT} rather than either.
 *
 * <p>Presence is decided by whether the API class loads, not by a mod id. Baritone ships as several
 * builds with different ids and the API package is the thing actually being called, so asking for it
 * directly is both simpler and harder to get wrong.
 */
public final class BaritoneBridge {
    /** What happened, so a caller can tell "no Baritone" from "Baritone said no". */
    public enum Result {
        /** The call was made and Baritone accepted it. */
        STARTED,
        /** Baritone is not installed. Not an error — most clients will be in this state. */
        ABSENT,
        /** Baritone is installed but the call did not go through; the reason is in the log. */
        FAILED
    }

    /**
     * Its own logger rather than the client's static one, so this stays usable — and testable —
     * without a running client behind it.
     */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("agalarhack-baritone");

    private static final String API = "baritone.api.BaritoneAPI";
    private static final String GOAL_BLOCK = "baritone.api.pathing.goals.GoalBlock";
    private static final String GOAL_XZ = "baritone.api.pathing.goals.GoalXZ";
    private static final String GOAL = "baritone.api.pathing.goals.Goal";

    /** Resolved once. Null means "not resolved yet"; {@link #missing} records a settled absence. */
    private Class<?> api;
    private boolean missing;
    private boolean warned;

    /** True when Baritone's API is on the classpath. */
    public boolean available() {
        return api() != null;
    }

    /** Paths to an exact block. */
    public Result pathTo(int x, int y, int z) {
        return path(GOAL_BLOCK, new Class<?>[]{int.class, int.class, int.class}, x, y, z);
    }

    /** Paths to a column, letting Baritone choose the height — what {@code #goto x z} does. */
    public Result pathTo(int x, int z) {
        return path(GOAL_XZ, new Class<?>[]{int.class, int.class}, x, z);
    }

    /** Stops pathing and every process that could start it again. */
    public Result cancel() {
        Class<?> loaded = api();
        if (loaded == null) return Result.ABSENT;
        try {
            Object behavior = pathingBehavior(loaded);
            call(behavior, "cancelEverything");
            return Result.STARTED;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return report("cancel pathing", failure);
        }
    }

    /** Whether Baritone is currently walking a path. False when it is absent. */
    public boolean pathing() {
        Class<?> loaded = api();
        if (loaded == null) return false;
        try {
            return Boolean.TRUE.equals(call(pathingBehavior(loaded), "isPathing"));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            report("read the pathing state", failure);
            return false;
        }
    }

    private Result path(String goalClass, Class<?>[] types, Object... coordinates) {
        Class<?> loaded = api();
        if (loaded == null) return Result.ABSENT;
        try {
            Object baritone = primaryBaritone(loaded);
            Object process = call(baritone, "getCustomGoalProcess");
            Object goal = Class.forName(goalClass, true, loaded.getClassLoader())
                    .getConstructor(types).newInstance(coordinates);
            Class<?> goalType = Class.forName(GOAL, true, loaded.getClassLoader());
            findMethod(process.getClass(), "setGoalAndPath", goalType).invoke(process, goal);
            return Result.STARTED;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return report("start pathing", failure);
        }
    }

    private Object primaryBaritone(Class<?> loaded) throws ReflectiveOperationException {
        return call(loaded.getMethod("getProvider").invoke(null), "getPrimaryBaritone");
    }

    private Object pathingBehavior(Class<?> loaded) throws ReflectiveOperationException {
        return call(primaryBaritone(loaded), "getPathingBehavior");
    }

    /** Invokes a no-argument method, resolved the same careful way as the rest. */
    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return findMethod(target.getClass(), name).invoke(target);
    }

    /**
     * Finds a method on the interface rather than on whatever class happens to implement it.
     *
     * <p>Two reasons, and the stub used by the tests hits both. {@code setGoalAndPath} is a
     * {@code default} method, so it is declared on the interface and not on the implementation at
     * all. And an implementation class is often not public, in which case a method found on it is
     * there but cannot be invoked — a reflection failure that reads like a missing method.
     */
    private static Method findMethod(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> candidate : type.getInterfaces()) {
            try {
                return candidate.getMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                // Keep looking; the next interface may declare it.
            }
        }
        Method direct = type.getMethod(name, parameters);
        if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
            // Found, but on a class nobody outside its package may call into.
            direct.setAccessible(true);
        }
        return direct;
    }

    private Class<?> api() {
        if (missing) return null;
        if (api == null) {
            try {
                api = Class.forName(API);
            } catch (ClassNotFoundException | LinkageError absent) {
                // Settled: a mod cannot appear mid-session, so this is not retried every call.
                missing = true;
                return null;
            }
        }
        return api;
    }

    /** Logs once per session. A broken bridge should say so, not fill the log every tick. */
    private Result report(String what, Throwable failure) {
        if (!warned) {
            warned = true;
            LOGGER.error("Baritone is installed but this client could not {}; "
                    + "its API may have changed. Nothing was sent to chat.", what, failure);
        }
        return Result.FAILED;
    }

    /** Forgets what was resolved. For tests, which install a stub after this has already looked. */
    public void forget() {
        api = null;
        missing = false;
        warned = false;
    }
}
