package me.mrhakan.agalarhack.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

/**
 * Reads back what the client's chat is actually showing.
 *
 * <p>Three modules here decide what chat looks like, and none of them can be checked without seeing
 * the result: a filtered line is one that never arrives, and a timestamp is a prefix on one that
 * does. {@code ChatComponent} keeps its messages in a private field with no accessor, so this is
 * reflection - acceptable in a test against the development jar, where the names are Mojang's own
 * and stable for the version.
 *
 * <p>It fails loudly rather than returning nothing if the field moves. A helper that quietly reports
 * an empty chat would turn every scenario built on it into one that passes for the wrong reason.
 */
final class ChatView {
    private ChatView() { }

    private static Field allMessages;

    /** Every line currently in the chat, oldest first, as plain text with formatting dropped. */
    static List<String> lines(Minecraft client) {
        ChatComponent chat = client.gui.hud.getChat();
        List<String> text = new ArrayList<>();
        for (GuiMessage message : messages(chat)) {
            text.add(message.content().getString());
        }
        return text;
    }

    /** True when some line contains the text; chat carries prefixes and colours around it. */
    static boolean contains(Minecraft client, String fragment) {
        return lines(client).stream().anyMatch(line -> line.contains(fragment));
    }

    @SuppressWarnings("unchecked")
    private static List<GuiMessage> messages(ChatComponent chat) {
        try {
            if (allMessages == null) {
                allMessages = ChatComponent.class.getDeclaredField("allMessages");
                allMessages.setAccessible(true);
            }
            return (List<GuiMessage>) allMessages.get(chat);
        } catch (ReflectiveOperationException | RuntimeException unreachable) {
            throw new AssertionError("Cannot read ChatComponent.allMessages; the field moved in this "
                    + "Minecraft version and every chat scenario is now checking nothing", unreachable);
        }
    }
}
