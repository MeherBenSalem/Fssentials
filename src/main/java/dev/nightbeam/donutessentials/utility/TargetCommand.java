package dev.nightbeam.donutessentials.utility;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A command that acts on the sender or, with {@code donutessentials.<command>.others}, on another online player
 * named at {@code targetIndex}. The action runs on the target's thread.
 */
abstract class TargetCommand extends EssCommand {

    private final int targetIndex;

    protected TargetCommand(DonutEssentialsPlugin plugin, String name, int targetIndex) {
        super(plugin, name);
        this.targetIndex = targetIndex;
    }

    /** The permission for using the command on others. */
    protected String othersPermission() {
        return "donutessentials." + name() + ".others";
    }

    /** Runs on the target's thread. {@code self} is true when the sender is the target. */
    protected abstract void apply(CommandSender sender, Player target, String[] args, boolean self);

    @Override
    public void run(CommandSender sender, String label, String[] args) {
        Player target = target(sender, args, targetIndex);
        if (target == null) {
            return;
        }
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        plugin.scheduler().forPlayer(target, () -> apply(sender, target, args, self));
    }

    /** The player at {@code index} (with the others permission), else the sender; null after telling the sender why. */
    protected Player target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player target = online(sender, args[index]);
            if (target == null) {
                return null;
            }
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            if (!self && !allowed(sender, othersPermission())) {
                return null;
            }
            return target;
        }
        return player(sender);
    }

    @Override
    public List<String> complete(CommandSender sender, String label, String[] args) {
        if (args.length == targetIndex + 1 && sender.hasPermission(othersPermission())) {
            return players(sender, args[targetIndex]);
        }
        return List.of();
    }

    /** Tells the target {@code utility.<key>} and, when someone else did it, the sender {@code utility.<key>-other}. */
    protected void tell(CommandSender sender, Player target, boolean self, String key, Object... keyValues) {
        Object[] values = withPlayer(target, keyValues);
        plugin.messages().send(target, "utility." + key, values);
        if (!self) {
            plugin.messages().send(sender, "utility." + key + "-other", values);
        }
    }

    private static Object[] withPlayer(Player target, Object[] keyValues) {
        Object[] out = new Object[keyValues.length + 2];
        out[0] = "player";
        out[1] = target.getName();
        System.arraycopy(keyValues, 0, out, 2, keyValues.length);
        return out;
    }
}
