package dev.nightbeam.donutessentials.command;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.config.Messages;
import dev.nightbeam.donutessentials.player.PlayerData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One command. The name is its key in commands.yml and its default label; the permission is
 * {@code donutessentials.command.<name>} unless overridden. On Folia, {@link #run} is called on the sender's thread
 * (the player's region, or the global region for the console): reach other players through the scheduler.
 */
public abstract class EssCommand {

    protected final DonutEssentialsPlugin plugin;
    private final String name;

    protected EssCommand(DonutEssentialsPlugin plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public final String name() {
        return name;
    }

    /** The permission needed to use the command at all. */
    public String permission() {
        return "donutessentials.command." + name;
    }

    public abstract void run(CommandSender sender, String label, String[] args);

    /** Tab completion; by default the first argument completes visible online players. */
    public List<String> complete(CommandSender sender, String label, String[] args) {
        return args.length == 1 ? players(sender, args[0]) : List.of();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────────────────────

    protected Messages messages() {
        return plugin.messages();
    }

    protected void send(CommandSender sender, String path, Object... keyValues) {
        plugin.messages().send(sender, path, keyValues);
    }

    /** Sends usage.<name> (with the label typed). */
    protected void usage(CommandSender sender, String label) {
        plugin.messages().send(sender, "usage." + name, "label", label);
    }

    /** The sender as a player, or null after telling the console this is for players. */
    protected Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "common.player-only");
        return null;
    }

    /** Checks a further permission (e.g. using a command on others); tells the sender when it is missing. */
    protected boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, "common.no-permission");
        return false;
    }

    /** An online player the sender can see, by exact name or unique prefix; tells the sender when there is none. */
    protected Player online(CommandSender sender, String name) {
        Player found = plugin.visibleTarget(sender, name);
        if (found == null) {
            send(sender, "common.player-not-found", "player", name);
        }
        return found;
    }

    /**
     * Looks a player up by name, online or not, then calls {@code found} on the sender's thread. Tells the sender when
     * nobody by that name ever joined.
     */
    protected void offline(CommandSender sender, String name, Consumer<PlayerData> found) {
        offline(sender, name, unknown -> null, found);
    }

    /**
     * {@link #offline(CommandSender, String, Consumer)} with a {@code fallback} for players without a record (it may
     * return null). It runs off the main thread.
     */
    protected void offline(CommandSender sender, String name, Function<String, PlayerData> fallback, Consumer<PlayerData> found) {
        plugin.players().find(name).whenComplete((result, error) -> {
            PlayerData data = error == null && result.isPresent() ? result.get() : fallback.apply(name);
            Runnable reply = () -> {
                if (data == null) {
                    send(sender, "common.player-never-joined", "player", name);
                } else {
                    found.accept(data);
                }
            };
            if (sender instanceof Player player) {
                plugin.scheduler().runForEntity(player, reply);
            } else {
                plugin.scheduler().runGlobal(reply);
            }
        });
    }

    /** Names of online players the sender can see, starting with {@code prefix}. */
    protected List<String> players(CommandSender sender, String prefix) {
        List<String> names = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower) && plugin.vanish().canSee(sender, player)) {
                names.add(player.getName());
            }
        }
        return names;
    }

    /** The entries of {@code options} starting with {@code prefix} (case-insensitive). */
    protected static List<String> filter(Collection<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    /** The arguments from {@code start} joined by spaces. */
    protected static String join(String[] args, int start) {
        if (start >= args.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, start, args.length));
    }
}
