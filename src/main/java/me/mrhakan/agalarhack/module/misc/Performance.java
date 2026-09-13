package me.mrhakan.agalarhack.module.misc;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ScannerService;
import me.mrhakan.agalarhack.services.scanning.ScanBudgets;

/**
 * Client-wide performance limits, in the same shape as Notifications: a module whose job is to
 * configure a shared service rather than to do something itself.
 *
 * <p>Only the shared scanner ceiling for now. It is the one figure that was genuinely not tunable —
 * each scanning module can already be told how much work to ask for, but the total they could be
 * given in one client tick was a constant, and a single constant suits neither a machine with
 * headroom nor one without.
 *
 * <p>Disabling this restores the built-in ceiling rather than removing it, because an unbounded
 * scanner is not a thing this client offers.
 */
public class Performance extends Module {
    public Performance() {
        super("Performance", Category.MISC, "Sets the shared per-tick scanning ceiling every ESP scanner draws from");
    }

    @Override
    public void selfSettings() {
        addChoiceSetting("scanBudget", "balanced",
                "Total scanning work allowed per client tick; balanced is what the client used before this was tunable",
                "low", "balanced", "high");
    }

    @Override
    public void onEnable() {
        apply();
    }

    /** Re-read every tick so a change in the ClickGUI takes effect without toggling the module. */
    @Override
    public void onUpdate() {
        apply();
    }

    @Override
    public void onDisable() {
        service(ScannerService.class).setBudgets(ScanBudgets.BALANCED);
    }

    /** Nothing to restore on disconnect: the ceiling is a client setting, not world state. */
    private void apply() {
        service(ScannerService.class).setBudgets(ScanBudgets.forProfile(getStringSetting("scanBudget", "balanced")));
    }

    /** The ceiling matters on the main menu too, and costs nothing to keep applied there. */
    @Override public boolean runsWithoutWorld() { return true; }
}
