package me.mrhakan.agalarhack.module;

import org.lwjgl.glfw.GLFW;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.Settings;
import net.minecraft.client.MinecraftClient;

public class Module {
	protected final MinecraftClient mc = MinecraftClient.getInstance();

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
		settings.addSetting("keybind", String.valueOf(GLFW.GLFW_KEY_UNKNOWN));
		selfSettings();
	}

	public void onEnable() {
	}

	public void onDisable() {
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
		AgalarHackClient.SETTINGS_MANAGER.updateSettings();
	}

	public void setSettings(Settings newSettings) {
		settings = newSettings;
	}

	public int getKey() {
		Object key = settings.getSetting("keybind");
		if (key == null) {
			return GLFW.GLFW_KEY_UNKNOWN;
		}
		try {
			return (int) Double.parseDouble(key.toString());
		} catch (NumberFormatException e) {
			return GLFW.GLFW_KEY_UNKNOWN;
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
}
