package me.mrhakan.agalarhack.ui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mrhakan.agalarhack.managers.ModuleManager;
import me.mrhakan.agalarhack.module.Module;

/**
 * One module's world-space overlay.
 *
 * <p>An overlay has up to two parts, matching the two places the 26.2 pipeline accepts world
 * drawing: labels are submitted as name tags while the frame is being collected, and lines are
 * written into the single shared {@code RenderTypes.lines()} buffer afterwards. {@link WorldOverlays}
 * calls each part inside the module's render guard, so a failure disables that module alone.
 *
 * @param <M> the module class this overlay reads its settings and results from
 */
public abstract class WorldOverlay<M extends Module> {
    private final String moduleName;
    private final Class<M> type;

    protected WorldOverlay(String moduleName, Class<M> type) {
        this.moduleName = moduleName;
        this.type = type;
    }

    /** The registered name of the module this overlay draws for. */
    public final String moduleName() {
        return moduleName;
    }

    /** The module, when it is registered with the expected class and this overlay should draw. */
    final M resolve(ModuleManager modules) {
        Module module = modules.getModule(moduleName);
        if (!type.isInstance(module)) return null;
        M typed = type.cast(module);
        return active(typed) ? typed : null;
    }

    /** Whether to draw this frame. Most overlays draw while their module is on. */
    protected boolean active(M module) {
        return module.isToggled();
    }

    /** False for an overlay with no labels, so the frame does not guard a call that does nothing. */
    protected boolean hasLabels() {
        return false;
    }

    /** False for an overlay with no line geometry. */
    protected boolean hasLines() {
        return true;
    }

    protected void submitLabels(OverlayFrame frame, M module) {
    }

    protected void drawLines(OverlayFrame frame, M module, PoseStack.Pose pose, VertexConsumer buffer) {
    }

    final void labels(OverlayFrame frame, Module module) {
        submitLabels(frame, type.cast(module));
    }

    final void lines(OverlayFrame frame, Module module, PoseStack.Pose pose, VertexConsumer buffer) {
        drawLines(frame, type.cast(module), pose, buffer);
    }
}
