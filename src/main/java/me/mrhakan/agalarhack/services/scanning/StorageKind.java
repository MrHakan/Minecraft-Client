package me.mrhakan.agalarhack.services.scanning;

/** Registry-id classification shared by storage scanning and its tests. */
public enum StorageKind {
    CHEST, ENDER_CHEST, BARREL, SHULKER, UTILITY, OTHER;
    public static StorageKind of(String id) {
        if (id == null) return OTHER;
        if (id.endsWith(":ender_chest")) return ENDER_CHEST;
        if (id.endsWith(":chest") || id.endsWith(":trapped_chest")) return CHEST;
        if (id.endsWith(":barrel")) return BARREL;
        if (id.endsWith(":shulker_box") || id.endsWith("_shulker_box")) return SHULKER;
        return switch (id.substring(id.indexOf(':') + 1)) {
            case "hopper", "furnace", "smoker", "blast_furnace", "dispenser", "dropper", "brewing_stand", "crafter" -> UTILITY;
            default -> OTHER;
        };
    }
}
