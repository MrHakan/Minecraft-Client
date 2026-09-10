package me.mrhakan.agalarhack.managers;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.Minecraft;

public class KeybindManager {
    private static final Map<Module, Boolean> lastPressed = new HashMap<>();

    public static void tick(Minecraft client) {
        boolean suppressToggles = client.gui.screen() != null;

        for (Module m : AgalarHackClient.moduleManager.getModuleList()) {
            int key = m.getKey();
            if (key == InputConstants.UNKNOWN.getValue()) {
                lastPressed.put(m, false);
                continue;
            }

            var input = me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.InputStateService.class);
            var chord = m.getChord();
            int modifiers = input.modifiers();
            // A modifier bound by itself does not require its own flag in the chord mask.
            if (key == 340 || key == 344) modifiers &= ~1;
            if (key == 341 || key == 345) modifiers &= ~2;
            if (key == 342 || key == 346) modifiers &= ~4;
            if (key == 343 || key == 347) modifiers &= ~8;
            boolean pressed = (chord.mouse() ? input.mouseDown(chord.mouseButton()) : input.keyDown(key)) && chord.matchesModifiers(modifiers);
            if (!suppressToggles && pressed && !lastPressed.getOrDefault(m, false)) {
                m.toggle();
            }

            // Keep edge-detection state in sync even while a menu/chat screen is
            // open. Otherwise a key held while closing a screen can trigger an
            // unexpected module toggle on the following tick.
            lastPressed.put(m, pressed);
        }
    }
}
