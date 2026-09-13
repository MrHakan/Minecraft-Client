package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.services.NotificationService.Type;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationSoundsTest {
    @Test void allSeveritiesAreAudibleByDefault() {
        for (Type type : Type.values()) assertTrue(NotificationSounds.audible(type, "all"), type.name());
    }

    @Test void warningsFilterKeepsWarningsAndErrorsOnly() {
        assertTrue(NotificationSounds.audible(Type.ERROR, "warnings"));
        assertTrue(NotificationSounds.audible(Type.WARNING, "warnings"));
        assertFalse(NotificationSounds.audible(Type.SUCCESS, "warnings"));
        assertFalse(NotificationSounds.audible(Type.INFO, "warnings"));
    }

    @Test void errorsFilterKeepsOnlyErrors() {
        assertTrue(NotificationSounds.audible(Type.ERROR, "errors"));
        assertFalse(NotificationSounds.audible(Type.WARNING, "errors"));
        assertFalse(NotificationSounds.audible(Type.INFO, "errors"));
    }

    @Test void unknownOrMissingFilterFallsBackToAudible() {
        assertTrue(NotificationSounds.audible(Type.INFO, null));
        assertTrue(NotificationSounds.audible(Type.INFO, "nonsense"));
        assertTrue(NotificationSounds.audible(Type.INFO, "ALL"));
    }

    @Test void severityPitchesAreDistinctAndOrdered() {
        assertTrue(NotificationSounds.pitchFor(Type.ERROR) < NotificationSounds.pitchFor(Type.WARNING));
        assertTrue(NotificationSounds.pitchFor(Type.WARNING) < NotificationSounds.pitchFor(Type.INFO));
        assertTrue(NotificationSounds.pitchFor(Type.INFO) < NotificationSounds.pitchFor(Type.SUCCESS));
    }
}
