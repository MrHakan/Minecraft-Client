package me.mrhakan.agalarhack.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime setting values plus lightweight metadata used by commands and future UI.
 * Metadata is transient so the on-disk config remains backward compatible with the
 * existing { module: { settings: ... } } format.
 */
public class Settings {
    public enum SettingType {
        BOOLEAN,
        NUMBER,
        CHOICE,
        STRING
    }

    public static final class SettingSpec {
        private final String name;
        private final SettingType type;
        private final Object defaultValue;
        private final String description;
        private final Double min;
        private final Double max;
        private final List<String> choices;

        private SettingSpec(String name, SettingType type, Object defaultValue, String description,
                Double min, Double max, List<String> choices) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            this.description = description == null ? "" : description;
            this.min = min;
            this.max = max;
            this.choices = choices == null ? List.of() : List.copyOf(choices);
        }

        public String getName() {
            return name;
        }

        public SettingType getType() {
            return type;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public String getDescription() {
            return description;
        }

        public Double getMin() { return min; }
        public Double getMax() { return max; }

        public List<String> getChoices() {
            return choices;
        }

        public String getConstraintText() {
            return switch (type) {
                case BOOLEAN -> "true/false or on/off";
                case NUMBER -> {
                    if (min != null && max != null) {
                        yield formatNumber(min) + ".." + formatNumber(max);
                    }
                    yield "number";
                }
                case CHOICE -> String.join("|", choices);
                case STRING -> "text";
            };
        }

        private Object parseStrict(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("Value cannot be empty.");
            }

            return switch (type) {
                case BOOLEAN -> parseBoolean(raw);
                case NUMBER -> parseNumberStrict(raw);
                case CHOICE -> parseChoice(raw);
                case STRING -> raw;
            };
        }

        private Object normalizeLoaded(Object value) {
            if (value == null) {
                return defaultValue;
            }

            try {
                return switch (type) {
                    case BOOLEAN -> value instanceof Boolean ? value : parseBoolean(value.toString());
                    case NUMBER -> normalizeNumber(value);
                    case CHOICE -> parseChoice(value.toString());
                    case STRING -> value.toString();
                };
            } catch (IllegalArgumentException ignored) {
                return defaultValue;
            }
        }

        private Boolean parseBoolean(String raw) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "true", "on" -> true;
                case "false", "off" -> false;
                default -> throw new IllegalArgumentException(
                        "Expected true/false or on/off for " + name + ".");
            };
        }

        private Double parseNumberStrict(String raw) {
            final double parsed;
            try {
                parsed = Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a number for " + name + ".");
            }
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException("Expected a finite number for " + name + ".");
            }
            if (min != null && parsed < min) {
                throw new IllegalArgumentException(name + " must be at least " + formatNumber(min) + ".");
            }
            if (max != null && parsed > max) {
                throw new IllegalArgumentException(name + " must be at most " + formatNumber(max) + ".");
            }
            return parsed;
        }

        private Double normalizeNumber(Object value) {
            final double parsed;
            if (value instanceof Number number) {
                parsed = number.doubleValue();
            } else {
                try {
                    parsed = Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    return (Double) defaultValue;
                }
            }
            if (!Double.isFinite(parsed)) {
                return (Double) defaultValue;
            }
            double normalized = parsed;
            if (min != null) {
                normalized = Math.max(min, normalized);
            }
            if (max != null) {
                normalized = Math.min(max, normalized);
            }
            return normalized;
        }

        private String parseChoice(String raw) {
            for (String choice : choices) {
                if (choice.equalsIgnoreCase(raw.trim())) {
                    return choice;
                }
            }
            throw new IllegalArgumentException(
                    "Expected one of " + String.join(", ", choices) + " for " + name + ".");
        }

        private static String formatNumber(double value) {
            if (value == Math.rint(value)) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
    }

    public Map<String, Object> settings = new LinkedHashMap<>();
    private transient Map<String, SettingSpec> specs = new LinkedHashMap<>();

    public Object addSetting(String settingName, Object defaultValue) {
        SettingType type;
        if (defaultValue instanceof Boolean) {
            type = SettingType.BOOLEAN;
        } else if (defaultValue instanceof Number) {
            type = SettingType.NUMBER;
            defaultValue = ((Number) defaultValue).doubleValue();
        } else {
            type = SettingType.STRING;
        }
        registerSpec(new SettingSpec(settingName, type, defaultValue, "", null, null, List.of()));
        return settings.putIfAbsent(settingName, defaultValue);
    }

    public Object addBooleanSetting(String settingName, boolean defaultValue, String description) {
        registerSpec(new SettingSpec(settingName, SettingType.BOOLEAN, defaultValue, description,
                null, null, List.of()));
        return settings.putIfAbsent(settingName, defaultValue);
    }

    public Object addNumberSetting(String settingName, double defaultValue, double min, double max, String description) {
        if (!Double.isFinite(defaultValue) || !Double.isFinite(min) || !Double.isFinite(max) || min > max
                || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Invalid numeric setting specification for " + settingName);
        }
        registerSpec(new SettingSpec(settingName, SettingType.NUMBER, defaultValue, description,
                min, max, List.of()));
        return settings.putIfAbsent(settingName, defaultValue);
    }

    public Object addChoiceSetting(String settingName, String defaultValue, String description, String... choices) {
        List<String> allowed = new ArrayList<>();
        if (choices != null) {
            for (String choice : choices) {
                if (choice != null && !choice.isBlank()) {
                    allowed.add(choice);
                }
            }
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("Choice setting needs at least one option: " + settingName);
        }
        boolean defaultAllowed = allowed.stream().anyMatch(value -> value.equalsIgnoreCase(defaultValue));
        if (!defaultAllowed) {
            throw new IllegalArgumentException("Default choice is not allowed for " + settingName);
        }
        String canonicalDefault = allowed.stream()
                .filter(value -> value.equalsIgnoreCase(defaultValue))
                .findFirst()
                .orElse(defaultValue);
        registerSpec(new SettingSpec(settingName, SettingType.CHOICE, canonicalDefault, description,
                null, null, allowed));
        return settings.putIfAbsent(settingName, canonicalDefault);
    }

    public void setSetting(String settingName, Object newValue) {
        settings.put(settingName, newValue);
    }

    public Object getSetting(String settingName) {
        return settings.get(settingName);
    }

    public String getKeyIgnoreCase(String settingName) {
        for (String key : settings.keySet()) {
            if (key.equalsIgnoreCase(settingName)) {
                return key;
            }
        }
        return null;
    }

    public SettingSpec getSpecIgnoreCase(String settingName) {
        for (SettingSpec spec : specMap().values()) {
            if (spec.getName().equalsIgnoreCase(settingName)) {
                return spec;
            }
        }
        return null;
    }

    public Collection<SettingSpec> getSpecs() {
        return Collections.unmodifiableCollection(specMap().values());
    }

    public Object parseSettingValue(String settingName, String rawValue) {
        SettingSpec spec = getSpecIgnoreCase(settingName);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown setting: " + settingName);
        }
        return spec.parseStrict(rawValue);
    }

    /**
     * Normalizes values loaded from older or manually edited configs. Invalid
     * values fall back to defaults; numeric values are clamped to the declared
     * bounds. This prevents a stale config from injecting NaN/Infinity or wildly
     * unsafe movement values into a module.
     */
    public void sanitizeLoadedValues() {
        for (SettingSpec spec : specMap().values()) {
            Object current = settings.get(spec.getName());
            settings.put(spec.getName(), spec.normalizeLoaded(current));
        }
    }

    private void registerSpec(SettingSpec spec) {
        specMap().putIfAbsent(spec.getName(), spec);
    }

    private Map<String, SettingSpec> specMap() {
        if (specs == null) {
            specs = new LinkedHashMap<>();
        }
        return specs;
    }
}
