package me.mrhakan.agalarhack.services;

import java.util.ArrayList;
import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.api.AddonContext;
import me.mrhakan.agalarhack.api.AgalarHackApi;
import me.mrhakan.agalarhack.api.AgalarHackAddon;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.module.Module;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Finds addons through the Fabric loader and gives each one a turn.
 *
 * <p>Discovery is the loader's job. An addon is an ordinary Fabric mod declaring an
 * {@code agalarhack} entrypoint, so version conflicts, load order and metadata are all handled by
 * something that already does them properly — there is no folder scan and no class loader here.
 *
 * <p>Each addon is called inside its own guard. A broken addon costs its author a bug report and
 * loses its own registrations; it does not stop the client or any other addon. The same applies to
 * an addon that registers a module whose name is already taken: that is its mistake to fix, and it
 * is reported against its own id.
 */
public final class AddonLoader {
    /** What loaded, and what did not, for the {@code .addons} command to report. */
    public record Loaded(String id, String name, String version, int modules, int commands,
                         String failure) {
        public boolean ok() { return failure == null; }
    }

    private final List<Loaded> loaded = new ArrayList<>();
    private boolean ran;

    public List<Loaded> loaded() { return List.copyOf(loaded); }

    /** True once loading has been attempted, whether or not anything was found. */
    public boolean ran() { return ran; }

    /**
     * Calls every declared addon once.
     *
     * <p>Must run after the commands are registered and before modules are loaded: an addon module
     * registered after {@code loadModules} never receives its saved settings, and an addon command
     * registered before {@code CommandManager.init} is wiped by the {@code clear()} it starts with.
     */
    public void load() {
        if (ran) return;
        ran = true;
        List<net.fabricmc.loader.api.entrypoint.EntrypointContainer<AgalarHackAddon>> found;
        try {
            found = FabricLoader.getInstance()
                    .getEntrypointContainers(AgalarHackApi.ENTRYPOINT, AgalarHackAddon.class);
        } catch (RuntimeException failure) {
            AgalarHackClient.LOGGER.error("Could not read addon entrypoints; continuing without addons",
                    failure);
            return;
        }
        for (var container : found) {
            var metadata = container.getProvider().getMetadata();
            Registrar registrar = new Registrar(metadata.getId(), metadata.getName(),
                    metadata.getVersion().getFriendlyString());
            try {
                container.getEntrypoint().onAgalarHackReady(registrar);
                loaded.add(registrar.result(null));
                AgalarHackClient.LOGGER.info("Loaded addon {} {} ({} module(s), {} command(s))",
                        registrar.name, registrar.version, registrar.modules, registrar.commands);
            } catch (RuntimeException | LinkageError failure) {
                loaded.add(registrar.result(failure.toString()));
                AgalarHackClient.LOGGER.error("Addon {} failed to load; it has been skipped",
                        registrar.id, failure);
            }
        }
    }

    /** The published surface, one instance per addon so each is told only about itself. */
    private static final class Registrar implements AddonContext {
        private final String id;
        private final String name;
        private final String version;
        private final org.slf4j.Logger logger;
        private int modules;
        private int commands;

        private Registrar(String id, String name, String version) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.logger = org.slf4j.LoggerFactory.getLogger("agalarhack-addon/" + id);
        }

        @Override public String id() { return id; }

        @Override public String name() { return name; }

        @Override public String version() { return version; }

        @Override public org.slf4j.Logger logger() { return logger; }

        @Override
        public void addModule(Module module) {
            if (module == null) throw new IllegalArgumentException("Addon " + id + " added a null module");
            AgalarHackClient.moduleManager.register(module);
            modules++;
        }

        @Override
        public void addCommand(Command command) {
            if (command == null) throw new IllegalArgumentException("Addon " + id + " added a null command");
            me.mrhakan.agalarhack.managers.CommandManager.register(command);
            commands++;
        }

        private Loaded result(String failure) {
            return new Loaded(id, name, version, modules, commands, failure);
        }
    }
}
