package me.mrhakan.agalarhack.services.projectile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Checks the simulator's constants against Minecraft 26.2's own.
 *
 * <p>A trajectory overlay that is subtly wrong is worse than none: it looks authoritative and lands
 * the arrow somewhere else, and nothing about it says it is stale. Gravity and drag are the values
 * most likely to drift silently across a Minecraft version, because they are one-line accessors
 * nobody thinks to check.
 *
 * <p>Read from the game's own bytecode rather than restated here. Each of these methods is a single
 * constant push followed by a return, so the constant is unambiguous — and if Mojang ever turns one
 * into a computation, this fails loudly rather than reading the wrong number out of it.
 *
 * <p>Launch speeds are not checked this way, because they sit inside long methods alongside other
 * constants and any extraction would be guesswork. They were verified by reading the 26.2 bytecode
 * directly: {@code BowItem} 3.0 times charge, {@code CrossbowItem} 3.15 for arrows, {@code
 * TridentItem} 2.5, {@code SnowballItem}/{@code EggItem}/{@code EnderpearlItem} 1.5, {@code
 * ThrowablePotionItem} 0.5 at -20 degrees, {@code ExperienceBottleItem} 0.7 at -20 degrees.
 */
class ProjectilePhysicsAuditTest {
    private static final String ARROW = "net/minecraft/world/entity/projectile/arrow/AbstractArrow";
    private static final String THROWABLE = "net/minecraft/world/entity/projectile/ThrowableProjectile";
    private static final String POTION =
            "net/minecraft/world/entity/projectile/throwableitemprojectile/AbstractThrownPotion";
    private static final String XP_BOTTLE =
            "net/minecraft/world/entity/projectile/throwableitemprojectile/ThrownExperienceBottle";

    /**
     * @return the single constant the method pushes, or null when it is not shaped that way
     */
    private static Double soleConstant(String internalName, String method, String descriptor) throws IOException {
        try (InputStream stream = ProjectilePhysicsAuditTest.class.getResourceAsStream("/" + internalName + ".class")) {
            assertNotNull(stream, "not on the test classpath: " + internalName);
            List<Object> constants = new ArrayList<>();
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc,
                                                 String signature, String[] exceptions) {
                    if (!name.equals(method) || !desc.equals(descriptor)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitLdcInsn(Object value) { constants.add(value); }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
            if (constants.size() != 1 || !(constants.get(0) instanceof Number number)) return null;
            return number.doubleValue();
        }
    }

    @Test
    void arrowGravityAndDragMatchTheGame() throws IOException {
        Double gravity = soleConstant(ARROW, "getDefaultGravity", "()D");
        Double drag = soleConstant(ARROW, "getAirDrag", "()F");
        assertNotNull(gravity, "AbstractArrow.getDefaultGravity is no longer a single constant");
        assertNotNull(drag, "AbstractArrow.getAirDrag is no longer a single constant");

        var bow = ProjectilePhysics.of(ProjectilePhysics.Family.BOW, 3.0);
        assertEquals(gravity, bow.gravity(), 1e-9);
        assertEquals(drag, bow.drag(), 1e-6);
        // Crossbow and trident are arrows too, so they must not have drifted apart from it.
        assertEquals(gravity, ProjectilePhysics.of(ProjectilePhysics.Family.CROSSBOW, 0).gravity(), 1e-9);
        assertEquals(gravity, ProjectilePhysics.of(ProjectilePhysics.Family.TRIDENT, 0).gravity(), 1e-9);
    }

    @Test
    void thrownGravityAndDragMatchTheGame() throws IOException {
        Double gravity = soleConstant(THROWABLE, "getDefaultGravity", "()D");
        Double drag = soleConstant(THROWABLE, "getAirDrag", "()F");
        assertNotNull(gravity, "ThrowableProjectile.getDefaultGravity is no longer a single constant");
        assertNotNull(drag, "ThrowableProjectile.getAirDrag is no longer a single constant");

        var thrown = ProjectilePhysics.of(ProjectilePhysics.Family.THROWN, 0);
        assertEquals(gravity, thrown.gravity(), 1e-9);
        assertEquals(drag, thrown.drag(), 1e-6);
    }

    @Test
    void potionGravityMatchesTheGame() throws IOException {
        Double gravity = soleConstant(POTION, "getDefaultGravity", "()D");
        assertNotNull(gravity, "AbstractThrownPotion.getDefaultGravity is no longer a single constant");
        assertEquals(gravity, ProjectilePhysics.of(ProjectilePhysics.Family.SPLASH_POTION, 0).gravity(), 1e-9);
        assertEquals(gravity, ProjectilePhysics.of(ProjectilePhysics.Family.LINGERING_POTION, 0).gravity(), 1e-9);
    }

    @Test
    void experienceBottleGravityMatchesTheGame() throws IOException {
        Double gravity = soleConstant(XP_BOTTLE, "getDefaultGravity", "()D");
        assertNotNull(gravity, "ThrownExperienceBottle.getDefaultGravity is no longer a single constant");
        assertEquals(gravity, ProjectilePhysics.of(ProjectilePhysics.Family.XP_BOTTLE, 0).gravity(), 1e-9);
    }

    @Test
    void thePotionFamiliesAreThrownDownwardFromTheCrosshair() {
        // ThrowablePotionItem and ExperienceBottleItem both launch at -20 degrees of pitch. Missing
        // that offset puts the whole arc above where the potion actually goes.
        assertEquals(-20.0, ProjectilePhysics.of(ProjectilePhysics.Family.SPLASH_POTION, 0).pitchOffset(), 1e-9);
        assertEquals(-20.0, ProjectilePhysics.of(ProjectilePhysics.Family.XP_BOTTLE, 0).pitchOffset(), 1e-9);
        assertEquals(0.0, ProjectilePhysics.of(ProjectilePhysics.Family.BOW, 3.0).pitchOffset(), 1e-9);
    }

    @Test
    void arrowsAndThrowablesApplyGravityInOppositeOrders() {
        // Getting this backwards produces a path that looks plausible and lands somewhere else.
        assertTrue(ProjectilePhysics.of(ProjectilePhysics.Family.THROWN, 0).gravityBeforeDrag());
        assertTrue(!ProjectilePhysics.of(ProjectilePhysics.Family.BOW, 3.0).gravityBeforeDrag());
    }

    @Test
    void everyFamilyIsCoveredSoANewOneCannotSlipThroughUnaudited() {
        for (ProjectilePhysics.Family family : ProjectilePhysics.Family.values()) {
            var physics = ProjectilePhysics.of(family, 1.0);
            assertTrue(physics.gravity() > 0, family + " has no gravity");
            assertTrue(physics.drag() > 0 && physics.drag() <= 1.0, family + " has an implausible drag");
        }
    }
}
