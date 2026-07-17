package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.List;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.combat.*;
import me.mrhakan.agalarhack.module.movement.*;
import me.mrhakan.agalarhack.module.render.*;

public class ModuleManager {

	public ArrayList<Module> modules;

	public ModuleManager() {
		modules = new ArrayList<Module>();
		//COMBAT
		this.modules.add(new Aura());
		//EXPLOIT

		//MISC

		//MOVEMENT
		this.modules.add(new Speed());
		this.modules.add(new Flight());
		this.modules.add(new Jesus());
		this.modules.add(new Sprint());
		this.modules.add(new Step());
		this.modules.add(new NoFall());
		//RENDER
		this.modules.add(new Fullbright());
		//WORLD

	}

	public Module getModule(String name) {
		for (Module m : this.modules) {
			if (m.getName().equalsIgnoreCase(name)) {
				return m;
			}
		}
		return null;
	}

	public ArrayList<Module> getModuleList() {
		return this.modules;
	}

	public static List<Module> getModulesByCategory(Category c) {
		List<Module> modules = new ArrayList<Module>();

		for (Module m : Main.moduleManager.modules) {
			if (m.getCategory() == c)
				modules.add(m);
		}
		return modules;
	}

	public void loadModules() {
		Main.SETTINGS_MANAGER.loadSettings();

		for (Module m : Main.moduleManager.getModuleList()) {
			if (Boolean.TRUE.equals(m.settings.getSetting("enabled")) && !m.isToggled()) {
				m.toggle();
			}
		}
	}
}
