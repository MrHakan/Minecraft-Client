package me.mrhakan.agalarhack.gametest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.ui.ClickGuiScreen;
import me.mrhakan.agalarhack.ui.ColorPickerScreen;
import me.mrhakan.agalarhack.ui.HudEditorScreen;
import me.mrhakan.agalarhack.ui.KeybindCaptureScreen;
import me.mrhakan.agalarhack.ui.ModuleActionsScreen;
import me.mrhakan.agalarhack.ui.ModuleSettingsScreen;
import me.mrhakan.agalarhack.ui.ProfileScreen;
import me.mrhakan.agalarhack.ui.TargetPolicyScreen;
import me.mrhakan.agalarhack.ui.ThemeScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens and renders every screen the mod can put on the display.
 *
 * <p>The assertion is the rendering itself. A screen that throws while laying out or drawing takes
 * the client down, and the game test framework reports that as a failure, so there is nothing to
 * assert by hand beyond "the screen we asked for is the screen that is showing". Widget layout is
 * arithmetic on the real window size, which is why this cannot be a unit test: every bad width
 * calculation in this mod would have been caught here and by nothing else.
 *
 * <p>Runs against the title screen, where there is no player and no level - the case a settings
 * screen is most likely to have assumed away.
 */
public class ClientScreensGameTest implements FabricClientGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("agalarhack-gametest");
    private int opened;

    @Override
    public void runTest(ClientGameTestContext context) {
        show(context, "ClickGUI", ClickGuiScreen::new);
        show(context, "HUD editor", () -> new HudEditorScreen(null));
        show(context, "Profiles", () -> new ProfileScreen(null));
        show(context, "Target policy", () -> new TargetPolicyScreen(null));
        show(context, "Themes", () -> new ThemeScreen(null));
        show(context, "Colour picker", () -> new ColorPickerScreen(null, 0xFF3366CC, colour -> { }));

        // Every module's own screens. The settings screen builds one row per declared setting, so
        // this is the only check that every setting in the mod can actually be drawn and paged.
        for (String name : moduleNames(context)) {
            Module module = context.computeOnClient(client -> AgalarHackClient.moduleManager.getModule(name));
            show(context, name + " settings", () -> new ModuleSettingsScreen(null, module));
            show(context, name + " actions", () -> new ModuleActionsScreen(null, module));
            show(context, name + " keybind", () -> new KeybindCaptureScreen(null, module));
        }

        // A screen left open would be inherited by whatever test runs next.
        context.runOnClient(client -> client.gui.setScreen(null));
        context.waitTicks(2);
        LOGGER.info("Opened and rendered {} screens with no world loaded", opened);
    }

    private static List<String> moduleNames(ClientGameTestContext context) {
        return context.computeOnClient(client -> AgalarHackClient.moduleManager.getModuleList()
                .stream().map(Module::getName).toList());
    }

    /**
     * Two ticks is enough for the screen to be initialised and drawn at least once; a screen that is
     * going to throw throws on the first of those.
     */
    private void show(ClientGameTestContext context, String label, java.util.function.Supplier<Screen> screen) {
        Class<? extends Screen> expected = context.computeOnClient(client -> {
            Screen built = screen.get();
            client.gui.setScreen(built);
            return built.getClass();
        });
        context.waitTicks(2);
        boolean showing = context.computeOnClient(client ->
                client.gui.screen() != null && client.gui.screen().getClass() == expected);
        if (!showing) {
            throw new AssertionError(label + " did not stay open after being set as the screen");
        }
        opened++;
    }
}
