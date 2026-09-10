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
}
