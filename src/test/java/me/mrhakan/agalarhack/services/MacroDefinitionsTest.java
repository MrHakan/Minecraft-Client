package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.services.MacroDefinitions.Kind;
import me.mrhakan.agalarhack.services.MacroDefinitions.Macro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacroDefinitionsTest {
    @Test void aDefinedMacroIsFoundByItsKey() {
        var macros = new MacroDefinitions();
        assertTrue(macros.define(72, false, Kind.CHAT, "hello"));
        var found = macros.find(72, false);
        assertNotNull(found);
        assertEquals(Kind.CHAT, found.kind());
        assertEquals("hello", found.action());
    }

    @Test void keyboardAndMouseKeysDoNotCollide() {
        var macros = new MacroDefinitions();
        macros.define(1, false, Kind.CHAT, "keyboard");
        macros.define(1, true, Kind.CHAT, "mouse");
        assertEquals("keyboard", macros.find(1, false).action());
        assertEquals("mouse", macros.find(1, true).action());
        assertEquals(2, macros.size());
    }

    @Test void redefiningReplacesWithoutGrowingTheStore() {
        var macros = new MacroDefinitions();
        macros.define(72, false, Kind.CHAT, "one");
        macros.define(72, false, Kind.COMMAND, "two");
        assertEquals(1, macros.size());
        assertEquals(Kind.COMMAND, macros.find(72, false).kind());
    }

    @Test void actionsAreTrimmedAndBounded() {
        var macros = new MacroDefinitions();
        macros.define(72, false, Kind.CHAT, "  spaced  ");
        assertEquals("spaced", macros.find(72, false).action());
        assertFalse(macros.define(73, false, Kind.CHAT, "x".repeat(MacroDefinitions.MAX_ACTION_LENGTH + 1)));
        assertFalse(macros.define(74, false, Kind.CHAT, "   "));
        assertFalse(macros.define(75, false, Kind.CHAT, null));
        assertFalse(macros.define(76, false, null, "hello"));
    }

    @Test void anUnboundKeyboardKeyIsRejected() {
        var macros = new MacroDefinitions();
        assertFalse(macros.define(-1, false, Kind.CHAT, "hello"));
        assertTrue(macros.define(-1, true, Kind.CHAT, "mouse codes may be negative"));
    }

    @Test void theStoreIsBoundedButStillAllowsReplacement() {
        var macros = new MacroDefinitions();
        for (int i = 0; i < MacroDefinitions.MAX_MACROS; i++) assertTrue(macros.define(i, false, Kind.CHAT, "x"));
        assertFalse(macros.define(999, false, Kind.CHAT, "overflow"));
        assertTrue(macros.define(0, false, Kind.CHAT, "replacement"));
        assertEquals(MacroDefinitions.MAX_MACROS, macros.size());
    }

    @Test void removalReportsWhetherAnythingMatched() {
        var macros = new MacroDefinitions();
        macros.define(72, false, Kind.CHAT, "hello");
        assertFalse(macros.remove(73, false));
        assertTrue(macros.remove(72, false));
        assertNull(macros.find(72, false));
    }

    @Test void kindParsingAcceptsAliasesAndRejectsNonsense() {
        assertEquals(Kind.CHAT, MacroDefinitions.parseKind("chat"));
        assertEquals(Kind.CHAT, MacroDefinitions.parseKind(" SAY "));
        assertEquals(Kind.COMMAND, MacroDefinitions.parseKind("cmd"));
        assertEquals(Kind.TOGGLE, MacroDefinitions.parseKind("module"));
        assertNull(MacroDefinitions.parseKind("nonsense"));
        assertNull(MacroDefinitions.parseKind(null));
    }

    @Test void loadingRevalidatesStoredEntries() {
        var macros = new MacroDefinitions();
        macros.replaceAll(java.util.List.of(
                new Macro(72, false, Kind.CHAT, "good"),
                new Macro(-1, false, Kind.CHAT, "unbound key"),
                new Macro(80, false, Kind.CHAT, "   ")));
        assertEquals(1, macros.size(), "hand-edited nonsense must not survive loading");
        assertEquals("good", macros.find(72, false).action());
    }

    @Test void listingIsAnImmutableSnapshot() {
        var macros = new MacroDefinitions();
        macros.define(72, false, Kind.CHAT, "hello");
        var snapshot = macros.all();
        macros.clear();
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }
}
