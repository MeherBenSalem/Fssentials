package dev.nightbeam.donutessentials.vanish;

import dev.nightbeam.donutessentials.DonutEssentialsPlugin;
import dev.nightbeam.donutessentials.command.EssCommand;
import dev.nightbeam.donutessentials.module.Module;
import dev.nightbeam.donutessentials.scheduler.FoliaScheduler;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Vanish: /vanish hides staff from players, the tab list, join and quit messages and the server list. Vanished
 * players pick nothing up, mobs ignore them, and an action bar reminds them. The state survives a rejoin.
 */
public final class VanishModule implements Module {

    private final DonutEssentialsPlugin plugin;
    private VanishListener listener;
    private FoliaScheduler.Task reminder = FoliaScheduler.Task.NONE;
    private volatile Settings settings = Settings.from(null);

    /** The vanish section of config.yml. */
    record Settings(boolean fakeMessages, boolean preventPickup, boolean invulnerable, boolean noPhysical, boolean hideFromServerList,
                    boolean ignoreSleep, int reminderSeconds) {

        static Settings from(ConfigurationSection section) {
            if (section == null) {
                return new Settings(true, true, true, true, true, true, 3);
            }
            return new Settings(section.getBoolean("fake-messages", true), section.getBoolean("prevent-pickup", true),
                    section.getBoolean("invulnerable", true), section.getBoolean("no-physical-interact", true),
                    section.getBoolean("hide-from-server-list", true), section.getBoolean("ignore-sleep", true),
                    Math.max(0, section.getInt("reminder-seconds", 3)));
        }
    }

    public VanishModule(DonutEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "vanish";
    }

    @Override
    public void enable() {
        settings = Settings.from(plugin.getConfig().getConfigurationSection("vanish"));
        plugin.vanish().active(true);
        listener = new VanishListener(plugin, this);
        startReminder();
    }

    @Override
    public void reload() {
        settings = Settings.from(plugin.getConfig().getConfigurationSection("vanish"));
        startReminder();
    }

    @Override
    public void disable() {
        reminder.cancel();
        plugin.vanish().revealAll();
        plugin.vanish().active(false);
    }

    @Override
    public List<EssCommand> commands() {
        return List.of(new VanishCommand(plugin, this));
    }

    @Override
    public List<Listener> listeners() {
        return List.of(listener);
    }

    Settings settings() {
        return settings;
    }

    private void startReminder() {
        reminder.cancel();
        int seconds = settings.reminderSeconds();
        if (seconds <= 0) {
            reminder = FoliaScheduler.Task.NONE;
            return;
        }
        long period = seconds * 20L;
        reminder = plugin.scheduler().runGlobalTimer(() -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (plugin.vanish().isVanished(player)) {
                    plugin.scheduler().runForEntity(player, () -> plugin.messages().actionBar(player, "vanish.action-bar"));
                }
            }
        }, period, period);
    }

    /**
     * Vanishes or shows a player with everything that goes with it: the state, the staff notice, the fake join or
     * quit message and sleep. Call on the target's thread. Returns false when an event cancelled it.
     */
    boolean set(Player target, boolean vanished) {
        if (!plugin.vanish().set(target, vanished)) {
            return false;
        }
        if (settings.ignoreSleep()) {
            target.setSleepingIgnored(vanished);
        }
        String staffNotice = vanished ? "vanish.notify-vanished" : "vanish.notify-visible";
        String fake = vanished ? "vanish.fake-quit" : "vanish.fake-join";
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.equals(target)) {
                continue;
            }
            if (online.hasPermission(VanishService.SEE)) {
                plugin.messages().send(online, staffNotice, "player", target.getName());
            } else if (settings.fakeMessages()) {
                plugin.messages().sendPlain(online, fake, "player", target.getName());
            }
        }
        plugin.getLogger().info(target.getName() + (vanished ? " vanished." : " is visible again."));
        return true;
    }
}
