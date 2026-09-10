package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Settings holder for the world-space entity box renderer. */
public class EntityESP extends Module {
    public EntityESP() {
        super("ESP", Category.RENDER, "Draws configurable world-space boxes around valid living entities");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 96.0, 8.0, 256.0, "Maximum render distance in blocks");
        addBooleanSetting("respectTargetPolicy", true, "Use the shared global target policy as the entity filter");
        addBooleanSetting("players", true, "Show players when target policy filtering is disabled");
        addBooleanSetting("mobs", true, "Show non-player living entities when target policy filtering is disabled");
        addNumberSetting("red", 85.0, 0.0, 255.0, "Box red channel");
        addNumberSetting("green", 170.0, 0.0, 255.0, "Box green channel");
        addNumberSetting("blue", 255.0, 0.0, 255.0, "Box blue channel");
        addNumberSetting("alpha", 230.0, 32.0, 255.0, "Box alpha channel");
    }
}
