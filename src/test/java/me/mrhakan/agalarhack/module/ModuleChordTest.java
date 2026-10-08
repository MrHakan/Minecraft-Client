package me.mrhakan.agalarhack.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ModuleChordTest {
    private static Module module() {
        Module module = new Module("ChordProbe", Category.MISC, "test");
        module.registerSettings();
        return module;
    }

    @Test
    void anUnchangedBindingIsTheSameChordEveryTick() {
        // The keybind loop asks for it twice per module per tick; it must not re-parse each time.
        Module module = module();
        module.settings.setSetting("keybind", "71");

        assertSame(module.getChord(), module.getChord());
        assertEquals(71, module.getKey());
    }

    @Test
    void rebindingIsSeenImmediately() {
        Module module = module();
        module.settings.setSetting("keybind", "71");
        assertEquals(71, module.getKey());

        module.settings.setSetting("keybind", "72");
        assertEquals(72, module.getKey());
    }

    @Test
    void changingOnlyTheModifiersIsSeenToo() {
        Module module = module();
        module.settings.setSetting("keybind", "71");
        assertEquals(0, module.getChord().modifiers());

        module.settings.setSetting("keyModifiers", 2.0);
        assertEquals(2, module.getChord().modifiers());
        assertEquals(71, module.getChord().key());
    }

    @Test
    void anUnboundOrInvalidBindingStaysUnbound() {
        Module module = module();
        assertEquals(-1, module.getKey());

        module.settings.setSetting("keybind", "not a key");
        assertEquals(-1, module.getKey());
    }

    @Test
    void replacingTheWholeSettingsObjectIsSeen() {
        // A profile load swaps in a new Settings instance rather than editing the old one.
        Module module = module();
        module.settings.setSetting("keybind", "71");
        module.getChord();

        Module other = module();
        other.settings.setSetting("keybind", "80");
        module.setSettings(other.settings);

        assertEquals(80, module.getKey());
    }
}
