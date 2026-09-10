package me.mrhakan.agalarhack.module.render;

import me.mrhakan.agalarhack.module.Category;
import me.mrhakan.agalarhack.module.Module;

/**
 * Toggles the XYZ readout rendered by the client HUD.
 */
public class Coordinates extends Module {

	public Coordinates() {
		super("Coordinates", Category.RENDER, "Shows your current XYZ coordinates on the HUD");
	}
}
