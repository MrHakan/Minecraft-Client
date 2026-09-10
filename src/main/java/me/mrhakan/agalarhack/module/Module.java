package me.mrhakan.agalarhack.module;

import com.mojang.blaze3d.platform.InputConstants;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.managers.Settings;
import net.minecraft.client.Minecraft;

public class Module {
	protected final Minecraft mc = Minecraft.getInstance();

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
		settings.addSetting("keybind", String.valueOf(InputConstants.UNKNOWN.getValue()));
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
		setToggled(!toggled, true);
	}

	public void setToggled(boolean enabled) {
		setToggled(enabled, true);
	}

	/**
	 * Sets the module state explicitly.
	 *
	 * @param enabled desired state
	 * @param persist when false, callers can batch several state changes and
	 *                persist the config once afterwards
	 */
	public void setToggled(boolean enabled, boolean persist) {
		if (toggled == enabled) {
			return;
		}

		toggled = enabled;
		settings.setSetting("enabled", toggled);
		onToggle();
		if (toggled) {
			onEnable();
		} else {
			onDisable();
		}

		if (persist) {
			AgalarHackClient.SETTINGS_MANAGER.updateSettings();
		}
	}

	public void setSettings(Settings newSettings) {
		settings = newSettings;
	}

	protected void addBooleanSetting(String name, boolean defaultValue, String description) {
		settings.addBooleanSetting(name, defaultValue, description);
	}

	protected void addNumberSetting(String name, double defaultValue, double min, double max, String description) {
		settings.addNumberSetting(name, defaultValue, min, max, description);
	}

	protected void addChoiceSetting(String name, String defaultValue, String description, String... choices) {
		settings.addChoiceSetting(name, defaultValue, description, choices);
	}

	public int getKey() {
		Object key = settings.getSetting("keybind");
		if (key == null) {
			return InputConstants.UNKNOWN.getValue();
		}
		try {
			return (int) Double.parseDouble(key.toString());
		} catch (NumberFormatException e) {
			return InputConstants.UNKNOWN.getValue();
		}
	}

	public double getNumberSetting(String settingName, double defaultValue) {
		Object value = settings.getSetting(settingName);
		if (value instanceof Number) {
			double parsed = ((Number) value).doubleValue();
			return Double.isFinite(parsed) ? parsed : defaultValue;
		}
		if (value != null) {
			try {
				double parsed = Double.parseDouble(value.toString());
				return Double.isFinite(parsed) ? parsed : defaultValue;
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
			String raw = value.toString();
			if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on")) {
				return true;
			}
			if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off")) {
				return false;
			}
		}
		return defaultValue;
	}

	public String getStringSetting(String settingName, String defaultValue) {
		Object value = settings.getSetting(settingName);
		return value == null ? defaultValue : value.toString();
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
