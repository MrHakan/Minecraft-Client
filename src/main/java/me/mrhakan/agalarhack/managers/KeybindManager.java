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
        if (client.gui.screen() != null) {
            return;
        }
        for (Module m : AgalarHackClient.moduleManager.getModuleList()) {
            int key = m.getKey();
            if (key == InputConstants.UNKNOWN.getValue()) {
                lastPressed.put(m, false);
                continue;
            }
            boolean pressed = InputConstants.isKeyDown(client.getWindow(), key);
            if (pressed && !lastPressed.getOrDefault(m, false)) {
                m.toggle();
            }
            lastPressed.put(m, pressed);
        }
    }
}
