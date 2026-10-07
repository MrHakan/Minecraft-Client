package me.mrhakan.agalarhack.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FriendManagerTest {
    @TempDir Path config;

    private FriendManager manager() {
        return new FriendManager(config.resolve("agalarhack-friends.json"));
    }

    @Test
    void lookupIgnoresCaseAndSurroundingSpace() {
        var friends = manager();
        assertTrue(friends.add("Notch"));

        assertTrue(friends.isFriend("Notch"));
        assertTrue(friends.isFriend("notch"));
        assertTrue(friends.isFriend("NOTCH"));
        assertTrue(friends.isFriend("  Notch "));
        assertFalse(friends.isFriend("Notc"));
        assertFalse(friends.isFriend(null));
        assertFalse(friends.isFriend(""));
    }

    @Test
    void aNameThatDiffersOnlyInCaseIsADuplicate() {
        var friends = manager();
        assertTrue(friends.add("Notch"));
        assertFalse(friends.add("NOTCH"));
        assertEquals(List.of("Notch"), friends.getFriends());
    }

    @Test
    void removalMatchesCaseInsensitivelyAndForgetsTheName() {
        var friends = manager();
        friends.add("Notch");
        friends.add("jeb_");

        assertTrue(friends.remove("NOTCH"));
        assertFalse(friends.isFriend("Notch"));
        assertFalse(friends.remove("Notch"));
        assertFalse(friends.remove(null));
        assertEquals(List.of("jeb_"), friends.getFriends());
    }

    @Test
    void namesKeepTheSpellingAndOrderTheyWereAddedIn() {
        var friends = manager();
        friends.add("  Zed ");
        friends.add("alex");
        friends.add("Bob");

        assertEquals(List.of("Zed", "alex", "Bob"), friends.getFriends());
    }

    @Test
    void invalidNamesAreRejected() {
        var friends = manager();
        assertFalse(friends.add(null));
        assertFalse(friends.add("   "));
        assertFalse(friends.add("x".repeat(65)));
        assertTrue(friends.getFriends().isEmpty());
    }

    @Test
    void theFileRoundTripsAndKeepsTheFirstOfAnyCaseDuplicate() throws IOException {
        Path file = config.resolve("agalarhack-friends.json");
        Files.writeString(file, "[\"Steve\", \"STEVE\", \"Alex\", \"\", null]", StandardCharsets.UTF_8);

        var loaded = manager();
        loaded.load();
        assertEquals(List.of("Steve", "Alex"), loaded.getFriends());
        assertTrue(loaded.isFriend("steve"));

        loaded.add("Herobrine");
        var reloaded = manager();
        reloaded.load();
        assertEquals(List.of("Steve", "Alex", "Herobrine"), reloaded.getFriends());
    }

    @Test
    void clearForgetsEverythingIncludingLookups() {
        var friends = manager();
        friends.add("Notch");
        friends.add("jeb_");

        assertEquals(2, friends.clear());
        assertFalse(friends.isFriend("notch"));
        assertEquals(0, friends.clear());
    }
}
