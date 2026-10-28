package dev.nightbeam.donutessentials.afk;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /afk [message]: go AFK, or come back. */
final class AfkCommand extends EssCommand {

    private final AfkModule module;

    AfkCommand(DonutEssentialsPlugin plugin, AfkModule module) {
        super(plugin, "afk");
        this.module = module;
    }

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (module.isAfk(player)) {
            module.set(player, false, false, null);
        } else {
            module.set(player, true, false, join(args, 0).trim());
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        return List.of();
    }
}
