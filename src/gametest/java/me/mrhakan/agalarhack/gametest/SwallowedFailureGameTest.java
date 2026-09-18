package me.mrhakan.agalarhack.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Fails the run if the mod logged a failure it had already decided to survive.
 *
 * <p>The mod catches a lot on purpose: a module whose tick throws is disabled rather than allowed to
 * crash the game, a render callback that throws is suspended, a scanner that throws is dropped. That
 * is the right behaviour for a player and the wrong behaviour for a test, because it turns a real bug
 * into a line in a log nobody reads. A module's {@code onDisable} throwing is invisible from
 * {@link ModuleLifecycleGameTest} for exactly this reason.
 *
 * <p>So this reads the log the client just wrote and treats every one of those catch sites as a
 * failure. It must be the last entry point to run.
 */
public class SwallowedFailureGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");

    /**
     * The {@code LOGGER.error} messages in the mod that sit inside a catch block. Kept as the literal
     * prefixes rather than a regex over the level, because the game itself logs errors this run has
     * no opinion about - a missing sound, a resource pack warning - and failing on those would make
     * the check worthless within a week.
     */
    private static final List<String> SWALLOWED = List.of(
            "Disabling module after tick failure",
            "Disabling module after a callback failure",
            "Module lifecycle failed",
            "Module cleanup failed",
            "World transition failed for",
            "Render callback failed for",
            "Scanner failed for",
            "HUD component suspended",
            "Event listener failed",
            "Block update dispatch failed",
            "Chat decoration failed",
            "Failed to apply partial profile state for",
            "Macro failed",
            "Malformed config",
            "Cannot read config",
            "Command registration failed for",
            "Could not read addon entrypoints",
            "failed to load; it has been skipped",
            "Baritone is installed but this client could not");

    /**
     * The one deliberate failure in the run: the broken addon fixture, which exists to prove a
     * failing addon is contained.
     *
     * <p>Exempted by mod id rather than by loosening the marker, so a real addon failure - or the
     * same failure from anything else - still fails the run. The exemption is also asserted rather
     * than merely applied: if no line mentions this id, the fixture has stopped failing and the
     * expectation here is stale, which is worth failing on because {@link ExternalAddonGameTest}
     * would then be checking containment that no longer happens.
     */
    private static final String EXPECTED_FAILING_ADDON = "agalarhack-broken-fixture";

    @Override
    public void runTest(ClientGameTestContext context) {
        Path logs = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("logs"));
        Path log = logs.resolve("latest.log");
        if (!Files.isRegularFile(log)) {
            // Not a soft pass: if the log moved, this check silently stops checking anything, which is
            // the failure mode that let a startup crash live on this branch for several commits.
            throw new AssertionError("No client log at " + log + "; the swallowed-failure check cannot run");
        }

        List<Path> files = runLogs(logs);
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        int expected = 0;
        for (Path file : files) {
            try (BufferedReader reader = open(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    scanned++;
                    for (String marker : SWALLOWED) {
                        if (!line.contains(marker)) continue;
                        if (line.contains(EXPECTED_FAILING_ADDON)) {
                            expected++;
                            break;
                        }
                        offenders.add(line.strip());
                        break;
                    }
                }
            } catch (IOException unreadable) {
                throw new UncheckedIOException("Could not read " + file, unreadable);
            }
        }

        if (!offenders.isEmpty()) {
            throw new AssertionError("The mod caught and logged " + offenders.size()
                    + " failure(s) during this run. Each is a real bug that a player would never see:\n  "
                    + String.join("\n  ", offenders.subList(0, Math.min(20, offenders.size()))));
        }
        if (expected == 0) {
            throw new AssertionError("No logged failure mentions " + EXPECTED_FAILING_ADDON
                    + ". That fixture is installed to fail on purpose, so either it no longer fails - "
                    + "in which case the containment checks in ExternalAddonGameTest are testing "
                    + "nothing - or it was never installed in this run.");
        }
        LOGGER.info("Scanned {} log lines across {} file(s) for swallowed failures; found none beyond "
                + "the {} expected from the deliberately broken addon fixture", scanned, files.size(), expected);
    }

    /**
     * Every log file this run wrote, not just the one it is writing now.
     *
     * <p>Reading only {@code latest.log} assumes the client writes one file for the whole run, and it
     * does not: the logger rolls on a date change, so a run that crosses midnight leaves everything
     * before it in an archive and starts {@code latest.log} again from empty. CI run 212 did exactly
     * that - the game tests ran from 23:54 to 00:00 - and the scan came up empty.
     *
     * <p><strong>The visible result was a failure, but the real risk was the opposite.</strong> The
     * scan would have gone on "passing" while reading almost nothing; what actually failed was the
     * assertion that the deliberately broken fixture's failure is still present, which exists to
     * catch precisely the case of this check testing nothing. It earned its keep.
     *
     * <p>Files are selected by modification time against this JVM's start, so an archive left by an
     * earlier run cannot contribute a failure that already happened and was already dealt with.
     */
    private static List<Path> runLogs(Path logs) {
        Instant started = ProcessHandle.current().info().startInstant().orElse(Instant.EPOCH);
        try (Stream<Path> entries = Files.list(logs)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        // debug.log is the same run at a finer level, so reading it as well would
                        // count every failure twice and say nothing new.
                        if (name.equals("debug.log")) return false;
                        return name.equals("latest.log") || name.endsWith(".log.gz") || name.endsWith(".log");
                    })
                    .filter(file -> !modifiedBefore(file, started))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not list " + logs, unreadable);
        }
    }

    private static boolean modifiedBefore(Path file, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException unreadable) {
            // Unreadable timestamps are included rather than skipped: a file this check cannot date
            // is a file it should read, not one it should quietly leave out.
            return false;
        }
    }

    private static BufferedReader open(Path file) throws IOException {
        InputStream stream = Files.newInputStream(file);
        if (file.getFileName().toString().endsWith(".gz")) stream = new GZIPInputStream(stream);
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
