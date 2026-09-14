package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TranslationsTest {
    @Test void keysAreNamespacedOnce() {
        assertEquals("agalarhack.gui.search", Translations.keyOf("gui.search"));
        assertEquals("agalarhack.gui.search", Translations.keyOf("agalarhack.gui.search"));
    }

    @Test void keysAreLowerCasedAndTrimmed() {
        assertEquals("agalarhack.gui.search", Translations.keyOf("  GUI.Search  "));
    }

    @Test void blankKeysAreRejectedRatherThanProducingABareNamespace() {
        assertThrows(IllegalArgumentException.class, () -> Translations.keyOf(null));
        assertThrows(IllegalArgumentException.class, () -> Translations.keyOf(""));
        assertThrows(IllegalArgumentException.class, () -> Translations.keyOf("   "));
    }
}
