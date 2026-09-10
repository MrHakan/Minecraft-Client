package me.mrhakan.agalarhack.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.module.Module;
import me.mrhakan.agalarhack.module.render.Freecam;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** World-space overlays built for Minecraft 26.2's submit-node render pipeline. */
public final class WorldOverlayRenderer {
    private WorldOverlayRenderer() {
    }

    public static void collect(LevelRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        Module esp = AgalarHackClient.moduleManager.getModule("ESP");
        Module trajectories = AgalarHackClient.moduleManager.getModule("Trajectories");
        Module freecamModule = AgalarHackClient.moduleManager.getModule("Freecam");
        Freecam freecam = freecamModule instanceof Freecam f ? f : null;
        boolean espEnabled = esp != null && esp.isToggled();
        boolean trajectoriesEnabled = trajectories != null && trajectories.isToggled();
        boolean bodyMarker = freecam != null && freecam.shouldRenderBodyMarker();
        if (!espEnabled && !trajectoriesEnabled && !bodyMarker) {
            return;
        }

        Vec3 camera = ctx.levelState().cameraRenderState.pos;
        List<LivingEntity> espTargets = espEnabled ? collectEspTargets(mc, esp) : List.of();

        if (espEnabled && esp.getBooleanSetting("labels", true)) {
            renderEspLabels(ctx, mc, esp, espTargets, camera);
        }

        ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.lines(), (pose, buffer) -> {
            if (espEnabled) {
                renderEspGeometry(mc, esp, espTargets, camera, pose, buffer);
            }
            if (trajectoriesEnabled) {
                renderTrajectory(mc, trajectories, camera, pose, buffer);
            }
            if (bodyMarker) {
                renderFreecamBodyMarker(mc, freecam, camera, pose, buffer);
            }
        });
    }

    private static List<LivingEntity> collectEspTargets(Minecraft mc, Module esp) {
        List<LivingEntity> targets = new ArrayList<>();
        double range = esp.getNumberSetting("range", 96.0);
        double rangeSq = range * range;
        boolean respectPolicy = esp.getBooleanSetting("respectTargetPolicy", true);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !living.isAlive()) {
                continue;
            }
            if (mc.player.distanceToSqr(entity) > rangeSq) {
                continue;
            }
            if (respectPolicy) {
                if (!AgalarHackClient.TARGET_POLICY.allows(living)) {
                    continue;
                }
            } else if (living instanceof Player) {
                if (!esp.getBooleanSetting("players", true)) {
                    continue;
                }
            } else if (!esp.getBooleanSetting("mobs", true)) {
                continue;
            }
            targets.add(living);
        }
        return targets;
    }

    private static void renderEspGeometry(Minecraft mc, Module esp, List<LivingEntity> targets,
            Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        int color = color(
                esp.getNumberSetting("red", 85.0),
                esp.getNumberSetting("green", 170.0),
                esp.getNumberSetting("blue", 255.0),
                esp.getNumberSetting("alpha", 230.0));
        boolean boxes = esp.getBooleanSetting("boxes", true);
        boolean tracers = esp.getBooleanSetting("tracers", false);

        for (LivingEntity living : targets) {
            if (boxes) {
                AABB box = living.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z);
                box(buffer, pose, box, color);
            }
            if (tracers) {
                Vec3 center = living.getBoundingBox().getCenter();
                line(buffer, pose,
                        0.0, -0.12, 0.0,
                        center.x - camera.x, center.y - camera.y, center.z - camera.z,
                        color);
            }
        }
    }

    private static void renderEspLabels(LevelRenderContext ctx, Minecraft mc, Module esp,
            List<LivingEntity> targets, Vec3 camera) {
        PoseStack stack = ctx.poseStack();
        for (LivingEntity living : targets) {
            StringBuilder label = new StringBuilder(living.getName().getString());
            if (esp.getBooleanSetting("showDistance", true)) {
                label.append(String.format(Locale.ROOT, " [%.1fm]", mc.player.distanceTo(living)));
            }
            if (esp.getBooleanSetting("showHealth", false)) {
                label.append(String.format(Locale.ROOT, " [%.1f HP]", living.getHealth()));
            }

            stack.pushPose();
            stack.translate(living.getX() - camera.x, living.getY() - camera.y, living.getZ() - camera.z);
            ctx.submitNodeCollector().submitNameTag(
                    stack,
                    new Vec3(0.0, living.getBbHeight() + 0.35, 0.0),
                    0,
                    Component.literal(label.toString()),
                    true,
                    LightCoordsUtil.FULL_BRIGHT,
                    ctx.levelState().cameraRenderState);
            stack.popPose();
        }
    }

    private static void renderTrajectory(Minecraft mc, Module module, Vec3 camera, PoseStack.Pose pose, VertexConsumer buffer) {
        if (module.getBooleanSetting("onlyWhenUsing", false) && !mc.options.keyUse.isDown()) {
            return;
        }

        ItemStack stack = mc.player.getMainHandItem();
        if (!isProjectile(stack.getItem())) {
            stack = mc.player.getOffhandItem();
        }
        Item item = stack.getItem();
        if (!isProjectile(item)) {
            return;
        }

        double basePower = launchPower(item) * module.getNumberSetting("powerScale", 1.0);
        Vec3 pos = mc.player.getEyePosition();
        Vec3 velocity = mc.player.getLookAngle().normalize().scale(basePower);
        int steps = (int) Math.round(module.getNumberSetting("steps", 80.0));
        double gravity = module.getNumberSetting("gravity", 0.05);
        double drag = module.getNumberSetting("drag", 0.99);
        boolean collisionEnabled = module.getBooleanSetting("collision", true);
        boolean landingMarker = module.getBooleanSetting("landingMarker", true);
        int color = color(
                module.getNumberSetting("red", 255.0),
                module.getNumberSetting("green", 220.0),
                module.getNumberSetting("blue", 80.0),
                240.0);

        for (int i = 0; i < steps; i++) {
            Vec3 next = pos.add(velocity);
            Vec3 end = next;
            boolean collided = false;
            if (collisionEnabled) {
                HitResult hit = mc.level.clip(new ClipContext(pos, next,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.BLOCK) {
                    end = hit.getLocation();
                    collided = true;
                }
            }

            line(buffer, pose,
                    pos.x - camera.x, pos.y - camera.y, pos.z - camera.z,
                    end.x - camera.x, end.y - camera.y, end.z - camera.z,
                    color);

            if (collided) {
                if (landingMarker) {
                    marker(buffer, pose, end.subtract(camera), module.getNumberSetting("markerSize", 0.22), color);
                }
                break;
            }

            pos = next;
            velocity = new Vec3(velocity.x * drag, velocity.y * drag - gravity, velocity.z * drag);
            if (pos.y < mc.level.getMinY() - 16) {
                break;
            }
        }
    }

    private static void renderFreecamBodyMarker(Minecraft mc, Freecam freecam, Vec3 camera,
            PoseStack.Pose pose, VertexConsumer buffer) {
        Vec3 anchor = freecam.getBodyAnchor();
        if (anchor == null) {
            return;
        }
        Vec3 delta = anchor.subtract(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        AABB body = mc.player.getBoundingBox().move(delta).inflate(0.05).move(-camera.x, -camera.y, -camera.z);
        int markerColor = color(255, 85, 170, 245);
        box(buffer, pose, body, markerColor);
        Vec3 center = new Vec3((body.minX + body.maxX) * 0.5, body.maxY + 0.18, (body.minZ + body.maxZ) * 0.5);
        marker(buffer, pose, center, 0.18, markerColor);
    }

    private static boolean isProjectile(Item item) {
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT
                || item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG
                || item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION
                || item == Items.LINGERING_POTION;
    }

    private static double launchPower(Item item) {
        if (item == Items.BOW || item == Items.CROSSBOW) {
            return 3.0;
        }
        if (item == Items.TRIDENT) {
            return 2.5;
        }
        if (item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            return 0.75;
        }
        return 1.5;
    }

    private static void marker(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, double size, int color) {
        line(buffer, pose, p.x - size, p.y, p.z, p.x + size, p.y, p.z, color);
        line(buffer, pose, p.x, p.y - size, p.z, p.x, p.y + size, p.z, color);
        line(buffer, pose, p.x, p.y, p.z - size, p.x, p.y, p.z + size, color);
        line(buffer, pose, p.x - size * 0.7, p.y, p.z - size * 0.7,
                p.x + size * 0.7, p.y, p.z + size * 0.7, color);
        line(buffer, pose, p.x - size * 0.7, p.y, p.z + size * 0.7,
                p.x + size * 0.7, p.y, p.z - size * 0.7, color);
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

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
            double ax, double ay, double az, double bx, double by, double bz, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >>> 24) & 0xFF;
        float nx = (float) (bx - ax);
        float ny = (float) (by - ay);
        float nz = (float) (bz - az);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0001f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        buffer.addVertex(pose, (float) ax, (float) ay, (float) az)
                .setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
        buffer.addVertex(pose, (float) bx, (float) by, (float) bz)
                .setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(1.0f);
    }

    private static int color(double red, double green, double blue, double alpha) {
        int r = (int) Math.max(0, Math.min(255, Math.round(red)));
        int g = (int) Math.max(0, Math.min(255, Math.round(green)));
        int b = (int) Math.max(0, Math.min(255, Math.round(blue)));
        int a = (int) Math.max(0, Math.min(255, Math.round(alpha)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
