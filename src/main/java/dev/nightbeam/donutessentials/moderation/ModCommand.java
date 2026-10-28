package dev.nightbeam.donutessentials.moderation;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared helpers for moderation commands: target lookups that reply on the sender's thread, and completions. */
abstract class ModCommand extends EssCommand {

    protected final ModerationModule module;

    ModCommand(DonutEssentialsPlugin plugin, ModerationModule module, String name) {
        super(plugin, name);
        this.module = module;
    }

    protected PunishmentService punishments() {
        return module.punishments();
    }

    /** Runs on the sender's thread (the player's region, or the global region for the console). */
    protected void onSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.scheduler().runForEntity(player, task);
        } else {
            plugin.scheduler().runGlobal(task);
        }
    }

    /** Looks a player up (online, known, or in the server's name cache), then calls back on the sender's thread. */
    protected void target(CommandSender sender, String name, Consumer<Target> found) {
        Target.find(plugin, name).whenComplete((result, error) -> onSender(sender, () -> {
            if (error != null || result == null || result.isEmpty()) {
                send(sender, "common.player-never-joined", "player", name);
            } else {
                found.accept(result.get());
            }
        }));
    }

    /** True (after telling the sender) when the target is online with the exempt permission and the sender is a player. */
    protected boolean exempt(CommandSender sender, Target target) {
        Player online = target.online();
        if (sender instanceof Player && online != null && online.hasPermission(PunishmentService.EXEMPT_PERMISSION)) {
            send(sender, "moderation.exempt", "player", target.name());
            return true;
        }
        return false;
    }

    /** {@code -s} and visible players for the first argument. */
    protected List<String> playersAndSilent(CommandSender sender, String prefix) {
        List<String> out = new ArrayList<>(filter(List.of("-s"), prefix));
        out.addAll(players(sender, prefix));
        return out;
    }

    /** Reason layouts as {@code @name}. */
    protected List<String> reasonLayouts(String prefix) {
        List<String> out = new ArrayList<>();
        for (String key : punishments().layouts().keySet()) {
            out.add("@" + key);
        }
        return filter(out, prefix);
    }

    /** Typical durations and {@code #layouts}. */
    protected List<String> durations(String prefix) {
        List<String> out = new ArrayList<>(List.of("30m", "1h", "6h", "1d", "3d", "7d", "30d"));
        for (String key : punishments().timeLayouts().keySet()) {
            out.add("#" + key);
        }
        return filter(out, prefix);
    }
}
