package me.mrhakan.agalarhack.managers;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class MessageManager {
    public static String prefix = ChatFormatting.GRAY + "[" + ChatFormatting.RED + "AGALAR HACK" + ChatFormatting.GRAY + "]" + ChatFormatting.RESET;

    public static void sendRawMessage(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    public static void sendMessagePrefix(String message) {
        sendRawMessage(prefix + " " + message);
    }
}
