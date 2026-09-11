package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/**
 * Safe client-side camera adjustments.
 *
 * <p>Everything here changes only what the local client draws. The vanilla options it borrows are
 * restored on disable, and only when the current value is still the one this module applied - if the
 * player changed the setting themselves while the module was on, their choice wins.
 */
public class CameraTweaks extends Module {
    private Boolean previousBobbing;
    private Integer previousFov;
    private Double previousFovEffects;
    private boolean appliedBobbing;
    private int appliedFov;
    private double appliedFovEffects;

    public CameraTweaks() {
        super("CameraTweaks", Category.RENDER, "Client-side camera adjustments: hurt shake, view bobbing, FOV and FOV effects");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("noHurtCamera", true, "Stop the camera shaking when you take damage");
        addBooleanSetting("disableBobbing", false, "Turn off view bobbing while this module is on");
        addBooleanSetting("overrideFov", false, "Force a fixed field of view");
        addNumberSetting("fov", 90, 30, 110, "Field of view to apply when overridden");
        addBooleanSetting("steadyFov", false, "Stop speed and sprint effects from changing the field of view");
    }

    /** Read by the mixin; static so the injected code stays a single predicate call. */
    public static boolean suppressHurtCamera() {
        var manager = AgalarHackClient.moduleManager;
        if (manager == null) return false;
        var module = manager.getModule("CameraTweaks");
        return module instanceof CameraTweaks tweaks && tweaks.isToggled()
                && tweaks.getBooleanSetting("noHurtCamera", true);
    }

    @Override
    public void onUpdate() {
        if (mc.options == null) return;
        applyBobbing();
        applyFov();
        applyFovEffects();
    }

    private void applyBobbing() {
        boolean disable = getBooleanSetting("disableBobbing", false);
        if (!disable) {
            restoreBobbing();
            return;
        }
        if (previousBobbing == null) previousBobbing = mc.options.bobView().get();
        appliedBobbing = false;
        mc.options.bobView().set(false);
    }

    private void applyFov() {
        if (!getBooleanSetting("overrideFov", false)) {
            restoreFov();
            return;
        }
        int wanted = (int) Math.round(getNumberSetting("fov", 90));
        if (previousFov == null) previousFov = mc.options.fov().get();
        appliedFov = wanted;
        mc.options.fov().set(wanted);
    }

    private void applyFovEffects() {
        if (!getBooleanSetting("steadyFov", false)) {
            restoreFovEffects();
            return;
        }
        if (previousFovEffects == null) previousFovEffects = mc.options.fovEffectScale().get();
        appliedFovEffects = 0.0;
        mc.options.fovEffectScale().set(0.0);
    }

    /** Each restore checks the value is still ours, so a manual change by the player is never undone. */
    private void restoreBobbing() {
        if (previousBobbing == null || mc.options == null) return;
        if (mc.options.bobView().get() == appliedBobbing) mc.options.bobView().set(previousBobbing);
        previousBobbing = null;
    }

    private void restoreFov() {
        if (previousFov == null || mc.options == null) return;
        if (mc.options.fov().get() == appliedFov) mc.options.fov().set(previousFov);
        previousFov = null;
    }

    private void restoreFovEffects() {
        if (previousFovEffects == null || mc.options == null) return;
        if (mc.options.fovEffectScale().get() == appliedFovEffects) {
            mc.options.fovEffectScale().set(previousFovEffects);
        }
        previousFovEffects = null;
    }

    @Override
    public void onDisable() {
        restoreBobbing();
        restoreFov();
        restoreFovEffects();
    }

    @Override public void onDisconnect() { onDisable(); }

    /** Camera options are client-wide, so they must be restored even without a world. */
    @Override public boolean runsWithoutWorld() { return true; }
}
