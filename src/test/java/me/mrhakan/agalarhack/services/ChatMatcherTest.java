package me.mrhakan.agalarhack.services;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMatcherTest {
    @Test void aNameMatchesAsAWholeWordOnly() {
        assertTrue(ChatMatcher.containsWord("hey Sam come here", "Sam"));
        assertFalse(ChatMatcher.containsWord("Samantha said hi", "Sam"));
        assertFalse(ChatMatcher.containsWord("that is flotsam", "Sam"));
    }

    @Test void matchingIsCaseInsensitive() {
        assertTrue(ChatMatcher.containsWord("HEY SAM", "sam"));
        assertTrue(ChatMatcher.containsWord("hey sam", "SAM"));
    }

    @Test void punctuationStillCountsAsABoundary() {
        assertTrue(ChatMatcher.containsWord("sam, come here", "sam"));
        assertTrue(ChatMatcher.containsWord("<sam> hello", "sam"));
        assertTrue(ChatMatcher.containsWord("@sam!", "sam"));
        assertTrue(ChatMatcher.containsWord("sam", "sam"));
    }

    @Test void underscoresAndDigitsArePartOfANameNotABoundary() {
        assertFalse(ChatMatcher.containsWord("sam_2 is here", "sam"));
        assertFalse(ChatMatcher.containsWord("sam2 is here", "sam"));
        assertTrue(ChatMatcher.containsWord("sam_2 is here", "sam_2"));
    }

    @Test void aLaterOccurrenceStillMatchesAfterAFailedOne() {
        // The first "sam" is inside a word; the scan must keep looking rather than give up.
        assertTrue(ChatMatcher.containsWord("samantha told sam", "sam"));
    }

    @Test void emptyAndNullInputsNeverMatch() {
        assertFalse(ChatMatcher.containsWord(null, "sam"));
        assertFalse(ChatMatcher.containsWord("hello", null));
        assertFalse(ChatMatcher.containsWord("hello", ""));
        assertFalse(ChatMatcher.containsAny("hello", null));
        assertFalse(ChatMatcher.containsAny(null, Set.of("sam")));
    }

    @Test void containsAnyMatchesOnTheFirstTermThatFits() {
        var terms = Set.of("base", "raid");
        assertTrue(ChatMatcher.containsAny("incoming raid!", terms));
        assertFalse(ChatMatcher.containsAny("nothing here", terms));
    }

    @Test void filteringUsesSubstringsBecauseItTargetsPhrases() {
        var patterns = ChatMatcher.parse("buy now, discord.gg");
        assertTrue(ChatMatcher.filtered("join discord.gg/spam", patterns));
        assertTrue(ChatMatcher.filtered("BUY NOW cheap ranks", patterns));
        assertFalse(ChatMatcher.filtered("hello there", patterns));
        assertFalse(ChatMatcher.filtered("anything", Set.of()));
        assertFalse(ChatMatcher.filtered(null, patterns));
    }

    @Test void parsingTrimsLowerCasesAndDeduplicates() {
        var terms = ChatMatcher.parse(" Base , BASE\nraid ");
        assertEquals(java.util.List.of("base", "raid"), java.util.List.copyOf(terms));
    }

    @Test void parsingIsBoundedInCountAndLength() {
        StringBuilder raw = new StringBuilder();
        for (int i = 0; i < ChatMatcher.MAX_PATTERNS * 3; i++) raw.append("term").append(i).append(',');
        assertEquals(ChatMatcher.MAX_PATTERNS, ChatMatcher.parse(raw.toString()).size());
        assertTrue(ChatMatcher.parse("x".repeat(ChatMatcher.MAX_PATTERN_LENGTH + 1)).isEmpty());
    }

    @Test void parsingBlankInputGivesAnEmptySet() {
        assertTrue(ChatMatcher.parse(null).isEmpty());
        assertTrue(ChatMatcher.parse("   ").isEmpty());
        assertTrue(ChatMatcher.parse(",,,").isEmpty());
    }
}
