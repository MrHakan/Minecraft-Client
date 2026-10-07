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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final Path path;
    /**
     * Case-insensitive key to the name as it was added, in insertion order.
     *
     * <p>Keyed rather than scanned because {@link #isFriend} is asked per player per frame by ESP,
     * Nametags and TargetHUD, and per candidate per tick by targeting. The scan it replaced lower-cased
     * every stored name on every one of those calls.
     */
    private final Map<String, String> friends = new LinkedHashMap<>();

    public FriendManager() {
        this(FabricLoader.getInstance().getConfigDir().resolve("agalarhack-friends.json"));
    }

    /** @param path the friend-list file */
    public FriendManager(Path path) {
        this.path = path;
    }

    public synchronized void load() {
        friends.clear();
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<String> loaded = gson.fromJson(reader, new TypeToken<List<String>>(){}.getType());
            if (loaded != null) {
                for (String name : loaded) {
                    if (isValidName(name)) friends.putIfAbsent(key(name), name.trim());
                }
            }
        } catch (JsonSyntaxException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Friend list JSON is malformed: " + e.getMessage());
        } catch (IOException e) {
            me.mrhakan.agalarhack.AgalarHackClient.LOGGER.warn("[Agalar Hack] Failed to read friend list: " + e.getMessage());
        }
    }

    public synchronized boolean add(String name) {
        if (!isValidName(name) || friends.containsKey(key(name))) {
            return false;
        }
        friends.put(key(name), name.trim());
        save();
        me.mrhakan.agalarhack.services.ClientServices.registry().find(me.mrhakan.agalarhack.services.NotificationService.class)
                .ifPresent(service -> service.publish(me.mrhakan.agalarhack.services.NotificationService.Type.SUCCESS, "Friend added: " + name.trim()));
        return true;
    }

    public synchronized boolean remove(String name) {
        if (name == null || friends.remove(key(name)) == null) {
            return false;
        }
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
        return name != null && friends.containsKey(key(name));
    }

    public synchronized List<String> getFriends() {
        return List.copyOf(friends.values());
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
                gson.toJson(new ArrayList<>(friends.values()), writer);
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

    /** Allocation-free for an already-trimmed lower-case name, which is what most player names are. */
    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        return !trimmed.isEmpty() && trimmed.length() <= 64;
    }
}
