package me.mrhakan.agalarhack.ui.components;

import java.util.List;
import me.mrhakan.agalarhack.ui.ClientUiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asks before something irreversible happens.
 *
 * <p>Deleting a profile, clearing every waypoint and resetting the whole HUD layout all happened on
 * a single click, next to buttons that do something harmless. None of them can be undone, and the
 * profile and waypoint stores are the only copy of work a player put real time into.
 *
 * <p>Cancel is the default: it is focused, it is what Escape does, and it is the left-hand button, so
 * the reflex of pressing Enter or clicking through does not confirm. The destructive button says what
 * it will destroy rather than "OK", because a dialog that only says "Are you sure?" trains people to
 * click yes without reading.
 */
public final class ConfirmScreen extends Screen implements me.mrhakan.agalarhack.ui.ClientScreen {
    @Override public Screen parentScreen() { return parent; }

    private final Screen parent;
    private final List<String> detail;
    private final String confirmLabel;
    private final Runnable onConfirm;

    /**
     * @param detail lines explaining exactly what will be lost; keep them concrete
     * @param confirmLabel the destructive button's text, which should name the action
     */
    public ConfirmScreen(Screen parent, String title, List<String> detail, String confirmLabel, Runnable onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.detail = List.copyOf(detail);
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    public void init() {
        int center = width / 2;
        int y = Math.min(height - 40, 60 + detail.size() * 14 + 16);
        Button cancel = Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(center - 104, y, 100, 20).build();
        addRenderableWidget(cancel);
        addRenderableWidget(Button.builder(Component.literal(confirmLabel), button -> {
            // Close first: the action may open a screen of its own, and reopening the parent
            // afterwards would put this dialog's parent on top of it.
            Screen back = parent;
            minecraft.gui.setScreen(back);
            onConfirm.run();
        }).bounds(center + 4, y, 100, 20).build());
        // Focused so Enter cancels rather than confirms.
        setInitialFocus(cancel);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ClientUiTheme.backdrop(graphics, width, height);
        int panelWidth = Math.min(320, width - 24);
        ClientUiTheme.panel(graphics, (width - panelWidth) / 2, 34, panelWidth,
                Math.min(height - 60, 46 + detail.size() * 14 + 24), true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, getTitle(), width / 2, 44, ClientUiTheme.TEXT);
        for (int index = 0; index < detail.size(); index++) {
            graphics.centeredText(font, Component.literal(detail.get(index)), width / 2, 62 + index * 14,
                    ClientUiTheme.MUTED);
        }
    }
}
