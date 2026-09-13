package me.mrhakan.agalarhack.services;

import java.util.List;
import me.mrhakan.agalarhack.commands.Command;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The duplicate check behind {@code CommandManager.register}.
 *
 * <p>The command list never had one: two commands answering the same word both stayed in it, and
 * registration order decided which one ran. Addons make that reachable by someone other than whoever
 * wrote this client, which is why the check exists - and why it is checked here rather than trusted.
 */
class CommandNamesTest {
    private static Command command(String name, String... aliases) {
        return new Command(name, "test", name, aliases) {
            @Override public void onCommand(String[] args) { }
        };
    }

    @Test void aDistinctCommandIsFree() {
        var registered = List.of(command("help"), command("toggle", "t"));
        assertNull(CommandNames.collision(registered, command("waypoint", "wp")));
    }

    @Test void aDuplicateNameIsCaught() {
        var registered = List.of(command("help"));
        assertEquals("help", CommandNames.collision(registered, command("help")));
    }

    /** Lookup is case-insensitive, so a stricter check here would let `.Help` shadow `.help`. */
    @Test void caseIsNotAWayAroundIt() {
        var registered = List.of(command("help"));
        assertEquals("HELP", CommandNames.collision(registered, command("HELP")));
    }

    @Test void anAliasColldingWithAnExistingNameIsCaught() {
        var registered = List.of(command("panic"));
        assertEquals("panic", CommandNames.collision(registered, command("stop", "panic")));
    }

    @Test void aNameCollidingWithAnExistingAliasIsCaught() {
        var registered = List.of(command("toggle", "t"));
        assertEquals("t", CommandNames.collision(registered, command("t")));
    }

    @Test void twoAliasesColliding() {
        var registered = List.of(command("waypoint", "wp", "way"));
        assertEquals("way", CommandNames.collision(registered, command("travel", "way")));
    }

    /** A command already in the list must not collide with itself, or a re-check would refuse it. */
    @Test void aCommandDoesNotCollideWithItself() {
        var mine = command("help", "h");
        assertNull(CommandNames.collision(List.of(mine), mine));
    }

    @Test void nullsAreTolerated() {
        assertNull(CommandNames.collision(List.of(command("help")), null));
        assertNull(CommandNames.collision(java.util.Arrays.asList((Command) null), command("help")));
    }

    /** A blank alias answers nothing, so it must not be treated as a claim on the empty word. */
    @Test void blankWordsAreIgnored() {
        var registered = List.of(command("help", ""));
        assertNull(CommandNames.collision(registered, command("toggle", "")));
    }
}
