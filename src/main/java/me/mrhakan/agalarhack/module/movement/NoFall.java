package me.mrhakan.agalarhack.module.movement;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;
import net.minecraft.network.play.client.CPacketPlayer;

public class NoFall extends Module {

	public NoFall() {
		super("NoFall", Category.MOVEMENT, "Prevents fall damage");
	}

	@Override
	public void onUpdate() {
		if (mc.player.fallDistance > 2.0f) {
			mc.player.connection.sendPacket(new CPacketPlayer(true));
		}
	}
}
