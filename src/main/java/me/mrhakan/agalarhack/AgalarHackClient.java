package me.mrhakan.agalarhack;

import me.mrhakan.agalarhack.events.*;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.InventoryService;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.platform.InputConstants;
import me.mrhakan.agalarhack.managers.CommandManager;
import me.mrhakan.agalarhack.managers.FriendManager;
import me.mrhakan.agalarhack.managers.HudLayoutManager;
import me.mrhakan.agalarhack.managers.KeybindManager;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.managers.ProfileManager;
import me.mrhakan.agalarhack.managers.SettingsManager;
import me.mrhakan.agalarhack.managers.TargetPolicyManager;
import me.mrhakan.agalarhack.managers.TargetTracker;
import me.mrhakan.agalarhack.managers.UtilityActionManager;
import me.mrhakan.agalarhack.ui.ClickGuiScreen;
import me.mrhakan.agalarhack.ui.Hud;
import me.mrhakan.agalarhack.ui.HudEditorScreen;
import me.mrhakan.agalarhack.ui.ModuleSettingsScreen;
import me.mrhakan.agalarhack.ui.ProfileScreen;
import me.mrhakan.agalarhack.ui.TargetPolicyScreen;
import me.mrhakan.agalarhack.ui.WorldOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class AgalarHackClient implements ClientModInitializer {

    public static final String NAME = "Agalar Hack";
    public static final String MOD_ID = "agalarhack";
    public static final String VERSION = "26.2.5";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final EventBus EVENTS = new EventBus((owner, failure) -> LOGGER.error("Event listener failed: {}", owner, failure));
    public static String prefix = ".";

    public static ModuleManager moduleManager = new ModuleManager();
    public static final SettingsManager SETTINGS_MANAGER = new SettingsManager();
    public static final FriendManager FRIEND_MANAGER = new FriendManager();
    public static final HudLayoutManager HUD_LAYOUT = new HudLayoutManager();
    public static final TargetPolicyManager TARGET_POLICY = new TargetPolicyManager();
    public static final TargetTracker TARGET_TRACKER = new TargetTracker();
    public static final ProfileManager PROFILES = new ProfileManager();
    public static final UtilityActionManager UTILITY_ACTIONS = new UtilityActionManager();

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "client"));
    private static KeyMapping clickGuiKey;

    @Override
    public void onInitializeClient() {
        var services = ClientServices.registry();
        services.register(EventBus.class, EVENTS);
        services.register(ModuleManager.class, moduleManager);
        services.register(SettingsManager.class, SETTINGS_MANAGER);
        services.register(FriendManager.class, FRIEND_MANAGER);
        services.register(HudLayoutManager.class, HUD_LAYOUT);
        services.register(TargetPolicyManager.class, TARGET_POLICY);
        services.register(TargetTracker.class, TARGET_TRACKER);
        services.register(ProfileManager.class, PROFILES);
        services.register(UtilityActionManager.class, UTILITY_ACTIONS);
        InventoryService inventory = services.register(InventoryService.class, new InventoryService(Minecraft.getInstance(), UTILITY_ACTIONS));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "inventory", 90, event -> inventory.tick());
        EVENTS.subscribe(ClientEvents.Disconnected.class, "inventory", 100, event -> inventory.reset());
        services.register(me.mrhakan.agalarhack.services.TargetService.class, new me.mrhakan.agalarhack.services.TargetService(Minecraft.getInstance(), FRIEND_MANAGER, TARGET_POLICY));
        var rotations = services.register(me.mrhakan.agalarhack.services.RotationService.class,
                new me.mrhakan.agalarhack.services.RotationService(Minecraft.getInstance()));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "rotations", 20, event -> rotations.resolve());
        EVENTS.subscribe(ClientEvents.Disconnected.class, "rotations", 100, event -> rotations.clear());
        var notifications = services.register(me.mrhakan.agalarhack.services.NotificationService.class,
                new me.mrhakan.agalarhack.services.NotificationService());
        var notificationHud = new me.mrhakan.agalarhack.ui.NotificationHud(notifications, moduleManager);
        EVENTS.subscribe(ClientEvents.HudRender.class, "notifications", -10, notificationHud::render);
        var context = services.register(me.mrhakan.agalarhack.services.ServerContextService.class,
                new me.mrhakan.agalarhack.services.ServerContextService(EVENTS));
        var input = services.register(me.mrhakan.agalarhack.services.InputStateService.class,
                new me.mrhakan.agalarhack.services.InputStateService(EVENTS));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "server-context", 300, event -> context.tick(event.client()));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "input", 200, event -> input.tick(event.client()));
        EVENTS.subscribe(ClientEvents.WorldChanged.class, "world-services", 100, event -> { inventory.reset(); rotations.clear(); });
        EVENTS.subscribe(ClientEvents.WorldChanged.class, "module-lifecycle", 0, event -> moduleManager.onWorldChanged(event.ready()));
        EVENTS.subscribe(ClientEvents.Disconnected.class, "server-context", 110, event -> context.disconnected());
        services.register(me.mrhakan.agalarhack.services.RenderService.class, new me.mrhakan.agalarhack.services.RenderService());
        FRIEND_MANAGER.load();
        HUD_LAYOUT.load();
        TARGET_POLICY.load();
        PROFILES.loadBindings();
        moduleManager.loadModules();
        CommandManager.init();

        clickGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.agalarhack.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KEY_CATEGORY));

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handleChat(message));

        EVENTS.subscribe(ClientEvents.Disconnected.class, "disconnect", 0, event -> {
            moduleManager.onDisconnect();
            PROFILES.onDisconnect();
            TARGET_TRACKER.clear();
        });

        EVENTS.subscribe(ClientEvents.ClientTick.class, "utility-actions", 100, event -> UTILITY_ACTIONS.beginTick());
        EVENTS.subscribe(ClientEvents.ClientTick.class, "profiles", 80, event -> PROFILES.tick(event.client()));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "keybinds", 60, event -> KeybindManager.tick(event.client()));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "modules", 40, event -> moduleManager.tick(event.client()));
        EVENTS.subscribe(ClientEvents.ClientTick.class, "gui-key", 0, event -> {
            var client = event.client();

            while (clickGuiKey.consumeClick()) {
                if (client.player == null) {
                    continue;
                }
                Screen current = client.gui.screen();
                if (isClientScreen(current)) {
                    client.gui.setScreen(null);
                } else {
                    client.gui.setScreen(new ClickGuiScreen());
                }
            }
        });

        Hud hud = new Hud();
        EVENTS.subscribe(ClientEvents.HudRender.class, "hud", 0, event -> hud.extractRenderState(event.graphics(), event.delta()));
        EVENTS.subscribe(ClientEvents.RenderSubmit.class, "world-overlays", 0, event -> WorldOverlayRenderer.collect(event.context()));
        FabricEventBridge.register(EVENTS);
    }

    private static boolean isClientScreen(Screen screen) {
        return screen instanceof ClickGuiScreen
                || screen instanceof ModuleSettingsScreen
                || screen instanceof HudEditorScreen
                || screen instanceof ProfileScreen
                || screen instanceof TargetPolicyScreen;
    }
}
