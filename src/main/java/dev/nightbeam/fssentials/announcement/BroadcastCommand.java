package dev.nightbeam.fssentials.announcement;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public final class BroadcastCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "server.broadcast";

    private final BroadcastService broadcastService;
    private final MiniMessage miniMessage;

    public BroadcastCommand(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(miniMessage.deserialize("<red>You do not have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        ParsedBroadcast parsed = parseBroadcastInput(args);
        if (parsed == null || parsed.title().isEmpty()) {
            sendUsage(sender);
            return true;
        }

        broadcastService.broadcast(parsed.title(), parsed.subtitle());
        sender.sendMessage(miniMessage.deserialize("<green>Broadcast title sent."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<yellow>Usage: /broadcast <title> [subtitle]"));
        sender.sendMessage(miniMessage.deserialize("<gray>Tip: use '|' to split title and subtitle when both have spaces."));
    }

    private ParsedBroadcast parseBroadcastInput(String[] args) {
        String input = String.join(" ", args).trim();
        if (input.isEmpty()) {
            return null;
        }

        int separatorIndex = input.indexOf('|');
        if (separatorIndex >= 0) {
            String title = input.substring(0, separatorIndex).trim();
            String subtitle = input.substring(separatorIndex + 1).trim();
            return new ParsedBroadcast(title, subtitle.isEmpty() ? null : subtitle);
        }

        if (args.length == 1) {
            return new ParsedBroadcast(args[0], null);
        }

        // Heuristic: if the title sentence ends with punctuation, treat the rest as subtitle.
        for (int i = 0; i < args.length - 1; i++) {
            if (endsSentence(args[i])) {
                String title = String.join(" ", java.util.Arrays.copyOfRange(args, 0, i + 1)).trim();
                String subtitle = String.join(" ", java.util.Arrays.copyOfRange(args, i + 1, args.length)).trim();
                return new ParsedBroadcast(title, subtitle.isEmpty() ? null : subtitle);
            }
        }

        return new ParsedBroadcast(input, null);
    }

    private boolean endsSentence(String token) {
        return token.endsWith("!") || token.endsWith("?") || token.endsWith(".");
    }

    private record ParsedBroadcast(String title, String subtitle) {
    }
}
