package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/** Controls the existing dynamically registered module-list HUD. */
public final class ModuleList extends Module {
    public ModuleList() { super("ModuleList", Category.RENDER, "Style, sort and align the enabled-module HUD"); }
    @Override public void selfSettings() {
        settings.setSetting("enabled", true);
        settings.setSetting("showInHud", false);
        addChoiceSetting("alignment", "Right", "Text alignment within the HUD bounds", "Left", "Right", "Anchor");
        addChoiceSetting("sorting", "Width", "Stable order for enabled modules", "Width", "Name", "Category");
        addChoiceSetting("letterCase", "Normal", "Display capitalization", "Normal", "Upper", "Lower");
        addChoiceSetting("displayMode", "Display", "Display name includes module-provided suffixes", "Display", "Name", "Category");
        addChoiceSetting("colorMode", "Rainbow", "High contrast and reduced motion override animated colors", "Rainbow", "Accent", "Category");
        addBooleanSetting("background", false, "Draw the theme panel behind each row");
        addBooleanSetting("sideBar", false, "Draw an accent strip on the aligned edge");
        addBooleanSetting("textShadow", true, "Draw a shadow behind module names");
        addNumberSetting("maximumRows", 32, 1, 64, "Maximum rows, further limited by the screen height");
        addBooleanSetting("rowAnimations", true, "Slide and fade rows in and out instead of jumping");
        addNumberSetting("animationSpeed", 0.25, 0.05, 1.0, "How quickly rows settle; 1 is instant");
        addNumberSetting("slideDistance", 14, 0, 60, "How far a row slides in from, in pixels");
    }
}
