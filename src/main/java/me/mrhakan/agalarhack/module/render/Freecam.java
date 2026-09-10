package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera onto a client-only armor-stand entity while keeping the real
 * player anchored. No fake movement packets or server-side teleporting are used.
 */
public class Freecam extends Module {
    private ArmorStand camera;
    private Entity previousCamera;
    private Vec3 playerAnchor;

    public Freecam() {
        super("Freecam", Category.RENDER, "Detaches the camera so you can look around without moving the real player");
    }

    @Override
    public void selfSettings() {
        addNumberSetting("speed", 0.65, 0.05, 3.0, "Camera movement speed per tick");
        addNumberSetting("sprintMultiplier", 2.5, 1.0, 8.0, "Speed multiplier while the sprint key is held");
        addBooleanSetting("freezePlayer", true, "Keep the real player anchored while freecam is active");
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        previousCamera = mc.getCameraEntity();
        playerAnchor = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
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

        double yaw = Math.toRadians(camera.getYRot());
        double dx = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
        double dz = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed;
        camera.setPos(camera.getX() + dx, camera.getY() + vertical * speed, camera.getZ() + dz);
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.setCameraEntity(previousCamera != null ? previousCamera : mc.player);
        }
        camera = null;
        previousCamera = null;
        playerAnchor = null;
    }

    @Override
    public void onDisconnect() {
        onDisable();
    }
}
