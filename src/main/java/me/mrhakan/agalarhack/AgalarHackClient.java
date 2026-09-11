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
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
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
    private static boolean capturedScreenInput;
    public static final me.mrhakan.agalarhack.ui.state.UiSession<Screen> UI_SESSION =
            new me.mrhakan.agalarhack.ui.state.UiSession<>(
                    screen -> screen instanceof me.mrhakan.agalarhack.ui.ClientScreen client ? client.parentScreen() : null,
                    screen -> { if (screen instanceof me.mrhakan.agalarhack.ui.ClientScreen client) client.abandoned(); });

    /** The GUI binding is vanilla-configurable and does not require an exact modifier mask. */
    public static boolean conflictsWithGuiKey(me.mrhakan.agalarhack.input.KeyChord chord) {
        if (clickGuiKey == null || chord.key() == -1) return false;
        var key = KeyMappingHelper.getBoundKeyOf(clickGuiKey);
        if (key.getType() == InputConstants.Type.MOUSE) return chord.mouse() && chord.mouseButton() == key.getValue();
        return key.getType() == InputConstants.Type.KEYSYM && !chord.mouse() && chord.key() == key.getValue();
    }

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
        var hudRegistry=services.register(me.mrhakan.agalarhack.ui.hud.HudRegistry.class,new me.mrhakan.agalarhack.ui.hud.HudRegistry(HUD_LAYOUT));
        var notificationHud = new me.mrhakan.agalarhack.ui.NotificationHud(notifications, moduleManager);
        hudRegistry.register(new me.mrhakan.agalarhack.ui.hud.HudRegistry.Component("notifications","Notifications",notificationHud::width,notificationHud::height,notificationHud::render),
                new HudLayoutManager.WidgetState(HudLayoutManager.Anchor.BOTTOM_RIGHT,8,8,true));
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
        var themes=services.register(me.mrhakan.agalarhack.services.ThemeService.class,new me.mrhakan.agalarhack.services.ThemeService());
        var waypoints = services.register(me.mrhakan.agalarhack.services.WaypointService.class,
                new me.mrhakan.agalarhack.services.WaypointService());
        var scanners = services.register(me.mrhakan.agalarhack.services.ScannerService.class,
                new me.mrhakan.agalarhack.services.ScannerService(Minecraft.getInstance()));
        var timings = services.register(me.mrhakan.agalarhack.services.ModuleTimings.class,
                new me.mrhakan.agalarhack.services.ModuleTimings());
        var movement = services.register(me.mrhakan.agalarhack.services.MovementStats.class,
                new me.mrhakan.agalarhack.services.MovementStats());
        var packets = services.register(me.mrhakan.agalarhack.services.PacketRates.class,
                new me.mrhakan.agalarhack.services.PacketRates());
        // Published to the network thread, which counts into it without touching the registry.
        packets.install();
        EVENTS.subscribe(ClientEvents.ClientTick.class, "packet-rates", 58,
                event -> packets.tick(System.nanoTime() / 1_000_000L));
        // A new connection counts from zero; the previous server's rate says nothing about this one.
        EVENTS.subscribe(ClientEvents.Disconnected.class, "packet-rates-disconnect", 100, event -> packets.reset());
        EVENTS.subscribe(ClientEvents.ClientTick.class, "movement-stats", 55, event -> {
            var player = Minecraft.getInstance().player;
            if (player == null) movement.reset();
            else movement.sample(player.getX(), player.getY(), player.getZ());
        });
        // A new world or a fresh connection is a different situation; smearing across it would lie.
        EVENTS.subscribe(ClientEvents.WorldChanged.class, "movement-stats-world", 100, event -> movement.reset());
        EVENTS.subscribe(ClientEvents.Disconnected.class, "movement-stats-disconnect", 100, event -> movement.reset());
        // Ahead of the module tick (priority 30 below) so a tick is counted from its own beginning.
        EVENTS.subscribe(ClientEvents.ClientTick.class, "module-timings", 60, event -> timings.beginTick());
        EVENTS.subscribe(ClientEvents.Disconnected.class, "module-timings-disconnect", 100, event -> timings.clear());
        EVENTS.subscribe(ClientEvents.ClientTick.class, "scanners", 30, event -> scanners.tick());
        EVENTS.subscribe(ClientEvents.WorldChanged.class, "scanner-world", 100, event -> scanners.reset());
        EVENTS.subscribe(ClientEvents.Disconnected.class, "scanner-disconnect", 100, event -> scanners.reset());
        themes.load();
        waypoints.load();
        FRIEND_MANAGER.load();
        HUD_LAYOUT.load();
        TARGET_POLICY.load();
        PROFILES.loadBindings();
        moduleManager.loadModules();
        var notificationModule = moduleManager.getModule("Notifications");
        notifications.setEnabled(notificationModule != null && notificationModule.isToggled());
        CommandManager.init();
        // The bus detaches a listener that throws, which would disable chat handling for every chat
        // module for the rest of the session because they share one callback. Each module runs inside
        // the guard instead, so a failure costs only the module that caused it.
        var moduleGuard = services.register(me.mrhakan.agalarhack.services.ModuleGuard.class,
                new me.mrhakan.agalarhack.services.ModuleGuard((module, failure) -> {
                    LOGGER.error("Disabling module after a callback failure: {}", module.getName(), failure);
                    moduleManager.forceDisable(module);
                    SETTINGS_MANAGER.updateSettings();
                    notifications.publish(me.mrhakan.agalarhack.services.NotificationService.Type.ERROR,
                            module.getName() + " disabled after an error");
                }));
        EVENTS.subscribe(ClientEvents.ChatReceived.class, "chat-modules", 0, event -> {
            var mentions = moduleManager.getModule("ChatMentions");
            if (mentions instanceof me.mrhakan.agalarhack.module.misc.ChatMentions chat && chat.isToggled()
                    && !event.gameMessage()) {
                moduleGuard.run(chat, () -> chat.onChatMessage(event.message()));
            }
            var accept = moduleManager.getModule("AutoAccept");
            if (accept instanceof me.mrhakan.agalarhack.module.misc.AutoAccept auto && auto.isToggled()) {
                // Game messages carry plugin request lines on most servers, so both kinds are offered;
                // the module's own rules decide, not the message kind.
                moduleGuard.run(auto, () -> auto.onChatMessage(event.message()));
            }
            var filter = moduleManager.getModule("ChatFilter");
            if (filter instanceof me.mrhakan.agalarhack.module.misc.ChatFilter chatFilter && chatFilter.isToggled()) {
                // A filter that throws must not hide the message: failing open keeps chat readable,
                // which is the safer of the two outcomes for something that decides what you see.
                boolean[] hide = { false };
                moduleGuard.run(chatFilter, () -> hide[0] = chatFilter.shouldHide(event.message(), event.gameMessage()));
                if (hide[0]) event.veto();
            }
        });

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
        EVENTS.subscribe(ClientEvents.ScreenKeyInput.class, "gui-key-capture", 100, event -> {
            if (event.screen() instanceof me.mrhakan.agalarhack.ui.KeybindCaptureScreen) capturedScreenInput = true;
        });
        EVENTS.subscribe(ClientEvents.ClientTick.class, "gui-key", 0, event -> {
            var client = event.client();

            while (clickGuiKey.consumeClick()) {
                if (client.player == null || capturedScreenInput) {
                    continue;
                }
                Screen current = client.gui.screen();
                if (current instanceof me.mrhakan.agalarhack.ui.KeybindCaptureScreen) {
                    continue;
                } else if (current instanceof me.mrhakan.agalarhack.ui.ClientScreen) {
                    UI_SESSION.transition(null);
                    client.gui.setScreen(null);
                } else if (current == null) {
                    client.gui.setScreen(new ClickGuiScreen());
                }
            }
            capturedScreenInput = false;
        });

        Hud hud = new Hud();
        EVENTS.subscribe(ClientEvents.HudRender.class, "hud", 0, event -> hud.extractRenderState(event.graphics(), event.delta()));
        EVENTS.subscribe(ClientEvents.RenderSubmit.class, "world-overlays", 0, event -> WorldOverlayRenderer.collect(event.context()));
        FabricEventBridge.register(EVENTS);
    }

}
