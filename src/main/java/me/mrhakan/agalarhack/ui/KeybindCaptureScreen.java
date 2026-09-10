package me.mrhakan.agalarhack.ui;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.input.KeyChord;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.InputStateService;
import me.mrhakan.agalarhack.services.NotificationService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class KeybindCaptureScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private final Screen parent;
    private final Module module;
    public KeybindCaptureScreen(Screen parent, Module module) {
        super(Component.literal("Bind " + module.getName())); this.parent=parent; this.module=module;
    }
    @Override public void init() {
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width/2-45,height/2+40,90,20).build());
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { save(new KeyChord(-1,0)); return true; }
        if (modifierKey(event.key())) return true;
        save(new KeyChord(event.key(),event.modifiers())); return true;
    }
    @Override public boolean keyReleased(KeyEvent event) {
        if (modifierKey(event.key())) { save(new KeyChord(event.key(),0)); return true; }
        return super.keyReleased(event);
    }
    private boolean modifierKey(int key) { return key >= 340 && key <= 347; }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event,doubleClick)) return true;
        save(new KeyChord(-100-event.button(),ClientServices.require(InputStateService.class).modifiers())); return true;
    }
    private void save(KeyChord chord) {
        module.settings.setSetting("keybind",String.valueOf(chord.key()));
        module.settings.setSetting("keyModifiers",(double)chord.modifiers());
        AgalarHackClient.SETTINGS_MANAGER.updateSettings();
        var duplicates = AgalarHackClient.moduleManager.getModuleList().stream()
                .filter(other -> other != module && chord.key() != -1 && chord.equals(other.getChord())).map(Module::getName).toList();
        if (!duplicates.isEmpty()) ClientServices.require(NotificationService.class).publish(NotificationService.Type.WARNING,
                "Shared bind: " + String.join(", ",duplicates));
        if (AgalarHackClient.conflictsWithGuiKey(chord)) {
            ClientServices.require(NotificationService.class).publish(NotificationService.Type.WARNING,
                    "This bind also opens ClickGUI. Choose another key to avoid triggering both.");
        }
        onClose();
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) { ClientUiTheme.backdrop(g,width,height); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        super.extractRenderState(g,mouseX,mouseY,delta);
        g.centeredText(font,"Bind " + module.getName(),width/2,height/2-35,ClientUiTheme.TEXT);
        g.centeredText(font,"Press a key or mouse button...",width/2,height/2-12,ClientUiTheme.ACCENT);
        g.centeredText(font,"ESC clears • Ctrl / Shift / Alt / Super supported",width/2,height/2+8,ClientUiTheme.MUTED);
    }
}
