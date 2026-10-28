package dev.nightbeam.donutessentials.player;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Display names: the nickname when a player has one (rendered from MiniMessage), else the name. Also keeps the
 * player's display name and tab list name in step with the nickname and AFK state.
 */
public final class Names {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final DonutEssentialsPlugin plugin;

    public Names(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public Component display(Player player) {
        return display(plugin.players().getIfOnline(player.getUniqueId()), player.getName());
    }

    public Component display(PlayerData data, String fallbackName) {
        if (data != null && data.nickname() != null) {
            try {
                String prefix = plugin.getConfig().getString("chat.nickname-prefix", "");
                return MINI.deserialize((prefix == null ? "" : prefix) + data.nickname());
            } catch (Exception ignored) {
                return Component.text(data.nickname());
            }
        }
        return Component.text(data != null ? data.name() : fallbackName);
    }

    public String plain(Player player) {
        return PlainTextComponentSerializer.plainText().serialize(display(player));
    }

    /** Sets the display name and tab list name from the nickname and AFK state. Call on the player's thread. */
    public void refresh(Player player) {
        PlayerData data = plugin.players().getIfOnline(player.getUniqueId());
        if (data == null) {
            return;
        }
        Component display = display(data, player.getName());
        player.displayName(data.nickname() == null ? null : display);
        boolean nickInTab = plugin.getConfig().getBoolean("chat.nickname-in-tab", true) && data.nickname() != null;
        boolean afkTag = data.afk() && plugin.getConfig().getBoolean("afk.tab-tag", true);
        if (!nickInTab && !afkTag) {
            player.playerListName(null);
            return;
        }
        Component name = nickInTab ? display : Component.text(player.getName());
        if (afkTag) {
            name = plugin.messages().component("afk.tab-prefix").append(name);
        }
        player.playerListName(name);
    }
}
