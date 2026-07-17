package me.mrhakan.agalarhack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.managers.SettingsManager;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.Hud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

@Mod(modid = Main.modid, name = Main.name, version = Main.currentvers)
public class Main {

	public static final String name = "Agalar Hack";
	public static final String modid = "agh";
	public static final String currentvers = "26.2";
	public static String prefix = ".";

	public static Minecraft mc = Minecraft.getMinecraft();

	public static ModuleManager moduleManager = new ModuleManager();
	public static final SettingsManager SETTINGS_MANAGER = new SettingsManager();
	public static Hud hud;

	@Instance
	public static Main instance;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Display.setTitle(name + " " + currentvers);
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		hud = new Hud();
		MinecraftForge.EVENT_BUS.register(hud);
		MinecraftForge.EVENT_BUS.register(this);
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		moduleManager.loadModules();
		CommandManager.init();
		MinecraftForge.EVENT_BUS.register(new CommandManager());
	}

	@SubscribeEvent
	public void onKeyPress(InputEvent.KeyInputEvent event) {
		if (!Keyboard.getEventKeyState()) {
			return;
		}
		int key = Keyboard.getEventKey();
		if (key == Keyboard.KEY_NONE) {
			return;
		}
		for (Module m : moduleManager.getModuleList()) {
			if (m.getKey() == key) {
				m.toggle();
			}
		}
	}
}
