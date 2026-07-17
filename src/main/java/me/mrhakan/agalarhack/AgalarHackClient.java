package me.mrhakan.agalarhack;

import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.KeybindManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.managers.SettingsManager;
import me.mrhakan.agalarhack.ui.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class AgalarHackClient implements ClientModInitializer {

	public static final String NAME = "Agalar Hack";
	public static final String MOD_ID = "agalarhack";
	public static final String VERSION = "26.2";
	public static String prefix = ".";

	public static ModuleManager moduleManager = new ModuleManager();
	public static final SettingsManager SETTINGS_MANAGER = new SettingsManager();

	@Override
	public void onInitializeClient() {
		moduleManager.loadModules();
		CommandManager.init();

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handleChat(message));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KeybindManager.tick(client);
			moduleManager.tick(client);
		});

		HudRenderCallback.EVENT.register(Hud::render);
	}
}
