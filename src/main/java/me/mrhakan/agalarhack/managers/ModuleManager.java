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
import me.mrhakan.agalarhack.module.movement.Flight;
import me.mrhakan.agalarhack.module.movement.Jesus;
import me.mrhakan.agalarhack.module.movement.NoFall;
import me.mrhakan.agalarhack.module.movement.Speed;
import me.mrhakan.agalarhack.module.movement.Sprint;
import me.mrhakan.agalarhack.module.movement.Step;
import me.mrhakan.agalarhack.module.render.Coordinates;
import me.mrhakan.agalarhack.module.render.Durability;
import me.mrhakan.agalarhack.module.render.Fullbright;
import net.minecraft.client.Minecraft;

public class ModuleManager {

	private final List<Module> modules = new ArrayList<>();
	private final Map<String, Module> modulesByName = new LinkedHashMap<>();

	public ModuleManager() {
		// COMBAT
		register(new Aura());
		register(new TriggerBot());
		// EXPLOIT

		// MISC

		// MOVEMENT
		register(new Speed());
		register(new Flight());
		register(new Jesus());
		register(new Sprint());
		register(new Step());
		register(new NoFall());
		// RENDER
		register(new Fullbright());
		register(new Coordinates());
		register(new Durability());
		// WORLD
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
		if (client.player == null || client.level == null) {
			return;
		}

		boolean stateChanged = false;
		for (Module module : modules) {
			if (!module.isToggled()) {
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

	/**
	 * Finds modules by name, description, category or setting name. This powers
	 * command-side discovery today and can be reused by a future ClickGUI search box.
	 */
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

	/**
	 * Disables all active modules and persists the resulting state once. Cleanup
	 * errors are isolated per module so one broken module cannot prevent the rest
	 * of the panic shutdown from completing.
	 *
	 * @return number of modules that were active
	 */
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

		// Restore enabled states without rewriting the complete config after
		// every individual module. A module that cannot initialize is forced back
		// to disabled state so it cannot remain half-enabled for the session.
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
			// setToggled changes the boolean before invoking onDisable(), so even
			// when cleanup throws the module is already inactive. Persist that fact.
			module.settings.setSetting("enabled", false);
			System.err.println("[Agalar Hack] Module cleanup failed: " + module.getName());
			disableError.printStackTrace();
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}
}
