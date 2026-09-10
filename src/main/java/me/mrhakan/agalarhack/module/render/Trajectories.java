package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Settings holder for held-projectile path prediction rendered by WorldOverlayRenderer. */
public class Trajectories extends Module {
    public Trajectories() {
        super("Trajectories", Category.RENDER, "Predicts held projectile flight paths using vanilla-aware launch physics");
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("physics", "Vanilla", "Vanilla uses item-family launch speed, bow charge and source movement; Custom uses the values below", "Vanilla", "Custom");
        addNumberSetting("steps", 100.0, 10.0, 300.0, "Number of simulation steps to render");
        addNumberSetting("powerScale", 1.0, 0.25, 2.0, "Multiplier applied after the vanilla/custom launch speed is resolved");
        addBooleanSetting("includePlayerMotion", true, "Add the player's known movement to the predicted launch like vanilla shootFromRotation");
        addNumberSetting("gravity", 0.05, 0.0, 0.2, "Custom-mode vertical gravity per simulation step");
        addNumberSetting("drag", 0.99, 0.8, 1.0, "Custom-mode velocity multiplier per simulation step");
        addBooleanSetting("onlyWhenUsing", false, "For instant-use throwables, only show a path while the use-item key is held");
        addBooleanSetting("collision", true, "Stop the prediction at the first block or living-entity collision");
        addBooleanSetting("landingMarker", true, "Draw a marker at the predicted impact point");
        addNumberSetting("markerSize", 0.22, 0.05, 1.0, "World-space impact marker radius");
        addNumberSetting("red", 255.0, 0.0, 255.0, "Path red channel");
        addNumberSetting("green", 220.0, 0.0, 255.0, "Path green channel");
        addNumberSetting("blue", 80.0, 0.0, 255.0, "Path blue channel");
    }
}
