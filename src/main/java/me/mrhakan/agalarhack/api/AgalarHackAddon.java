package me.mrhakan.agalarhack.api;

/**
 * Implemented by an addon and named in its {@code fabric.mod.json}:
 *
 * <pre>{@code
 * "entrypoints": { "agalarhack": [ "com.example.ExampleAddon" ] }
 * }</pre>
 *
 * <p>An addon is an ordinary Fabric mod. It is discovered by the loader rather than by this client
 * scanning a folder and calling {@code URLClassLoader} on whatever it finds, which is both less code
 * here and a great deal less to get wrong: version conflicts, load order and mod metadata are all
 * the loader's job and it already does them.
 */
public interface AgalarHackAddon {
    /**
     * Called once, on the client thread, after this client's own modules, commands and services
     * exist and before the first tick.
     *
     * <p>Throwing from here disables the addon and reports it. It does not stop the client: a broken
     * addon should cost its author a bug report, not everyone else their game.
     */
    void onAgalarHackReady(AddonContext context);
}
