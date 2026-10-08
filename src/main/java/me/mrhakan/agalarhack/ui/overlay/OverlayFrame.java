package me.mrhakan.agalarhack.ui.overlay;

import me.mrhakan.agalarhack.ui.ViewCulling;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * What every overlay needs from the frame being drawn.
 *
 * @param camera  the camera position; overlay geometry is written relative to it
 * @param culling the frame's view volume, built once and shared by every box-shaped overlay
 */
public record OverlayFrame(Minecraft mc, LevelRenderContext ctx, Vec3 camera, ViewCulling culling) {
}
