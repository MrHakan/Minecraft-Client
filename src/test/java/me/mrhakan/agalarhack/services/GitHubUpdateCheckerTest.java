package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitHubUpdateCheckerTest {
    private static final String INSTALLED = "0123456789abcdef0123456789abcdef01234567";
    private static final String LATEST = "89abcdef0123456789abcdef0123456789abcdef";

    @Test
    void parsesVersionAndFullCommitFromStableReleaseName() {
        var release = GitHubUpdateChecker.parseLatestRelease("""
                {"name":"Agalar Hack v26.2.5+89abcdef0123456789abcdef0123456789abcdef"}
                """);

        assertNotNull(release);
        assertEquals("26.2.5", release.version());
        assertEquals(LATEST, release.commit());
    }

    @Test
    void reportsOnlyAReleaseBuiltFromADifferentCommit() {
        var current = new GitHubUpdateChecker.Release("26.2.5", INSTALLED);
        var newer = new GitHubUpdateChecker.Release("26.2.5", LATEST);

        assertFalse(GitHubUpdateChecker.isUpdateAvailable(INSTALLED, current));
        assertTrue(GitHubUpdateChecker.isUpdateAvailable(INSTALLED, newer));
        assertFalse(GitHubUpdateChecker.isUpdateAvailable("unknown", newer));
    }

    @Test
    void ignoresMalformedOrUnstampedReleaseNames() {
        assertNull(GitHubUpdateChecker.parseLatestRelease("not-json"));
        assertNull(GitHubUpdateChecker.parseLatestRelease("{\"name\":\"Agalar Hack v26.2.5\"}"));
        assertNull(GitHubUpdateChecker.parseLatestRelease("{\"tag_name\":\"26.2.5\"}"));
        assertFalse(GitHubUpdateChecker.isUpdateAvailable(INSTALLED, null));
    }
}
