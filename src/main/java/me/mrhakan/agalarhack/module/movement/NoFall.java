package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {

	public NoFall() {
		super("NoFall", Category.MOVEMENT, "Prevents fall damage");
	}

	@Override
	public void onUpdate() {
		if (mc.player.fallDistance > 2.0) {
			mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
		}
	}
}
