package dev.nightbeam.donutessentials.config;

import dev.nightbeam.donutessentials.util.Text;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * messages.yml: MiniMessage templates (legacy {@code &} codes also work). Placeholders are passed as key/value
 * pairs; a {@link Component} value is inserted as is, anything else as plain text that players cannot style.
 * An empty message sends nothing, so any message can be switched off.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private volatile FileConfiguration yaml = new YamlConfiguration();
    private volatile String prefixRaw = "";

    public void load(FileConfiguration yaml) {
        this.yaml = yaml == null ? new YamlConfiguration() : yaml;
        String loaded = this.yaml.getString("prefix");
        this.prefixRaw = loaded == null ? "" : loaded;
    }

    public FileConfiguration yaml() {
        return yaml;
    }

    /** The template at a path, or the path itself when missing (so a typo is visible, not silent). */
    public String raw(String path) {
        String value = yaml.getString(path);
        return value == null ? path : value;
    }

    public boolean isEmpty(String path) {
        String value = yaml.getString(path);
        return value != null && value.isEmpty();
    }

    public Component component(String path, Object... keyValues) {
        return render(raw(path), keyValues);
    }

    public List<Component> list(String path, Object... keyValues) {
        List<String> lines = yaml.getStringList(path);
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(render(line, keyValues));
        }
        return out;
    }

    /** Each line of a list message, or the single message when it is a plain string. */
    public List<Component> lines(String path, Object... keyValues) {
        if (yaml.isList(path)) {
            return list(path, keyValues);
        }
        String value = yaml.getString(path);
        return value == null || value.isEmpty() ? List.of() : List.of(render(value, keyValues));
    }

    /** Sends every line of a list message (or one line), without the prefix. */
    public void sendLines(CommandSender sender, String path, Object... keyValues) {
        if (sender == null) {
            return;
        }
        for (Component line : lines(path, keyValues)) {
            sender.sendMessage(line);
        }
    }

    public Component prefixed(String path, Object... keyValues) {
        return render(prefixRaw + raw(path), keyValues);
    }

    public void send(CommandSender sender, String path, Object... keyValues) {
        if (sender == null || isEmpty(path)) {
            return;
        }
        sender.sendMessage(prefixed(path, keyValues));
    }

    /** Sends without the prefix (multi-line info blocks). */
    public void sendPlain(CommandSender sender, String path, Object... keyValues) {
        if (sender == null || isEmpty(path)) {
            return;
        }
        sender.sendMessage(component(path, keyValues));
    }

    public void actionBar(Player player, String path, Object... keyValues) {
        if (player == null || isEmpty(path)) {
            return;
        }
        player.sendActionBar(component(path, keyValues));
    }

    public void title(Player player, String titlePath, String subtitlePath, long stayTicks, Object... keyValues) {
        if (player == null) {
            return;
        }
        Component title = isEmpty(titlePath) ? Component.empty() : component(titlePath, keyValues);
        Component subtitle = subtitlePath == null || isEmpty(subtitlePath) ? Component.empty() : component(subtitlePath, keyValues);
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ZERO,
                Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(250))));
    }

    /** A clickable chat button that runs a command; its look and hover text come from messages.yml. */
    public Component button(String path, String command, Object... keyValues) {
        Component label = component("buttons." + path + ".label", keyValues);
        Component hover = component("buttons." + path + ".hover", keyValues);
        return label.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(hover));
    }

    /** Sends a component built elsewhere, after the prefix. */
    public void sendComponent(CommandSender sender, Component component) {
        if (sender != null) {
            sender.sendMessage(render(prefixRaw).append(component));
        }
    }

    public Component render(String template, Object... keyValues) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        String source = Text.legacyToMini(template);
        try {
            return MINI.deserialize(source, resolver(keyValues));
        } catch (Exception ignored) {
            return Component.text(template);
        }
    }

    /** An item name or lore line: not italic unless the template says so. */
    public static Component item(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> item(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static TagResolver resolver(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            Object value = keyValues[i + 1];
            if (value instanceof Component component) {
                builder.resolver(Placeholder.component(key, component));
            } else if (value instanceof TagResolver resolver) {
                builder.resolver(resolver);
            } else {
                builder.resolver(Placeholder.unparsed(key, value == null ? "" : String.valueOf(value)));
            }
        }
        return builder.build();
    }
}
