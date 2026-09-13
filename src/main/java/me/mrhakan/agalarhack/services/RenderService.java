package me.mrhakan.agalarhack.services;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.client.Minecraft;

/** Keeps one overlay failure from suppressing other modules; lifecycle writes run on the client thread. */
public final class RenderService {
    private final Set<Module> pendingFailures = ConcurrentHashMap.newKeySet();
    public void guard(Module module, Runnable render) {
        if (module == null || !module.isToggled() || pendingFailures.contains(module)) return;
        try { render.run(); }
        catch (RuntimeException failure) {
            if (!pendingFailures.add(module)) return;
            AgalarHackClient.LOGGER.error("Render callback failed for {}", module.getName(), failure);
            Minecraft.getInstance().execute(() -> {
                try {
                    module.setToggled(false);
                    ClientServices.require(NotificationService.class).publish(NotificationService.Type.ERROR,
                            module.getName() + " disabled after a render error");
                } finally { pendingFailures.remove(module); }
            });
        }
    }
}
