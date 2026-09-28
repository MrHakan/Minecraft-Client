package me.mrhakan.agalarhack.module.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoToolPolicyTest {
    @Test void creativeIsSkippedByDefaultPolicy() {
        assertTrue(AutoToolPolicy.skipForCreative(true, true));
    }

    @Test void settingCanOptCreativeBackIn() {
        assertFalse(AutoToolPolicy.skipForCreative(true, false));
    }

    @Test void survivalIsNeverSkippedByCreativePolicy() {
        assertFalse(AutoToolPolicy.skipForCreative(false, true));
        assertFalse(AutoToolPolicy.skipForCreative(false, false));
    }
}
