package me.mrhakan.agalarhack.services;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import me.mrhakan.agalarhack.module.Module;

/**
 * Keeps one module's failure from taking down the callback it shares with others.
 *
 * <p>The event bus detaches a listener that throws, which is right for a listener that belongs to one
 * thing and wrong for one that fans out to several: the chat callback runs three modules, so a bug in
 * any of them would silently disable chat handling for all three for the rest of the session. Running
 * each module through this makes a failure cost only the module that caused it, which is the policy
 * the module tick loop has always had.
 *
 * <p>Repeated failures are reported once. A module that throws every tick would otherwise produce a
 * notification per tick, which buries the first report — the one that says what actually broke.
 *
 * <p>The failure handler is injected rather than hard-coded so the suppression is unit tested without
 * a client; the composition root supplies the real disable-and-notify.
 */
public final class ModuleGuard {
    private final Set<Module> reported = ConcurrentHashMap.newKeySet();
    private final BiConsumer<Module, RuntimeException> onFailure;

    public ModuleGuard(BiConsumer<Module, RuntimeException> onFailure) {
        this.onFailure = onFailure;
    }

    /**
     * @return true when the work completed; false when it threw and was contained
     */
    public boolean run(Module module, Runnable work) {
        if (module == null || work == null) return false;
        try {
            work.run();
            // Recovered: a later failure is news again rather than being swallowed forever.
            reported.remove(module);
            return true;
        } catch (RuntimeException failure) {
            if (reported.add(module)) {
                // A broken reporter must not turn one module's failure into the client's.
                try { onFailure.accept(module, failure); }
                catch (RuntimeException reportingFailure) { failure.addSuppressed(reportingFailure); }
            }
            return false;
        }
    }

    /** True while a module is still in its failed state and being suppressed. */
    public boolean isSuppressed(Module module) {
        return reported.contains(module);
    }

    public void clear() {
        reported.clear();
    }
}
