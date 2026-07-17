package me.mrhakan.agalarhack.managers;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class MessageManager {
    public static String prefix = Formatting.GRAY + "[" + Formatting.RED + "AGALAR HACK" + Formatting.GRAY + "]" + Formatting.RESET;

    public static void sendRawMessage(String message) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }

    public static void sendMessagePrefix(String message) {
        sendRawMessage(prefix + " " + message);
    }
}
