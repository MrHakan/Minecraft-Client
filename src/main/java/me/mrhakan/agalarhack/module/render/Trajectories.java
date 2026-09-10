package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Settings holder for projectile path prediction rendered by WorldOverlayRenderer. */
public class Trajectories extends Module {
    public Trajectories() {
        super("Trajectories", Category.RENDER, "Predicts the flight path of common held projectiles");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("steps", 80.0, 10.0, 240.0, "Number of simulation steps to render");
        addNumberSetting("powerScale", 1.0, 0.25, 2.0, "Multiplier applied to the projectile launch velocity");
        addNumberSetting("gravity", 0.05, 0.0, 0.2, "Vertical gravity applied per simulation step");
        addNumberSetting("drag", 0.99, 0.8, 1.0, "Velocity multiplier applied per simulation step");
        addBooleanSetting("onlyWhenUsing", false, "Only show a path while the use-item key is held");
        addNumberSetting("red", 255.0, 0.0, 255.0, "Path red channel");
        addNumberSetting("green", 220.0, 0.0, 255.0, "Path green channel");
        addNumberSetting("blue", 80.0, 0.0, 255.0, "Path blue channel");
    }
}
