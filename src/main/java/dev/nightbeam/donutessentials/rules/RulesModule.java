package dev.nightbeam.donutessentials.rules;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.hook.Placeholders;
import dev.nightbeam.donutessentials.module.Module;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * The rules (/rules, and an accept screen for new players), server links in the pause menu (1.21+) and the message
 * of the day (/motd, and on join).
 */
public final class RulesModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private RulesService rules;
    private ServerLinksSupport links;
    private RulesListener listener;
    private volatile Set<String> rulesLabels = Set.of("rules");

    public RulesModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "rules";
    }

    @Override
    public void enable() {
        rules = new RulesService(plugin);
        links = new ServerLinksSupport(plugin.getLogger());
        listener = new RulesListener(plugin, this);
        apply();
    }

    @Override
    public void reload() {
        apply();
        // Rules switched on (or a new version) take effect for players already online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().runForEntity(player, () -> {
                if (rules.check(player)) {
                    rules.prompt(player);
                }
            });
        }
    }

    @Override
    public void disable() {
        if (links != null) {
            plugin.scheduler().global(links::clear);
        }
    }

    private void apply() {
        rules.load(plugin.getConfig().getConfigurationSection("rules"));
        Set<String> labels = new HashSet<>();
        labels.add("rules");
        for (String alias : plugin.loadFile("commands.yml").getStringList("rules.aliases")) {
            labels.add(alias.trim().toLowerCase(Locale.ROOT));
        }
        rulesLabels = Set.copyOf(labels);
        plugin.scheduler().global(() -> links.apply(plugin.getConfig().getConfigurationSection("server-links"), plugin.messages()));
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new RulesCommands.Rules(plugin, this), new RulesCommands.Motd(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(listener);
    }

    RulesService rules() {
        return rules;
    }

    Set<String> rulesLabels() {
        return rulesLabels;
    }

    /** Sends the message of the day: {@code <player>}, {@code <display>}, {@code <online>}, {@code <max>} and PlaceholderAPI. */
    void sendMotd(CommandSender sender) {
        Player player = sender instanceof Player p ? p : null;
        String name = player == null ? plugin.messages().raw("common.console") : player.getName();
        Component display = player == null ? Component.text(name) : plugin.names().display(player);
        for (String line : plugin.getConfig().getStringList("motd.lines")) {
            String text = player == null ? line : Placeholders.apply(player, line);
            sender.sendMessage(plugin.messages().render(text, "player", name, "display", display,
                    "online", plugin.vanish().visibleCount(), "max", Bukkit.getMaxPlayers()));
        }
    }
}
