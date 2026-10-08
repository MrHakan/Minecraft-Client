package me.mrhakan.agalarhack.ui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mrhakan.agalarhack.module.render.BlockESP;
import me.mrhakan.agalarhack.module.render.HoleESP;
import me.mrhakan.agalarhack.module.render.SpawnESP;
import me.mrhakan.agalarhack.module.render.StorageESP;
import me.mrhakan.agalarhack.services.scanning.HoleDetector;
import me.mrhakan.agalarhack.services.scanning.SpawnLightRules;
import me.mrhakan.agalarhack.services.scanning.StorageKind;
import me.mrhakan.agalarhack.ui.ViewCulling;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static me.mrhakan.agalarhack.ui.overlay.OverlayDraw.*;

/** Overlays drawn on blocks: storage containers, scanned blocks, spawnable floor and holes. */
final class BlockOverlays {
    private BlockOverlays() {
    }

    static final class StorageOverlay extends WorldOverlay<StorageESP> {
        StorageOverlay() { super("StorageESP", StorageESP.class); }
        @Override protected boolean hasLabels() { return true; }
        @Override protected void submitLabels(OverlayFrame frame, StorageESP module) {
            if (module.getBooleanSetting("labels", false)) renderStorageLabels(frame.ctx(), frame.mc(), module, frame.camera());
        }
        @Override protected void drawLines(OverlayFrame frame, StorageESP module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderStorageEsp(frame.mc(), module, frame.camera(), pose, buffer, frame.culling());
            if (module.getBooleanSetting("tracers", false)) renderStorageTracers(frame.mc(), module, frame.camera(), pose, buffer);
        }
    }

    static final class BlockOverlay extends WorldOverlay<BlockESP> {
        BlockOverlay() { super("BlockESP", BlockESP.class); }
        @Override protected void drawLines(OverlayFrame frame, BlockESP module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderBlockEsp(frame.mc(), module, frame.camera(), pose, buffer, frame.culling());
        }
    }

    static final class SpawnOverlay extends WorldOverlay<SpawnESP> {
        SpawnOverlay() { super("SpawnESP", SpawnESP.class); }
        @Override protected void drawLines(OverlayFrame frame, SpawnESP module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderSpawns(module, frame.camera(), pose, buffer, frame.culling());
        }
    }

    static final class HoleOverlay extends WorldOverlay<HoleESP> {
        HoleOverlay() { super("HoleESP", HoleESP.class); }
        @Override protected void drawLines(OverlayFrame frame, HoleESP module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderHoles(module, frame.camera(), pose, buffer, frame.culling());
        }
    }

    static void renderStorageEsp(Minecraft mc, StorageESP module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, ViewCulling culling) {
        // Settings are fixed for the frame; read once rather than per marker.
        double range = module.getNumberSetting("range", 64.0);
        double alphaSetting = module.getNumberSetting("alpha", 220.0);
        boolean distanceFade = module.getBooleanSetting("distanceFade", true);
        for (BlockPos pos : module.getCachedPositions()) {
            if (!culling.isVisible(pos)) continue;
            double distance = blockDistance(mc, pos);
            if (distance > range || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            String id = blockId(mc, pos);
            if (!module.matches(id)) continue;
            int alpha = fadedAlpha(alphaSetting, distance, range, distanceFade);
            AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)
                    .inflate(0.02).move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, block, (alpha << 24) | storageRgb(id));
        }
    }

    /**
     * Lines from the view to each container, in that container's own colour.
     *
     * <p>Not culled, for the same reason the other tracers are not: the far end is usually off screen
     * - that is what makes a tracer useful for finding a stash - while the line itself crosses the
     * view.
     */
    static void renderStorageTracers(Minecraft mc, StorageESP module, Vec3 camera,
            PoseStack.Pose pose, VertexConsumer buffer) {
        double range = module.getNumberSetting("range", 64.0);
        int alpha = (int) Math.max(32, Math.min(255, module.getNumberSetting("tracerAlpha", 150.0))) << 24;
        for (BlockPos pos : module.getCachedPositions()) {
            if (blockDistance(mc, pos) > range || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            String id = blockId(mc, pos);
            if (!module.matches(id)) continue;
            line(buffer, pose, 0.0, -0.12, 0.0,
                    pos.getX() + 0.5 - camera.x, pos.getY() + 0.5 - camera.y, pos.getZ() + 0.5 - camera.z,
                    alpha | storageRgb(id));
        }
    }

    static void renderStorageLabels(LevelRenderContext ctx, Minecraft mc, StorageESP module, Vec3 camera) {
        PoseStack stack = ctx.poseStack();
        double range = module.getNumberSetting("range", 64.0);
        for (BlockPos pos : module.getCachedPositions()) {
            if (blockDistance(mc, pos) > range
                    || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            String id = blockId(mc, pos);
            if (!module.matches(id)) continue;
            int rgb = storageRgb(id);
            stack.pushPose();
            try {
            stack.translate(pos.getX() + 0.5 - camera.x, pos.getY() + 0.5 - camera.y, pos.getZ() + 0.5 - camera.z);
            ctx.submitNodeCollector().submitNameTag(stack, new Vec3(0.0, 0.8, 0.0), 0,
                    Component.literal(prettyBlockName(id)).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))), true,
                    LightCoordsUtil.FULL_BRIGHT, ctx.levelState().cameraRenderState);
            } finally { stack.popPose(); }
        }
    }

    static int storageRgb(String id) {
        if (id.endsWith(":ender_chest")) return 0xAA55FF;
        if (StorageKind.of(id) == StorageKind.SHULKER) return 0xFF55FF;
        if (id.endsWith(":barrel")) return 0xD89A55;
        if (StorageESP.isUtilityStorage(id)) return 0x55CCFF;
        return 0xFFAA33;
    }

    static String prettyBlockName(String id) {
        int separator = id.indexOf(':');
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        String[] words = path.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    static void renderBlockEsp(Minecraft mc, BlockESP module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, ViewCulling culling) {
        double range = module.getNumberSetting("horizontalRange", 24.0);
        int rgb = moduleRgb(module, module.getNumberSetting("red", 255.0), module.getNumberSetting("green", 100.0), module.getNumberSetting("blue", 220.0), 0.0);
        double alphaSetting = module.getNumberSetting("alpha", 220.0);
        boolean distanceFade = module.getBooleanSetting("distanceFade", true);
        for (BlockPos pos : module.getMatches()) {
            if (!culling.isVisible(pos)) continue;
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            String id = blockId(mc, pos);
            if (!module.matches(id)) continue;
            int alpha = fadedAlpha(alphaSetting, blockDistance(mc, pos), range, distanceFade);
            AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)
                    .inflate(0.015).move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, block, (alpha << 24) | module.colorFor(id, rgb));
        }
    }

    static void renderSpawns(SpawnESP module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, ViewCulling culling) {
        int alpha = (int) Math.max(32, Math.min(255, module.getNumberSetting("alpha", 120.0))) << 24;
        for (var entry : module.results()) {
            BlockPos pos = entry.getKey();
            if (!culling.isVisible(pos)) continue;
            // Red for always-spawnable, amber for night-only: the distinction is what the player acts on.
            int rgb = entry.getValue() == SpawnLightRules.Spawnable.ALWAYS
                    ? 0xFF4444 : 0xFFBB44;
            AABB box = new AABB(pos.getX() + 0.02, pos.getY(), pos.getZ() + 0.02,
                    pos.getX() + 0.98, pos.getY() + 0.02, pos.getZ() + 0.98)
                    .move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, box, alpha | rgb);
        }
    }

    static void renderHoles(HoleESP module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, ViewCulling culling) {
        int alpha = (int) Math.max(32, Math.min(255, module.getNumberSetting("alpha", 150.0))) << 24;
        for (var entry : module.results()) {
            BlockPos pos = entry.getKey();
            if (!culling.isVisible(pos)) continue;
            // Green reads as safe and orange as "encloses you but will not hold"; the distinction
            // is the whole point of the module, so it is carried by colour rather than a label.
            int rgb = entry.getValue() == HoleDetector.Hole.SAFE
                    ? 0x55FF88 : 0xFFAA33;
            AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 0.12, pos.getZ() + 1.0)
                    .move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, box, alpha | rgb);
        }
    }
}
