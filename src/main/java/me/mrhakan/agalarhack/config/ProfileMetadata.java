package me.mrhakan.agalarhack.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A note to yourself about what a profile is for.
 *
 * <p>Profile names are short because they are typed, which means after a few months a list of them
 * says nothing: "pvp2", "anarchy", "test3". A description and a few tags are the difference between a
 * list you can pick from and one you have to load each entry to identify.
 *
 * <p>Stored as an optional field, so a profile written before this existed loads with no metadata
 * rather than failing, and one written now still loads in a client that does not know about it.
 *
 * <p>Kept free of Minecraft and Gson types so the normalising and the matching rules are unit tested
 * directly. Gson needs the mutable fields and the no-argument constructor; everything that reads
 * them goes through the accessors, which never return null.
 */
public final class ProfileMetadata {
    public static final int MAX_DESCRIPTION = 200;
    public static final int MAX_TAGS = 12;
    public static final int MAX_TAG_LENGTH = 24;

    /** Public and mutable because Gson populates them directly; treat them as private otherwise. */
    public String description;
    public List<String> tags;
    public long createdAt;
    public long updatedAt;

    public ProfileMetadata() { }

    public static ProfileMetadata of(String description, String rawTags, long now) {
        ProfileMetadata metadata = new ProfileMetadata();
        metadata.description = normaliseDescription(description);
        metadata.tags = parseTags(rawTags);
        metadata.createdAt = now;
        metadata.updatedAt = now;
        return metadata;
    }

    public String description() {
        return description == null ? "" : description;
    }

    public List<String> tags() {
        return tags == null ? List.of() : List.copyOf(tags);
    }

    /** Trimmed, collapsed and bounded: this is free text and it ends up in a chat line. */
    public static String normaliseDescription(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        return trimmed.length() > MAX_DESCRIPTION ? trimmed.substring(0, MAX_DESCRIPTION) : trimmed;
    }

    /**
     * Tags are lower-cased so searching for one does not depend on how it was typed, de-duplicated,
     * and bounded in both count and length.
     */
    public static List<String> parseTags(String raw) {
        Set<String> tags = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return List.of();
        for (String part : raw.split("[,\\s]+")) {
            String tag = part.trim().toLowerCase(Locale.ROOT);
            if (tag.isEmpty()) continue;
            if (tag.length() > MAX_TAG_LENGTH) tag = tag.substring(0, MAX_TAG_LENGTH);
            tags.add(tag);
            if (tags.size() >= MAX_TAGS) break;
        }
        return List.copyOf(tags);
    }

    /**
     * Case-insensitive substring match over the profile name, its description and its tags.
     *
     * <p>An empty query matches everything, so a bare search command lists rather than returns
     * nothing — the opposite would look like a broken search.
     *
     * @param metadata may be null, for a profile saved before descriptions existed
     */
    public static boolean matches(String profileName, ProfileMetadata metadata, String query) {
        if (query == null || query.isBlank()) return true;
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        if (profileName != null && profileName.toLowerCase(Locale.ROOT).contains(wanted)) return true;
        if (metadata == null) return false;
        if (metadata.description().toLowerCase(Locale.ROOT).contains(wanted)) return true;
        for (String tag : metadata.tags()) {
            if (tag.contains(wanted)) return true;
        }
        return false;
    }

    /** One line for a list: the description if there is one, else the tags, else nothing. */
    public static String summary(ProfileMetadata metadata) {
        if (metadata == null) return "";
        if (!metadata.description().isEmpty()) return metadata.description();
        List<String> tags = metadata.tags();
        return tags.isEmpty() ? "" : String.join(", ", tags);
    }

    /** Values from a hand-edited or older file, brought back inside the documented bounds. */
    public ProfileMetadata sanitised() {
        ProfileMetadata clean = new ProfileMetadata();
        clean.description = normaliseDescription(description);
        List<String> bounded = new ArrayList<>();
        for (String tag : tags()) {
            String value = tag.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) continue;
            if (value.length() > MAX_TAG_LENGTH) value = value.substring(0, MAX_TAG_LENGTH);
            if (!bounded.contains(value)) bounded.add(value);
            if (bounded.size() >= MAX_TAGS) break;
        }
        clean.tags = List.copyOf(bounded);
        clean.createdAt = Math.max(0, createdAt);
        clean.updatedAt = Math.max(0, updatedAt);
        return clean;
    }
}
