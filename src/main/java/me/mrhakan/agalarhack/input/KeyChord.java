package me.mrhakan.agalarhack.input;

/** Backward-compatible numeric key storage; mouse buttons occupy -100 through -107. */
public record KeyChord(int key, int modifiers) {
    public KeyChord {
        if (!valid(key)) key = -1;
        modifiers = key == -1 ? 0 : modifiers & 15;
    }
    public static boolean valid(int key) { return key == -1 || key >= 32 && key <= 348 || key <= -100 && key >= -107; }
    public boolean mouse() { return key <= -100 && key >= -107; }
    public int mouseButton() { return -100 - key; }
    public static KeyChord parse(Object raw, int modifiers) {
        try {
            double value = Double.parseDouble(String.valueOf(raw));
            return Double.isFinite(value) && value == Math.rint(value) && value >= -107 && value <= 348
                    ? new KeyChord((int)value,modifiers) : new KeyChord(-1,0);
        } catch (NumberFormatException ignored) { return new KeyChord(-1,0); }
    }
    public boolean matchesModifiers(int down) { return modifiers == (down & 15); }

    /**
     * Resolves a key name typed by the player into a GLFW code.
     *
     * <p>Accepts both bare names ("g", "f7") and Minecraft's own identifiers
     * ("key.keyboard.g"), because the player sees the short form in the GUI and the long form in
     * their options file.
     *
     * @return the code, or -1 when the name is not a keyboard key
     */
    public static int keyFromName(String name) {
        if (name == null || name.isBlank()) return -1;
        String trimmed = name.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '.');
        String id = trimmed.startsWith("key.") ? trimmed : "key.keyboard." + trimmed;
        try {
            var key = com.mojang.blaze3d.platform.InputConstants.getKey(id);
            if (key.getType() != com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) return -1;
            int value = key.getValue();
            return valid(value) && value != -1 ? value : -1;
        } catch (RuntimeException unknown) {
            return -1;
        }
    }

    /** Human-readable label for a stored key, matching what the bind UI shows. */
    public static String nameOf(int key, boolean mouse) {
        if (mouse) return "Mouse " + (key <= -100 ? -100 - key + 1 : key + 1);
        if (key == -1) return "Unbound";
        return com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }
}
