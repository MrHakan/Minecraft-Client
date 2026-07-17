package me.mrhakan.agalarhack.module;

import org.lwjgl.input.Keyboard;

import me.mrhakan.agalarhack.Main;
import me.mrhakan.agalarhack.managers.Settings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Module {
	protected Minecraft mc = Minecraft.getMinecraft();

	private String name, displayName;
	private String description;
	private Category category;
	private boolean toggled;
	public Settings settings = new Settings();

	public Module(String name, Category category) {
		this(name, category, "");
	}

	public Module(String name, Category category, String description) {
		this.name = name;
		this.category = category;
		this.description = description;
		toggled = false;
	}

	public void registerSettings() {
		settings.addSetting("enabled", false);
		settings.addSetting("keybind", String.valueOf(Keyboard.KEY_NONE));
		selfSettings();
	}

	public void onEnable() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	public void onDisable() {
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void gameTickEvent(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START) {
			return;
		}
		if (!toggled || mc.player == null || mc.world == null) {
			return;
		}
		onUpdate();
	}

	public void setSettings(Settings newSettings) {
		settings = newSettings;
	}

	public void onUpdate() {
	}

	public void selfSettings() {
	}

	public void onToggle() {
	}

	public void toggle() {
		toggled = !toggled;
		onToggle();
		if (toggled) {
			onEnable();
		} else {
			onDisable();
		}
		settings.setSetting("enabled", toggled);
		Main.SETTINGS_MANAGER.updateSettings();
	}

	public int getKey() {
		Object key = settings.getSetting("keybind");
		if (key == null) {
			return Keyboard.KEY_NONE;
		}
		try {
			return (int) Double.parseDouble(key.toString());
		} catch (NumberFormatException e) {
			return Keyboard.KEY_NONE;
		}
	}

	public double getNumberSetting(String settingName, double defaultValue) {
		Object value = settings.getSetting(settingName);
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		if (value != null) {
			try {
				return Double.parseDouble(value.toString());
			} catch (NumberFormatException ignored) {
			}
		}
		return defaultValue;
	}

	public boolean getBooleanSetting(String settingName, boolean defaultValue) {
		Object value = settings.getSetting(settingName);
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value != null) {
			return Boolean.parseBoolean(value.toString());
		}
		return defaultValue;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public boolean isToggled() {
		return toggled;
	}

	public String getDisplayName() {
		return displayName == null ? name : displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public void setup() {
	}
}
