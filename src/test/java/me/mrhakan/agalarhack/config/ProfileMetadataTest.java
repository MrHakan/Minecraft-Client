package me.mrhakan.agalarhack.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileMetadataTest {

    @Test
    void descriptionIsTrimmedAndCollapsed() {
        assertEquals("for anarchy servers",
                ProfileMetadata.normaliseDescription("  for   anarchy\n servers  "));
        assertEquals("", ProfileMetadata.normaliseDescription(null));
        assertEquals("", ProfileMetadata.normaliseDescription("   "));
    }

    @Test
    void descriptionIsBoundedBecauseItEndsUpInAChatLine() {
        String long_ = "x".repeat(ProfileMetadata.MAX_DESCRIPTION * 3);
        assertEquals(ProfileMetadata.MAX_DESCRIPTION, ProfileMetadata.normaliseDescription(long_).length());
    }

    @Test
    void tagsAreLowerCasedAndDeduplicated() {
        assertEquals(List.of("pvp", "anarchy"), ProfileMetadata.parseTags("PvP, anarchy, PVP"));
    }

    @Test
    void tagsAcceptCommasOrSpaces() {
        assertEquals(List.of("a", "b", "c"), ProfileMetadata.parseTags("a,b c"));
        assertEquals(List.of("a", "b"), ProfileMetadata.parseTags("  a ,, b  "));
    }

    @Test
    void tagsAreBoundedInCountAndLength() {
        StringBuilder many = new StringBuilder();
        for (int index = 0; index < ProfileMetadata.MAX_TAGS * 3; index++) many.append("tag").append(index).append(' ');
        assertEquals(ProfileMetadata.MAX_TAGS, ProfileMetadata.parseTags(many.toString()).size());
        assertEquals(ProfileMetadata.MAX_TAG_LENGTH,
                ProfileMetadata.parseTags("z".repeat(ProfileMetadata.MAX_TAG_LENGTH * 2)).get(0).length());
    }

    @Test
    void emptyTagsAreNotAList() {
        assertEquals(List.of(), ProfileMetadata.parseTags(null));
        assertEquals(List.of(), ProfileMetadata.parseTags("  "));
    }

    @Test
    void searchMatchesTheNameEvenWithoutMetadata() {
        assertTrue(ProfileMetadata.matches("anarchy", null, "narc"));
        assertTrue(ProfileMetadata.matches("Anarchy", null, "ANARCH"));
        assertFalse(ProfileMetadata.matches("anarchy", null, "pvp"));
    }

    @Test
    void searchMatchesDescriptionAndTags() {
        ProfileMetadata metadata = ProfileMetadata.of("for crystal fights", "pvp, endcrystal", 0);
        assertTrue(ProfileMetadata.matches("p2", metadata, "crystal"));
        assertTrue(ProfileMetadata.matches("p2", metadata, "PVP"));
        assertFalse(ProfileMetadata.matches("p2", metadata, "mining"));
    }

    @Test
    void anEmptyQueryListsEverythingRatherThanNothing() {
        assertTrue(ProfileMetadata.matches("anything", null, ""));
        assertTrue(ProfileMetadata.matches("anything", null, null));
        assertTrue(ProfileMetadata.matches("anything", null, "   "));
    }

    @Test
    void summaryPrefersTheDescriptionThenFallsBackToTags() {
        assertEquals("for crystal fights",
                ProfileMetadata.summary(ProfileMetadata.of("for crystal fights", "pvp", 0)));
        assertEquals("pvp, anarchy", ProfileMetadata.summary(ProfileMetadata.of("", "pvp anarchy", 0)));
        assertEquals("", ProfileMetadata.summary(ProfileMetadata.of("", "", 0)));
        assertEquals("", ProfileMetadata.summary(null));
    }

    @Test
    void accessorsNeverReturnNullEvenStraightFromGson() {
        // Gson builds the object without the constructor, so the fields can genuinely be null.
        ProfileMetadata raw = new ProfileMetadata();
        assertEquals("", raw.description());
        assertEquals(List.of(), raw.tags());
        assertEquals("", ProfileMetadata.summary(raw));
        assertFalse(ProfileMetadata.matches("name", raw, "anything"));
    }

    @Test
    void aHandEditedFileIsBroughtBackInsideTheBounds() {
        ProfileMetadata raw = new ProfileMetadata();
        raw.description = "  too    spaced  ";
        raw.tags = new java.util.ArrayList<>(List.of("PVP", "pvp", "  ", "z".repeat(99)));
        raw.createdAt = -5;
        raw.updatedAt = -1;
        ProfileMetadata clean = raw.sanitised();
        assertEquals("too spaced", clean.description());
        assertEquals(List.of("pvp", "z".repeat(ProfileMetadata.MAX_TAG_LENGTH)), clean.tags());
        assertEquals(0, clean.createdAt);
        assertEquals(0, clean.updatedAt);
    }

    @Test
    void sanitisingNullFieldsDoesNotThrow() {
        assertEquals("", new ProfileMetadata().sanitised().description());
        assertEquals(List.of(), new ProfileMetadata().sanitised().tags());
    }
}
