package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {

	private boolean sentForCurrentFall;

	public NoFall() {
		super("NoFall", Category.MOVEMENT, "Reports a grounded state once per fall after a configurable distance");
	}

	@Override
	public void selfSettings() {
		addNumberSetting("threshold", 3.0, 2.0, 20.0, "Fall distance before the grounded packet is sent");
	}

	@Override
	public void onEnable() {
		sentForCurrentFall = false;
	}

	@Override
	public void onDisable() {
		sentForCurrentFall = false;
	}

	@Override
	public void onUpdate() {
		if (mc.player == null || mc.player.connection == null) {
			return;
		}

		double threshold = getNumberSetting("threshold", 3.0);
		if (mc.player.onGround() || mc.player.fallDistance < threshold) {
			sentForCurrentFall = false;
			return;
		}

		if (!sentForCurrentFall) {
			mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
			sentForCurrentFall = true;
		}
	}
}
