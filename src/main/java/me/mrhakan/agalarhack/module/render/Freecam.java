package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera onto a client-only armor-stand entity while keeping the real
 * player anchored. Position packets are not spoofed or replaced.
 */
public class Freecam extends Module {
    private ArmorStand camera;
    private Entity previousCamera;
    private Vec3 playerAnchor;
    private Vec3 cameraTarget;

    public Freecam() {
        super("Freecam", Category.RENDER, "Detaches and smoothly moves the camera without moving the real player");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("speed", 0.65, 0.05, 3.0, "Camera movement speed per tick");
        addNumberSetting("sprintMultiplier", 2.5, 1.0, 8.0, "Speed multiplier while the sprint key is held");
        addNumberSetting("smoothing", 0.45, 0.05, 1.0, "Position interpolation; 1 is immediate and lower values are smoother");
        addBooleanSetting("freezePlayer", true, "Keep the real player's position anchored while freecam is active");
        addBooleanSetting("bodyMarker", true, "Draw a marker around the real player body while the camera is detached");
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        previousCamera = mc.getCameraEntity();
        playerAnchor = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        cameraTarget = playerAnchor;
        camera = new ArmorStand(mc.level, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        camera.setInvisible(true);
        camera.setNoGravity(true);
        camera.setYRot(mc.player.getYRot());
        camera.setXRot(mc.player.getXRot());
        mc.setCameraEntity(camera);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.level == null || camera == null) {
            return;
        }

        // Vanilla mouse input still updates the local player's look rotation. Mirror
        // that rotation onto the detached camera so normal mouse-look keeps working.
        camera.setYRot(mc.player.getYRot());
        camera.setXRot(mc.player.getXRot());

        if (getBooleanSetting("freezePlayer", true) && playerAnchor != null) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setPos(playerAnchor.x, playerAnchor.y, playerAnchor.z);
        }

        double forward = (mc.options.keyUp.isDown() ? 1.0 : 0.0) - (mc.options.keyDown.isDown() ? 1.0 : 0.0);
        double strafe = (mc.options.keyRight.isDown() ? 1.0 : 0.0) - (mc.options.keyLeft.isDown() ? 1.0 : 0.0);
        double vertical = (mc.options.keyJump.isDown() ? 1.0 : 0.0) - (mc.options.keyShift.isDown() ? 1.0 : 0.0);

        double length = Math.sqrt(forward * forward + strafe * strafe);
        if (length > 1.0) {
            forward /= length;
            strafe /= length;
        }

        double speed = getNumberSetting("speed", 0.65);
        if (mc.options.keySprint.isDown()) {
            speed *= getNumberSetting("sprintMultiplier", 2.5);
        }

        if (cameraTarget == null) {
            cameraTarget = new Vec3(camera.getX(), camera.getY(), camera.getZ());
        }
        double yaw = Math.toRadians(camera.getYRot());
        double dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
        double dz = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed;
        cameraTarget = cameraTarget.add(dx, vertical * speed, dz);

        double smoothing = getNumberSetting("smoothing", 0.45);
        Vec3 current = new Vec3(camera.getX(), camera.getY(), camera.getZ());
        Vec3 next = current.lerp(cameraTarget, smoothing);
        camera.setPos(next.x, next.y, next.z);
    }

    public Vec3 getBodyAnchor() {
        return playerAnchor;
    }

    public boolean shouldRenderBodyMarker() {
        return isToggled() && getBooleanSetting("bodyMarker", true) && playerAnchor != null;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.setCameraEntity(previousCamera != null ? previousCamera : mc.player);
        }
        camera = null;
        previousCamera = null;
        playerAnchor = null;
        cameraTarget = null;
    }

    @Override
    public void onDisconnect() {
        onDisable();
    }
}
