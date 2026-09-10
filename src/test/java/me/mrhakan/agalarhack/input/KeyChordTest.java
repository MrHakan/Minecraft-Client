package me.mrhakan.agalarhack.input;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KeyChordTest {
    @Test void retainsLegacyKeysAndRejectsInvalidNumbers() {
        assertEquals(82, KeyChord.parse("82.0",0).key());
        for (String invalid : new String[]{"NaN","Infinity","82.5","99999999999","null"}) assertEquals(-1,KeyChord.parse(invalid,2).key());
        assertEquals(0,new KeyChord(-1,15).modifiers());
    }
    @Test void mouseAndModifierBindingsStayDistinct() {
        var chord = new KeyChord(-102,2);
        assertTrue(chord.mouse()); assertEquals(2,chord.mouseButton());
        assertTrue(chord.matchesModifiers(2)); assertFalse(chord.matchesModifiers(3));
        assertNotEquals(new KeyChord(82,0),new KeyChord(82,2));
    }
}
