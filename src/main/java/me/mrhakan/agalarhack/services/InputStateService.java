package me.mrhakan.agalarhack.services;

import com.mojang.blaze3d.platform.InputConstants;
import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Bounded tick-sampled input transitions. Does not replace GLFW callbacks or consume vanilla input. */
public final class InputStateService {
    private final boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private final boolean[] mouse = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];
    private final EventBus events;
    public InputStateService(EventBus events) { this.events = events; }
    public void tick(Minecraft mc) {
        for (int key = GLFW.GLFW_KEY_SPACE; key < keys.length; key++) {
            boolean down = InputConstants.isKeyDown(mc.getWindow(), key);
            if (keys[key] != down) {
                keys[key] = down;
                events.post(new ClientEvents.KeyInput(key, down, mc.gui.screen() != null));
            }
        }
        for (int button = 0; button < mouse.length; button++) {
            boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), button) == GLFW.GLFW_PRESS;
            if (mouse[button] != down) {
                mouse[button] = down;
                events.post(new ClientEvents.MouseInput(button, down, mc.gui.screen() != null));
            }
        }
    }
    public int modifiers() {
        return (keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT) ? 1 : 0)
                | (keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL) ? 2 : 0)
                | (keyDown(GLFW.GLFW_KEY_LEFT_ALT) || keyDown(GLFW.GLFW_KEY_RIGHT_ALT) ? 4 : 0)
                | (keyDown(GLFW.GLFW_KEY_LEFT_SUPER) || keyDown(GLFW.GLFW_KEY_RIGHT_SUPER) ? 8 : 0);
    }
    public boolean keyDown(int key) { return key >= 0 && key < keys.length && keys[key]; }
    public boolean mouseDown(int button) { return button >= 0 && button < mouse.length && mouse[button]; }
}
