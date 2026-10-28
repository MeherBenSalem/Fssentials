package dev.nightbeam.donutessentials.command;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.importer.EssentialsImporter;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** /donutessentials (aliases /dess, /fssentials): help, reload, info and imports. */
public final class AdminCommand extends EssCommand {

    private static final List<String> SUBCOMMANDS = List.of("help", "reload", "info", "version", "import");

    public AdminCommand(DonutEssentialsPlugin plugin) {
        super(plugin, "donutessentials");
    }

    @Override
    public String permission() {
        return "donutessentials.admin";
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                long start = System.currentTimeMillis();
                plugin.reloadPlugin();
                send(sender, "admin.reloaded", "ms", System.currentTimeMillis() - start);
            }
            case "info", "version" -> info(sender);
            case "import" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("essentials")) {
                    send(sender, "admin.import-usage", "label", label);
                    return;
                }
                boolean confirm = args.length > 2 && args[2].equalsIgnoreCase("confirm");
                EssentialsImporter.start(plugin, sender, confirm, label);
            }
            default -> messages().sendLines(sender, "admin.help", "label", label, "version", plugin.version());
        }
    }

    private void info(CommandSender sender) {
        messages().sendLines(sender, "admin.info",
                "version", plugin.version(),
                "platform", plugin.platform() + " " + Bukkit.getMinecraftVersion(),
                "java", System.getProperty("java.version"),
                "storage", plugin.database().typeName(),
                "economy", plugin.economyName(),
                "modules", String.join(", ", plugin.modules().enabledIds()),
                "module_count", plugin.modules().enabledIds().size(),
                "commands", plugin.commands().count(),
                "players", plugin.players().knownPlayers(),
                "dialogs", plugin.input().dialogsAvailable() ? "yes" : "no",
                "placeholderapi", plugin.hook("PlaceholderAPI") ? "yes" : "no",
                "vault", plugin.hook("Vault") ? "yes" : "no");
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return filter(List.of("essentials"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("import")) {
            return filter(List.of("confirm"), args[2]);
        }
        return List.of();
    }
}
