package me.mrhakan.agalarhack.ui;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Saves/loads named snapshots and binds them to the current multiplayer server. */
public class ProfileScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;
    private String feedback = "";
    private int feedbackColor = 0xFFAAAAAA;

    public ProfileScreen(Screen parent) {
        super(Component.literal("Profiles"));
        this.parent = parent;
    }

    @Override
    public void init() {
        super.init();
        int center = width / 2;
        int inputWidth = Math.max(120, Math.min(260, width - 40));
        nameBox = new EditBox(font, center - inputWidth / 2, 34, inputWidth, 20, Component.literal("Profile name"));
        nameBox.setValue(AgalarHackClient.PROFILES.getActiveProfile());
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> action("save"))
                .bounds(center - 154, 66, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Load"), b -> action("load"))
                .bounds(center - 78, 66, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> action("delete"))
                .bounds(center - 2, 66, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bind server"), b -> action("bind"))
                .bounds(center + 74, 66, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Unbind current server"), b -> action("unbind"))
                .bounds(center - 85, 94, 170, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(center - 40, height - 28, 80, 20).build());
    }

    private void action(String action) {
        String name = nameBox.getValue().trim();
        try {
            switch (action) {
                case "save" -> {
                    AgalarHackClient.PROFILES.save(name);
                    feedback = "Saved profile: " + name;
                }
                case "load" -> {
                    AgalarHackClient.PROFILES.load(name);
                    feedback = "Loaded profile: " + name;
                }
                case "delete" -> {
                    boolean deleted = AgalarHackClient.PROFILES.delete(name);
                    feedback = deleted ? "Deleted profile: " + name : "Profile not found: " + name;
                }
                case "bind" -> {
                    AgalarHackClient.PROFILES.bindCurrentServer(minecraft, name);
                    feedback = "Bound current server to: " + name;
                }
                case "unbind" -> {
                    boolean removed = AgalarHackClient.PROFILES.unbindCurrentServer(minecraft);
                    feedback = removed ? "Removed current server binding" : "Current server has no binding";
                }
                default -> feedback = "Unknown action";
            }
            feedbackColor = 0xFF55FF55;
        } catch (RuntimeException e) {
            feedback = e.getMessage();
            feedbackColor = 0xFFFF5555;
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "Profiles & Per-Server Config", width / 2, 10, 0xFFFFFFFF);
        String bound = AgalarHackClient.PROFILES.getBoundProfile(minecraft);
        graphics.centeredText(font, "Current server profile: " + (bound == null ? "none" : bound),
                width / 2, 120, 0xFFAAAAAA);

        List<String> profiles = AgalarHackClient.PROFILES.list();
        graphics.centeredText(font, "Profiles: " + (profiles.isEmpty() ? "none" : String.join(", ", profiles)),
                width / 2, 140, 0xFFDDDDDD);
        if (!feedback.isBlank()) {
            graphics.centeredText(font, feedback, width / 2, 162, feedbackColor);
        }
    }
}
