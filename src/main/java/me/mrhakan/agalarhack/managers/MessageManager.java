package me.mrhakan.agalarhack.managers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class MessageManager {
    public static String prefix = TextFormatting.GRAY + "[" + TextFormatting.RED + "AGALAR HACK" + TextFormatting.GRAY + "]" + TextFormatting.RESET;

    public static void sendRawMessage(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString(message));
        }
    }

    public static void sendMessagePrefix(String message) {
        sendRawMessage(prefix + " " + message);
    }
}
