package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.combat.Aura;
import me.mrhakan.agalarhack.module.movement.Flight;
import me.mrhakan.agalarhack.module.movement.Jesus;
import me.mrhakan.agalarhack.module.movement.NoFall;
import me.mrhakan.agalarhack.module.movement.Speed;
import me.mrhakan.agalarhack.module.movement.Sprint;
import me.mrhakan.agalarhack.module.movement.Step;
import me.mrhakan.agalarhack.module.render.Fullbright;
import net.minecraft.client.MinecraftClient;

public class ModuleManager {

	public final ArrayList<Module> modules = new ArrayList<>();

	public ModuleManager() {
		//COMBAT
		modules.add(new Aura());
		//EXPLOIT

		//MISC

		//MOVEMENT
		modules.add(new Speed());
		modules.add(new Flight());
		modules.add(new Jesus());
		modules.add(new Sprint());
		modules.add(new Step());
		modules.add(new NoFall());
		//RENDER
		modules.add(new Fullbright());
		//WORLD
	}

	public void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		for (Module m : modules) {
			if (m.isToggled()) {
				m.onUpdate();
			}
		}
	}

	public Module getModule(String name) {
		for (Module m : modules) {
			if (m.getName().equalsIgnoreCase(name)) {
				return m;
			}
		}
		return null;
	}

	public ArrayList<Module> getModuleList() {
		return modules;
	}

	public static List<Module> getModulesByCategory(Category c) {
		List<Module> result = new ArrayList<>();
		for (Module m : AgalarHackClient.moduleManager.modules) {
			if (m.getCategory() == c) {
				result.add(m);
			}
		}
		return result;
	}

	public void loadModules() {
		AgalarHackClient.SETTINGS_MANAGER.loadSettings();

		for (Module m : modules) {
			if (Boolean.TRUE.equals(m.settings.getSetting("enabled")) && !m.isToggled()) {
				m.toggle();
			}
		}
	}
}
