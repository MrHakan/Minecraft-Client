package me.mrhakan.agalarhack.managers;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class KeybindManager {
    private static final Map<Module, Boolean> lastPressed = new HashMap<>();

    public static void tick(MinecraftClient client) {
        if (client.currentScreen != null) {
            return;
        }
        long window = client.getWindow().getHandle();
        for (Module m : AgalarHackClient.moduleManager.getModuleList()) {
            int key = m.getKey();
            if (key == GLFW.GLFW_KEY_UNKNOWN) {
                lastPressed.put(m, false);
                continue;
            }
            boolean pressed = InputUtil.isKeyPressed(window, key);
            if (pressed && !lastPressed.getOrDefault(m, false)) {
                m.toggle();
            }
            lastPressed.put(m, pressed);
        }
    }
}
