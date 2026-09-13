package me.mrhakan.agalarhack.managers;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.Minecraft;

public class KeybindManager {
    private static final Map<Module, Boolean> lastPressed = new HashMap<>();
    private static final Map<String, Boolean> macroPressed = new HashMap<>();

    public static void tick(Minecraft client) {
        boolean suppressToggles = client.gui.screen() != null;
        tickMacros(client, suppressToggles);

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
            boolean pressed = chord.mouse() ? input.mouseDown(chord.mouseButton()) : input.keyDown(key);
            if (!suppressToggles && pressed && !lastPressed.getOrDefault(m, false) && chord.matchesModifiers(modifiers)) {
                m.toggle();
            }

            // Keep edge-detection state in sync even while a menu/chat screen is
            // open. Otherwise a key held while closing a screen can trigger an
            // unexpected module toggle on the following tick.
            lastPressed.put(m, pressed);
        }
    }

    /**
     * Runs user macros on the same edge-detection rules as module binds, so a held key fires once
     * and a key held while closing a screen does not fire on the following tick.
     */
    private static void tickMacros(Minecraft client, boolean suppressed) {
        var macros = CommandManager.macros();
        if (macros.size() == 0) { macroPressed.clear(); return; }
        var input = me.mrhakan.agalarhack.services.ClientServices.require(
                me.mrhakan.agalarhack.services.InputStateService.class);
        for (var macro : macros.all()) {
            String id = (macro.mouse() ? "m" : "k") + macro.key();
            boolean pressed = macro.mouse() ? input.mouseDown(macro.key()) : input.keyDown(macro.key());
            if (!suppressed && pressed && !macroPressed.getOrDefault(id, false)) run(client, macro);
            macroPressed.put(id, pressed);
        }
    }

    private static void run(Minecraft client, me.mrhakan.agalarhack.services.MacroDefinitions.Macro macro) {
        try {
            switch (macro.kind()) {
                case CHAT -> {
                    if (client.player != null) client.player.connection.sendChat(macro.action());
                }
                case COMMAND -> CommandManager.handleChat(AgalarHackClient.prefix + macro.action());
                case TOGGLE -> {
                    Module module = AgalarHackClient.moduleManager.getModule(macro.action());
                    if (module != null) module.toggle();
                    else MessageManager.sendMessagePrefix(net.minecraft.ChatFormatting.RED
                            + "Macro target module not found: " + macro.action());
                }
            }
        } catch (RuntimeException failure) {
            // A bad macro must not break key handling for everything else.
            AgalarHackClient.LOGGER.error("Macro failed: {}", macro.action(), failure);
        }
    }
}
