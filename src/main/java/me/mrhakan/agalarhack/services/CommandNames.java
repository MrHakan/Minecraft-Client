package me.mrhakan.agalarhack.services;

import me.mrhakan.agalarhack.commands.Command;

/**
 * Decides whether a command's name or any of its aliases is already answered by something else.
 *
 * <p>Its own class because {@code CommandManager} cannot be unit tested: its static initialiser
 * opens the alias and macro files through {@code FabricLoader.getConfigDir()}, so touching the class
 * at all needs a running loader. The check itself is list logic with nothing game-shaped in it, and
 * it became worth testing the moment addons made it reachable by someone other than whoever wrote
 * this client.
 *
 * <p>Matching is case-insensitive because command lookup is, and a check stricter than the lookup it
 * guards would let {@code .Panic} shadow {@code .panic}.
 */
public final class CommandNames {
    private CommandNames() { }

    /**
     * @return the word that is already taken, or null when the candidate is free to register. Both
     *         the candidate's name and each of its aliases are checked against both the name and the
     *         aliases of everything already registered.
     */
    public static String collision(Iterable<Command> registered, Command candidate) {
        if (candidate == null) return null;
        for (String word : words(candidate)) {
            if (word == null || word.isBlank()) continue;
            for (Command existing : registered) {
                if (existing != null && existing != candidate && existing.matches(word)) {
                    return word;
                }
            }
        }
        return null;
    }

    private static String[] words(Command command) {
        String[] aliases = command.getAliases();
        String[] all = new String[(aliases == null ? 0 : aliases.length) + 1];
        all[0] = command.getCommand();
        if (aliases != null) System.arraycopy(aliases, 0, all, 1, aliases.length);
        return all;
    }
}
