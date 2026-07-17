package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

public class Fullbright extends Module {

	private float previousGamma = 1.0f;

	public Fullbright() {
		super("Fullbright", Category.RENDER, "Lights up the whole world");
	}

	@Override
	public void onEnable() {
		super.onEnable();
		previousGamma = mc.gameSettings.gammaSetting;
		mc.gameSettings.gammaSetting = 100.0f;
	}

	@Override
	public void onDisable() {
		super.onDisable();
		mc.gameSettings.gammaSetting = previousGamma;
	}
}
