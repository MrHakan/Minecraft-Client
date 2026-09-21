package me.mrhakan.agalarhack.commands.impl;

import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.LookController;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

/**
 * Turns the player's view to an angle or towards a position.
 *
 * <p>Useful on its own for lining a build up, and useful inside an alias or macro, which is where a
 * repeatable exact heading actually earns its keep.
 *
 * <p>It moves the real view, visibly, through the same arbitration and per-tick step limits every
 * other rotation goes through. There is no silent variant of this and there is not going to be: a
 * rotation the player cannot see but the server can exists to defeat server-side checks.
 */
public class Look extends Command {
    /** Above Aura's 50, because this one was asked for explicitly and just now. */
    private static final int PRIORITY = 70;
    /** Long enough to turn right around at the default speed, short enough to let go of a hopeless goal. */
    private static final int BUDGET_TICKS = 60;

    public Look() {
        super("look", "Turns your view to a heading or towards a position",
                "look <yaw> <pitch> | look <x> <y> <z> | look cancel", "aim");
    }

    @Override
    public void onCommand(String[] args) {
        LookController look = ClientServices.require(LookController.class);
        if (args.length == 2 && args[1].equalsIgnoreCase("cancel")) {
            look.cancel();
            MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Look cancelled.");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "You need to be in a world.");
            return;
        }

        Float yaw;
        Float pitch;
        if (args.length == 3) {
            yaw = number(args[1]);
            pitch = number(args[2]);
        } else if (args.length == 4) {
            Float x = number(args[1]), y = number(args[2]), z = number(args[3]);
            if (x == null || y == null || z == null) { sendUsage(); return; }
            // From the eyes, not the feet: looking at a block from foot height aims above it.
            double dx = x - mc.player.getX();
            double dy = y - mc.player.getEyeY();
            double dz = z - mc.player.getZ();
            yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        } else {
            sendUsage();
            return;
        }

        if (yaw == null || pitch == null) { sendUsage(); return; }
        if (pitch < -90 || pitch > 90) {
            MessageManager.sendMessagePrefix(ChatFormatting.RED + "Pitch runs from -90 (straight up) to 90 (straight down).");
            return;
        }

        look.aimAt(new LookController.Aim(yaw, pitch, true, 180, 45, 30), BUDGET_TICKS);
        MessageManager.sendMessagePrefix(ChatFormatting.GREEN
                + String.format(java.util.Locale.ROOT, "Looking towards %.1f, %.1f.", yaw, pitch));
    }

    private static Float number(String raw) {
        try {
            float value = Float.parseFloat(raw);
            return Float.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static int priority() { return PRIORITY; }
}
