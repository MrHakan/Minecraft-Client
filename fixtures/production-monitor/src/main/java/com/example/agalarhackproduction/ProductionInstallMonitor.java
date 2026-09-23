package com.example.agalarhackproduction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;


/** Test-only monitor used by the three real production-client launches in CI. */
public final class ProductionInstallMonitor implements ClientModInitializer {
    private boolean finished;
    private final AtomicBoolean stageComplete = new AtomicBoolean();

    @Override
    public void onInitializeClient() {
        String stage = System.getProperty("agalarhack.productionProbeStage", "");
        if (!stage.equals("write") && !stage.equals("verify-addon")
                && !stage.equals("verify-removed")) return;
        AgalarHackClient.LOGGER.info("Production addon probe stage {} started", stage);
        startStartupWatchdog(stage);
        // Fabric fires this on the client thread after construction and before its first tick,
        // while the splash screen is displayed. The production title-screen blur can be very slow
        // on software OpenGL, so validate settings before it starts rendering.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (finished) return;
            finished = true;
            String result = "ok";
            try {
                switch (stage) {
                    case "write" -> writeSettings();
                    case "verify-addon" -> verifyAddonSettingsReloaded();
                    case "verify-removed" -> verifyWithoutAddon();
                    default -> throw new IllegalStateException("Unknown production probe stage: " + stage);
                }
            } catch (Throwable failure) {
                result = "failed: " + failure;
                AgalarHackClient.LOGGER.error("Production addon install probe failed at {}", stage, failure);
            } finally {
                writeMarker(stage, result);
                stageComplete.set(true);
                client.stop();
            }
        });
    }

    /** A stuck production startup must produce diagnostics instead of hanging CI. */
    private void startStartupWatchdog(String stage) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(120_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!stageComplete.compareAndSet(false, true)) return;

            String reason = "failed: production client did not finish " + stage + " within 120 seconds";
            try {
                Path directory = Path.of(System.getProperty("agalarhack.productionProbeMarker"));
                Files.createDirectories(directory);
                StringBuilder dump = new StringBuilder("Startup watchdog expired during ")
                        .append(stage).append('\n');
                for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                    Thread thread = entry.getKey();
                    dump.append('\n').append('"').append(thread.getName()).append('"')
                            .append(" state=").append(thread.getState()).append('\n');
                    for (StackTraceElement frame : entry.getValue()) {
                        dump.append("  at ").append(frame).append('\n');
                    }
                }
                Files.writeString(directory.resolve(stage + "-thread-dump.txt"), dump);
            } catch (Exception failure) {
                AgalarHackClient.LOGGER.error("Could not write production probe thread dump", failure);
            }
            writeMarker(stage, reason);
            AgalarHackClient.LOGGER.error("{}; stopping the production client for CI diagnostics", reason);
            System.exit(2);
        }, "agalarhack-production-probe-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void writeSettings() throws Exception {
        if (!FabricLoader.getInstance().isModLoaded("agalarhack-external-fixture")) {
            throw new IllegalStateException("separately installed addon was not loaded");
        }
        Module addon = AgalarHackClient.moduleManager.getModule("ExternalFixtureModule");
        Module noFall = AgalarHackClient.moduleManager.getModule("NoFall");
        if (addon == null || noFall == null) throw new IllegalStateException("expected modules missing");
        addon.settings.setSetting("externalFlag", false);
        noFall.settings.setSetting("threshold", 9.0);
        if (!Boolean.FALSE.equals(addon.settings.getSetting("externalFlag"))) {
            throw new IllegalStateException("the addon setting could not be changed before writing config");
        }
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        Path config = FabricLoader.getInstance().getConfigDir().resolve("agalarhack.json");
        if (!Files.isRegularFile(config) || Files.size(config) == 0) {
            throw new IllegalStateException("the installed client did not write its config");
        }
        String saved = Files.readString(config);
        if (!saved.contains("ExternalFixtureModule") || !saved.contains("externalFlag")) {
            throw new IllegalStateException("the external addon's settings were absent from the saved config");
        }
    }

    /** The second launch keeps the add-on jar installed and proves its saved setting was reloaded. */
    private static void verifyAddonSettingsReloaded() {
        if (!FabricLoader.getInstance().isModLoaded("agalarhack-external-fixture")) {
            throw new IllegalStateException("separately installed addon was missing on restart");
        }
        Module addon = AgalarHackClient.moduleManager.getModule("ExternalFixtureModule");
        Object savedAddon = addon == null ? null : addon.settings.getSetting("externalFlag");
        if (!Boolean.FALSE.equals(savedAddon)) {
            throw new IllegalStateException("addon setting did not survive restart: " + savedAddon);
        }
        verifyBaseClientSetting();
    }

    /** The third launch removes only the add-on jar and proves the base config still loads. */
    private static void verifyWithoutAddon() {
        if (FabricLoader.getInstance().isModLoaded("agalarhack-external-fixture")) {
            throw new IllegalStateException("the removed addon was still present on the removal launch");
        }
        verifyBaseClientSetting();
    }

    private static void verifyBaseClientSetting() {
        Module noFall = AgalarHackClient.moduleManager.getModule("NoFall");
        Object saved = noFall == null ? null : noFall.settings.getSetting("threshold");
        if (!(saved instanceof Number number) || Math.abs(number.doubleValue() - 9.0) > 0.001) {
            throw new IllegalStateException("base client settings did not survive restart: " + saved);
        }
        Path config = FabricLoader.getInstance().getConfigDir().resolve("agalarhack.json");
        if (!Files.isRegularFile(config)) throw new IllegalStateException("base config disappeared after restart");
    }

    private static void writeMarker(String stage, String result) {
        try {
            Path directory = Path.of(System.getProperty("agalarhack.productionProbeMarker"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(stage + ".txt"), result);
        } catch (Exception failure) {
            AgalarHackClient.LOGGER.error("Could not write production install marker for {}", stage, failure);
        }
    }
}
