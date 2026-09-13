package me.mrhakan.agalarhack.ui;

import net.minecraft.client.gui.screens.Screen;

/** Client screen hierarchy; transient edit sessions can clean up when the whole branch closes. */
public interface ClientScreen {
    default Screen parentScreen() { return null; }
    default void abandoned() { }
}
