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
		settings.addNumberSetting("keyModifiers", 0, 0, 15, "Keyboard modifier mask for the captured binding");
        settings.addBooleanSetting("showInHud", true, "Include this module in the enabled-module HUD");
        settings.addBooleanSetting("favorite", false, "Pin in the module browser");
        settings.addNumberSetting("lastUsed", 0, 0, 9007199254740991d, "Last module toggle time");
		selfSettings();
	}

	protected final <T> T service(Class<T> type) {
		return me.mrhakan.agalarhack.services.ClientServices.require(type);
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	public void onUpdate() {
	}

	/** Called after the play connection closes, including before menu-only ticks. */
    public void onDisconnect() {
        if (!runsWithoutWorld()) onDisable();
    }

    /** Preserve enabled preference while rebuilding world-scoped state. */
    public void onWorldChanged(boolean ready) {
        onDisable();
        if (ready) onEnable();
    }

	/** Override for modules such as AutoReconnect that must tick without a loaded world. */
	public boolean runsWithoutWorld() {
		return false;
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

	public void setToggled(boolean enabled, boolean persist) {
		if (toggled == enabled) {
			return;
		}

        if (persist) settings.setSetting("lastUsed", (double)System.currentTimeMillis());
		toggled = enabled;
		settings.setSetting("enabled", toggled);
        try {
            onToggle();
            if (toggled) onEnable(); else onDisable();
        } catch (RuntimeException failure) {
            toggled = false;
            settings.setSetting("enabled", false);
            try { onDisable(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            AgalarHackClient.LOGGER.error("Module lifecycle failed: {}", name, failure);
            me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.NotificationService.class)
                    .ifPresent(service -> service.publish(me.mrhakan.agalarhack.services.NotificationService.Type.ERROR, name + " disabled after an error"));
        }
        if (persist) {
            Module notifications = AgalarHackClient.moduleManager.getModule("Notifications");
            if (notifications != null && notifications.getBooleanSetting("moduleToggles", true)) {
                me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.NotificationService.class)
                        .ifPresent(service -> service.publish(me.mrhakan.agalarhack.services.NotificationService.Type.INFO,
                                name + (toggled ? " enabled" : " disabled")));
            }
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

    public me.mrhakan.agalarhack.input.KeyChord getChord() {
        return me.mrhakan.agalarhack.input.KeyChord.parse(settings.getSetting("keybind"), (int)getNumberSetting("keyModifiers",0));
    }
    public int getKey() { return getChord().key(); }
    public String getBindLabel() {
        var chord = getChord();
        if (chord.key() == -1) return "Unbound";
        String prefix = ((chord.modifiers() & 2) != 0 ? "Ctrl+" : "") + ((chord.modifiers() & 1) != 0 ? "Shift+" : "")
                + ((chord.modifiers() & 4) != 0 ? "Alt+" : "") + ((chord.modifiers() & 8) != 0 ? "Super+" : "");
        return prefix + (chord.mouse() ? "Mouse " + (chord.mouseButton()+1)
                : InputConstants.Type.KEYSYM.getOrCreate(chord.key()).getDisplayName().getString());
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
