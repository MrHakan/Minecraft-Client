package me.mrhakan.agalarhack.ui;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Skips overlay geometry that is outside the camera's view volume.
 *
 * <p>Uses the frustum the game has already built for this frame rather than deriving one. That is
 * both cheaper and safer: it is the same test that decided whether the terrain itself is drawn, so an
 * overlay it rejects sits somewhere the player could not have seen it anyway. This matters because
 * the ESP overlays draw through walls — occlusion culling would hide things the player is relying on
 * seeing, but view-volume culling cannot, since the geometry is off screen either way.
 *
 * <p>Boxes must be given in <em>world</em> coordinates, before the camera offset is applied, because
 * that is the space the game prepared the frustum in. Passing a camera-relative box would test the
 * wrong place — and quietly, since the result is still a plausible-looking boolean.
 *
 * <p>Deliberately not applied to tracers, beams or breadcrumb trails: those are lines whose far end
 * is meant to be off screen while the line itself crosses it, so testing an endpoint would erase
 * exactly the geometry that carries the information.
 */
public final class ViewCulling {
    /** Used when the game has no frustum to lend; nothing is culled rather than everything. */
    private static final ViewCulling PASS_THROUGH = new ViewCulling(null);

    private final Frustum frustum;

    private ViewCulling(Frustum frustum) {
        this.frustum = frustum;
    }

    public static ViewCulling of(LevelRenderContext context) {
        var camera = context == null ? null : context.levelState().cameraRenderState;
        return camera == null || camera.cullFrustum == null ? PASS_THROUGH : new ViewCulling(camera.cullFrustum);
    }

    /** @param worldBox in world coordinates, not camera-relative */
    public boolean isVisible(AABB worldBox) {
        return frustum == null || frustum.isVisible(worldBox);
    }

    /** The unit cube at a block position, which is what every block marker occupies. */
    public boolean isVisible(BlockPos pos) {
        if (frustum == null) return true;
        return frustum.isVisible(new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
    }

    /** True when no frustum was available and this is letting everything through. */
    public boolean isPassThrough() {
        return frustum == null;
    }
}
