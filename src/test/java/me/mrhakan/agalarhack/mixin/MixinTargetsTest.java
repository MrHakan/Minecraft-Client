package me.mrhakan.agalarhack.mixin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts that every method this mod injects into still exists in Minecraft.
 *
 * <p>This is the branch's highest-consequence unverified assumption. {@code agalarhack.mixins.json}
 * sets {@code required: true} with {@code defaultRequire: 1}, so an injection that fails to find its
 * target is not a silent no-op - it stops the client from starting. Nothing in a normal build
 * catches that: the mixin source compiles whether or not the target exists, because the method is
 * named in an annotation string.
 *
 * <p>Classes are loaded with initialization disabled, so no Minecraft bootstrap runs; only the
 * declared methods are inspected. That checks the failure this actually protects against - a method
 * renamed, removed or re-signatured by a Minecraft update - without needing a running game.
 *
 * <p>It does not prove the injection points inside those methods resolve, or that the mod works. It
 * proves the targets exist.
 */
class MixinTargetsTest {
    private record Target(String owner, String method, String... parameters) { }

    /** Kept in step with the @Inject annotations by hand; a mismatch here is the point of the test. */
    private static final List<Target> TARGETS = List.of(
            new Target("net.minecraft.client.multiplayer.ClientPacketListener", "handleBlockUpdate",
                    "net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket"),
            new Target("net.minecraft.client.multiplayer.ClientPacketListener", "handleChunkBlocksUpdate",
                    "net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket"),
            new Target("net.minecraft.client.multiplayer.ClientPacketListener", "handleEntityEvent",
                    "net.minecraft.network.protocol.game.ClientboundEntityEventPacket"),
            new Target("net.minecraft.client.multiplayer.ClientPacketListener", "handleSetTime",
                    "net.minecraft.network.protocol.game.ClientboundSetTimePacket"),
            new Target("net.minecraft.world.entity.player.Player", "isStayingOnGroundSurface"),
            // Where the keyboard is turned into the movement record. Modules run a tick earlier, so
            // this is the only point at which a requested key survives to be read.
            new Target("net.minecraft.client.player.KeyboardInput", "tick"),
            new Target("net.minecraft.client.player.KeyboardInput", "calculateImpulse",
                    "boolean", "boolean"),
            new Target("net.minecraft.client.renderer.GameRenderer", "bobHurt",
                    "net.minecraft.client.renderer.state.level.CameraRenderState",
                    "com.mojang.blaze3d.vertex.PoseStack"),
            new Target("net.minecraft.network.Connection", "channelRead0",
                    "io.netty.channel.ChannelHandlerContext",
                    "net.minecraft.network.protocol.Packet"),
            // The three-argument send, which is where both other overloads end up in 26.2. Counting
            // on a different one would either miss packets or count them twice.
            new Target("net.minecraft.network.Connection", "send",
                    "net.minecraft.network.protocol.Packet",
                    "io.netty.channel.ChannelFutureListener",
                    "boolean"),
            // The private choke point all three public add* methods delegate to.
            new Target("net.minecraft.client.gui.components.ChatComponent", "addMessage",
                    "net.minecraft.network.chat.Component",
                    "net.minecraft.network.chat.MessageSignature",
                    "net.minecraft.client.multiplayer.chat.GuiMessageSource",
                    "net.minecraft.client.multiplayer.chat.GuiMessageTag"));

    /** Loads without running static initialisers, so no Minecraft bootstrap is needed. */
    private static Class<?> load(String name) throws ClassNotFoundException {
        // Primitives have no class to load by name, and one target takes a boolean.
        return switch (name) {
            case "boolean" -> boolean.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            default -> Class.forName(name, false, MixinTargetsTest.class.getClassLoader());
        };
    }

    @Test void everyInjectionTargetExists() {
        List<String> missing = new ArrayList<>();
        for (Target target : TARGETS) {
            try {
                Class<?> owner = load(target.owner());
                Class<?>[] parameters = new Class<?>[target.parameters().length];
                for (int i = 0; i < parameters.length; i++) parameters[i] = load(target.parameters()[i]);
                Method method = owner.getDeclaredMethod(target.method(), parameters);
                assertNotNull(method);
            } catch (ClassNotFoundException | NoSuchMethodException absent) {
                missing.add(target.owner() + "#" + target.method() + " -> " + absent.getClass().getSimpleName()
                        + ": " + absent.getMessage());
            }
        }
        assertTrue(missing.isEmpty(),
                "Mixin targets are missing; the client would fail to start:\n" + String.join("\n", missing));
    }

    @Test void theTargetListCoversEveryMixinClass() throws Exception {
        // A mixin added without a target entry here would slip past the guard above. Read from the
        // config and the sources rather than a hand-kept number, which is the thing that goes stale.
        var config = com.google.gson.JsonParser
                .parseString(java.nio.file.Files.readString(
                        java.nio.file.Path.of("src/main/resources/agalarhack.mixins.json")))
                .getAsJsonObject();
        List<String> declared = new ArrayList<>();
        config.getAsJsonArray("client").forEach(element -> declared.add(element.getAsString()));
        assertFalse(declared.isEmpty(), "the mixin config lists no client mixins");

        var owners = TARGETS.stream().map(Target::owner).collect(java.util.stream.Collectors.toSet());
        List<String> uncovered = new ArrayList<>();
        for (String simple : declared) {
            java.nio.file.Path source =
                    java.nio.file.Path.of("src/main/java/me/mrhakan/agalarhack/mixin", simple + ".java");
            assertTrue(java.nio.file.Files.isRegularFile(source), "listed but missing: " + simple);
            String text = java.nio.file.Files.readString(source);
            var annotation = java.util.regex.Pattern.compile("@Mixin\\(\\s*(\\w+)\\.class").matcher(text);
            assertTrue(annotation.find(), simple + " has no @Mixin target");
            String target = annotation.group(1);
            var imported = java.util.regex.Pattern
                    .compile("import ([\\w.]+\\." + target + ");").matcher(text);
            assertTrue(imported.find(), simple + " does not import " + target);
            if (!owners.contains(imported.group(1))) uncovered.add(simple + " -> " + imported.group(1));
        }
        assertTrue(uncovered.isEmpty(), "these mixins have no entry in TARGETS: " + uncovered);
    }

    @Test void theLookupItselfWorks() throws Exception {
        // Guards against the whole test passing because Minecraft is absent from the test classpath.
        assertNotNull(load("net.minecraft.client.multiplayer.ClientPacketListener"));
        assertThrows(NoSuchMethodException.class,
                () -> load("net.minecraft.client.multiplayer.ClientPacketListener")
                        .getDeclaredMethod("thisMethodDoesNotExist"));
    }
}
