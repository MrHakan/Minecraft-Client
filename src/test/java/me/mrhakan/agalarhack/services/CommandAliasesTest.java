package me.mrhakan.agalarhack.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandAliasesTest {
    @Test void anAliasExpandsToItsCommand() {
        var aliases = new CommandAliases();
        assertTrue(aliases.define("homebase", "waypoint goto home"));
        assertEquals("waypoint goto home", aliases.expand("homebase").orElseThrow());
    }

    @Test void argumentsAreAppendedToTheExpansion() {
        var aliases = new CommandAliases();
        aliases.define("wp", "waypoint");
        assertEquals("waypoint add mine", aliases.expand("wp add mine").orElseThrow());
    }

    @Test void namesAreCaseInsensitive() {
        var aliases = new CommandAliases();
        aliases.define("Home", "waypoint goto home");
        assertTrue(aliases.expand("HOME").isPresent());
        assertTrue(aliases.remove("hOmE"));
    }

    @Test void anUnknownFirstWordIsNotExpanded() {
        var aliases = new CommandAliases();
        aliases.define("home", "waypoint goto home");
        assertTrue(aliases.expand("toggle flight").isEmpty());
        assertTrue(aliases.expand("").isEmpty());
        assertTrue(aliases.expand(null).isEmpty());
    }

    @Test void expansionIsSinglePassSoAliasesCannotLoop() {
        var aliases = new CommandAliases();
        aliases.define("a", "b");
        aliases.define("b", "a");
        // Each expands once to the other and stops; neither recurses.
        assertEquals("b", aliases.expand("a").orElseThrow());
        assertEquals("a", aliases.expand("b").orElseThrow());
    }

    @Test void anAliasCannotNameItself() {
        var aliases = new CommandAliases();
        assertFalse(aliases.define("loop", "loop something"));
        assertFalse(aliases.define("loop", "LOOP"));
        assertEquals(0, aliases.size());
    }

    @Test void unusableNamesAreRejectedRatherThanCoerced() {
        var aliases = new CommandAliases();
        assertFalse(aliases.define("two words", "toggle flight"));
        assertFalse(aliases.define("", "toggle flight"));
        assertFalse(aliases.define(null, "toggle flight"));
        assertFalse(aliases.define("bad!", "toggle flight"));
        assertFalse(aliases.define("x".repeat(100), "toggle flight"));
    }

    @Test void unusableExpansionsAreRejected() {
        var aliases = new CommandAliases();
        assertFalse(aliases.define("a", ""));
        assertFalse(aliases.define("a", "   "));
        assertFalse(aliases.define("a", null));
        assertFalse(aliases.define("a", "x".repeat(CommandAliases.MAX_EXPANSION + 1)));
    }

    @Test void redefiningReplacesWithoutGrowingTheStore() {
        var aliases = new CommandAliases();
        aliases.define("home", "waypoint goto home");
        aliases.define("home", "waypoint goto base");
        assertEquals(1, aliases.size());
        assertEquals("waypoint goto base", aliases.expand("home").orElseThrow());
    }

    @Test void theStoreIsBoundedButStillAllowsReplacement() {
        var aliases = new CommandAliases();
        for (int i = 0; i < CommandAliases.MAX_ALIASES; i++) assertTrue(aliases.define("a" + i, "toggle x"));
        assertFalse(aliases.define("overflow", "toggle x"));
        assertTrue(aliases.define("a0", "toggle y"), "replacing at the limit must still work");
        assertEquals(CommandAliases.MAX_ALIASES, aliases.size());
    }

    @Test void listingIsAnImmutableCopy() {
        var aliases = new CommandAliases();
        aliases.define("home", "waypoint goto home");
        var copy = aliases.all();
        assertThrows(UnsupportedOperationException.class, () -> copy.put("x", "y"));
        aliases.clear();
        assertEquals(1, copy.size(), "the copy is unaffected by later changes");
    }
}
