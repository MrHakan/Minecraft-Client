package me.mrhakan.agalarhack.module.world;

import me.mrhakan.agalarhack.services.InventoryService;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Selects the fastest hotbar tool for the block currently being mined. */
public class AutoTool extends Module {
    private static final String OWNER = "autotool";
    private static final int PRIORITY = 40;

    public AutoTool() {
        super("AutoTool", Category.WORLD, "Automatically selects the fastest hotbar tool while mining");
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("swapBack", true, "Return to the previous hotbar slot after mining");
        addBooleanSetting("miningOnly", true, "Only switch tools while the attack key is held");
        addNumberSetting("minDurability", 5.0, 0.0, 1000.0, "Avoid damageable tools with this many or fewer uses remaining");
    }

    @Override
    public void onUpdate() {
        InventoryService inventory = service(InventoryService.class);
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.player.isUsingItem()
                || !(mc.hitResult instanceof BlockHitResult hit)
                || (getBooleanSetting("miningOnly", true) && !mc.options.keyAttack.isDown())) {
            inventory.release(OWNER); return;
        }
        int slot = inventory.findBestTool(mc.level.getBlockState(hit.getBlockPos()),
                (int) Math.round(getNumberSetting("minDurability", 5)));
        if (slot < 0) { inventory.release(OWNER); return; }
        inventory.select(OWNER, PRIORITY, slot, false, getBooleanSetting("swapBack", true));
    }
    @Override public void onDisable() { service(InventoryService.class).release(OWNER); }
    @Override public void onDisconnect() { onDisable(); }
}
