package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepeatSuppressorTest {

    @Test
    void theFirstTimeIsNeverARepeat() {
        assertFalse(new RepeatSuppressor().isRepeat("hello", 4));
    }

    @Test
    void anImmediateRepeatIsHidden() {
        var suppressor = new RepeatSuppressor();
        assertFalse(suppressor.isRepeat("buying diamonds", 4));
        assertTrue(suppressor.isRepeat("buying diamonds", 4));
        assertTrue(suppressor.isRepeat("buying diamonds", 4));
    }

    @Test
    void aWindowCatchesAlternatingSpammers() {
        var suppressor = new RepeatSuppressor();
        // A window of one is defeated by two people taking turns, which is the real complaint.
        suppressor.isRepeat("shop one", 4);
        suppressor.isRepeat("shop two", 4);
        assertTrue(suppressor.isRepeat("shop one", 4));
        assertTrue(suppressor.isRepeat("shop two", 4));
    }

    @Test
    void aLineFallsOutOfTheWindowEventually() {
        var suppressor = new RepeatSuppressor();
        suppressor.isRepeat("first", 2);
        suppressor.isRepeat("second", 2);
        suppressor.isRepeat("third", 2);
        assertFalse(suppressor.isRepeat("first", 2), "it is no longer recent");
    }

    @Test
    void caseAndPaddingDoNotMakeALineNew() {
        var suppressor = new RepeatSuppressor();
        suppressor.isRepeat("Buying Diamonds", 4);
        assertTrue(suppressor.isRepeat("  buying   diamonds ", 4));
        assertTrue(suppressor.isRepeat("BUYING DIAMONDS", 4));
    }

    @Test
    void oneDifferentCharacterIsADifferentMessage() {
        var suppressor = new RepeatSuppressor();
        suppressor.isRepeat("buying diamonds", 4);
        assertFalse(suppressor.isRepeat("buying diamonds!", 4),
                "guessing beyond case and padding starts hiding real chat");
    }

    @Test
    void blankLinesAreNeverSuppressed() {
        var suppressor = new RepeatSuppressor();
        assertFalse(suppressor.isRepeat("", 4));
        assertFalse(suppressor.isRepeat("   ", 4));
        assertFalse(suppressor.isRepeat(null, 4));
        assertEquals(0, suppressor.remembered());
    }

    @Test
    void shrinkingTheWindowDropsTheOldestImmediately() {
        var suppressor = new RepeatSuppressor();
        suppressor.isRepeat("a", 4);
        suppressor.isRepeat("b", 4);
        suppressor.isRepeat("c", 4);
        assertFalse(suppressor.isRepeat("a", 1), "only the most recent line is compared now");
    }

    @Test
    void theWindowIsClampedAtBothEnds() {
        var suppressor = new RepeatSuppressor();
        suppressor.isRepeat("a", 0);
        assertTrue(suppressor.isRepeat("a", 0), "a window of zero must still be a window of one");

        var wide = new RepeatSuppressor();
        for (int index = 0; index < RepeatSuppressor.MAX_WINDOW * 3; index++) {
            wide.isRepeat("line" + index, Integer.MAX_VALUE);
        }
        assertTrue(wide.remembered() <= RepeatSuppressor.MAX_WINDOW, "was " + wide.remembered());
    }

    @Test
    void resetStartsANewConversation() {
        var suppressor = new RepeatSuppressor();
        suppressor.isRepeat("hello", 4);
        suppressor.reset();
        assertFalse(suppressor.isRepeat("hello", 4), "a new server's chat must not be hidden by the last one");
    }
}
