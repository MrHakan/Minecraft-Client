package me.mrhakan.agalarhack.ui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Locale;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.render.EntityESP;
import me.mrhakan.agalarhack.module.render.ItemESP;
import me.mrhakan.agalarhack.module.render.Nametags;
import me.mrhakan.agalarhack.module.render.ProjectileESP;
import me.mrhakan.agalarhack.module.render.Tracers;
import me.mrhakan.agalarhack.ui.ViewCulling;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static me.mrhakan.agalarhack.ui.overlay.OverlayDraw.*;

/** Overlays drawn around entities: ESP boxes, tracers and labels, nametags, item drops and projectiles. */
final class EntityOverlays {
    private EntityOverlays() {
    }

    static final class EspOverlay extends WorldOverlay<EntityESP> {
        EspOverlay() { super("ESP", EntityESP.class); }
        @Override protected boolean hasLabels() { return true; }
        @Override protected void submitLabels(OverlayFrame frame, EntityESP esp) {
            if (esp.getBooleanSetting("labels", true)) renderEspLabels(frame.ctx(), frame.mc(), esp, esp.targets(), frame.camera());
        }
        @Override protected void drawLines(OverlayFrame frame, EntityESP esp, PoseStack.Pose pose, VertexConsumer buffer) {
            renderEspGeometry(frame.mc(), esp, esp.targets(), frame.camera(), pose, buffer);
        }
    }

    static final class NametagOverlay extends WorldOverlay<Nametags> {
        NametagOverlay() { super("Nametags", Nametags.class); }
        @Override protected boolean hasLabels() { return true; }
        @Override protected boolean hasLines() { return false; }
        @Override protected void submitLabels(OverlayFrame frame, Nametags module) {
            renderNametags(frame.ctx(), frame.mc(), module, frame.camera());
        }
    }

    static final class ItemOverlay extends WorldOverlay<ItemESP> {
        ItemOverlay() { super("ItemESP", ItemESP.class); }
        @Override protected boolean hasLabels() { return true; }
        @Override protected void submitLabels(OverlayFrame frame, ItemESP module) {
            if (module.getBooleanSetting("labels", true)) renderItemLabels(frame.ctx(), frame.mc(), module, frame.camera());
        }
        @Override protected void drawLines(OverlayFrame frame, ItemESP module, PoseStack.Pose pose, VertexConsumer buffer) {
            if (module.getBooleanSetting("boxes", true)) renderItemEsp(frame.mc(), module, frame.camera(), pose, buffer, frame.culling());
        }
    }

    static final class ProjectileOverlay extends WorldOverlay<ProjectileESP> {
        ProjectileOverlay() { super("ProjectileESP", ProjectileESP.class); }
        @Override protected void drawLines(OverlayFrame frame, ProjectileESP module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderProjectiles(module, frame.camera(), pose, buffer);
        }
    }

    static final class TracerOverlay extends WorldOverlay<Tracers> {
        TracerOverlay() { super("Tracers", Tracers.class); }
        @Override protected void drawLines(OverlayFrame frame, Tracers module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderTracers(module, frame.camera(), pose, buffer);
        }
    }

    /**
     * ESP settings resolved once per pass instead of once per target.
     *
     * <p>The colour and fade of every target used to re-read about a dozen settings, and the fade was
     * worked out twice for a labelled one. None of it can change between the targets of one frame.
     */
    record EspStyle(double rangeSquared, int baseRgb, boolean friendColors, int friendRgb, boolean teamColors,
            boolean distanceFade, int baseAlpha, int minimumAlpha, double fadeStart, double fadeLimit) {
        static EspStyle of(Module esp) {
            double range = esp.getNumberSetting("range", 96.0);
            double fadeLimit = Math.max(0.001, range);
            return new EspStyle(range * range,
                    moduleRgb(esp, esp.getNumberSetting("red", 85.0), esp.getNumberSetting("green", 170.0),
                            esp.getNumberSetting("blue", 255.0), 0.0),
                    esp.getBooleanSetting("friendColors", true),
                    rgb(esp.getNumberSetting("friendRed", 85.0), esp.getNumberSetting("friendGreen", 255.0),
                            esp.getNumberSetting("friendBlue", 120.0)),
                    esp.getBooleanSetting("teamColors", true),
                    esp.getBooleanSetting("distanceFade", true),
                    clampChannel(esp.getNumberSetting("alpha", 230.0)),
                    clampChannel(esp.getNumberSetting("minimumAlpha", 64.0)),
                    Math.max(0.0, Math.min(fadeLimit, esp.getNumberSetting("fadeStart", 32.0))),
                    fadeLimit);
        }

        /** 1 up to {@code fadeStart}, falling linearly to 0 at the ESP range. */
        double fade(double distance) {
            if (!distanceFade || distance <= fadeStart || fadeStart >= fadeLimit) return 1.0;
            return Math.max(0.0, Math.min(1.0, 1.0 - (distance - fadeStart) / (fadeLimit - fadeStart)));
        }

        /** Friend colour first, then the scoreboard team colour, then the configured colour. */
        int color(Minecraft mc, LivingEntity living, double distance) {
            int chosen = baseRgb;
            if (living instanceof Player) {
                if (friendColors && AgalarHackClient.FRIEND_MANAGER.isFriend(living.getName().getString())) {
                    chosen = friendRgb;
                } else if (teamColors && mc.player.isAlliedTo(living)) {
                    chosen = living.getTeamColor() & 0xFFFFFF;
                }
            }
            int alpha = baseAlpha;
            if (distanceFade) {
                alpha = (int) Math.round(minimumAlpha + (baseAlpha - minimumAlpha) * fade(distance));
                alpha = Math.max(0, Math.min(255, alpha));
            }
            return (alpha << 24) | chosen;
        }
    }

    static boolean currentEspTarget(Minecraft mc, LivingEntity target, double distanceSquared, EspStyle style) {
        return mc.level != null && mc.player != null && target.level() == mc.level && target.isAlive()
                && mc.level.getEntity(target.getId()) == target && distanceSquared <= style.rangeSquared();
    }

    static void renderEspGeometry(Minecraft mc, Module esp, List<LivingEntity> targets, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        boolean boxes = esp.getBooleanSetting("boxes", true);
        boolean tracers = esp.getBooleanSetting("tracers", false);
        EspStyle style = EspStyle.of(esp);
        for (LivingEntity living : targets) {
            double distanceSquared = mc.player.distanceToSqr(living);
            if (!currentEspTarget(mc, living, distanceSquared, style)) continue;
            int color = style.color(mc, living, mc.player.distanceTo(living));
            if (boxes) box(buffer, pose, living.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z), color);
            if (tracers) {
                Vec3 center = living.getBoundingBox().getCenter();
                line(buffer, pose, 0.0, -0.12, 0.0, center.x - camera.x, center.y - camera.y, center.z - camera.z, color);
            }
        }
    }

    static void renderEspLabels(LevelRenderContext ctx, Minecraft mc, Module esp, List<LivingEntity> targets, Vec3 camera) {
        PoseStack stack = ctx.poseStack();
        double labelRange = esp.getNumberSetting("labelRange", 64);
        double labelRangeSquared = labelRange * labelRange;
        boolean showDistance = esp.getBooleanSetting("showDistance", true);
        boolean showHealth = esp.getBooleanSetting("showHealth", false);
        EspStyle style = EspStyle.of(esp);
        for (LivingEntity living : targets) {
            double distanceSquared = mc.player.distanceToSqr(living);
            if (!currentEspTarget(mc, living, distanceSquared, style) || distanceSquared > labelRangeSquared) continue;
            float distance = mc.player.distanceTo(living);
            StringBuilder label = new StringBuilder(living.getName().getString());
            if (showDistance) label.append(String.format(Locale.ROOT, " [%.1fm]", distance));
            if (showHealth) label.append(String.format(Locale.ROOT, " [%.1f HP]", living.getHealth()));
            int styled = style.color(mc, living, distance);
            int labelRgb = dimRgb(styled & 0xFFFFFF, 0.55 + 0.45 * style.fade(distance));
            Component text = Component.literal(label.toString()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(labelRgb)));
            stack.pushPose();
            try {
            stack.translate(living.getX() - camera.x, living.getY() - camera.y, living.getZ() - camera.z);
            ctx.submitNodeCollector().submitNameTag(stack, new Vec3(0.0, living.getBbHeight() + 0.35, 0.0), 0, text, true,
                    LightCoordsUtil.FULL_BRIGHT, ctx.levelState().cameraRenderState);
            } finally { stack.popPose(); }
        }
    }

    static void renderProjectiles(ProjectileESP module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        int color = (clampChannel(module.getNumberSetting("alpha", 220.0)) << 24)
                | moduleRgb(module, module.getNumberSetting("red", 255.0), module.getNumberSetting("green", 90.0),
                        module.getNumberSetting("blue", 90.0), 0.0);
        boolean boxes = module.getBooleanSetting("boxes", true);
        boolean velocity = module.getBooleanSetting("velocity", true);
        double scale = module.getNumberSetting("velocityScale", 8.0);
        for (var projectile : module.projectiles()) {
            if (!projectile.isAlive()) continue;
            if (boxes) box(buffer, pose, projectile.getBoundingBox().inflate(0.08).move(-camera.x, -camera.y, -camera.z), color);
            if (!velocity) continue;
            Vec3 motion = projectile.getDeltaMovement();
            if (motion.lengthSqr() < 1.0E-4) continue;
            Vec3 from = projectile.getBoundingBox().getCenter();
            // Direction of travel over the next few ticks, ignoring gravity: a heading, not a path.
            Vec3 to = from.add(motion.scale(scale));
            line(buffer, pose, from.x - camera.x, from.y - camera.y, from.z - camera.z,
                    to.x - camera.x, to.y - camera.y, to.z - camera.z, color);
        }
    }

    static void renderTracers(Tracers module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        // Origins are camera-relative; the ESP tracer's -0.12 sits just under the crosshair.
        double originY = switch (module.getStringSetting("origin", "center")) {
            case "bottom" -> -0.8;
            case "crosshair" -> -0.12;
            default -> 0.0;
        };
        int alpha = (int) Math.max(32, Math.min(255, module.getNumberSetting("alpha", 180.0))) << 24;
        var drawn = module.targets();
        for (int index = 0; index < drawn.size(); index++) {
            var entity = drawn.get(index);
            if (!entity.isAlive()) continue;
            var group = Tracers.group(entity);
            if (!module.enabled(group)) continue;
            Vec3 center = entity.getBoundingBox().getCenter();
            line(buffer, pose, 0.0, originY, 0.0,
                    center.x - camera.x, center.y - camera.y, center.z - camera.z,
                    alpha | module.colorFor(group, index / (double) Math.max(1, drawn.size())));
        }
    }

    static void renderNametags(LevelRenderContext ctx, Minecraft mc,
            Nametags module, Vec3 camera) {
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

    static int itemColor(ItemESP module,
            net.minecraft.world.entity.item.ItemEntity drop) {
        return module.getBooleanSetting("rarityColors", true)
                ? ItemESP.rarityRgb(drop.getItem().getRarity())
                : moduleRgb(module, module.getNumberSetting("red", 255.0), module.getNumberSetting("green", 220.0),
                        module.getNumberSetting("blue", 60.0), 0.0);
    }

    static void renderItemEsp(Minecraft mc, ItemESP module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, ViewCulling culling) {
        int alpha = (int) module.getNumberSetting("alpha", 220.0);
        for (var drop : module.items()) {
            if (!drop.isAlive()) continue;
            AABB world = drop.getBoundingBox().inflate(0.08);
            if (!culling.isVisible(world)) continue;
            AABB box = world.move(-camera.x, -camera.y, -camera.z);
            box(buffer, pose, box, (Math.max(32, Math.min(255, alpha)) << 24) | itemColor(module, drop));
        }
    }

    static void renderItemLabels(LevelRenderContext ctx, Minecraft mc,
            ItemESP module, Vec3 camera) {
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
}
