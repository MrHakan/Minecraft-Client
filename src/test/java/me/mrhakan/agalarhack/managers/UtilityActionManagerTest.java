package me.mrhakan.agalarhack.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UtilityActionManagerTest {
    @org.junit.jupiter.api.Test
    void dualClaimDoesNotPartiallyAcquire() {
        UtilityActionManager actions = new UtilityActionManager();
        actions.claimUse("high", 100);
        org.junit.jupiter.api.Assertions.assertFalse(actions.claimHotbarAndUse("low", 50));
        org.junit.jupiter.api.Assertions.assertFalse(actions.ownsHotbar("low"));
        org.junit.jupiter.api.Assertions.assertTrue(actions.claimHotbarAndUse("higher", 101));
    }

    @Test
    void higherPriorityOwnerWinsChannel() {
        UtilityActionManager manager = new UtilityActionManager();
        manager.beginTick();

        assertTrue(manager.claimHotbar("tool", 40));
        assertTrue(manager.claimHotbar("eat", 60));
        assertFalse(manager.ownsHotbar("tool"));
        assertTrue(manager.ownsHotbar("eat"));
        assertFalse(manager.claimHotbar("tool", 40));
    }

    @Test
    void ownershipResetsAtStartOfEachTick() {
        UtilityActionManager manager = new UtilityActionManager();
        manager.beginTick();
        assertTrue(manager.claimUse("eat", 60));
        assertTrue(manager.ownsUse("eat"));

        manager.beginTick();
        assertFalse(manager.ownsUse("eat"));
        assertTrue(manager.claimUse("other", 10));
    }

    @Test
    void sameOwnerCanReclaimItsChannel() {
        UtilityActionManager manager = new UtilityActionManager();
        manager.beginTick();
        assertTrue(manager.claimHotbar("eat", 60));
        assertTrue(manager.claimHotbar("eat", 60));
        assertTrue(manager.ownsHotbar("eat"));
    }
}
