package me.mrhakan.agalarhack.services;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** One winner per tick. Requests expire unless renewed; no fabricated server rotations.
 * Client and Smooth use vanilla player rotation. Silent mode is deliberately not exposed
 * until a verified movement-packet integration can preserve camera and interaction ordering.
 */
public final class RotationService {
    public enum Mode { NONE, CLIENT, SMOOTH }
    public record Request(String owner, int priority, Mode mode, float yaw, float pitch,
                          float speed, float maxYawStep, float maxPitchStep, boolean returnRotation) {
        public Request {
            if (owner == null || owner.isBlank() || mode == null || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                    || !Float.isFinite(speed) || speed <= 0 || !Float.isFinite(maxYawStep) || maxYawStep <= 0
                    || !Float.isFinite(maxPitchStep) || maxPitchStep <= 0) throw new IllegalArgumentException("Invalid rotation request");
        }
    }
    private final Minecraft mc;
    private Request pending, active;
    private LocalPlayer captured;
    private float originalYaw, originalPitch, appliedYaw, appliedPitch;
    public RotationService(Minecraft mc) { this.mc = mc; }
    public void request(Request request) {
        if (request.mode() == Mode.NONE) { release(request.owner()); return; }
        if (pending == null || request.priority() > pending.priority() || pending.owner().equals(request.owner())) pending = request;
    }
    public void resolve() {
        Request next = pending; pending = null;
        if (next == null || mc.player == null || mc.level == null || !mc.player.isAlive() || mc.gui.screen() != null) { reset(); return; }
        if (captured != mc.player || active == null || !active.owner().equals(next.owner())) {
            reset(); captured = mc.player;
            originalYaw = captured.getYRot(); originalPitch = captured.getXRot();
        }
        active = next;
        float speed = next.mode() == Mode.SMOOTH ? next.speed() / 20f : 360f;
        appliedYaw = RotationMath.stepYaw(captured.getYRot(), next.yaw(), Math.min(speed, next.maxYawStep()));
        appliedPitch = RotationMath.stepPitch(captured.getXRot(), next.pitch(), Math.min(speed, next.maxPitchStep()));
        captured.setYRot(appliedYaw); captured.setXRot(appliedPitch);
    }
    public void release(String owner) {
        if (pending != null && pending.owner().equals(owner)) pending = null;
        if (active != null && active.owner().equals(owner)) reset();
    }
    public void reset() {
        if (captured != null && active != null && active.returnRotation()
                && Math.abs(TargetSelection.wrapDegrees(captured.getYRot() - appliedYaw)) < 0.001
                && Math.abs(captured.getXRot() - appliedPitch) < 0.001) {
            captured.setYRot(originalYaw); captured.setXRot(originalPitch);
        }
        captured = null; active = null;
    }
    public void clear() { pending = null; reset(); }
}
