package me.mrhakan.agalarhack.api;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.module.Module;
import org.slf4j.Logger;

/**
 * What an addon is handed when it loads: its own identity, somewhere to log, and the two things it
 * can add.
 *
 * <p>Both {@code add} methods are the client's existing {@link Module} and {@link Command} types
 * rather than addon-specific copies. That is a real commitment and worth stating plainly: those two
 * classes are now part of the published surface, and changing them breaks addons. Everything else
 * this client has stays private.
 */
public interface AddonContext {
    /** The addon's Fabric mod id, from its own {@code fabric.mod.json}. */
    String id();

    /** The addon's display name. */
    String name();

    /** The addon's version string. */
    String version();

    /** A logger named for the addon, so its output is attributable. */
    Logger logger();

    /**
     * Registers a module, which appears in the ClickGUI and the module list like any other.
     *
     * @throws IllegalStateException if a module of that name already exists
     */
    void addModule(Module module);

    /**
     * Registers a command under the client's own prefix.
     *
     * @throws IllegalStateException if that name or alias is already taken
     */
    void addCommand(Command command);
}
