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

            boolean pressed = InputConstants.isKeyDown(client.getWindow(), key);
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
