package me.mrhakan.agalarhack.ui;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Full profile lifecycle UI including per-server binding and clipboard import/export. */
public class ProfileScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }
    private final Screen parent;
    private EditBox nameBox;
    private EditBox targetBox;
    private List<String> profileNames = List.of();
    private String pendingProfileSelection;
    private String sourceNameValue;
    private String targetNameValue;
    private String profileSummary = "Profiles: none";
    private String serverSummary = "Current server: no bound profile";
    private String feedback = "";
    private int feedbackColor = ClientUiTheme.MUTED;

    public ProfileScreen(Screen parent) { super(Component.literal("Profiles")); this.parent = parent; }

    @Override
    public void init() {
        super.init();
        int center = width / 2;
        int inputWidth = Math.max(110, Math.min(260, width - 80));
        String currentName = nameBox == null ? null : nameBox.getValue();
        String currentTarget = targetBox == null ? null : targetBox.getValue();
        nameBox = new EditBox(font, center - inputWidth / 2, 44, inputWidth, 20, Component.literal("Source/profile name"));
        String selected = pendingProfileSelection;
        pendingProfileSelection = null;
        String restoredName = selected != null ? selected
                : sourceNameValue != null ? sourceNameValue : currentName;
        sourceNameValue = null;
        nameBox.setValue(restoredName == null ? AgalarHackClient.PROFILES.getActiveProfile() : restoredName);
        addRenderableWidget(nameBox);
        targetBox = new EditBox(font, center - inputWidth / 2, 70, inputWidth, 20, Component.literal("New/target name"));
        String restoredTarget = targetNameValue != null ? targetNameValue : currentTarget;
        targetNameValue = null;
        targetBox.setValue(restoredTarget == null ? "" : restoredTarget);
        addRenderableWidget(targetBox);
        int buttonWidth = 68, gap = 4, rowWidth = buttonWidth * 4 + gap * 3, x = center - rowWidth / 2;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> action("save")).bounds(x, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Load"), b -> action("load")).bounds(x + 72, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> confirmDelete()).bounds(x + 144, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bind"), b -> action("bind")).bounds(x + 216, 102, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Duplicate"), b -> action("duplicate")).bounds(x, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> action("rename")).bounds(x + 72, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export"), b -> action("export")).bounds(x + 144, 126, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import"), b -> action("import")).bounds(x + 216, 126, buttonWidth, 20).build());
        int utilityWidth = 82, utilityGap = 4, unbindWidth = 170;
        int utilityX = center - (utilityWidth + utilityGap + unbindWidth) / 2;
        addRenderableWidget(Button.builder(Component.literal("Browse"), b -> chooseProfile())
                .bounds(utilityX, 154, utilityWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Unbind current server"), b -> action("unbind"))
                .bounds(utilityX + utilityWidth + utilityGap, 154, unbindWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(center - 40, height - 28, 80, 20).build());
        refreshProfileDisplay();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ClientUiTheme.backdrop(graphics, width, height);
        int panelWidth = Math.min(350, width - 24);
        ClientUiTheme.panel(graphics, (width - panelWidth) / 2, 30, panelWidth, Math.min(220, height - 68), false);
    }

    /**
     * Delete sits between Load and Bind, both harmless, and removes the only copy of a profile.
     * One misclick there is unrecoverable, so it asks first.
     */
    private void confirmDelete() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) { feedback = "Enter a profile name first."; feedbackColor = ClientUiTheme.MUTED; return; }
        if (!AgalarHackClient.PROFILES.exists(name)) {
            feedback = "Profile not found: " + name;
            feedbackColor = ClientUiTheme.MUTED;
            return;
        }
        minecraft.gui.setScreen(new me.mrhakan.agalarhack.ui.components.ConfirmScreen(this,
                "Delete profile " + name + "?",
                List.of("Its settings, HUD layout and target policy are removed.",
                        "Any server or dimension bound to it loses the binding.",
                        "This cannot be undone."),
                "Delete " + name, () -> action("delete")));
    }

    /** Opens the existing paginated, keyboard-accessible picker instead of making users retype a name. */
    private void chooseProfile() {
        refreshProfileDisplay();
        if (profileNames.isEmpty()) {
            feedback = "No saved profiles to choose.";
            feedbackColor = ClientUiTheme.MUTED;
            return;
        }
        minecraft.gui.setScreen(new me.mrhakan.agalarhack.ui.components.ChoiceScreen(this,
                "Select profile", profileNames, choice -> {
                    pendingProfileSelection = choice;
                    feedback = "Selected profile: " + choice;
                    feedbackColor = ClientUiTheme.SUCCESS;
                }));
    }

    /** File listing is refreshed on screen entry and after an action, never once per rendered frame. */
    private void refreshProfileDisplay() {
        profileNames = List.copyOf(AgalarHackClient.PROFILES.list());
        String names = profileNames.isEmpty() ? "none" : String.join(", ", profileNames);
        profileSummary = ClientUiTheme.truncate(font,
                "Profiles (" + profileNames.size() + "): " + names, Math.max(120, width - 40));
        String bound = AgalarHackClient.PROFILES.getBoundProfile(minecraft);
        serverSummary = ClientUiTheme.truncate(font,
                "Current server: " + (bound == null ? "no bound profile" : bound), Math.max(120, width - 40));
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
        refreshProfileDisplay();
    }

    private void requireTarget(String target) { if (target.isBlank()) throw new IllegalArgumentException("Enter a new/target profile name in the second field."); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void removed() {
        if (nameBox != null) {
            sourceNameValue = nameBox.getValue();
            targetNameValue = targetBox.getValue();
        }
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, "PROFILES & PER-SERVER CONFIG", width / 2, 12, ClientUiTheme.TEXT);
        graphics.centeredText(font, serverSummary, width / 2, 184, ClientUiTheme.MUTED);
        graphics.centeredText(font, profileSummary, width / 2, 202, ClientUiTheme.TEXT);
        graphics.centeredText(font, "Clipboard export/import • second field = duplicate/rename target", width / 2, 220, ClientUiTheme.MUTED);
        if (!feedback.isBlank()) graphics.centeredText(font, ClientUiTheme.truncate(font, feedback, Math.max(120, width - 40)), width / 2, 238, feedbackColor);
    }
}
