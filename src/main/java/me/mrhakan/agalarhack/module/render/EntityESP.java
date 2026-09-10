package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Settings holder for the world-space entity overlay renderer. */
public class EntityESP extends Module {
    public EntityESP() {
        super("ESP", Category.RENDER, "Draws configurable boxes, tracers and labels around valid living entities");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("range", 96.0, 8.0, 256.0, "Maximum render distance in blocks");
        addBooleanSetting("respectTargetPolicy", true, "Use the shared global target policy as the entity filter");
        addBooleanSetting("players", true, "Show players when target policy filtering is disabled");
        addBooleanSetting("mobs", true, "Show non-player living entities when target policy filtering is disabled");
        addBooleanSetting("boxes", true, "Draw world-space bounding boxes");
        addBooleanSetting("tracers", false, "Draw a line from the camera toward each visible ESP target");
        addBooleanSetting("labels", true, "Draw the entity name above ESP targets");
        addBooleanSetting("showDistance", true, "Include distance in ESP labels");
        addBooleanSetting("showHealth", false, "Include current health in ESP labels");
        addBooleanSetting("friendColors", true, "Render players in the local friend list with a dedicated color");
        addBooleanSetting("teamColors", true, "Use the scoreboard team color for allied players");
        addBooleanSetting("distanceFade", true, "Fade ESP geometry as targets approach the configured range limit");
        addNumberSetting("fadeStart", 32.0, 0.0, 256.0, "Distance in blocks where fading begins");
        addNumberSetting("minimumAlpha", 64.0, 0.0, 255.0, "Minimum geometry alpha at maximum range");
        addNumberSetting("friendRed", 85.0, 0.0, 255.0, "Friend overlay red channel");
        addNumberSetting("friendGreen", 255.0, 0.0, 255.0, "Friend overlay green channel");
        addNumberSetting("friendBlue", 120.0, 0.0, 255.0, "Friend overlay blue channel");
        addNumberSetting("red", 85.0, 0.0, 255.0, "Default overlay red channel");
        addNumberSetting("green", 170.0, 0.0, 255.0, "Default overlay green channel");
        addNumberSetting("blue", 255.0, 0.0, 255.0, "Default overlay blue channel");
        addNumberSetting("alpha", 230.0, 32.0, 255.0, "Default overlay alpha channel");
    }
}
