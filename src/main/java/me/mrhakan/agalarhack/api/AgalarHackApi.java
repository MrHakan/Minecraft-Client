package me.mrhakan.agalarhack.api;

/**
 * What an addon is allowed to assume about this client.
 *
 * <p><strong>This surface is provisional.</strong> It is deliberately small, and it will grow before
 * it settles: publishing an interface other people build against is the hardest decision in this
 * project to walk back, so what is published is the least that is useful rather than everything that
 * exists. Nothing internal is exposed through it — not the event bus, not the service registry, not
 * the scanner — because every one of those would be frozen the day an addon touched it.
 *
 * <p>Addons should check {@link #version()} and refuse to register against a number they do not know.
 * Additive changes keep the number; anything that could break an existing addon raises it.
 */
public final class AgalarHackApi {
    /**
     * Raised whenever a change could break an addon written against the previous number.
     *
     * <p>Kept private on purpose. A {@code public static final int} is a compile-time constant, so
     * javac bakes its value into every addon that reads it — an addon built against version 1 would
     * compare the literal {@code 1} against the literal {@code 1} and pass its own version check no
     * matter which version of this client it was later dropped into. A method call cannot be
     * inlined that way, so {@link #version()} is the only way to ask.
     */
    private static final int VERSION = 1;

    /** The version of this surface, read at runtime. */
    public static int version() {
        return VERSION;
    }

    /**
     * The {@code fabric.mod.json} entrypoint key an addon declares itself under.
     *
     * <p>Safe to inline: an addon writes this into its own JSON as a literal anyway, and the key
     * cannot change without every existing addon's JSON needing an edit regardless.
     */
    public static final String ENTRYPOINT = "agalarhack";

    private AgalarHackApi() { }
}
