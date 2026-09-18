package me.mrhakan.agalarhack.services;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMatcherHighlightTest {
    private static String rebuilt(List<ChatMatcher.Segment> segments) {
        return segments.stream().map(ChatMatcher.Segment::text).reduce("", String::concat);
    }

    /** Whatever else it does, it must never lose or reorder a character of the message. */
    @Test void theMessageSurvivesUnchanged() {
        String line = "hey Sam, is Sam there? sam.";
        assertEquals(line, rebuilt(ChatMatcher.highlight(line, Set.of("sam"))));
        assertEquals(line, rebuilt(ChatMatcher.highlight(line, Set.of())));
        assertEquals(line, rebuilt(ChatMatcher.highlight(line, Set.of("nothing"))));
    }

    @Test void everyWholeWordOccurrenceIsMarked() {
        var segments = ChatMatcher.highlight("hey Sam, is Sam there?", Set.of("sam"));
        assertEquals(2, segments.stream().filter(ChatMatcher.Segment::matched).count());
        segments.stream().filter(ChatMatcher.Segment::matched)
                .forEach(segment -> assertEquals("Sam", segment.text(), "the original casing is kept"));
    }

    /** The same whole-word rule the mention test uses: "Sam" must not light up inside "flotsam". */
    @Test void aSubstringInsideAWordIsNotMarked() {
        var segments = ChatMatcher.highlight("flotsam and Samantha", Set.of("sam"));
        assertTrue(segments.stream().noneMatch(ChatMatcher.Segment::matched));
    }

    @Test void theLongerOfTwoTermsStartingTogetherWins() {
        var segments = ChatMatcher.highlight("hello sammy", Set.of("sam", "sammy"));
        assertEquals(List.of("sammy"), segments.stream().filter(ChatMatcher.Segment::matched)
                .map(ChatMatcher.Segment::text).toList());
    }

    @Test void aMatchAtEitherEndIsHandled() {
        assertTrue(ChatMatcher.highlight("sam waves", Set.of("sam")).get(0).matched());
        var trailing = ChatMatcher.highlight("waves at sam", Set.of("sam"));
        assertTrue(trailing.get(trailing.size() - 1).matched());
    }

    @Test void adjacentMatchesDoNotSwallowTheTextBetween() {
        var segments = ChatMatcher.highlight("sam sam", Set.of("sam"));
        assertEquals(3, segments.size());
        assertEquals(" ", segments.get(1).text());
        assertFalse(segments.get(1).matched());
    }

    @Test void nothingIsAssumedAboutEmptyInput() {
        assertTrue(ChatMatcher.highlight("", Set.of("sam")).isEmpty());
        assertTrue(ChatMatcher.highlight(null, Set.of("sam")).isEmpty());
        assertEquals(List.of(new ChatMatcher.Segment("hi", false)), ChatMatcher.highlight("hi", null));
    }

    /** An empty term would otherwise match at every position and never advance the cursor. */
    @Test void anEmptyTermCannotLoopForever() {
        var segments = ChatMatcher.highlight("hello", Set.of(""));
        assertEquals("hello", rebuilt(segments));
        assertTrue(segments.stream().noneMatch(ChatMatcher.Segment::matched));
    }

    @Test void containsWordStillAgreesWithTheSegmenter() {
        for (String line : new String[]{"sam", "flotsam", "hey sam!", "Samantha", "", "sam sam"}) {
            boolean any = ChatMatcher.highlight(line, Set.of("sam")).stream()
                    .anyMatch(ChatMatcher.Segment::matched);
            assertEquals(ChatMatcher.containsWord(line, "sam"), any, line);
        }
    }

    /**
     * A character whose lowercase is longer than itself must not break the offsets.
     *
     * <p>Matching ran against a lowercased copy of the message and the result was used to slice the
     * original. Lowercasing a Turkish capital I with a dot above gives two characters, so the copy
     * is longer than the message and every index past it points somewhere else - far enough along
     * and it points off the end. `ChatHooks.decorate` swallows what that throws, so the visible
     * result was not a crash but a chat line that silently lost its timestamp and highlighting,
     * on a client whose own module descriptions are in Turkish.
     */
    @Test void aLetterThatGrowsWhenLowercasedDoesNotBreakTheOffsets() {
        String line = "\u0130 sam";
        var segments = ChatMatcher.highlight(line, Set.of("sam"));
        assertEquals(line, rebuilt(segments), "the message must survive unchanged");
        assertEquals(1, segments.stream().filter(ChatMatcher.Segment::matched).count());
        assertEquals("sam", segments.stream().filter(ChatMatcher.Segment::matched)
                .findFirst().orElseThrow().text());
    }

    /** The same, further in, where the drift is large enough to run off the end of the message. */
    @Test void severalGrowingLettersStillLeaveTheMatchInTheRightPlace() {
        String line = "\u0130\u0130\u0130 ping sam";
        var segments = ChatMatcher.highlight(line, Set.of("sam"));
        assertEquals(line, rebuilt(segments));
        assertEquals("sam", segments.get(segments.size() - 1).text());
        assertTrue(segments.get(segments.size() - 1).matched());
    }

    /** An index handed back must address the message itself, not a transformed copy of it. */
    @Test void theReportedIndexAddressesTheOriginalMessage() {
        String line = "\u0130 sam";
        int at = ChatMatcher.indexOfWord(line, "sam", 0);
        assertTrue(at >= 0, "the word is there");
        assertEquals("sam", line.substring(at, at + 3),
                "the index pointed into a lowercased copy rather than the message");
    }
}
