package dev.nightbeam.donutessentials.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chat filter: listed words become stars (or a chosen replacement), whole words only and ignoring case; links
 * can be refused, except to allowed domains. Pure logic, no Bukkit.
 */
public final class WordFilter {

    private static final Pattern LINK = Pattern.compile("(?i)(?<![\\p{L}\\p{N}@])((?:https?://)?(?:[a-z0-9-]+\\.)+"
            + "(?:com|net|org|gg|io|me|xyz|co|tv|ly|us|uk|de|fr|ru|info|biz|dev|app|link|club|online|site|store|tk|ml|eu|nl"
            + "|be|es|it|pl|br|ca|au|cc|to|sh|fun|live|pro|top|world|space|host|shop|ws|in|gl)(?![\\p{L}\\p{N}])(?:[/:?#][^\\s]*)?)");

    private record Rule(Pattern pattern, String replacement) {
    }

    private final List<Rule> rules;
    private final boolean blockLinks;
    private final List<String> allowedLinks;

    public WordFilter(List<String> words, Map<String, String> replacements, boolean blockLinks, List<String> allowedLinks) {
        List<Rule> built = new ArrayList<>();
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                built.add(new Rule(wordPattern(word.trim()), null));
            }
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                built.add(new Rule(wordPattern(entry.getKey().trim()), entry.getValue() == null ? "" : entry.getValue()));
            }
        }
        this.rules = built;
        this.blockLinks = blockLinks;
        List<String> allowed = new ArrayList<>();
        for (String domain : allowedLinks) {
            if (domain != null && !domain.isBlank()) {
                allowed.add(domain.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.allowedLinks = allowed;
    }

    public static WordFilter none() {
        return new WordFilter(List.of(), Map.of(), false, List.of());
    }

    private static Pattern wordPattern(String word) {
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])");
    }

    /** The text with filtered words replaced. */
    public String apply(String text) {
        if (text == null || rules.isEmpty()) {
            return text;
        }
        String out = text;
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(out);
            if (!matcher.find()) {
                continue;
            }
            StringBuilder builder = new StringBuilder();
            do {
                String replacement = rule.replacement() != null ? rule.replacement() : "*".repeat(matcher.group().length());
                matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
            } while (matcher.find());
            matcher.appendTail(builder);
            out = builder.toString();
        }
        return out;
    }

    /** True when links are refused and the text has a link to a domain that is not allowed. */
    public boolean blocksLink(String text) {
        if (!blockLinks || text == null) {
            return false;
        }
        Matcher matcher = LINK.matcher(text);
        while (matcher.find()) {
            String link = matcher.group(1).toLowerCase(Locale.ROOT);
            boolean allowed = false;
            for (String domain : allowedLinks) {
                if (link.contains(domain)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return true;
            }
        }
        return false;
    }
}
