package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.combat.Aura;
import me.mrhakan.agalarhack.module.combat.TriggerBot;
import me.mrhakan.agalarhack.module.misc.AutoEat;
import me.mrhakan.agalarhack.module.misc.AutoReconnect;
import me.mrhakan.agalarhack.module.movement.Flight;
import me.mrhakan.agalarhack.module.movement.Jesus;
import me.mrhakan.agalarhack.module.movement.NoFall;
import me.mrhakan.agalarhack.module.movement.Speed;
import me.mrhakan.agalarhack.module.movement.Sprint;
import me.mrhakan.agalarhack.module.movement.Step;
import me.mrhakan.agalarhack.module.render.BlockESP;
import me.mrhakan.agalarhack.module.render.Coordinates;
import me.mrhakan.agalarhack.module.render.Durability;
import me.mrhakan.agalarhack.module.render.EntityESP;
import me.mrhakan.agalarhack.module.render.Freecam;
import me.mrhakan.agalarhack.module.render.Fullbright;
import me.mrhakan.agalarhack.module.render.StorageESP;
import me.mrhakan.agalarhack.module.render.TargetHUD;
import me.mrhakan.agalarhack.module.render.Trajectories;
import me.mrhakan.agalarhack.module.world.AutoTool;
import net.minecraft.client.Minecraft;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    private final Map<String, Module> modulesByName = new LinkedHashMap<>();

    public ModuleManager() {
        register(new Aura());
        register(new TriggerBot());

        register(new AutoEat());
        register(new AutoReconnect());

        register(new Speed());
        register(new Flight());
        register(new Jesus());
        register(new Sprint());
        register(new Step());
        register(new NoFall());

        register(new Fullbright());
        register(new Coordinates());
        register(new Durability());
        register(new EntityESP());
        register(new StorageESP());
        register(new BlockESP());
        register(new Trajectories());
        register(new TargetHUD());
        register(new Freecam());

        register(new AutoTool());
    }

    private void register(Module module) {
        String key = normalize(module.getName());
        if (modulesByName.containsKey(key)) {
            throw new IllegalStateException("Duplicate module name: " + module.getName());
        }
        modules.add(module);
        modulesByName.put(key, module);
    }

    public void tick(Minecraft client) {
        boolean worldReady = client.player != null && client.level != null;
        boolean stateChanged = false;
        for (Module module : modules) {
            if (!module.isToggled() || (!worldReady && !module.runsWithoutWorld())) {
                continue;
            }
            try {
                module.onUpdate();
            } catch (RuntimeException e) {
                System.err.println("[Agalar Hack] Disabling module after tick failure: " + module.getName());
                e.printStackTrace();
                forceDisable(module);
                stateChanged = true;
            }
        }
        if (stateChanged) {
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        }
    }

    public void onDisconnect() {
        for (Module module : modules) {
            if (!module.isToggled()) {
                continue;
            }
            try {
                module.onDisconnect();
            } catch (RuntimeException e) {
                System.err.println("[Agalar Hack] Disconnect cleanup failed for " + module.getName());
                e.printStackTrace();
            }
        }
    }

    public Module getModule(String name) {
        return name == null ? null : modulesByName.get(normalize(name));
    }

    public List<Module> getModuleList() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> getModulesByCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public List<Module> searchModules(String query) {
        if (query == null || query.isBlank()) {
            return getModuleList();
        }
        String normalized = normalize(query);
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            boolean matches = normalize(module.getName()).contains(normalized)
                    || normalize(module.getDescription()).contains(normalized)
                    || normalize(module.getCategory().name).contains(normalized);
            if (!matches) {
                matches = module.settings.getSpecs().stream()
                        .anyMatch(spec -> normalize(spec.getName()).contains(normalized)
                                || normalize(spec.getDescription()).contains(normalized));
            }
            if (matches) {
                result.add(module);
            }
        }
        return result;
    }

    public int disableAll() {
        int disabled = 0;
        for (Module module : modules) {
            if (!module.isToggled()) {
                continue;
            }
            disabled++;
            forceDisable(module);
        }
        if (disabled > 0) {
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        }
        return disabled;
    }

    public void loadModules() {
        AgalarHackClient.SETTINGS_MANAGER.loadSettings();
        boolean changed = false;
        for (Module module : modules) {
            if (!Boolean.TRUE.equals(module.settings.getSetting("enabled")) || module.isToggled()) {
                continue;
            }
            try {
                module.setToggled(true, false);
            } catch (RuntimeException e) {
                System.err.println("[Agalar Hack] Could not restore enabled module: " + module.getName());
                e.printStackTrace();
                forceDisable(module);
                changed = true;
            }
        }
        if (changed) {
            AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        }
    }

    private void forceDisable(Module module) {
        try {
            if (module.isToggled()) {
                module.setToggled(false, false);
            } else {
                module.settings.setSetting("enabled", false);
            }
        } catch (RuntimeException disableError) {
            module.settings.setSetting("enabled", false);
            System.err.println("[Agalar Hack] Module cleanup failed: " + module.getName());
            disableError.printStackTrace();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
