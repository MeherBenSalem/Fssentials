package dev.nightbeam.donutessentials.chat;

import dev.nightbeam.donutessentials.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Player-typed text with styling. Players with a colour permission may use {@code &} codes, {@code &#RRGGBB} and
 * MiniMessage colour, gradient, rainbow and decoration tags; click, hover, insertion and every other tag stay
 * literal text. Everyone else's text is shown exactly as typed.
 */
public final class ChatStyle {

    /** A MiniMessage that only understands styling tags. */
    static final MiniMessage STYLE = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolvers(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(), StandardTags.rainbow(),
                            StandardTags.reset())
                    .build())
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ChatStyle() {
    }

    /** The text as a component: styled when {@code colours} is true, otherwise literal. */
    public static Component style(String raw, boolean colours) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (!colours) {
            return Component.text(raw);
        }
        try {
            return STYLE.deserialize(Text.legacyToMini(raw));
        } catch (Exception ignored) {
            return Component.text(raw);
        }
    }

    /** Styled input as safe MiniMessage (only styling tags survive), for storing nicknames. */
    public static String safeMiniMessage(String raw) {
        return MiniMessage.miniMessage().serialize(style(raw, true));
    }

    /** What the text reads as without styling. */
    public static String plain(String raw, boolean colours) {
        return PLAIN.serialize(style(raw, colours));
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    /** True when the text has styling (codes or tags) that would change how it looks. */
    public static boolean hasStyling(String raw) {
        return raw != null && !plain(raw, true).equals(raw);
    }
}
