package me.mrhakan.agalarhack.ui.state;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiSessionTest {
    record Screen(Screen parent) { }
    @Test void childrenRetainPreviewAndLeavingBranchAbandonsEachScreenOnce() {
        var exited = new ArrayList<Screen>();
        var session = new UiSession<Screen>(Screen::parent, exited::add);
        var root = new Screen(null);
        var theme = new Screen(root);
        var color = new Screen(theme);
        session.transition(theme);
        session.transition(color);
        assertTrue(exited.isEmpty());
        session.transition(theme);
        assertEquals(1, exited.size());
        assertSame(color, exited.getFirst());
        session.transition(null);
        session.transition(null);
        assertEquals(3, exited.size());
        assertSame(theme, exited.get(1));
        assertSame(root, exited.get(2));
    }
    @Test void equalButDifferentScreenIdentityClosesPreviousSession() {
        var exited = new ArrayList<Screen>();
        var session = new UiSession<Screen>(Screen::parent, exited::add);
        var old = new Screen(null);
        session.transition(old);
        session.transition(new Screen(null));
        assertSame(old, exited.getFirst());
    }
}
