package dev.nightbeam.donutessentials.rules;

import dev.nightbeam.donutessentials.config.Messages;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Server links in the pause menu (Minecraft 1.21+), reached by reflection so older servers skip them quietly. Only
 * the links added here are removed again on reload and disable.
 */
final class ServerLinksSupport {

    private final Logger logger;
    private final List<Object> added = new ArrayList<>();
    private Object links;
    private Method remove;

    ServerLinksSupport(Logger logger) {
        this.logger = logger;
    }

    /** True when the running server has server links. */
    static boolean supported() {
        try {
            Bukkit.getServer().getClass().getMethod("getServerLinks");
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    /** Replaces the links this plugin added with the ones in {@code server-links}. Returns how many were added. */
    int apply(ConfigurationSection section, Messages messages) {
        clear();
        if (section == null || !section.getBoolean("enabled", false) || !supported()) {
            return 0;
        }
        ConfigurationSection entries = section.getConfigurationSection("links");
        if (entries == null) {
            return 0;
        }
        try {
            links = Bukkit.getServer().getClass().getMethod("getServerLinks").invoke(Bukkit.getServer());
            Class<?> type = Class.forName("org.bukkit.ServerLinks");
            Class<?> linkType = Class.forName("org.bukkit.ServerLinks$ServerLink");
            remove = type.getMethod("removeLink", linkType);
            Method addComponent = find(type, Component.class);
            Method addText = find(type, String.class);
            for (String key : entries.getKeys(false)) {
                ConfigurationSection entry = entries.getConfigurationSection(key);
                String label = entry == null ? key : entry.getString("label", key);
                URI uri = uri(entry == null ? null : entry.getString("url"));
                if (uri == null) {
                    logger.warning("server-links.links." + key + ": the url must start with http:// or https://");
                    continue;
                }
                Component display = messages.render(label);
                Object link;
                if (addComponent != null) {
                    link = addComponent.invoke(links, display, uri);
                } else if (addText != null) {
                    link = addText.invoke(links, messages.plain(display), uri);
                } else {
                    return 0;
                }
                if (link != null) {
                    added.add(link);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            logger.warning("Could not set the server links: " + error);
        }
        return added.size();
    }

    /** Removes the links this plugin added. */
    void clear() {
        if (links != null && remove != null) {
            for (Object link : added) {
                try {
                    remove.invoke(links, link);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Gone already.
                }
            }
        }
        added.clear();
    }

    private static Method find(Class<?> type, Class<?> labelType) {
        try {
            return type.getMethod("addLink", labelType, URI.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** The URL as a URI when it is an http(s) address with a host, otherwise null. */
    static URI uri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null;
        }
        try {
            URI uri = URI.create(text);
            return uri.getHost() == null ? null : uri;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
