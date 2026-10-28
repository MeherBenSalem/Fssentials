package dev.nightbeam.donutessentials.rules;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.config.Messages;
import dev.nightbeam.donutessentials.menu.Dialogs;
import dev.nightbeam.donutessentials.player.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * The rules: shown by /rules, and, with {@code rules.require-accept}, a screen every player must accept before they
 * play (a native dialog on 1.21.7+, chat buttons before). Acceptance is stored as the rules version a player accepted,
 * so raising {@code rules.version} asks everyone again.
 */
final class RulesService {

    private final DonutEssentialsPlugin plugin;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean requireAccept;
    private volatile boolean block = true;
    private volatile int version = 1;

    RulesService(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    void load(ConfigurationSection section) {
        requireAccept = section != null && section.getBoolean("require-accept", false);
        block = section == null || section.getBoolean("block-until-accepted", true);
        version = section == null ? 1 : Math.max(1, section.getInt("version", 1));
        if (!requireAccept) {
            pending.clear();
        }
    }

    boolean requireAccept() {
        return requireAccept;
    }

    /** True when the player still has to accept the rules. */
    boolean isPending(Player player) {
        return pending.contains(player.getUniqueId());
    }

    /** True when the player has to accept and may not play until then. */
    boolean isBlocked(Player player) {
        return block && pending.contains(player.getUniqueId());
    }

    /** Whether a stored acceptance covers the current rules. */
    static boolean accepted(long acceptedVersion, int currentVersion) {
        return acceptedVersion >= currentVersion;
    }

    /** On join (and after a reload): marks the player as pending when they have not accepted these rules. */
    boolean check(Player player) {
        if (!requireAccept) {
            pending.remove(player.getUniqueId());
            return false;
        }
        PlayerData data = plugin.players().get(player);
        if (accepted(data.rulesAccepted(), version)) {
            pending.remove(player.getUniqueId());
            return false;
        }
        pending.add(player.getUniqueId());
        return true;
    }

    void forget(UUID uuid) {
        pending.remove(uuid);
    }

    /** The rules as lines. */
    List<Component> lines() {
        List<Component> out = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("rules.lines")) {
            out.add(plugin.messages().render(line));
        }
        return out;
    }

    /** Sends the rules in chat. */
    void show(CommandSender sender) {
        for (Component line : lines()) {
            sender.sendMessage(line);
        }
    }

    /** The accept screen: a dialog when available, else the rules in chat with buttons. Call on the player's thread. */
    void prompt(Player player) {
        Messages messages = plugin.messages();
        Dialogs dialogs = plugin.input().dialogs();
        if (dialogs != null) {
            try {
                List<Component> body = new ArrayList<>(lines());
                if (!messages.isEmpty("rules.dialog-footer")) {
                    body.add(messages.component("rules.dialog-footer"));
                }
                dialogs.notice(player, messages.component("rules.dialog-title"), body, messages.component("rules.dialog-accept"),
                        messages.component("rules.dialog-decline"),
                        () -> plugin.scheduler().forPlayer(player, () -> accept(player)),
                        () -> plugin.scheduler().forPlayer(player, () -> decline(player)));
                return;
            } catch (Throwable error) {
                plugin.getLogger().warning("The rules dialog failed, showing the rules in chat instead: " + error);
            }
        }
        show(player);
        messages.send(player, "rules.accept-prompt");
        player.sendMessage(messages.component("rules.chat-buttons",
                "accept", messages.button("rules-accept", "/rules accept"),
                "decline", messages.button("rules-decline", "/rules decline")));
    }

    /** Records acceptance of the current rules. */
    void accept(Player player) {
        if (!isPending(player)) {
            plugin.messages().send(player, "rules.already-accepted");
            return;
        }
        PlayerData data = plugin.players().get(player);
        data.rulesAccepted(version);
        plugin.players().save(data);
        pending.remove(player.getUniqueId());
        plugin.messages().send(player, "rules.accepted");
        plugin.sounds().play(player, "rules-accepted");
    }

    /** Leaves the server: rules not accepted. */
    void decline(Player player) {
        if (!isPending(player)) {
            plugin.messages().send(player, "rules.already-accepted");
            return;
        }
        player.kick(plugin.messages().component("rules.kick"));
    }
}
