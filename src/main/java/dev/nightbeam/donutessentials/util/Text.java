package dev.nightbeam.donutessentials.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts legacy {@code &} / {@code §} colour codes and {@code &#RRGGBB} to MiniMessage, so both styles work. */
public final class Text {

    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])|[&§]#([0-9a-fA-F]{6})");
    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"));

    private Text() {
    }

    public static boolean hasLegacy(String text) {
        return text != null && LEGACY.matcher(text).find();
    }

    /** A colour code resets formatting, as it does in legacy text; {@code &r} resets everything. */
    public static String legacyToMini(String text) {
        if (!hasLegacy(text)) {
            return text;
        }
        Matcher matcher = LEGACY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(2) != null) {
                replacement = "<reset><#" + matcher.group(2).toLowerCase() + ">";
            } else {
                char code = Character.toLowerCase(matcher.group(1).charAt(0));
                if (code == 'r') {
                    replacement = "<reset>";
                } else if (code <= 'f' && (Character.isDigit(code) || code >= 'a')) {
                    replacement = "<reset><" + CODES.get(code) + ">";
                } else {
                    replacement = "<" + CODES.get(code) + ">";
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Escapes MiniMessage tag openers in player text so it shows literally. */
    public static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("<", "\\<");
    }
}
