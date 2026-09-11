package me.mrhakan.agalarhack.config;

import com.google.gson.JsonObject;

/**
 * One reading of {@code schemaVersion}, shared by every store that has one.
 *
 * <p>Three codecs had grown their own copy, and they had already drifted: two used {@code getAsInt},
 * which truncates {@code 1.9} to 1 and accepts a value far outside the int range, while the third
 * used {@code intValueExact} and rejected both. A config file is exactly where that difference
 * matters, because the whole point of the field is to notice a file this client should not touch.
 *
 * <p>The policy in one place, so it can be argued with rather than rediscovered:
 *
 * <ul>
 *   <li>A missing version means the file predates versioning, which is the current version by
 *       definition — every file written before the field existed had today's shape.</li>
 *   <li>A version newer than this client is refused, not loaded and re-saved. The caller preserves
 *       the file instead, because writing today's shape over tomorrow's is how a downgrade silently
 *       destroys settings.</li>
 *   <li>An older version is returned rather than accepted blindly, so the caller can migrate. Nothing
 *       is migrating yet; the field exists so the first change does not have to guess.</li>
 * </ul>
 */
public final class SchemaVersions {
    private SchemaVersions() { }

    /**
     * @param what the store's name, used in the message a player sees
     * @return the file's version, or {@code current} when it has none
     * @throws IllegalArgumentException when the field is present but not a whole number
     */
    public static int read(JsonObject object, int current, String what) {
        if (object == null || !object.has("schemaVersion")) return current;
        var value = object.get("schemaVersion");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid " + what + " schema version");
        }
        try {
            // Exact rather than getAsInt: 1.9 is not version 1, and a value outside the int range is
            // not a version at all. Truncating either would let a file through that should not be.
            return value.getAsJsonPrimitive().getAsBigDecimal().intValueExact();
        } catch (ArithmeticException notWhole) {
            throw new IllegalArgumentException("Invalid " + what + " schema version", notWhole);
        }
    }

    /**
     * Reads the version and refuses anything this client does not understand.
     *
     * @throws IllegalArgumentException on a malformed or future version
     */
    public static int require(JsonObject object, int current, String what) {
        int version = read(object, current, what);
        if (version > current) {
            throw new IllegalArgumentException(capitalise(what) + " file was written by a newer version");
        }
        if (version < 1) {
            throw new IllegalArgumentException("Invalid " + what + " schema version");
        }
        return version;
    }

    private static String capitalise(String what) {
        return what == null || what.isEmpty() ? "" : Character.toUpperCase(what.charAt(0)) + what.substring(1);
    }
}
