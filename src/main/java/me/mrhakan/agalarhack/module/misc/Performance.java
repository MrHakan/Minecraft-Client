package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ScanBudgets;

/** Client-wide performance limits shared by scanning modules. */
public class Performance extends Module {
    private String appliedProfile;

    public Performance() {
        super("Performance", Category.MISC, "Sets the shared per-tick scanning ceiling every ESP scanner draws from");
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("scanBudget", "balanced",
                "Total scanning work allowed per client tick; balanced is the recommended default",
                "low", "balanced", "high");
    }

    @Override public void onEnable() { applyIfChanged(); }

    /** Settings can change while enabled, but unchanged profiles no longer rewrite the service every tick. */
    @Override public void onUpdate() { applyIfChanged(); }

    @Override
    public void onDisable() {
        service(ScannerService.class).setBudgets(ScanBudgets.BALANCED);
        appliedProfile = null;
    }

    private void applyIfChanged() {
        String profile = getStringSetting("scanBudget", "balanced");
        if (profile.equals(appliedProfile)) return;
        service(ScannerService.class).setBudgets(ScanBudgets.forProfile(profile));
        appliedProfile = profile;
    }

    @Override public void onWorldChanged(boolean worldReady) { applyIfChanged(); }
    @Override public boolean runsWithoutWorld() { return true; }
}
