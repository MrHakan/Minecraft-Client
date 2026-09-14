package baritone.api.process;

/** Minimal source-compatible subset of Baritone's 26.2 mining process used by the bridge test. */
public interface IMineProcess {
    void mineByName(int quantity, String... blocks);

    void cancel();

    boolean isActive();
}
