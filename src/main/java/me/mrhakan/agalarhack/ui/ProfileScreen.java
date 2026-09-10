package me.mrhakan.agalarhack.ui;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full profile lifecycle UI including per-server binding and clipboard import/export. */
public class ProfileScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox;
    private EditBox targetBox;
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
        int inputWidth = Math.max(110, Math.min(240, width - 80));

        nameBox = new EditBox(font, center - inputWidth / 2, 32, inputWidth, 20, Component.literal("Source/profile name"));
        nameBox.setValue(AgalarHackClient.PROFILES.getActiveProfile());
        addRenderableWidget(nameBox);

        targetBox = new EditBox(font, center - inputWidth / 2, 58, inputWidth, 20, Component.literal("New/target name"));
        addRenderableWidget(targetBox);

        int buttonWidth = 68;
        int gap = 4;
        int rowWidth = buttonWidth * 4 + gap * 3;
        int x = center - rowWidth / 2;

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> action("save"))
                .bounds(x, 88, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Load"), b -> action("load"))
                .bounds(x + (buttonWidth + gap), 88, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> action("delete"))
                .bounds(x + 2 * (buttonWidth + gap), 88, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bind"), b -> action("bind"))
                .bounds(x + 3 * (buttonWidth + gap), 88, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Duplicate"), b -> action("duplicate"))
                .bounds(x, 112, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> action("rename"))
                .bounds(x + (buttonWidth + gap), 112, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> action("export"))
                .bounds(x + 2 * (buttonWidth + gap), 112, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> action("import"))
                .bounds(x + 3 * (buttonWidth + gap), 112, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Unbind current server"), b -> action("unbind"))
                .bounds(center - 85, 140, 170, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(center - 40, height - 28, 80, 20).build());
    }

    private void action(String action) {
        String name = nameBox.getValue().trim();
        String target = targetBox.getValue().trim();
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
                case "duplicate" -> {
                    requireTarget(target);
                    AgalarHackClient.PROFILES.duplicate(name, target);
                    feedback = "Duplicated " + name + " -> " + target;
                }
                case "rename" -> {
                    requireTarget(target);
                    AgalarHackClient.PROFILES.rename(name, target);
                    nameBox.setValue(target);
                    feedback = "Renamed " + name + " -> " + target;
                }
                case "export" -> {
                    minecraft.keyboardHandler.setClipboard(AgalarHackClient.PROFILES.exportJson(name));
                    feedback = "Exported profile JSON to clipboard: " + name;
                }
                case "import" -> {
                    AgalarHackClient.PROFILES.importJson(name, minecraft.keyboardHandler.getClipboard());
                    feedback = "Imported clipboard JSON as: " + name;
                }
                default -> feedback = "Unknown action";
            }
            feedbackColor = 0xFF55FF55;
        } catch (RuntimeException e) {
            feedback = e.getMessage();
            feedbackColor = 0xFFFF5555;
        }
    }

    private void requireTarget(String target) {
        if (target.isBlank()) {
            throw new IllegalArgumentException("Enter a new/target profile name in the second field.");
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
        graphics.centeredText(font, "Current server: " + (bound == null ? "no bound profile" : bound),
                width / 2, 168, 0xFFAAAAAA);

        List<String> profiles = AgalarHackClient.PROFILES.list();
        String joined = profiles.isEmpty() ? "none" : String.join(", ", profiles);
        graphics.centeredText(font, truncate("Profiles: " + joined, Math.max(120, width - 24)),
                width / 2, 186, 0xFFDDDDDD);
        graphics.centeredText(font, "Export/Import use the system clipboard. Duplicate/Rename use the second field.",
                width / 2, 204, 0xFF777777);
        if (!feedback.isBlank()) {
            graphics.centeredText(font, truncate(feedback, Math.max(120, width - 24)), width / 2, 224, feedbackColor);
        }
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String candidate = out.toString() + text.charAt(i) + suffix;
            if (font.width(candidate) > maxWidth) {
                break;
            }
            out.append(text.charAt(i));
        }
        return out + suffix;
    }
}
