package me.mrhakan.agalarhack.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Local-only friend list used by combat targeting. Names are persisted separately
 * from module settings so the existing module config schema stays compatible.
 */
public class FriendManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("agalarhack-friends.json");
    private final Set<String> friends = new LinkedHashSet<>();

    public synchronized void load() {
        friends.clear();
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> loaded = gson.fromJson(reader, new TypeToken<List<String>>(){}.getType());
            if (loaded != null) {
                for (String name : loaded) {
                    if (isValidName(name) && !containsIgnoreCase(name)) {
                        friends.add(name.trim());
                    }
                }
            }
        } catch (JsonSyntaxException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Friend list JSON is malformed: " + e.getMessage());
        } catch (IOException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to read friend list: " + e.getMessage());
        }
    }

    public synchronized boolean add(String name) {
        if (!isValidName(name) || containsIgnoreCase(name)) {
            return false;
        }
        friends.add(name.trim());
        save();
        me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.NotificationService.class)
                .ifPresent(service -> service.publish(me.mrhakan.agalarhack.services.NotificationService.Type.SUCCESS, "Friend added: " + name.trim()));
        return true;
    }

    public synchronized boolean remove(String name) {
        String existing = findIgnoreCase(name);
        if (existing == null) {
            return false;
        }
        friends.remove(existing);
        save();
        return true;
    }

    public synchronized int clear() {
        int count = friends.size();
        if (count > 0) {
            friends.clear();
            save();
        }
        return count;
    }

    public synchronized boolean isFriend(String name) {
        return findIgnoreCase(name) != null;
    }

    public synchronized List<String> getFriends() {
        return List.copyOf(friends);
    }

    private void save() {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }

        Path temp = null;
        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, "agalarhack-friends-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(friends), writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to write friend list: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private boolean containsIgnoreCase(String name) {
        return findIgnoreCase(name) != null;
    }

    private String findIgnoreCase(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (String friend : friends) {
            if (friend.toLowerCase(Locale.ROOT).equals(normalized)) {
                return friend;
            }
        }
        return null;
    }

    private static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 64;
    }
}
