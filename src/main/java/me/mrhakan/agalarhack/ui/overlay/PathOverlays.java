package me.mrhakan.agalarhack.ui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.render.Breadcrumbs;
import me.mrhakan.agalarhack.module.render.Freecam;
import me.mrhakan.agalarhack.module.render.Trajectories;
import me.mrhakan.agalarhack.module.render.Waypoints;
import me.mrhakan.agalarhack.services.projectile.ProjectilePhysics;
import me.mrhakan.agalarhack.services.projectile.ProjectileSimulator;
import me.mrhakan.agalarhack.ui.ViewCulling;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.LivingEntity;
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

import static me.mrhakan.agalarhack.ui.overlay.OverlayDraw.*;

/** Overlays drawn as paths and markers: projectile trajectories, breadcrumbs, waypoints and the Freecam body. */
final class PathOverlays {
    private PathOverlays() {
    }

    static final class TrajectoryOverlay extends WorldOverlay<Trajectories> {
        TrajectoryOverlay() { super("Trajectories", Trajectories.class); }
        @Override protected void drawLines(OverlayFrame frame, Trajectories module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderTrajectory(frame.mc(), module, frame.camera(), pose, buffer);
        }
    }

    static final class BreadcrumbOverlay extends WorldOverlay<Breadcrumbs> {
        BreadcrumbOverlay() { super("Breadcrumbs", Breadcrumbs.class); }
        @Override protected void drawLines(OverlayFrame frame, Breadcrumbs module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderBreadcrumbs(module, frame.camera(), pose, buffer);
        }
    }

    static final class WaypointOverlay extends WorldOverlay<Waypoints> {
        WaypointOverlay() { super("Waypoints", Waypoints.class); }
        @Override protected boolean hasLabels() { return true; }
        @Override protected void submitLabels(OverlayFrame frame, Waypoints module) {
            if (module.getBooleanSetting("labels", true)) renderWaypointLabels(frame.ctx(), frame.mc(), module, frame.camera());
        }
        @Override protected void drawLines(OverlayFrame frame, Waypoints module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderWaypoints(frame.mc(), module, frame.camera(), pose, buffer, frame.culling());
        }
    }

    /** Active while the camera is detached, not merely while Freecam is on. */
    static final class FreecamBodyOverlay extends WorldOverlay<Freecam> {
        FreecamBodyOverlay() { super("Freecam", Freecam.class); }
        @Override protected boolean active(Freecam module) { return module.shouldRenderBodyMarker(); }
        @Override protected void drawLines(OverlayFrame frame, Freecam module, PoseStack.Pose pose, VertexConsumer buffer) {
            renderFreecamBodyMarker(frame.mc(), module, frame.camera(), pose, buffer);
        }
    }

    static void renderTrajectory(Minecraft mc, Module module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        ItemStack stack = projectileStack(mc);
        if (stack == null) return;
        LaunchSpec launch = launchSpec(mc, module, stack);
        if (launch == null) return;

        Vec3 eye = mc.player.getEyePosition().add(0.0, -0.1, 0.0);
        var physics = new ProjectilePhysics(
                1.0, launch.gravity, launch.drag, 0.0, launch.gravityBeforeDrag);
        var state = new ProjectileSimulator.State(
                eye.x, eye.y, eye.z, launch.velocity.x, launch.velocity.y, launch.velocity.z);
        int steps = (int) Math.round(module.getNumberSetting("steps", 100.0));
        boolean collisionEnabled = module.getBooleanSetting("collision", true);
        boolean landingMarker = module.getBooleanSetting("landingMarker", true);
        int color = (clampChannel(240.0) << 24) | moduleRgb(module, module.getNumberSetting("red", 255.0),
                module.getNumberSetting("green", 220.0), module.getNumberSetting("blue", 80.0), 0.0);

        for (int i = 0; i < steps; i++) {
            // Stepping is the shared simulator; only collision stays here, because it needs the world.
            var stepped = ProjectileSimulator.advance(state, physics);
            Vec3 pos = new Vec3(state.x(), state.y(), state.z());
            Vec3 next = new Vec3(stepped.x(), stepped.y(), stepped.z());
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
            state = stepped;
            if (state.y() < mc.level.getMinY() - 16) break;
        }
    }

    static ItemStack projectileStack(Minecraft mc) {
        ItemStack main = mc.player.getMainHandItem();
        if (isProjectile(main.getItem())) return main;
        ItemStack off = mc.player.getOffhandItem();
        return isProjectile(off.getItem()) ? off : null;
    }

    static LaunchSpec launchSpec(Minecraft mc, Module module, ItemStack stack) {
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

    static Vec3 launchDirection(float yawDegrees, float pitchDegrees, double pitchOffset) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double offsetPitch = Math.toRadians(pitchDegrees + pitchOffset);
        Vec3 raw = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(offsetPitch), Math.cos(yaw) * Math.cos(pitch));
        return raw.normalize();
    }

    static double customLaunchPower(Item item) {
        if (item == Items.BOW || item == Items.CROSSBOW) return 3.0;
        if (item == Items.TRIDENT) return 2.5;
        if (item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) return 0.75;
        return 1.5;
    }

    static boolean isProjectile(Item item) {
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT || item == Items.ENDER_PEARL
                || item == Items.SNOWBALL || item == Items.EGG || item == Items.EXPERIENCE_BOTTLE
                || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    record LaunchSpec(Vec3 velocity, double gravity, double drag, boolean gravityBeforeDrag) {}

    static void renderBreadcrumbs(Breadcrumbs module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        var points = module.points();
        if (points.size() < 2) return;
        int maxAlpha = (int) Math.max(32, Math.min(255, module.getNumberSetting("alpha", 200.0)));
        boolean fade = module.getBooleanSetting("fade", true);
        for (int index = 1; index < points.size(); index++) {
            var from = points.get(index - 1);
            var to = points.get(index);
            // Spread along the cycle by position in the trail: without the offset every segment is
            // the same colour at the same moment and the rainbow reads as a flashing line.
            int rgb = moduleRgb(module, module.getNumberSetting("red", 120.0), module.getNumberSetting("green", 220.0),
                    module.getNumberSetting("blue", 255.0), index / (double) points.size());
            // Older segments sit nearer the start of the list, so fade by position in the trail.
            int alpha = fade ? Math.max(16, (int) (maxAlpha * (index / (double) points.size()))) : maxAlpha;
            line(buffer, pose,
                    from.x() - camera.x, from.y() - camera.y, from.z() - camera.z,
                    to.x() - camera.x, to.y() - camera.y, to.z() - camera.z,
                    (alpha << 24) | rgb);
        }
    }

    static void renderWaypoints(Minecraft mc, Waypoints module,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer, ViewCulling culling) {
        double limit = module.getNumberSetting("renderDistance", 512);
        double size = module.getNumberSetting("markerSize", 1.0);
        boolean beams = module.getBooleanSetting("beams", true);
        double beamHeight = module.getNumberSetting("beamHeight", 256);
        for (var point : module.visible()) {
            if (point.horizontalDistanceTo(mc.player.getX(), mc.player.getZ()) > limit) continue;
            double half = size / 2.0;
            // Marker and beam are tested separately: a beam reaches hundreds of blocks above its
            // marker, so one of the two is very often on screen while the other is not.
            AABB marker = new AABB(point.x() + 0.5 - half, point.y(), point.z() + 0.5 - half,
                    point.x() + 0.5 + half, point.y() + size, point.z() + 0.5 + half);
            if (culling.isVisible(marker)) {
                box(buffer, pose, marker.move(-camera.x, -camera.y, -camera.z), point.color());
            }
            if (!beams || !point.beam()) continue;
            // A thin tall box reads as a beam and reuses the same line geometry as every other overlay.
            AABB beam = new AABB(point.x() + 0.45, point.y(), point.z() + 0.45,
                    point.x() + 0.55, point.y() + beamHeight, point.z() + 0.55);
            if (!culling.isVisible(beam)) continue;
            box(buffer, pose, beam.move(-camera.x, -camera.y, -camera.z), (point.color() & 0x00FFFFFF) | 0x60000000);
        }
    }

    static void renderWaypointLabels(LevelRenderContext ctx, Minecraft mc,
            Waypoints module, Vec3 camera) {
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

    static void renderFreecamBodyMarker(Minecraft mc, Freecam freecam, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        Vec3 anchor = freecam.getBodyAnchor();
        if (anchor == null) return;
        Vec3 delta = anchor.subtract(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        AABB body = mc.player.getBoundingBox().move(delta).inflate(0.05).move(-camera.x, -camera.y, -camera.z);
        int markerColor = color(255, 85, 170, 245);
        box(buffer, pose, body, markerColor);
        marker(buffer, pose, new Vec3((body.minX + body.maxX) * 0.5, body.maxY + 0.18, (body.minZ + body.maxZ) * 0.5), 0.18, markerColor);
    }
}
