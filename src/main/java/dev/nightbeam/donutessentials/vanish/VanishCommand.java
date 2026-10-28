package dev.nightbeam.donutessentials.vanish;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /vanish [player|list]. */
final class VanishCommand extends EssCommand {

    static final String OTHERS = "donutessentials.vanish.others";

    private final VanishModule module;

    VanishCommand(DonutEssentialsPlugin plugin, VanishModule module) {
        super(plugin, "vanish");
        this.module = module;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            list(sender);
            return;
        }
        if (args.length > 0) {
            if (!allowed(sender, OTHERS)) {
                return;
            }
            Player target = online(sender, args[0]);
            if (target != null) {
                toggle(sender, target);
            }
            return;
        }
        Player player = player(sender);
        if (player != null) {
            toggle(sender, player);
        }
    }

    private void toggle(CommandSender sender, Player target) {
        plugin.scheduler().forPlayer(target, () -> {
            boolean vanish = !plugin.vanish().isVanished(target);
            if (!module.set(target, vanish)) {
                return;
            }
            send(target, vanish ? "vanish.enabled" : "vanish.disabled");
            if (!(sender instanceof Player player && player.equals(target))) {
                send(sender, vanish ? "vanish.enabled-other" : "vanish.disabled-other", "player", target.getName());
            }
        });
    }

    private void list(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.vanish().isVanished(player)) {
                names.add(player.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) {
            send(sender, "vanish.list-empty");
        } else {
            send(sender, "vanish.list", "count", names.size(), "players", String.join(", ", names));
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("list"));
        if (sender.hasPermission(OTHERS)) {
            options.addAll(players(sender, args[0]));
        }
        return filter(options, args[0]);
    }
}
