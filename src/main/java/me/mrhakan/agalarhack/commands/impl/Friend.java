package me.mrhakan.agalarhack.commands.impl;

import java.util.List;

import me.mrhakan.agalarhack.AgalarHackClient;
import me.mrhakan.agalarhack.commands.Command;
import me.mrhakan.agalarhack.managers.MessageManager;
import net.minecraft.ChatFormatting;

public class Friend extends Command {
    public Friend() {
        super("friend", "Manages players excluded from friend-aware combat modules",
                "friend <add|remove|list|clear> [name]", "friends");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2) {
            sendUsage();
            return;
        }

        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> add(args);
            case "remove", "del", "delete" -> remove(args);
            case "list" -> list();
            case "clear" -> clear();
            default -> sendUsage();
        }
    }

    private void add(String[] args) {
        if (args.length != 3) {
            sendUsage();
            return;
        }
        if (AgalarHackClient.FRIEND_MANAGER.add(args[2])) {
            MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "Added " + ChatFormatting.WHITE + args[2]
                    + ChatFormatting.GREEN + " to friends.");
        } else {
            MessageManager.sendMessagePrefix(ChatFormatting.YELLOW + args[2] + " is already a friend or is not a valid name.");
        }
    }

    private void remove(String[] args) {
        if (args.length != 3) {
            sendUsage();
            return;
        }
        if (AgalarHackClient.FRIEND_MANAGER.remove(args[2])) {
            MessageManager.sendMessagePrefix(ChatFormatting.GREEN + "Removed " + ChatFormatting.WHITE + args[2]
                    + ChatFormatting.GREEN + " from friends.");
        } else {
            MessageManager.sendMessagePrefix(ChatFormatting.YELLOW + args[2] + " is not in the friend list.");
        }
    }

    private void list() {
        List<String> friends = AgalarHackClient.FRIEND_MANAGER.getFriends();
        if (friends.isEmpty()) {
            MessageManager.sendMessagePrefix(ChatFormatting.GRAY + "Friend list is empty.");
            return;
        }
        MessageManager.sendMessagePrefix(ChatFormatting.AQUA + "Friends " + ChatFormatting.GRAY + "(" + friends.size() + "): "
                + ChatFormatting.WHITE + String.join(", ", friends));
    }

    private void clear() {
        int removed = AgalarHackClient.FRIEND_MANAGER.clear();
        MessageManager.sendMessagePrefix(removed == 0
                ? ChatFormatting.GRAY + "Friend list was already empty."
                : ChatFormatting.GREEN + "Cleared " + removed + " friend" + (removed == 1 ? "." : "s."));
    }
}
