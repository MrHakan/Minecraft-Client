package me.mrhakan.agalarhack;

import com.mojang.blaze3d.platform.InputConstants;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.FriendManager;
import me.mrhakan.agalarhack.managers.KeybindManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.managers.SettingsManager;
import me.mrhakan.agalarhack.ui.ClickGuiScreen;
import me.mrhakan.agalarhack.ui.Hud;
import me.mrhakan.agalarhack.ui.ModuleSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class AgalarHackClient implements ClientModInitializer {

	public static final String NAME = "Agalar Hack";
	public static final String MOD_ID = "agalarhack";
	public static final String VERSION = "26.2.2";
	public static String prefix = ".";

	public static ModuleManager moduleManager = new ModuleManager();
	public static final SettingsManager SETTINGS_MANAGER = new SettingsManager();
	public static final FriendManager FRIEND_MANAGER = new FriendManager();

	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "client"));
	private static KeyMapping clickGuiKey;

	@Override
	public void onInitializeClient() {
		FRIEND_MANAGER.load();
		moduleManager.loadModules();
		CommandManager.init();

		clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.agalarhack.open_gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KEY_CATEGORY));

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handleChat(message));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KeybindManager.tick(client);
			moduleManager.tick(client);

			while (clickGuiKey.consumeClick()) {
				if (client.player == null) {
					continue;
				}
				if (client.gui.screen() instanceof ClickGuiScreen
						|| client.gui.screen() instanceof ModuleSettingsScreen) {
					client.gui.setScreen(null);
				} else {
					client.gui.setScreen(new ClickGuiScreen());
				}
			}
		});

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), new Hud());
	}
}
