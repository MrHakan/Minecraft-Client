package me.mrhakan.agalarhack.module.combat;

import me.mrhakan.agalarhack.events.ClientEvents;
import me.mrhakan.agalarhack.events.EventBus;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.NotificationService;
import me.mrhakan.agalarhack.services.TotemPopCounts;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

/**
 * Reports totem activations the client observed.
 *
 * <p>The server never tells the client how many totems a player has; it only sends the activation
 * event. This counts what was seen and says so, rather than presenting a tally as fact.
 */
public class TotemTracker extends Module {
    private final TotemPopCounts counts = new TotemPopCounts(TotemPopCounts.DEFAULT_CAPACITY, 30_000);
    private EventBus.Subscription entityEvents;

    public TotemTracker() {
        super("TotemTracker", Category.COMBAT, "Counts totem activations you have actually seen and reports them");
        markExperimental();
    }

    @Override
    public void selfSettings() {
        addBooleanSetting("self", true, "Report your own totem pops");
        addBooleanSetting("others", true, "Report other players' totem pops");
        addNumberSetting("forgetAfter", 30, 5, 600, "Seconds without seeing a player before their count resets");
        addNumberSetting("range", 64, 8, 256, "Only report players within this distance");
    }

    /** Observed count for a player, for HUD and other consumers. */
    public int popsFor(String name) {
        return counts.count(name, System.currentTimeMillis());
    }

    @Override
    public void onEnable() {
        counts.clear();
        closeSubscription();
        entityEvents = service(EventBus.class).subscribe(ClientEvents.EntityEventReceived.class,
                "totem-tracker", 0, this::onEntityEvent);
    }

    @Override
    public void onDisable() {
        closeSubscription();
        counts.clear();
        setDisplayName(null);
    }

    @Override public void onDisconnect() { onDisable(); }

    private void closeSubscription() {
        if (entityEvents != null) { entityEvents.close(); entityEvents = null; }
    }

    private void onEntityEvent(ClientEvents.EntityEventReceived event) {
        if (mc.player == null || event.level() != mc.level) return;
        if (!(event.entity() instanceof Player player)) return;
        long now = System.currentTimeMillis();
        counts.setTimeout((long) (getNumberSetting("forgetAfter", 30) * 1000));

        if (event.eventId() == EntityEvent.DEATH) {
            // A death makes any running tally meaningless rather than merely stale.
            counts.reset(player.getName().getString());
            return;
        }
        if (event.eventId() != EntityEvent.PROTECTED_FROM_DEATH) return;

        boolean self = player == mc.player;
        if (!getBooleanSetting(self ? "self" : "others", true)) return;
        if (!self && mc.player.distanceTo(player) > getNumberSetting("range", 64)) return;

        String name = player.getName().getString();
        int total = counts.pop(name, now);
        setDisplayName("TotemTracker [" + total + "]");
        service(NotificationService.class).publish(NotificationService.Type.INFO,
                (self ? "You" : name) + " popped " + total + (total == 1 ? " totem" : " totems") + " (seen)");
    }
}
