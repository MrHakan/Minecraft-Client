package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.combat.*;
import me.mrhakan.agalarhack.module.misc.*;
import me.mrhakan.agalarhack.module.movement.*;
import me.mrhakan.agalarhack.module.player.*;
import me.mrhakan.agalarhack.module.render.*;
import me.mrhakan.agalarhack.module.world.*;
import net.minecraft.client.Minecraft;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final List<Module> moduleView = Collections.unmodifiableList(modules);
    private final Map<String, Module> modulesByName = new LinkedHashMap<>();
    private final Map<Module, String> searchText = new HashMap<>();
    private final Map<Category, List<Module>> modulesByCategory = new EnumMap<>(Category.class);
    private final Map<Category, List<Module>> categoryViews = new EnumMap<>(Category.class);

    public ModuleManager() {
        for (Category category : Category.values()) {
            List<Module> categoryModules = new ArrayList<>();
            modulesByCategory.put(category, categoryModules);
            categoryViews.put(category, Collections.unmodifiableList(categoryModules));
        }
        register(new Aura()); register(new TriggerBot()); register(new CritInfo()); register(new TotemTracker()); register(new CombatHistory());
        register(new AutoEat()); register(new AutoReconnect()); register(new ServerInfo()); register(new Performance()); register(new AutoFish());
        register(new AutoAccept()); register(new BetterChat()); register(new ChatMentions()); register(new GamemodeAlerts()); register(new PlayerAlerts()); register(new ChatFilter());
        register(new Speed()); register(new Flight()); register(new Jesus()); register(new Sprint()); register(new Step()); register(new NoFall()); register(new SafeWalk()); register(new Parkour()); register(new AutoWalk());
        register(new Notifications()); register(new ModuleList()); register(new Fullbright()); register(new Coordinates()); register(new Durability());
        register(new SessionTimer()); register(new EntityESP()); register(new StorageESP()); register(new BlockESP()); register(new Trajectories()); register(new TargetHUD());
        register(new Freecam()); register(new Waypoints()); register(new ItemESP()); register(new Nametags()); register(new Breadcrumbs()); register(new HoleESP());
        register(new CameraTweaks()); register(new SpawnESP()); register(new Tracers()); register(new ProjectileESP()); register(new ProjectileWarning());
        register(new AutoTotem()); register(new AutoArmor()); register(new AutoWeapon()); register(new AutoRefill()); register(new AutoRespawn()); register(new InventoryCleaner()); register(new ElytraInfo());
        register(new AutoTool()); register(new BaseFinder());
    }

    public void register(Module module) {
        String key = normalize(module.getName());
        if (modulesByName.containsKey(key)) throw new IllegalStateException("Duplicate module name: " + module.getName());
        modules.add(module);
        modulesByName.put(key, module);
        modulesByCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        searchText.remove(module);
    }

    public void tick(Minecraft client) {
        boolean worldReady = client.player != null && client.level != null && client.player.isAlive();
        boolean stateChanged = false;
        var registry = me.mrhakan.agalarhack.services.ClientServices.registry();
        var timings = registry == null ? null : registry.find(me.mrhakan.agalarhack.services.ModuleTimings.class).orElse(null);
        boolean measure = timings != null && timings.isRecording();
        for (Module module : modules) {
            if (!module.isToggled() || (!worldReady && !module.runsWithoutWorld())) continue;
            try {
                if (measure) {
                    long started = System.nanoTime();
                    module.onUpdate();
                    timings.record(module.getName(), System.nanoTime() - started);
                } else module.onUpdate();
            } catch (RuntimeException e) {
                AgalarHackClient.LOGGER.error("Disabling module after tick failure: {}", module.getName(), e);
                me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.NotificationService.class).publish(
                        me.mrhakan.agalarhack.services.NotificationService.Type.ERROR, module.getName() + " disabled after an error");
                forceDisable(module); stateChanged = true;
            }
        }
        if (stateChanged) AgalarHackClient.SETTINGS_MANAGER.updateSettings();
    }

    public void onWorldChanged(boolean worldReady) {
        for (Module module : modules) {
            if (!module.isToggled() || module.runsWithoutWorld()) continue;
            try { module.onWorldChanged(worldReady); }
            catch (RuntimeException failure) {
                AgalarHackClient.LOGGER.error("World transition failed for {}", module.getName(), failure);
                forceDisable(module); AgalarHackClient.SETTINGS_MANAGER.updateSettings();
            }
        }
        AgalarHackClient.TARGET_TRACKER.clear();
    }

    public void onDisconnect() {
        for (Module module : modules) {
            if (!module.isToggled()) continue;
            try { module.onDisconnect(); }
            catch (RuntimeException e) {
                AgalarHackClient.LOGGER.warn("[Agalar Hack] Disconnect cleanup failed for " + module.getName());
                AgalarHackClient.LOGGER.error("Operation failed", e);
            }
        }
    }

    public Module getModule(String name) { return name == null ? null : modulesByName.get(normalize(name)); }
    public List<Module> getModuleList() { return moduleView; }
    public List<Module> getModulesByCategory(Category category) {
        List<Module> view = categoryViews.get(category);
        return view == null ? Collections.emptyList() : view;
    }

    public List<Module> searchModules(String query) {
        if (query == null || query.isBlank()) return getModuleList();
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            String haystack = searchText.computeIfAbsent(module, m -> me.mrhakan.agalarhack.ui.ModuleSearch.prepare(buildSearchText(m)));
            if (me.mrhakan.agalarhack.ui.ModuleSearch.matchesPrepared(query, haystack)) result.add(module);
        }
        return result;
    }

    private static String buildSearchText(Module module) {
        StringBuilder text = new StringBuilder(192).append(module.getName()).append(' ').append(module.getDescription()).append(' ').append(module.getCategory().name);
        for (var spec : module.settings.getSpecs()) text.append(' ').append(spec.getName()).append(' ').append(spec.getDescription());
        return text.toString();
    }

    public int disableAll() {
        int disabled = 0;
        for (Module module : modules) if (module.isToggled()) { disabled++; forceDisable(module); }
        if (disabled > 0) AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        return disabled;
    }

    public void loadModules() {
        AgalarHackClient.SETTINGS_MANAGER.loadSettings();
        searchText.clear();
        boolean changed = false;
        for (Module module : modules) {
            if (!Boolean.TRUE.equals(module.settings.getSetting("enabled")) || module.isToggled()) continue;
            try { module.setToggled(true, false); }
            catch (RuntimeException e) {
                AgalarHackClient.LOGGER.warn("[Agalar Hack] Could not restore enabled module: " + module.getName());
                AgalarHackClient.LOGGER.error("Operation failed", e);
                forceDisable(module); changed = true;
            }
        }
        if (changed) AgalarHackClient.SETTINGS_MANAGER.updateSettings();
    }

    public void forceDisable(Module module) {
        try {
            if (module.isToggled()) module.setToggled(false, false);
            else module.settings.setSetting("enabled", false);
        } catch (RuntimeException disableError) {
            module.settings.setSetting("enabled", false);
            AgalarHackClient.LOGGER.warn("[Agalar Hack] Module cleanup failed: " + module.getName());
            AgalarHackClient.LOGGER.error("Module cleanup failed", disableError);
        }
    }

    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
}
