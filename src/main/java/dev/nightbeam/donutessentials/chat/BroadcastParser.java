package dev.nightbeam.donutessentials.chat;

import java.util.Arrays;

/**
 * Splits {@code /broadcast} arguments into a title and a message: {@code Title | message}; without a bar, the first
 * sentence (ending in . ! or ?) is the title and the rest the message; otherwise everything is the title.
 */
public final class BroadcastParser {

    public record Parsed(String title, String message) {
    }

    private BroadcastParser() {
    }

    public static Parsed parse(String[] args) {
        String input = String.join(" ", args).trim();
        if (input.isEmpty()) {
            return null;
        }
        int bar = input.indexOf('|');
        if (bar >= 0) {
            String title = input.substring(0, bar).trim();
            String message = input.substring(bar + 1).trim();
            return title.isEmpty() && message.isEmpty() ? null : new Parsed(title, message);
        }
        for (int i = 0; i < args.length - 1; i++) {
            String token = args[i];
            if (token.endsWith(".") || token.endsWith("!") || token.endsWith("?")) {
                String title = String.join(" ", Arrays.copyOfRange(args, 0, i + 1)).trim();
                String message = String.join(" ", Arrays.copyOfRange(args, i + 1, args.length)).trim();
                return new Parsed(title, message);
            }
        }
        return new Parsed(input, "");
    }
}
