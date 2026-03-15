package dev.nightbeam.fssentials.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public final class Text {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Text() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '&' && i + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(i + 1));
                if ((next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')
                        || (next >= 'k' && next <= 'o') || next == 'r') {
                    out.append('\u00A7').append(next);
                    i++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    public static String format(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return color(result);
    }

    public static String formatTimestamp(long millis) {
        return DATE_FORMAT.format(new Date(millis));
    }
}
