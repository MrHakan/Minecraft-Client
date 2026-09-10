package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.mrhakan.agalarhack.AgalarHackClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Shared first-pass target filter used by all combat/target-aware modules.
 * Individual modules can still apply stricter range/FOV/timing rules afterwards.
 */
public class TargetPolicyManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-target-policy.json");
    private final Settings settings = new Settings();

    public TargetPolicyManager() {
        registerDefaults();
    }

    private void registerDefaults() {
        settings.addBooleanSetting("players", true, "Allow player targets globally");
        settings.addBooleanSetting("mobs", true, "Allow non-player living targets globally");
        settings.addBooleanSetting("ignoreFriends", true, "Reject players from the local friend list");
        settings.addBooleanSetting("ignoreInvisible", true, "Reject invisible targets");
        settings.addBooleanSetting("ignoreSleeping", true, "Reject sleeping living entities");
        settings.addNumberSetting("minHealth", 0.0, 0.0, 2048.0, "Reject targets below this health value");
        settings.addNumberSetting("maxHealth", 2048.0, 1.0, 2048.0, "Reject targets above this health value");
    }

    public Settings getSettings() {
        return settings;
    }

    public void load() {
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Settings loaded = gson.fromJson(reader, Settings.class);
            if (loaded != null && loaded.settings != null) {
                settings.settings.putAll(loaded.settings);
                settings.sanitizeLoadedValues();
            }
        } catch (Exception e) {
            System.err.println("[Agalar Hack] Failed to load target policy: " + e.getMessage());
        }
        save();
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                gson.toJson(settings, writer);
            }
        } catch (IOException e) {
            System.err.println("[Agalar Hack] Failed to save target policy: " + e.getMessage());
        }
    }

    public void applySnapshot(Settings snapshot) {
        registerDefaults();
        if (snapshot != null && snapshot.settings != null) {
            settings.settings.putAll(snapshot.settings);
        }
        settings.sanitizeLoadedValues();
        save();
    }

    public boolean allows(LivingEntity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null || target == mc.player || !target.isAlive()
                || target.getHealth() <= 0 || target.isSpectator()) {
            return false;
        }

        if (Boolean.TRUE.equals(settings.getSetting("ignoreInvisible")) && target.isInvisible()) {
            return false;
        }
        if (Boolean.TRUE.equals(settings.getSetting("ignoreSleeping")) && target.isSleeping()) {
            return false;
        }

        double health = target.getHealth();
        double minHealth = ((Number) settings.getSetting("minHealth")).doubleValue();
        double maxHealth = ((Number) settings.getSetting("maxHealth")).doubleValue();
        if (health < minHealth || health > maxHealth) {
            return false;
        }

        if (target instanceof Player player) {
            if (!Boolean.TRUE.equals(settings.getSetting("players"))) {
                return false;
            }
            return !Boolean.TRUE.equals(settings.getSetting("ignoreFriends"))
                    || !AgalarHackClient.FRIEND_MANAGER.isFriend(player.getName().getString());
        }

        return Boolean.TRUE.equals(settings.getSetting("mobs"));
    }
}
