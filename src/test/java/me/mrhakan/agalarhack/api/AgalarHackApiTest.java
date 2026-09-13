package me.mrhakan.agalarhack.api;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the one thing about the published surface that is easy to get wrong by tidying it.
 *
 * <p>Turning {@link AgalarHackApi#version()} back into a {@code public static final int} looks like
 * a simplification and silently breaks every addon's version check, because javac inlines a
 * constant into the addon's own bytecode: an addon built against version 1 would then compare the
 * literal 1 against the literal 1 and pass no matter which client it was dropped into. Nothing
 * about that fails to compile, so it is checked here instead.
 */
class AgalarHackApiTest {
    @Test void theVersionIsReadableAndPositive() {
        assertTrue(AgalarHackApi.version() >= 1);
    }

    @Test void noPublicConstantExposesTheVersion() {
        for (Field field : AgalarHackApi.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers())) continue;
            assertFalse(field.getType().isPrimitive(),
                    "Public field " + field.getName() + " is a compile-time constant, so addons "
                            + "would inline its value and their version checks would never fire. "
                            + "Expose it through a method instead.");
        }
    }

    @Test void theEntrypointKeyIsTheOneAddonsWriteInTheirJson() {
        assertEquals("agalarhack", AgalarHackApi.ENTRYPOINT);
    }
}
