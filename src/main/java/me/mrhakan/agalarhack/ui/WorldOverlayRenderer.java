package me.mrhakan.agalarhack.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.render.BlockESP;
import me.mrhakan.agalarhack.module.render.EntityESP;
import me.mrhakan.agalarhack.module.render.Freecam;
import me.mrhakan.agalarhack.module.render.StorageESP;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** World-space overlays built for Minecraft 26.2's submit-node render pipeline. */
public final class WorldOverlayRenderer {
    private WorldOverlayRenderer() {
    }

    public static void collect(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Module esp = AgalarHackClient.moduleManager.getModule("ESP");
        Module trajectories = AgalarHackClient.moduleManager.getModule("Trajectories");
        Module freecamModule = AgalarHackClient.moduleManager.getModule("Freecam");
        Module storageModule = AgalarHackClient.moduleManager.getModule("StorageESP");
        Module blockModule = AgalarHackClient.moduleManager.getModule("BlockESP");
        Module breadcrumbModule = AgalarHackClient.moduleManager.getModule("Breadcrumbs");
        me.mrhakan.agalarhack.module.render.Breadcrumbs breadcrumbs =
                breadcrumbModule instanceof me.mrhakan.agalarhack.module.render.Breadcrumbs b ? b : null;
        Module nametagModule = AgalarHackClient.moduleManager.getModule("Nametags");
        me.mrhakan.agalarhack.module.render.Nametags nametags =
                nametagModule instanceof me.mrhakan.agalarhack.module.render.Nametags n ? n : null;
        Module itemModule = AgalarHackClient.moduleManager.getModule("ItemESP");
        me.mrhakan.agalarhack.module.render.ItemESP itemEsp =
                itemModule instanceof me.mrhakan.agalarhack.module.render.ItemESP i ? i : null;
        Module waypointModule = AgalarHackClient.moduleManager.getModule("Waypoints");
        me.mrhakan.agalarhack.module.render.Waypoints waypoints =
                waypointModule instanceof me.mrhakan.agalarhack.module.render.Waypoints w ? w : null;
        Freecam freecam = freecamModule instanceof Freecam f ? f : null;
        StorageESP storage = storageModule instanceof StorageESP s ? s : null;
        BlockESP blockEsp = blockModule instanceof BlockESP b ? b : null;
        boolean espEnabled = esp != null && esp.isToggled();
        boolean trajectoriesEnabled = trajectories != null && trajectories.isToggled();
        boolean storageEnabled = storage != null && storage.isToggled();
        boolean blockEnabled = blockEsp != null && blockEsp.isToggled();
        boolean bodyMarker = freecam != null && freecam.shouldRenderBodyMarker();
        boolean waypointsEnabled = waypoints != null && waypoints.isToggled();
        boolean itemsEnabled = itemEsp != null && itemEsp.isToggled();
        boolean nametagsEnabled = nametags != null && nametags.isToggled();
        boolean breadcrumbsEnabled = breadcrumbs != null && breadcrumbs.isToggled();
        if (!breadcrumbsEnabled && !espEnabled && !trajectoriesEnabled && !storageEnabled && !blockEnabled && !bodyMarker
                && !waypointsEnabled && !itemsEnabled && !nametagsEnabled) return;

        var renderService = me.mrhakan.agalarhack.services.ClientServices.require(me.mrhakan.agalarhack.services.RenderService.class);
        Vec3 camera = ctx.levelState().cameraRenderState.pos;
        List<LivingEntity> espTargets = espEnabled && esp instanceof EntityESP entityEsp ? entityEsp.targets() : List.of();
        if (espEnabled && esp.getBooleanSetting("labels", true)) renderService.guard(esp, () -> renderEspLabels(ctx, mc, esp, espTargets, camera));
        if (storageEnabled && storage.getBooleanSetting("labels", false)) renderService.guard(storage, () -> renderStorageLabels(ctx, mc, storage, camera));
        if (nametagsEnabled) {
            var tagged = nametags;
            renderService.guard(tagged, () -> renderNametags(ctx, mc, tagged, camera));
        }
        if (itemsEnabled && itemEsp.getBooleanSetting("labels", true)) {
            var labelledItems = itemEsp;
            renderService.guard(labelledItems, () -> renderItemLabels(ctx, mc, labelledItems, camera));
        }
        if (waypointsEnabled && waypoints.getBooleanSetting("labels", true)) {
            var labelled = waypoints;
            renderService.guard(labelled, () -> renderWaypointLabels(ctx, mc, labelled, camera));
        }

        var submittedLevel = mc.level;
        var submittedPlayer = mc.player;
        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
            if (mc.level != submittedLevel || mc.player != submittedPlayer || mc.player == null) return;
            if (espEnabled) renderService.guard(esp, () -> renderEspGeometry(mc, esp, espTargets, camera, pose, buffer));
            if (storageEnabled) renderService.guard(storage, () -> renderStorageEsp(mc, storage, camera, pose, buffer));
            if (blockEnabled) renderService.guard(blockEsp, () -> renderBlockEsp(mc, blockEsp, camera, pose, buffer));
            if (trajectoriesEnabled) renderService.guard(trajectories, () -> renderTrajectory(mc, trajectories, camera, pose, buffer));
            if (breadcrumbsEnabled) renderService.guard(breadcrumbs, () -> renderBreadcrumbs(breadcrumbs, camera, pose, buffer));
            if (itemsEnabled && itemEsp.getBooleanSetting("boxes", true)) {
                renderService.guard(itemEsp, () -> renderItemEsp(mc, itemEsp, camera, pose, buffer));
            }
            if (waypointsEnabled) renderService.guard(waypoints, () -> renderWaypoints(mc, waypoints, camera, pose, buffer));
            if (bodyMarker) renderService.guard(freecam, () -> renderFreecamBodyMarker(mc, freecam, camera, pose, buffer));
        });
    }

    private static boolean currentEspTarget(Minecraft mc, Module esp, LivingEntity target) {
        double range = esp.getNumberSetting("range", 96);
        return mc.level != null && mc.player != null && target.level() == mc.level && target.isAlive()
                && mc.level.getEntity(target.getId()) == target && mc.player.distanceToSqr(target) <= range * range;
    }

    private static void renderEspGeometry(Minecraft mc, Module esp, List<LivingEntity> targets, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        boolean boxes = esp.getBooleanSetting("boxes", true);
        boolean tracers = esp.getBooleanSetting("tracers", false);
        for (LivingEntity living : targets) {
            if (!currentEspTarget(mc, esp, living)) continue;
            int color = entityEspColor(mc, esp, living);
            if (boxes) box(buffer, pose, living.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z), color);
            if (tracers) {
                Vec3 center = living.getBoundingBox().getCenter();
                line(buffer, pose, 0.0, -0.12, 0.0, center.x - camera.x, center.y - camera.y, center.z - camera.z, color);
            }
        }
    }

    private static void renderEspLabels(LevelRenderContext ctx, Minecraft mc, Module esp, List<LivingEntity> targets, Vec3 camera) {
        PoseStack stack = ctx.poseStack();
        double labelRange = esp.getNumberSetting("labelRange", 64);
        for (LivingEntity living : targets) {
            if (!currentEspTarget(mc, esp, living) || mc.player.distanceToSqr(living) > labelRange * labelRange) continue;
            StringBuilder label = new StringBuilder(living.getName().getString());
            if (esp.getBooleanSetting("showDistance", true)) label.append(String.format(Locale.ROOT, " [%.1fm]", mc.player.distanceTo(living)));
            if (esp.getBooleanSetting("showHealth", false)) label.append(String.format(Locale.ROOT, " [%.1f HP]", living.getHealth()));
            int styled = entityEspColor(mc, esp, living);
            int labelRgb = dimRgb(styled & 0xFFFFFF, 0.55 + 0.45 * espFadeFactor(mc, esp, living));
            Component text = Component.literal(label.toString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(labelRgb)));
            stack.pushPose();
            try {
            stack.translate(living.getX() - camera.x, living.getY() - camera.y, living.getZ() - camera.z);
            ctx.submitNodeCollector().submitNameTag(stack, new Vec3(0.0, living.getBbHeight() + 0.35, 0.0), 0, text, true,
                    LightCoordsUtil.FULL_BRIGHT, ctx.levelState().cameraRenderState);
            } finally { stack.popPose(); }
        }
    }

    private static int entityEspColor(Minecraft mc, Module esp, LivingEntity living) {
        int rgb = rgb(esp.getNumberSetting("red", 85.0), esp.getNumberSetting("green", 170.0), esp.getNumberSetting("blue", 255.0));
        if (living instanceof Player) {
            boolean friend = AgalarHackClient.FRIEND_MANAGER.isFriend(living.getName().getString());
            if (friend && esp.getBooleanSetting("friendColors", true)) {
                rgb = rgb(esp.getNumberSetting("friendRed", 85.0), esp.getNumberSetting("friendGreen", 255.0), esp.getNumberSetting("friendBlue", 120.0));
            } else if (esp.getBooleanSetting("teamColors", true) && mc.player.isAlliedTo(living)) {
                rgb = living.getTeamColor() & 0xFFFFFF;
            }
        }
        int baseAlpha = clampChannel(esp.getNumberSetting("alpha", 230.0));
        int minimumAlpha = clampChannel(esp.getNumberSetting("minimumAlpha", 64.0));
        int alpha = baseAlpha;
        if (esp.getBooleanSetting("distanceFade", true)) {
            double factor = espFadeFactor(mc, esp, living);
            alpha = (int) Math.round(minimumAlpha + (baseAlpha - minimumAlpha) * factor);
            alpha = Math.max(0, Math.min(255, alpha));
        }
        return (alpha << 24) | rgb;
    }

    private static double espFadeFactor(Minecraft mc, Module esp, LivingEntity living) {
        if (!esp.getBooleanSetting("distanceFade", true)) return 1.0;
        double range = Math.max(0.001, esp.getNumberSetting("range", 96.0));
        double fadeStart = Math.max(0.0, Math.min(range, esp.getNumberSetting("fadeStart", 32.0)));
        double distance = mc.player.distanceTo(living);
        if (distance <= fadeStart || fadeStart >= range) return 1.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - (distance - fadeStart) / (range - fadeStart)));
    }

    private static int dimRgb(int rgb, double factor) {
        factor = Math.max(0.0, Math.min(1.0, factor));
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static void renderStorageEsp(Minecraft mc, StorageESP module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        double range = module.getNumberSetting("range", 64.0);
        for (BlockPos pos : module.getCachedPositions()) {
            if (blockDistance(mc, pos) > module.getNumberSetting("range", 64.0)
                    || !mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            String id = blockId(mc, pos);
            if (!module.matches(id)) continue;
            int alpha = fadedAlpha(module.getNumberSetting("alpha", 220.0), blockDistance(mc, pos), range, module.getBooleanSetting("distanceFade", true));
            AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)
                    .inflate(0.02).move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, block, (alpha << 24) | storageRgb(id));
        }
    }

    private static void renderStorageLabels(LevelRenderContext ctx, Minecraft mc, StorageESP module, Vec3 camera) {
        PoseStack stack = ctx.poseStack();
        for (BlockPos pos : module.getCachedPositions()) {
            if (blockDistance(mc, pos) > module.getNumberSetting("range", 64.0)
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

    private static void renderBlockEsp(Minecraft mc, BlockESP module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        double range = module.getNumberSetting("horizontalRange", 24.0);
        int rgb = rgb(module.getNumberSetting("red", 255.0), module.getNumberSetting("green", 100.0), module.getNumberSetting("blue", 220.0));
        for (BlockPos pos : module.getMatches()) {
            if (!mc.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            String id = blockId(mc, pos);
            if (!module.matches(id)) continue;
            int alpha = fadedAlpha(module.getNumberSetting("alpha", 220.0), blockDistance(mc, pos), range, module.getBooleanSetting("distanceFade", true));
            AABB block = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)
                    .inflate(0.015).move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, block, (alpha << 24) | rgb);
        }
    }

    private static void renderBreadcrumbs(me.mrhakan.agalarhack.module.render.Breadcrumbs module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        var points = module.points();
        if (points.size() < 2) return;
        int rgb = rgb(module.getNumberSetting("red", 120.0), module.getNumberSetting("green", 220.0),
                module.getNumberSetting("blue", 255.0));
        int maxAlpha = (int) Math.max(32, Math.min(255, module.getNumberSetting("alpha", 200.0)));
        boolean fade = module.getBooleanSetting("fade", true);
        for (int index = 1; index < points.size(); index++) {
            var from = points.get(index - 1);
            var to = points.get(index);
            // Older segments sit nearer the start of the list, so fade by position in the trail.
            int alpha = fade ? Math.max(16, (int) (maxAlpha * (index / (double) points.size()))) : maxAlpha;
            line(buffer, pose,
                    from.x() - camera.x, from.y() - camera.y, from.z() - camera.z,
                    to.x() - camera.x, to.y() - camera.y, to.z() - camera.z,
                    (alpha << 24) | rgb);
        }
    }

    private static void renderNametags(LevelRenderContext ctx, Minecraft mc,
            me.mrhakan.agalarhack.module.render.Nametags module, Vec3 camera) {
        PoseStack stack = ctx.poseStack();
        var friends = AgalarHackClient.FRIEND_MANAGER;
        for (LivingEntity entity : module.targets()) {
            if (!entity.isAlive()) continue;
            double distance = mc.player.distanceTo(entity);
            boolean friend = entity instanceof net.minecraft.world.entity.player.Player
                    && friends != null && friends.isFriend(entity.getName().getString());
            String label = module.label(entity, distance, friend);
            if (label.isBlank()) continue;
            stack.pushPose();
            try {
                stack.translate(entity.getX() - camera.x,
                        entity.getY() + entity.getBbHeight() - camera.y, entity.getZ() - camera.z);
                ctx.submitNodeCollector().submitNameTag(stack, new Vec3(0.0, 0.5, 0.0), 0,
                        Component.literal(label).withStyle(Style.EMPTY.withColor(
                                TextColor.fromRgb(friend ? 0x55FF78 : 0xFFFFFF))),
                        true, LightCoordsUtil.FULL_BRIGHT, ctx.levelState().cameraRenderState);
            } finally { stack.popPose(); }
        }
    }

    private static int itemColor(me.mrhakan.agalarhack.module.render.ItemESP module,
            net.minecraft.world.entity.item.ItemEntity drop) {
        return module.getBooleanSetting("rarityColors", true)
                ? me.mrhakan.agalarhack.module.render.ItemESP.rarityRgb(drop.getItem().getRarity())
                : rgb(module.getNumberSetting("red", 255.0), module.getNumberSetting("green", 220.0),
                        module.getNumberSetting("blue", 60.0));
    }

    private static void renderItemEsp(Minecraft mc, me.mrhakan.agalarhack.module.render.ItemESP module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        int alpha = (int) module.getNumberSetting("alpha", 220.0);
        for (var drop : module.items()) {
            if (!drop.isAlive()) continue;
            AABB box = drop.getBoundingBox().inflate(0.08).move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, box, (Math.max(32, Math.min(255, alpha)) << 24) | itemColor(module, drop));
        }
    }

    private static void renderItemLabels(LevelRenderContext ctx, Minecraft mc,
            me.mrhakan.agalarhack.module.render.ItemESP module, Vec3 camera) {
        boolean count = module.getBooleanSetting("showCount", true);
        boolean distance = module.getBooleanSetting("showDistance", false);
        PoseStack stack = ctx.poseStack();
        for (var drop : module.items()) {
            if (!drop.isAlive()) continue;
            var item = drop.getItem();
            StringBuilder label = new StringBuilder(item.getHoverName().getString());
            if (count && item.getCount() > 1) label.append(" x").append(item.getCount());
            if (distance) label.append(' ').append(Math.round(mc.player.distanceTo(drop))).append('m');
            stack.pushPose();
            try {
                stack.translate(drop.getX() - camera.x, drop.getY() + 0.6 - camera.y, drop.getZ() - camera.z);
                ctx.submitNodeCollector().submitNameTag(stack, new Vec3(0.0, 0.0, 0.0), 0,
                        Component.literal(label.toString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(itemColor(module, drop)))),
                        true, LightCoordsUtil.FULL_BRIGHT, ctx.levelState().cameraRenderState);
            } finally { stack.popPose(); }
        }
    }

    private static void renderWaypoints(Minecraft mc, me.mrhakan.agalarhack.module.render.Waypoints module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        double limit = module.getNumberSetting("renderDistance", 512);
        double size = module.getNumberSetting("markerSize", 1.0);
        boolean beams = module.getBooleanSetting("beams", true);
        double beamHeight = module.getNumberSetting("beamHeight", 256);
        for (var point : module.visible()) {
            if (point.horizontalDistanceTo(mc.player.getX(), mc.player.getZ()) > limit) continue;
            double half = size / 2.0;
            AABB marker = new AABB(point.x() + 0.5 - half, point.y(), point.z() + 0.5 - half,
                    point.x() + 0.5 + half, point.y() + size, point.z() + 0.5 + half)
                    .move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, marker, point.color());
            if (!beams || !point.beam()) continue;
            // A thin tall box reads as a beam and reuses the same line geometry as every other overlay.
            AABB beam = new AABB(point.x() + 0.45, point.y(), point.z() + 0.45,
                    point.x() + 0.55, point.y() + beamHeight, point.z() + 0.55)
                    .move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, beam, (point.color() & 0x00FFFFFF) | 0x60000000);
        }
    }

    private static void renderWaypointLabels(LevelRenderContext ctx, Minecraft mc,
            me.mrhakan.agalarhack.module.render.Waypoints module, Vec3 camera) {
        double limit = module.getNumberSetting("renderDistance", 512);
        boolean withDistance = module.getBooleanSetting("distanceInLabel", true);
        double size = module.getNumberSetting("markerSize", 1.0);
        PoseStack stack = ctx.poseStack();
        for (var point : module.visible()) {
            double distance = point.horizontalDistanceTo(mc.player.getX(), mc.player.getZ());
            if (distance > limit) continue;
            String label = withDistance ? point.name() + " " + Math.round(distance) + "m" : point.name();
            stack.pushPose();
            try {
                stack.translate(point.x() + 0.5 - camera.x, point.y() + size - camera.y, point.z() + 0.5 - camera.z);
                ctx.submitNodeCollector().submitNameTag(stack, new Vec3(0.0, 0.4, 0.0), 0,
                        Component.literal(label).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(point.color() & 0x00FFFFFF))),
                        true, LightCoordsUtil.FULL_BRIGHT, ctx.levelState().cameraRenderState);
            } finally { stack.popPose(); }
        }
    }

    private static String blockId(Minecraft mc, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
    }

    private static double blockDistance(Minecraft mc, BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - mc.player.getY();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int fadedAlpha(double alphaSetting, double distance, double range, boolean enabled) {
        int alpha = clampChannel(alphaSetting);
        if (!enabled || range <= 0.0) return alpha;
        double factor = Math.max(0.18, Math.min(1.0, 1.0 - distance / range));
        return Math.max(24, Math.min(255, (int) Math.round(alpha * factor)));
    }

    private static int storageRgb(String id) {
        if (id.endsWith(":ender_chest")) return 0xAA55FF;
        if (me.mrhakan.agalarhack.services.scanning.StorageKind.of(id) == me.mrhakan.agalarhack.services.scanning.StorageKind.SHULKER) return 0xFF55FF;
        if (id.endsWith(":barrel")) return 0xD89A55;
        if (StorageESP.isUtilityStorage(id)) return 0x55CCFF;
        return 0xFFAA33;
    }

    private static String prettyBlockName(String id) {
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

    private static void renderTrajectory(Minecraft mc, Module module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        ItemStack stack = projectileStack(mc);
        if (stack == null) return;
        LaunchSpec launch = launchSpec(mc, module, stack);
        if (launch == null) return;

        Vec3 pos = mc.player.getEyePosition().add(0.0, -0.1, 0.0);
        Vec3 velocity = launch.velocity;
        int steps = (int) Math.round(module.getNumberSetting("steps", 100.0));
        boolean collisionEnabled = module.getBooleanSetting("collision", true);
        boolean landingMarker = module.getBooleanSetting("landingMarker", true);
        int color = color(module.getNumberSetting("red", 255.0), module.getNumberSetting("green", 220.0), module.getNumberSetting("blue", 80.0), 240.0);

        for (int i = 0; i < steps; i++) {
            if (launch.gravityBeforeDrag) velocity = new Vec3(velocity.x, velocity.y - launch.gravity, velocity.z).scale(launch.drag);
            Vec3 next = pos.add(velocity);
            Vec3 end = next;
            boolean collided = false;
            if (collisionEnabled) {
                HitResult blockHit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    end = blockHit.getLocation();
                    collided = true;
                }
                AABB search = AABB.ofSize(pos, 0.3, 0.3, 0.3).expandTowards(end.subtract(pos)).inflate(1.0);
                EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mc.player, pos, end, search,
                        entity -> entity != mc.player && entity instanceof LivingEntity living && living.isAlive(), pos.distanceToSqr(end));
                if (entityHit != null) {
                    end = entityHit.getLocation();
                    collided = true;
                }
            }
            line(buffer, pose, pos.x - camera.x, pos.y - camera.y, pos.z - camera.z,
                    end.x - camera.x, end.y - camera.y, end.z - camera.z, color);
            if (collided) {
                if (landingMarker) marker(buffer, pose, end.subtract(camera), module.getNumberSetting("markerSize", 0.22), color);
                break;
            }
            pos = next;
            if (!launch.gravityBeforeDrag) velocity = new Vec3(velocity.x * launch.drag, velocity.y * launch.drag - launch.gravity, velocity.z * launch.drag);
            if (pos.y < mc.level.getMinY() - 16) break;
        }
    }

    private static ItemStack projectileStack(Minecraft mc) {
        ItemStack main = mc.player.getMainHandItem();
        if (isProjectile(main.getItem())) return main;
        ItemStack off = mc.player.getOffhandItem();
        return isProjectile(off.getItem()) ? off : null;
    }

    private static LaunchSpec launchSpec(Minecraft mc, Module module, ItemStack stack) {
        Item item = stack.getItem();
        boolean vanilla = module.getStringSetting("physics", "Vanilla").equalsIgnoreCase("Vanilla");
        double powerScale = module.getNumberSetting("powerScale", 1.0);
        double gravity = module.getNumberSetting("gravity", 0.05);
        double drag = module.getNumberSetting("drag", 0.99);
        boolean gravityBeforeDrag = false;
        double speed;
        double pitchOffset = 0.0;

        if (vanilla) {
            if (item == Items.BOW) {
                if (!mc.player.isUsingItem() || mc.player.getUseItem() != stack) return null;
                int drawTicks = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
                double bowPower = BowItem.getPowerForTime(drawTicks);
                if (bowPower < 0.1) return null;
                speed = bowPower * 3.0;
                gravity = 0.05;
                drag = 0.99;
            } else if (item == Items.CROSSBOW) {
                ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                if (charged == null || charged.isEmpty()) return null;
                speed = 3.15;
                gravity = 0.05;
                drag = 0.99;
            } else if (item == Items.TRIDENT) {
                if (!mc.player.isUsingItem() || mc.player.getUseItem() != stack) return null;
                int drawTicks = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();
                if (drawTicks < 10) return null;
                speed = 2.5;
                gravity = 0.05;
                drag = 0.99;
            } else if (item == Items.EXPERIENCE_BOTTLE) {
                if (module.getBooleanSetting("onlyWhenUsing", false) && !mc.options.keyUse.isDown()) return null;
                speed = 0.7;
                pitchOffset = -20.0;
                gravity = 0.07;
                drag = 0.99;
                gravityBeforeDrag = true;
            } else if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
                if (module.getBooleanSetting("onlyWhenUsing", false) && !mc.options.keyUse.isDown()) return null;
                speed = 0.5;
                pitchOffset = -20.0;
                gravity = 0.05;
                drag = 0.99;
                gravityBeforeDrag = true;
            } else {
                if (module.getBooleanSetting("onlyWhenUsing", false) && !mc.options.keyUse.isDown()) return null;
                speed = 1.5;
                gravity = 0.03;
                drag = 0.99;
                gravityBeforeDrag = true;
            }
        } else {
            if (module.getBooleanSetting("onlyWhenUsing", false) && !mc.options.keyUse.isDown()) return null;
            speed = customLaunchPower(item);
        }

        Vec3 velocity = launchDirection(mc.player.getYRot(), mc.player.getXRot(), pitchOffset).scale(speed * powerScale);
        if (module.getBooleanSetting("includePlayerMotion", true)) {
            Vec3 movement = mc.player.getKnownMovement();
            velocity = velocity.add(movement.x, mc.player.onGround() ? 0.0 : movement.y, movement.z);
        }
        return new LaunchSpec(velocity, gravity, drag, gravityBeforeDrag);
    }

    private static Vec3 launchDirection(float yawDegrees, float pitchDegrees, double pitchOffset) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double offsetPitch = Math.toRadians(pitchDegrees + pitchOffset);
        Vec3 raw = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(offsetPitch), Math.cos(yaw) * Math.cos(pitch));
        return raw.normalize();
    }

    private static double customLaunchPower(Item item) {
        if (item == Items.BOW || item == Items.CROSSBOW) return 3.0;
        if (item == Items.TRIDENT) return 2.5;
        if (item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) return 0.75;
        return 1.5;
    }

    private static void renderFreecamBodyMarker(Minecraft mc, Freecam freecam, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        Vec3 anchor = freecam.getBodyAnchor();
        if (anchor == null) return;
        Vec3 delta = anchor.subtract(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        AABB body = mc.player.getBoundingBox().move(delta).inflate(0.05).move(-camera.x, -camera.y, -camera.z);
        int markerColor = color(255, 85, 170, 245);
        box(buffer, pose, body, markerColor);
        marker(buffer, pose, new Vec3((body.minX + body.maxX) * 0.5, body.maxY + 0.18, (body.minZ + body.maxZ) * 0.5), 0.18, markerColor);
    }

    private static boolean isProjectile(Item item) {
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT || item == Items.ENDER_PEARL
                || item == Items.SNOWBALL || item == Items.EGG || item == Items.EXPERIENCE_BOTTLE
                || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private static void marker(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, double size, int color) {
        line(buffer, pose, p.x - size, p.y, p.z, p.x + size, p.y, p.z, color);
        line(buffer, pose, p.x, p.y - size, p.z, p.x, p.y + size, p.z, color);
        line(buffer, pose, p.x, p.y, p.z - size, p.x, p.y, p.z + size, color);
        line(buffer, pose, p.x - size * 0.7, p.y, p.z - size * 0.7, p.x + size * 0.7, p.y, p.z + size * 0.7, color);
        line(buffer, pose, p.x - size * 0.7, p.y, p.z + size * 0.7, p.x + size * 0.7, p.y, p.z - size * 0.7, color);
    }

    private static void box(VertexConsumer buffer, PoseStack.Pose pose, AABB b, int color) {
        line(buffer, pose, b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, color);
        line(buffer, pose, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, color);
        line(buffer, pose, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, color);
        line(buffer, pose, b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ, color);
        line(buffer, pose, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, color);
        line(buffer, pose, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, color);
        line(buffer, pose, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, color);
        line(buffer, pose, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ, color);
        line(buffer, pose, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, color);
        line(buffer, pose, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, color);
        line(buffer, pose, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, color);
        line(buffer, pose, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, color);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose, double ax, double ay, double az, double bx, double by, double bz, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >>> 24) & 0xFF;
        float nx = (float) (bx - ax), ny = (float) (by - ay), nz = (float) (bz - az);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
        buffer.addVertex(pose, (float) ax, (float) ay, (float) az).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
        buffer.addVertex(pose, (float) bx, (float) by, (float) bz).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
    }

    private static int rgb(double red, double green, double blue) {
        return (clampChannel(red) << 16) | (clampChannel(green) << 8) | clampChannel(blue);
    }

    private static int color(double red, double green, double blue, double alpha) {
        return (clampChannel(alpha) << 24) | rgb(red, green, blue);
    }

    private static int clampChannel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private record LaunchSpec(Vec3 velocity, double gravity, double drag, boolean gravityBeforeDrag) {}
}
