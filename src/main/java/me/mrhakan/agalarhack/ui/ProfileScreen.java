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
    private int feedbackColor = ClientUiTheme.MUTED;

    public ProfileScreen(Screen parent) { super(Component.literal("Profiles")); this.parent = parent; }

    @Override
    public void init() {
        super.init();
        int center = width / 2;
        int inputWidth = Math.max(110, Math.min(260, width - 80));
        nameBox = new EditBox(font, center - inputWidth / 2, 44, inputWidth, 20, Component.literal("Source/profile name"));
        nameBox.setValue(AgalarHackClient.PROFILES.getActiveProfile());
        addRenderableWidget(nameBox);
        targetBox = new EditBox(font, center - inputWidth / 2, 70, inputWidth, 20, Component.literal("New/target name"));
        addRenderableWidget(targetBox);
        int buttonWidth = 68, gap = 4, rowWidth = buttonWidth * 4 + gap * 3, x = center - rowWidth / 2;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> action("save")).bounds(x, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Load"), b -> action("load")).bounds(x + 72, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> action("delete")).bounds(x + 144, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bind"), b -> action("bind")).bounds(x + 216, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Duplicate"), b -> action("duplicate")).bounds(x, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> action("rename")).bounds(x + 72, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> action("export")).bounds(x + 144, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> action("import")).bounds(x + 216, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unbind current server"), b -> action("unbind")).bounds(center - 85, 154, 170, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(center - 40, height - 28, 80, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ClientUiTheme.backdrop(graphics, width, height);
        int panelWidth = Math.min(350, width - 24);
        ClientUiTheme.panel(graphics, (width - panelWidth) / 2, 30, panelWidth, Math.min(220, height - 68), false);
    }

    private void action(String action) {
        String name = nameBox.getValue().trim(), target = targetBox.getValue().trim();
        try {
            switch (action) {
                case "save" -> { AgalarHackClient.PROFILES.save(name); feedback = "Saved profile: " + name; }
                case "load" -> { AgalarHackClient.PROFILES.load(name); feedback = "Loaded profile: " + name; }
                case "delete" -> { boolean deleted = AgalarHackClient.PROFILES.delete(name); feedback = deleted ? "Deleted profile: " + name : "Profile not found: " + name; }
                case "bind" -> { AgalarHackClient.PROFILES.bindCurrentServer(minecraft, name); feedback = "Bound current server to: " + name; }
                case "unbind" -> { boolean removed = AgalarHackClient.PROFILES.unbindCurrentServer(minecraft); feedback = removed ? "Removed current server binding" : "Current server has no binding"; }
                case "duplicate" -> { requireTarget(target); AgalarHackClient.PROFILES.duplicate(name, target); feedback = "Duplicated " + name + " -> " + target; }
                case "rename" -> { requireTarget(target); AgalarHackClient.PROFILES.rename(name, target); nameBox.setValue(target); feedback = "Renamed " + name + " -> " + target; }
                case "export" -> { minecraft.keyboardHandler.setClipboard(AgalarHackClient.PROFILES.exportJson(name)); feedback = "Exported profile JSON to clipboard: " + name; }
                case "import" -> { AgalarHackClient.PROFILES.importJson(name, minecraft.keyboardHandler.getClipboard()); feedback = "Imported clipboard JSON as: " + name; }
                default -> feedback = "Unknown action";
            }
            feedbackColor = ClientUiTheme.SUCCESS;
        } catch (RuntimeException e) {
            feedback = e.getMessage(); feedbackColor = ClientUiTheme.DANGER;
        }
    }

    private void requireTarget(String target) { if (target.isBlank()) throw new IllegalArgumentException("Enter a new/target profile name in the second field."); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "PROFILES & PER-SERVER CONFIG", width / 2, 12, ClientUiTheme.TEXT);
        String bound = AgalarHackClient.PROFILES.getBoundProfile(minecraft);
        graphics.centeredText(font, "Current server: " + (bound == null ? "no bound profile" : bound), width / 2, 184, ClientUiTheme.MUTED);
        List<String> profiles = AgalarHackClient.PROFILES.list();
        graphics.centeredText(font, truncate("Profiles: " + (profiles.isEmpty() ? "none" : String.join(", ", profiles)), Math.max(120, width - 40)), width / 2, 202, ClientUiTheme.TEXT);
        graphics.centeredText(font, "Clipboard export/import • second field = duplicate/rename target", width / 2, 220, ClientUiTheme.MUTED);
        if (!feedback.isBlank()) graphics.centeredText(font, truncate(feedback, Math.max(120, width - 40)), width / 2, 238, feedbackColor);
    }

    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String suffix = "…"; StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) { if (font.width(out.toString() + text.charAt(i) + suffix) > maxWidth) break; out.append(text.charAt(i)); }
        return out + suffix;
    }
}
