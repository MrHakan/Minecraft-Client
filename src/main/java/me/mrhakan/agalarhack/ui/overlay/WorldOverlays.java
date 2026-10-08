package me.mrhakan.agalarhack.ui.overlay;

import java.util.List;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.services.ClientServices;
import me.mrhakan.agalarhack.services.RenderService;
import me.mrhakan.agalarhack.ui.ViewCulling;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * World-space overlays built for Minecraft 26.2's submit-node render pipeline.
 *
 * <p>Adding an overlay means writing one {@link WorldOverlay} and listing it here. The list order is
 * the draw order: labels are submitted in it, then every overlay's lines go into the one shared
 * line buffer in it.
 */
public final class WorldOverlays {
    private static final List<WorldOverlay<?>> OVERLAYS = List.of(
            new EntityOverlays.EspOverlay(),
            new BlockOverlays.StorageOverlay(),
            new BlockOverlays.BlockOverlay(),
            new PathOverlays.TrajectoryOverlay(),
            new BlockOverlays.SpawnOverlay(),
            new BlockOverlays.HoleOverlay(),
            new EntityOverlays.ProjectileOverlay(),
            new EntityOverlays.TracerOverlay(),
            new PathOverlays.BreadcrumbOverlay(),
            new EntityOverlays.NametagOverlay(),
            new EntityOverlays.ItemOverlay(),
            new PathOverlays.WaypointOverlay(),
            new PathOverlays.FreecamBodyOverlay());

    private WorldOverlays() {
    }

    /** The overlays in draw order, for tests and diagnostics. */
    public static List<WorldOverlay<?>> overlays() {
        return OVERLAYS;
    }

    public static void collect(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Resolved once per frame and handed to the deferred line pass, which runs after collection.
        Module[] active = new Module[OVERLAYS.size()];
        boolean any = false;
        for (int index = 0; index < active.length; index++) {
            active[index] = OVERLAYS.get(index).resolve(AgalarHackClient.moduleManager);
            any |= active[index] != null;
        }
        if (!any) return;

        var renderService = ClientServices.require(RenderService.class);
        // Built once per frame from the frustum the game already prepared, then shared by every
        // box-shaped overlay. Line overlays deliberately do not use it; see ViewCulling.
        OverlayFrame frame = new OverlayFrame(mc, ctx, ctx.levelState().cameraRenderState.pos, ViewCulling.of(ctx));
        for (int index = 0; index < active.length; index++) {
            WorldOverlay<?> overlay = OVERLAYS.get(index);
            Module module = active[index];
            if (module != null && overlay.hasLabels()) renderService.guard(module, () -> overlay.labels(frame, module));
        }

        var submittedLevel = mc.level;
        var submittedPlayer = mc.player;
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
            if (mc.level != submittedLevel || mc.player != submittedPlayer || mc.player == null) return;
            for (int index = 0; index < active.length; index++) {
                WorldOverlay<?> overlay = OVERLAYS.get(index);
                Module module = active[index];
                if (module != null && overlay.hasLines()) {
                    renderService.guard(module, () -> overlay.lines(frame, module, pose, buffer));
                }
            }
        });
    }
}
