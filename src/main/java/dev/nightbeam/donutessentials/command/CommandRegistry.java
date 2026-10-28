package dev.nightbeam.donutessentials.command;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Registers commands in the server's command map at start-up, as commands.yml describes them: switched off commands
 * are never registered, aliases are added, and names that vanilla or Bukkit commands use are left to them unless
 * listed in {@code commands.take-over-vanilla}. Every command stays reachable as {@code /donutessentials:<label>}.
 */
public final class CommandRegistry {

    public static final String PREFIX = "donutessentials";

    /** Vanilla, Bukkit, Spigot and Paper command names (1.20 – 26.x). */
    static final Set<String> SERVER_COMMANDS = Set.of("advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear",
            "clone", "damage", "data", "datapack", "debug", "defaultgamemode", "deop", "dialog", "difficulty", "effect", "enchant",
            "execute", "experience", "xp", "fetchprofile", "fill", "fillbiome", "forceload", "function", "gamemode", "gamerule",
            "give", "help", "item", "jfr", "kick", "kill", "list", "locate", "loot", "me", "msg", "tell", "w", "op", "pardon",
            "pardon-ip", "particle", "perf", "place", "playsound", "publish", "random", "recipe", "reload", "return", "ride",
            "rotate", "save-all", "save-off", "save-on", "say", "schedule", "scoreboard", "seed", "setblock", "setidletimeout",
            "setworldspawn", "spawnpoint", "spectate", "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg",
            "tm", "teleport", "tp", "tellraw", "test", "tick", "time", "title", "transfer", "trigger", "waypoint", "weather",
            "whitelist", "worldborder", "plugins", "pl", "version", "ver", "about", "rl", "timings", "tps", "mspt", "paper",
            "spigot", "restart", "icanhasbukkit", "callback");

    private final DonutEssentialsPlugin plugin;
    private final Map<String, Wrapper> registered = new LinkedHashMap<>();
    private final List<String> leftToServer = new ArrayList<>();
    private final List<String> taken = new ArrayList<>();
    private YamlConfiguration commandsYml = new YamlConfiguration();
    private Set<String> takeOver = Set.of();

    public CommandRegistry(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reads commands.yml and the take-over list; call before {@link #register}. */
    public void configure(YamlConfiguration commandsYml, List<String> takeOverVanilla) {
        this.commandsYml = commandsYml;
        Set<String> names = new HashSet<>();
        for (String name : takeOverVanilla) {
            names.add(name.toLowerCase(Locale.ROOT));
        }
        this.takeOver = names;
    }

    public void register(List<EssCommand> commands) {
        CommandMap map = Bukkit.getCommandMap();
        for (EssCommand handler : commands) {
            ConfigurationSection section = commandsYml.getConfigurationSection(handler.name());
            if (section != null && !section.getBoolean("enabled", true)) {
                continue;
            }
            List<String> aliases = new ArrayList<>();
            if (section != null) {
                for (String alias : section.getStringList("aliases")) {
                    String clean = alias.trim().toLowerCase(Locale.ROOT);
                    if (!clean.isEmpty() && !clean.equals(handler.name())) {
                        aliases.add(clean);
                    }
                }
            }
            String description = section == null ? "" : section.getString("description", "");
            Wrapper wrapper = new Wrapper(handler, description);
            wrapper.setPermission(handler.permission());
            registered.put(handler.name(), wrapper);
            List<String> labels = new ArrayList<>();
            labels.add(handler.name());
            labels.addAll(aliases);
            List<String> kept = new ArrayList<>();
            for (String label : labels) {
                map.getKnownCommands().put(PREFIX + ":" + label, wrapper);
                if (SERVER_COMMANDS.contains(label) && !takeOver.contains(label)) {
                    leftToServer.add(label);
                    continue;
                }
                Command existing = map.getKnownCommands().get(label);
                if (existing != null && existing != wrapper && !SERVER_COMMANDS.contains(label)) {
                    // Another plugin got there first; ours stays reachable with the prefix.
                    taken.add(label + " (" + owner(existing) + ")");
                    continue;
                }
                map.getKnownCommands().put(label, wrapper);
                kept.add(label);
            }
            wrapper.setAliases(kept.size() > 1 ? kept.subList(1, kept.size()) : List.of());
            wrapper.register(map);
        }
    }

    private static String owner(Command command) {
        if (command instanceof PluginIdentifiableCommand identifiable) {
            return identifiable.getPlugin().getName();
        }
        return "server";
    }

    /** Labels left to vanilla commands (for the start-up line). */
    public List<String> leftToServer() {
        return leftToServer;
    }

    /** Labels another plugin had already registered. */
    public List<String> takenByOthers() {
        return taken;
    }

    public int count() {
        return registered.size();
    }

    public boolean isRegistered(String name) {
        return registered.containsKey(name);
    }

    /** Removes every command this plugin registered (plugin disable). */
    public void unregisterAll() {
        try {
            CommandMap map = Bukkit.getCommandMap();
            map.getKnownCommands().entrySet().removeIf(entry -> entry.getValue() instanceof Wrapper);
            for (Wrapper wrapper : registered.values()) {
                wrapper.unregister(map);
            }
        } catch (Throwable error) {
            plugin.getLogger().log(Level.FINE, "Could not unregister commands", error);
        }
        registered.clear();
    }

    /** The command map entry for one of our commands. */
    private final class Wrapper extends Command implements PluginIdentifiableCommand {

        private final EssCommand handler;

        Wrapper(EssCommand handler, String description) {
            super(handler.name());
            this.handler = handler;
            setDescription(description == null ? "" : description);
            setUsage("/" + handler.name());
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!sender.hasPermission(handler.permission())) {
                plugin.messages().send(sender, "common.no-permission");
                return true;
            }
            String clean = label.toLowerCase(Locale.ROOT);
            int colon = clean.indexOf(':');
            if (colon >= 0) {
                clean = clean.substring(colon + 1);
            }
            try {
                handler.run(sender, clean, args);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "/" + clean + " failed", error);
                plugin.messages().send(sender, "common.error");
            }
            return true;
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (!sender.hasPermission(handler.permission())) {
                return List.of();
            }
            try {
                List<String> result = handler.complete(sender, alias, args);
                return result == null ? List.of() : result;
            } catch (Throwable error) {
                return List.of();
            }
        }

        @Override
        public @NotNull Plugin getPlugin() {
            return plugin;
        }
    }
}
