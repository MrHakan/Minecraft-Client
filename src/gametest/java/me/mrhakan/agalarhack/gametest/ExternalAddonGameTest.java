package me.mrhakan.agalarhack.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.services.AddonLoader;
import me.mrhakan.agalarhack.services.ClientServices;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;

/**
 * Checks two separately packaged addon jars, installed in this client, from the outside.
 *
 * <p>{@link ModuleBehaviourGameTest}'s addon scenario proves the {@code agalarhack} entrypoint is
 * read. It cannot prove anything about packaging: that addon's classes are on the client's own
 * classpath and its entrypoint is declared in the game-test mod's own metadata, so it would keep
 * passing if a genuinely installed jar were impossible to load. The two fixtures here are built by
 * {@code addonFixtureJar} and {@code brokenAddonFixtureJar} into their own jars, with their own
 * {@code fabric.mod.json}, in packages outside {@code me.mrhakan.agalarhack}.
 *
 * <p>Everything is asserted through the client's own state - the module catalogue, the command
 * registry, the loader's record - because that is all an installed addon's effect is. Nothing here
 * references the fixtures' classes, which is why the ids and names below are literals: they have to
 * match the fixtures' metadata, and a mismatch fails with a message saying so.
 *
 * <p><strong>What this does not prove.</strong> The jars are compiled against named mappings and
 * handed to the launch classpath, so this is not evidence for intermediary remapping of a production
 * jar, nor for discovery from a {@code mods/} folder, nor for anything surviving a restart - one
 * client launch cannot show persistence. Those stay on the manual acceptance list.
 */
public class ExternalAddonGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    /** Must match {@code fixtures/addon/src/main/resources/fabric.mod.json}. */
    private static final String GOOD_ID = "agalarhack-external-fixture";
    private static final String GOOD_MODULE = "ExternalFixtureModule";
    private static final String GOOD_COMMAND = "externalfixture";
    private static final String GOOD_SETTING = "externalFlag";

    /** Must match {@code fixtures/broken-addon/src/main/resources/fabric.mod.json}. */
    private static final String BROKEN_ID = "agalarhack-broken-fixture";
    private static final String BROKEN_COMMAND = "brokenfixture";
    private static final String TAKEN_MODULE = "Fullbright";
    private static final String TAKEN_COMMAND = "help";

    @Override
    public void runTest(ClientGameTestContext context) {
        theJarTestDiscriminates(context);
        installedFromAJar(context);
        registeredWhatItDeclared(context);
        settingsReachedIt(context);
        aBrokenAddonIsContained(context);
        collisionsAreRefused(context);
        LOGGER.info("  external addon jars: one loaded and registered, one contained");
    }

    /**
     * The control for the check below, run before it.
     *
     * <p>"Loaded from a jar" is only evidence if it is not true of everything. In a development
     * launch the client itself is loaded from classpath directories, so the same predicate applied
     * to the client must come back false. If it ever comes back true, this environment stopped
     * distinguishing the two and the packaging claim in this file is empty rather than proven -
     * which is worth failing on, because a silent pass here would be indistinguishable from real
     * evidence.
     */
    private void theJarTestDiscriminates(ClientGameTestContext context) {
        var paths = originPaths(context, "agalarhack");
        if (paths.isEmpty()) {
            throw new AssertionError("the loader has no path origin for the client's own mod id, so "
                    + "the control for the jar check cannot run");
        }
        if (looksLikeAJar(paths)) {
            throw new AssertionError("the client itself reports being loaded from a single jar ("
                    + paths + "), so \"loaded from a jar\" no longer separates an installed addon "
                    + "from a classpath one and proves nothing about packaging");
        }
    }

    /**
     * The packaging claim itself: the loader found this mod inside a jar file on disk.
     *
     * <p>Without this the rest of the scenario would pass for a classpath mod and prove nothing new.
     */
    private void installedFromAJar(ClientGameTestContext context) {
        var paths = originPaths(context, GOOD_ID);
        if (paths.isEmpty()) {
            throw new AssertionError("the loader has no mod \"" + GOOD_ID + "\" with a path origin, "
                    + "so the external addon jar was never installed and nothing below this tests "
                    + "packaging. Check that runClientGameTest still puts the fixture jars on the "
                    + "launch classpath.");
        }
        if (!looksLikeAJar(paths)) {
            throw new AssertionError("mod \"" + GOOD_ID + "\" was loaded from " + paths
                    + " rather than from a single jar file, so this is classpath loading and the "
                    + "packaging claim is not supported");
        }
        LOGGER.info("    external addon loaded from {}", paths.get(0));
    }

    private void registeredWhatItDeclared(ClientGameTestContext context) {
        boolean module = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModule(GOOD_MODULE) != null);
        boolean command = context.computeOnClient(client ->
                me.mrhakan.agalarhack.managers.CommandManager.getCommand(GOOD_COMMAND) != null);
        if (!module) {
            throw new AssertionError("the external addon's module \"" + GOOD_MODULE + "\" is not in "
                    + "the catalogue, so addModule does not work for an addon outside this jar");
        }
        if (!command) {
            throw new AssertionError("the external addon's command \"" + GOOD_COMMAND + "\" is not "
                    + "registered, so addCommand does not work for an addon outside this jar");
        }
        var record = recordsFor(context, GOOD_ID);
        if (record.size() != 1 || !record.get(0).ok()
                || record.get(0).modules() != 1 || record.get(0).commands() != 1) {
            throw new AssertionError("the loader recorded " + record + " for the external addon, "
                    + "which does not match the one module and one command it registered");
        }
    }

    /**
     * An addon module has to be a first-class one, and settings are where that is decided.
     *
     * <p>{@code registerSettings} runs only inside {@code loadModules}, so a module registered after
     * it has no settings at all and keeps nothing across restarts. The addon's own declared setting
     * is checked as well as the inherited keybind: the inherited one would still be there if
     * {@code selfSettings} were never called.
     */
    private void settingsReachedIt(ClientGameTestContext context) {
        var missing = context.computeOnClient(client -> {
            var module = AgalarHackClient.moduleManager.getModule(GOOD_MODULE);
            if (module == null) return "the module is gone";
            if (module.settings.getSetting("keybind") == null) return "no keybind setting";
            if (module.settings.getSetting(GOOD_SETTING) == null) return "no " + GOOD_SETTING;
            return "";
        });
        if (!missing.isEmpty()) {
            throw new AssertionError("the external addon's module has " + missing + ", so addons "
                    + "register too late for loadModules to apply settings to them");
        }
    }

    /**
     * A broken addon costs its author a bug report and nothing else.
     *
     * <p>Its jar declares two entrypoints and both fail, so the loader has to guard them separately
     * and record both against the same mod id. What the failing addon managed to register before it
     * threw stays registered, which is the documented behaviour rather than an accident.
     */
    private void aBrokenAddonIsContained(ClientGameTestContext context) {
        var records = recordsFor(context, BROKEN_ID);
        if (records.size() != 2 || records.stream().anyMatch(AddonLoader.Loaded::ok)) {
            throw new AssertionError("expected two failed records for \"" + BROKEN_ID
                    + "\" - one per entrypoint - but the loader recorded " + records);
        }
        boolean survived = context.computeOnClient(client ->
                me.mrhakan.agalarhack.managers.CommandManager.getCommand(BROKEN_COMMAND) != null);
        if (!survived) {
            throw new AssertionError("the failing addon's \"" + BROKEN_COMMAND + "\" command is "
                    + "gone. Registrations made before an addon throws are documented as kept, so "
                    + "either the loader now rolls them back or the command never registered");
        }
    }

    /** A name already taken has to be refused, and the built-in has to be the one still answering. */
    private void collisionsAreRefused(ClientGameTestContext context) {
        String moduleOwner = context.computeOnClient(client -> {
            var module = AgalarHackClient.moduleManager.getModule(TAKEN_MODULE);
            return module == null ? "absent" : module.getClass().getName();
        });
        if (!moduleOwner.startsWith("me.mrhakan.agalarhack")) {
            throw new AssertionError("module \"" + TAKEN_MODULE + "\" is now " + moduleOwner
                    + ", so an addon replaced or shadowed a built-in module");
        }
        String commandOwner = context.computeOnClient(client -> {
            var command = me.mrhakan.agalarhack.managers.CommandManager.getCommand(TAKEN_COMMAND);
            return command == null ? "absent" : command.getClass().getName();
        });
        if (!commandOwner.startsWith("me.mrhakan.agalarhack")) {
            throw new AssertionError("command \"" + TAKEN_COMMAND + "\" is now " + commandOwner
                    + ", so an addon replaced or shadowed a built-in command");
        }
        long duplicates = context.computeOnClient(client ->
                AgalarHackClient.moduleManager.getModuleList().stream()
                        .filter(module -> module.getName().equalsIgnoreCase(TAKEN_MODULE))
                        .count());
        if (duplicates != 1) {
            throw new AssertionError("the catalogue holds " + duplicates + " modules named \""
                    + TAKEN_MODULE + "\"; a refused registration must leave exactly one");
        }
    }

    /** Path origins the loader recorded for a mod id, or empty when it has none. */
    private List<String> originPaths(ClientGameTestContext context, String id) {
        return context.computeOnClient(client -> FabricLoader.getInstance()
                .getModContainer(id)
                .map(mod -> mod.getOrigin().getKind() == ModOrigin.Kind.PATH
                        ? mod.getOrigin().getPaths().stream().map(Path::toString).toList()
                        : List.<String>of())
                .orElse(List.of()));
    }

    /** One path, ending in {@code .jar}, that is a real file - not a set of classpath directories. */
    private static boolean looksLikeAJar(List<String> paths) {
        return paths.size() == 1 && paths.get(0).endsWith(".jar")
                && Files.isRegularFile(Path.of(paths.get(0)));
    }

    private List<AddonLoader.Loaded> recordsFor(ClientGameTestContext context, String id) {
        return context.computeOnClient(client -> ClientServices.require(AddonLoader.class).loaded()
                .stream().filter(entry -> id.equals(entry.id())).toList());
    }
}
