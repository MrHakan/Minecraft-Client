package me.mrhakan.agalarhack.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reach, line of sight and aiming for AutoGrind's block interactions, plus the bounded nearby-block
 * lookup they start from.
 * Moved out of {@link GrindExecutor} in 2.0.05; it reaches the executor's shared state through
 * {@code g}.
 */
final class GrindReach {
    private final GrindExecutor g;

    GrindReach(GrindExecutor g) {
        this.g = g;
    }

    BlockPos findNearbyBlock(Block block) {
        if (g.client == null || g.client.player == null || g.client.level == null) return null;
        BlockPos origin = g.client.player.blockPosition();
        Vec3 eye = g.client.player.getEyePosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dy = -GrindExecutor.SCAN_VERTICAL; dy <= GrindExecutor.SCAN_VERTICAL; dy++) {
            for (int dx = -GrindExecutor.SCAN_HORIZONTAL; dx <= GrindExecutor.SCAN_HORIZONTAL; dx++) {
                for (int dz = -GrindExecutor.SCAN_HORIZONTAL; dz <= GrindExecutor.SCAN_HORIZONTAL; dz++) {
                    int x = origin.getX() + dx, y = origin.getY() + dy, z = origin.getZ() + dz;
                    LevelChunk chunk = g.scanner.loadedChunk(x >> 4, z >> 4);
                    if (chunk == null || y < g.client.level.getMinY() || y >= g.client.level.getMaxY()) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!chunk.getBlockState(pos).is(block)) continue;
                    double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < nearestDistance) { nearestDistance = distance; nearest = pos.immutable(); }
                }
            }
        }
        return nearest;
    }

    boolean withinReach(BlockPos pos) {
        return g.client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= GrindExecutor.MAX_REACH * GrindExecutor.MAX_REACH;
    }

    BlockHitResult hitTarget(BlockPos pos) {
        Vec3 from = g.client.player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(pos);
        var hit = g.client.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, g.client.player));
        return hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos) ? blockHit : null;
    }

    /** A stable top-face hit; aiming at a support block's center intersects its upper edge. */
    BlockHitResult placementHit(BlockPos support) {
        Vec3 from = g.client.player.getEyePosition();
        Vec3 topCenter = Vec3.atBottomCenterOf(support.above());
        var sight = g.client.level.clip(new ClipContext(from, topCenter.add(0, -0.001, 0),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, g.client.player));
        return sight instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(support)
                ? new BlockHitResult(topCenter, Direction.UP, support, false) : null;
    }

    void aimAt(BlockPos pos) {
        aimAt(Vec3.atCenterOf(pos));
    }

    void aimAt(Vec3 target) {
        Vec3 delta = target.subtract(g.client.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        g.rotations.request(new RotationService.Request(GrindExecutor.OWNER, GrindExecutor.PRIORITY, RotationService.Mode.CLIENT,
                yaw, pitch, 360f, 180f, 180f, true));
    }

    boolean aimedAt(BlockPos pos) {
        return aimedAt(Vec3.atCenterOf(pos));
    }

    boolean aimedAt(Vec3 target) {
        Vec3 delta = target.subtract(g.client.player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return Math.abs(Mth.wrapDegrees(yaw - g.client.player.getYRot())) <= 2.0f
                && Math.abs(pitch - g.client.player.getXRot()) <= 2.0f;
    }
}
