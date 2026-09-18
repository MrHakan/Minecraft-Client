package me.mrhakan.agalarhack.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequestMatcherTest {
    private static final Set<String> PHRASES = Set.of("has requested", "teleport");
    private static final List<String> FRIENDS = List.of("Alice", "Bob");

    @Test
    void matchesARealRequestFromAListedName() {
        assertEquals("Alice",
                RequestMatcher.match("Alice has requested to teleport to you.", PHRASES, FRIENDS));
    }

    @Test
    void everyPhraseMustBePresentNotJustOne() {
        // One phrase is a trap: "teleport" alone shows up in ordinary chat.
        assertNull(RequestMatcher.match("Alice said teleport is broken", PHRASES, FRIENDS));
        assertNull(RequestMatcher.match("Alice has requested a refund", PHRASES, FRIENDS));
    }

    @Test
    void aStrangerCannotTriggerItAtAll() {
        assertNull(RequestMatcher.match("Mallory has requested to teleport to you.", PHRASES, FRIENDS));
    }

    @Test
    void aStrangerCannotImpersonateAListedNameBySubstring() {
        // "Alicen" is a different player; whole-word matching is what keeps them apart.
        assertNull(RequestMatcher.match("Alicen has requested to teleport to you.", PHRASES, FRIENDS));
    }

    @Test
    void emptyConfigurationNeverMatches() {
        assertNull(RequestMatcher.match("Alice has requested to teleport to you.", Set.of(), FRIENDS));
        assertNull(RequestMatcher.match("Alice has requested to teleport to you.", PHRASES, List.of()));
        assertNull(RequestMatcher.match("Alice has requested to teleport to you.", null, null));
    }

    @Test
    void phraseMatchingIsCaseInsensitive() {
        assertEquals("Alice",
                RequestMatcher.match("ALICE HAS REQUESTED TO TELEPORT TO YOU", PHRASES, FRIENDS));
    }

    @Test
    void blankMessagesMatchNothing() {
        assertNull(RequestMatcher.match("", PHRASES, FRIENDS));
        assertNull(RequestMatcher.match(null, PHRASES, FRIENDS));
    }

    @Test
    void replyTemplateSubstitutesTheName() {
        assertEquals("/tpaccept Alice", RequestMatcher.fillReply("/tpaccept {name}", "Alice"));
        assertEquals("/tpaccept", RequestMatcher.fillReply("/tpaccept", "Alice"));
        assertEquals("/tpaccept", RequestMatcher.fillReply("/tpaccept {name}", null));
    }

    @Test
    void onlyCommandsAreSendable() {
        assertTrue(RequestMatcher.isSendableReply("/tpaccept"));
        assertFalse(RequestMatcher.isSendableReply("tpaccept"),
                "plain text would be broadcast to the server every time the phrase appears");
        assertFalse(RequestMatcher.isSendableReply("/"));
        assertFalse(RequestMatcher.isSendableReply(""));
        assertFalse(RequestMatcher.isSendableReply(null));
    }

    @Test
    void aCraftedNameCannotTurnOneReplyIntoSeveral() {
        String reply = RequestMatcher.fillReply("/tpaccept {name}", "Alice\n/say owned");
        assertFalse(RequestMatcher.isSendableReply(reply));
        assertFalse(RequestMatcher.isSendableReply("/tpaccept\rmore"));
    }

    @Test
    void anAbsurdlyLongReplyIsRefused() {
        assertFalse(RequestMatcher.isSendableReply("/" + "x".repeat(300)));
    }
}
