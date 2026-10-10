package me.mrhakan.agalarhack.gametest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.render.EntityESP;
import me.mrhakan.agalarhack.module.render.Nametags;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.ThemeService;
import me.mrhakan.agalarhack.ui.ClickGuiScreen;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import me.mrhakan.agalarhack.ui.Hud;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The repeatable half of the visual acceptance list in {@code docs/VISUAL_ACCEPTANCE.md}.
 *
 * <p>Every check here compares the client against itself or against arithmetic, never against a
 * stored image, for the reason {@link Frames} gives. The screenshots it takes are kept and uploaded
 * by CI under their {@code visual-} names, because the other half of the list is a person looking at
 * them: whether a theme reads well is not something a pixel count can settle.
 *
 * <ul>
 *   <li>The ClickGUI under four themes at GUI scales 1 to 4, in a window enlarged to 1280x960 for
 *       the purpose: every widget inside the window, the
 *       theme's accent actually on screen, and the light theme brighter than the dark ones.</li>
 *   <li>ESP box geometry: the box drawn where the target's bounding box projects, from the camera's
 *       own position and field of view; ESP labels and Nametags above it and centred on it.</li>
 *   <li>The TargetHUD face: the patch where the card says it drew a face holds a skin.</li>
 * </ul>
 */
public class VisualAcceptanceGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");
    private static final String[] THEMES = { "Default Dark", "AMOLED", "Light", "High contrast" };
    private static final int[] SCALES = { 1, 2, 3, 4 };
    /** Large enough for every GUI scale: vanilla needs 320x240 of scaled space per step. */
    private static final int SCALED_WIDTH = 1280, SCALED_HEIGHT = 960;
    private static final UUID PROBE_ID = UUID.fromString("5c6e7a1d-0b2f-4c3a-9e1d-2a6f0d7b8c91");

    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> failures = new ArrayList<>();
        clickGuiThemesAndScales(context, failures);
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, server);
                server.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
                ServerPlayer player = world.getConnection().getServerPlayer();
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            });
            context.waitTicks(20);
            espGeometry(context, world, failures);
            targetHudFace(context, world, failures);
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Visual acceptance failed:\n  " + String.join("\n  ", failures));
        }
        LOGGER.info("Visual acceptance: themes and GUI scales, ESP and nametag geometry, TargetHUD face");
    }

    // ---------------------------------------------------------------- ClickGUI, themes, GUI scales

    private static void clickGuiThemesAndScales(ClientGameTestContext context, List<String> failures) {
        ThemeService themes = ClientServices.require(ThemeService.class);
        ThemeService.Theme original = context.computeOnClient(client -> themes.copy());
        int originalScale = context.computeOnClient(client -> client.options.guiScale().get());
        int[] window = context.computeOnClient(client ->
                new int[] { client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight() });
        Map<Integer, Map<String, Double>> brightness = new LinkedHashMap<>();
        try {
            // The harness opens 854x480, which allows GUI scale 2 at most. 1280x960 allows all four
            // (vanilla needs 320x240 of scaled space per step); the window is put back afterwards.
            context.runOnClient(client -> client.getWindow().setWindowed(SCALED_WIDTH, SCALED_HEIGHT));
            context.waitTicks(10);
            for (String name : THEMES) {
                for (int scale : SCALES) {
                    int accent = context.computeOnClient(client -> {
                        themes.preview(theme(themes, name));
                        client.options.guiScale().set(scale);
                        client.resizeGui();
                        client.gui.setScreen(new ClickGuiScreen());
                        return ClientUiTheme.ACCENT & 0xFFFFFF;
                    });
                    context.waitTicks(3);
                    int applied = context.computeOnClient(client -> client.getWindow().getGuiScale());
                    String label = name + " at GUI scale " + scale + (applied == scale ? "" : " (window allows " + applied + ")");
                    String outside = context.computeOnClient(client -> widgetsOutside(client));
                    if (!outside.isEmpty()) failures.add("ClickGUI, " + label + ": widgets outside the window: " + outside);
                    if (applied != scale && brightness.containsKey(applied)) continue;
                    Path shot = context.takeScreenshot("visual-clickgui-" + slug(name) + "-x" + applied);
                    int accentPixels = Frames.pixelsNear(shot, accent, 24);
                    if (accentPixels < 20) {
                        failures.add("ClickGUI, " + label + ": only " + accentPixels + " pixels in the accent colour #"
                                + String.format(Locale.ROOT, "%06X", accent) + "; the theme did not reach the screen");
                    }
                    brightness.computeIfAbsent(applied, key -> new LinkedHashMap<>()).put(name, Frames.meanLuminance(shot));
                }
            }
        } finally {
            context.runOnClient(client -> {
                themes.preview(original);
                client.options.guiScale().set(originalScale);
                client.getWindow().setWindowed(window[0], window[1]);
                client.resizeGui();
                client.gui.setScreen(null);
            });
            context.waitTicks(10);
        }
        brightness.forEach((scale, values) -> {
            LOGGER.info("    ClickGUI brightness at GUI scale {}: {}", scale, values.entrySet().stream()
                    .map(entry -> String.format(Locale.ROOT, "%s=%.1f", entry.getKey(), entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(" ")));
            double light = values.getOrDefault("Light", 0.0);
            for (String dark : List.of("Default Dark", "AMOLED", "High contrast")) {
                if (values.containsKey(dark) && values.get(dark) >= light) {
                    failures.add("ClickGUI at GUI scale " + scale + ": " + dark + " is not darker than Light (" + values + ")");
                }
            }
        });
    }

    private static ThemeService.Theme theme(ThemeService themes, String name) {
        if (!name.equals("High contrast")) return themes.preset(name);
        ThemeService.Theme theme = themes.preset("Default Dark");
        theme.highContrast = true;
        return theme;
    }

    /** Visible widgets whose bounds reach past the scaled window, as "name x,y wxh" entries. */
    private static String widgetsOutside(Minecraft client) {
        Screen screen = client.gui.screen();
        if (screen == null) return "no screen open";
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        List<String> outside = new ArrayList<>();
        for (var child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > width || widget.getBottom() > height) {
                outside.add(widget.getMessage().getString() + " " + widget.getX() + "," + widget.getY()
                        + " " + widget.getWidth() + "x" + widget.getHeight());
            }
        }
        return String.join("; ", outside);
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    // ---------------------------------------------------------------- ESP and nametag geometry

    /**
     * One invisible stand six blocks straight ahead. The box ESP draws must sit where the stand's
     * bounding box projects through the camera, which is worked out here from the camera's own
     * position and field of view rather than from anything the overlay computed.
     */
    private static void espGeometry(ClientGameTestContext context, TestSingleplayerContext world, List<String> failures) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            for (Entity entity : snapshot(level)) if (!(entity instanceof ServerPlayer)) entity.discard();
            Entity stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            if (stand == null) throw new AssertionError("could not create an armor stand");
            stand.setPos(player.getX(), player.getY(), player.getZ() + 6);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            level.addFreshEntity(stand);
        });
        boolean[] bob = new boolean[1];
        context.runOnClient(client -> {
            bob[0] = client.options.bobView().get();
            client.options.bobView().set(false);
            hideHud(client, true);
            face(client, 0.0f, 0.0f);
            configure("ESP", module -> {
                module.settings.setSetting("respectTargetPolicy", false);
                module.settings.setSetting("mobs", true);
                module.settings.setSetting("labels", false);
                module.settings.setSetting("tracers", false);
                module.settings.setSetting("boxes", true);
                module.settings.setSetting("distanceFade", false);
            });
            configure("Nametags", module -> module.settings.setSetting("mobs", true));
        });
        try {
            context.waitTicks(20);
            Path off = context.takeScreenshot("visual-esp-off");
            context.waitTicks(10);
            Path stillOff = context.takeScreenshot("visual-esp-off-2");
            int[] noise = Frames.changedBounds(off, stillOff);
            if (noise != null && noise[4] > 50) {
                failures.add("ESP geometry: the scene would not hold still (" + noise[4] + " pixels changed with nothing on)");
                return;
            }
            context.runOnClient(client -> toggle("ESP", true));
            context.waitFor(client -> !((EntityESP) AgalarHackClient.moduleManager.getModule("ESP")).targets().isEmpty(), 100);
            context.waitTicks(5);
            Path box = context.takeScreenshot("visual-esp-box");
            double[] expected = context.computeOnClient(VisualAcceptanceGameTest::projectedStand);
            int[] drawn = Frames.changedBounds(stillOff, box);
            double tolerance = Math.max(4, expected[4] * 0.006);
            LOGGER.info("    ESP box: expected {} drawn {}", rect(expected), drawn == null ? "nothing" : rect(drawn));
            if (drawn == null) {
                failures.add("ESP geometry: ESP drew nothing around a target straight ahead");
            } else if (Math.abs(drawn[0] - expected[0]) > tolerance || Math.abs(drawn[1] - expected[1]) > tolerance
                    || Math.abs(drawn[2] - expected[2]) > tolerance || Math.abs(drawn[3] - expected[3]) > tolerance) {
                failures.add("ESP geometry: the box was drawn at " + rect(drawn) + ", the target's bounding box projects to "
                        + rect(expected) + " (tolerance " + Math.round(tolerance) + " px)");
            }

            context.runOnClient(client -> configure("ESP", module -> module.settings.setSetting("labels", true)));
            context.waitTicks(5);
            Path labelled = context.takeScreenshot("visual-esp-label");
            labelAbove("ESP label", Frames.changedBounds(box, labelled), expected, tolerance, failures);

            context.runOnClient(client -> {
                toggle("ESP", false);
                toggle("Nametags", true);
            });
            context.waitFor(client -> !((Nametags) AgalarHackClient.moduleManager.getModule("Nametags")).targets().isEmpty(), 100);
            context.waitTicks(5);
            Path nametag = context.takeScreenshot("visual-nametag");
            labelAbove("Nametag", Frames.changedBounds(stillOff, nametag), expected, tolerance, failures);
        } finally {
            context.runOnClient(client -> {
                toggle("ESP", false);
                toggle("Nametags", false);
                configure("ESP", module -> {
                    module.settings.setSetting("respectTargetPolicy", true);
                    module.settings.setSetting("labels", true);
                    module.settings.setSetting("distanceFade", true);
                });
                client.options.bobView().set(bob[0]);
                hideHud(client, false);
            });
        }
    }

    /** A label must sit above the box and be centred on it. */
    private static void labelAbove(String what, int[] drawn, double[] box, double tolerance, List<String> failures) {
        LOGGER.info("    {}: drawn {}", what, drawn == null ? "nothing" : rect(drawn));
        if (drawn == null) {
            failures.add(what + ": nothing was drawn");
            return;
        }
        double centre = (drawn[0] + drawn[2]) / 2.0, expectedCentre = (box[0] + box[2]) / 2.0;
        if (drawn[3] > box[1] + tolerance) {
            failures.add(what + " reaches down to y=" + drawn[3] + ", into the box that starts at y=" + Math.round(box[1]));
        }
        if (Math.abs(centre - expectedCentre) > tolerance) {
            failures.add(what + " is centred at x=" + Math.round(centre) + ", the target at x=" + Math.round(expectedCentre));
        }
    }

    /**
     * The stand's bounding box, grown by the 0.03 ESP adds, projected through the camera:
     * {minX, minY, maxX, maxY, frame width} in framebuffer pixels.
     */
    private static double[] projectedStand(Minecraft client) {
        LivingEntity stand = ((EntityESP) AgalarHackClient.moduleManager.getModule("ESP")).targets().get(0);
        AABB box = stand.getBoundingBox().inflate(0.03);
        var camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        double width = client.getWindow().getWidth(), height = client.getWindow().getHeight();
        double tanV = Math.tan(Math.toRadians(camera.getFov()) / 2), tanH = tanV * width / height;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            double x = ((corner & 1) == 0 ? box.minX : box.maxX) - eye.x;
            double y = ((corner & 2) == 0 ? box.minY : box.maxY) - eye.y;
            double z = ((corner & 4) == 0 ? box.minZ : box.maxZ) - eye.z;
            // Facing +z (yaw 0), screen right is world -x and screen up is world +y.
            double sx = width / 2 + (-x / z) / tanH * width / 2;
            double sy = height / 2 - (y / z) / tanV * height / 2;
            minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
        }
        return new double[] { minX, minY, maxX, maxY, width };
    }

    // ---------------------------------------------------------------- TargetHUD face

    /**
     * A second player as the target. The card reports where it drew the face; that patch must hold
     * a skin, which a flat card background or a missing texture would not.
     */
    private static void targetHudFace(ClientGameTestContext context, TestSingleplayerContext world, List<String> failures) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = world.getConnection().getServerPlayer();
            ServerLevel level = player.level();
            var probe = net.fabricmc.fabric.api.entity.FakePlayer.get(level,
                    new com.mojang.authlib.GameProfile(PROBE_ID, "VisualProbe"));
            probe.setPos(player.getX() + 2, player.getY(), player.getZ() + 3);
            server.getPlayerList().broadcastAll(
                    net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(probe)));
            level.addNewPlayer(probe);
        });
        try {
            context.waitFor(client -> client.level.getPlayerByUUID(PROBE_ID) != null, 100);
            context.runOnClient(client -> {
                toggle("TargetHUD", true);
                AgalarHackClient.TARGET_TRACKER.set((LivingEntity) client.level.getPlayerByUUID(PROBE_ID));
            });
            context.waitTicks(5);
            context.runOnClient(client -> AgalarHackClient.TARGET_TRACKER.set((LivingEntity) client.level.getPlayerByUUID(PROBE_ID)));
            context.waitTicks(1);
            Path card = context.takeScreenshot("visual-targethud");
            int[] face = Hud.lastFaceBounds();
            if (face == null) {
                failures.add("TargetHUD: a player target produced no face");
                return;
            }
            double scale = context.computeOnClient(client -> AgalarHackClient.HUD_LAYOUT.scale() * client.getWindow().getGuiScale());
            int left = (int) Math.round(face[0] * scale), top = (int) Math.round(face[1] * scale);
            int size = (int) Math.round(face[2] * scale);
            int colours = Frames.distinctColours(card, left + 1, top + 1, size - 2, size - 2);
            LOGGER.info("    TargetHUD face: {}x{} px at {},{}, {} distinct colours", size, size, left, top, colours);
            if (colours < 4) {
                failures.add("TargetHUD: the face patch at " + left + "," + top + " holds " + colours
                        + " colours; a skin has at least four");
            }
        } finally {
            context.runOnClient(client -> {
                toggle("TargetHUD", false);
                AgalarHackClient.TARGET_TRACKER.clear();
            });
            world.getServer().runOnServer(server -> {
                ServerLevel level = world.getConnection().getServerPlayer().level();
                if (level.getPlayerByUUID(PROBE_ID) instanceof ServerPlayer probe) {
                    level.removePlayerImmediately(probe, Entity.RemovalReason.DISCARDED);
                }
                server.getPlayerList().broadcastAll(
                        new net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket(List.of(PROBE_ID)));
            });
        }
    }

    // ---------------------------------------------------------------- helpers

    /** F1: the vanilla HUD, the hand and the crosshair, none of which belong in a geometry frame. */
    private static void hideHud(Minecraft client, boolean hidden) {
        if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
    }

    private static void face(Minecraft client, float yaw, float pitch) {
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
        client.player.yRotO = yaw;
        client.player.xRotO = pitch;
    }

    private static void configure(String name, java.util.function.Consumer<Module> change) {
        change.accept(AgalarHackClient.moduleManager.getModule(name));
    }

    private static void toggle(String name, boolean on) {
        AgalarHackClient.moduleManager.getModule(name).setToggled(on, false);
    }

    private static List<Entity> snapshot(ServerLevel level) {
        List<Entity> present = new ArrayList<>();
        level.getAllEntities().forEach(present::add);
        return present;
    }

    private static String rect(int[] r) {
        return r[0] + "," + r[1] + " .. " + r[2] + "," + r[3];
    }

    private static String rect(double[] r) {
        return Math.round(r[0]) + "," + Math.round(r[1]) + " .. " + Math.round(r[2]) + "," + Math.round(r[3]);
    }
}
