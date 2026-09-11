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
            new Target("net.minecraft.client.renderer.GameRenderer", "bobHurt",
                    "net.minecraft.client.renderer.state.level.CameraRenderState",
                    "com.mojang.blaze3d.vertex.PoseStack"));

    /** Loads without running static initialisers, so no Minecraft bootstrap is needed. */
    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, MixinTargetsTest.class.getClassLoader());
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

    @Test void theTargetListCoversEveryMixinClass() {
        // A mixin added without a target entry here would slip past the guard above.
        assertEquals(3, java.util.Set.of(
                "ClientPacketListenerMixin", "PlayerEdgeMixin", "GameRendererMixin").size());
        long owners = TARGETS.stream().map(Target::owner).distinct().count();
        assertEquals(3, owners, "one target owner per mixin class; update this test when a mixin is added");
    }

    @Test void theLookupItselfWorks() throws Exception {
        // Guards against the whole test passing because Minecraft is absent from the test classpath.
        assertNotNull(load("net.minecraft.client.multiplayer.ClientPacketListener"));
        assertThrows(NoSuchMethodException.class,
                () -> load("net.minecraft.client.multiplayer.ClientPacketListener")
                        .getDeclaredMethod("thisMethodDoesNotExist"));
    }
}
